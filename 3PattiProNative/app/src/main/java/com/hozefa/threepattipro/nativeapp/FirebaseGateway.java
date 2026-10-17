package com.hozefa.threepattipro.nativeapp;

import android.content.Context;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.android.gms.tasks.Tasks;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.MediaType;
import okhttp3.Response;
import java.util.concurrent.TimeUnit;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class FirebaseGateway {
    public interface Listener {
        void dataUpdated(List<Map<String,Object>> players, List<Map<String,Object>> rounds, List<Map<String,Object>> sessions);
        void authChanged(boolean loggedIn);
        void notice(String message);
        void busy(boolean active);
        void syncState(boolean online);
    }
    public static final class Stamp {
        public final String value;
        public Stamp(String v) { value = v; }
    }
    private final String project = BuildConfig.USE_EMULATORS?"demo-three-patti":"patti-pro-a61d8";
    private final Context context;
    private final Listener listener;
    private final String apiKey;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService fetchers = Executors.newFixedThreadPool(3);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final OkHttpClient http = new OkHttpClient.Builder().callTimeout(25, TimeUnit.SECONDS).connectTimeout(14, TimeUnit.SECONDS).readTimeout(14, TimeUnit.SECONDS).build();
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final List<ListenerRegistration> subscriptions=new ArrayList<>();
    private volatile Map<String,String> versions=Collections.emptyMap();
    private volatile Map<String,JSONObject> rawDocuments=Collections.emptyMap();
    private static final class CollectionSnapshot {
        final List<Map<String,Object>> rows=new ArrayList<>();
        final Map<String,JSONObject> documents=new HashMap<>();
        final Map<String,String> versions=new HashMap<>();
    }
    private final java.util.concurrent.atomic.AtomicLong liveRevision=new java.util.concurrent.atomic.AtomicLong();
    private volatile long snapshotAt=0;
    private volatile boolean signingOut=false;
    private final Runnable liveRefresh=()->{if(this.running)executor.execute(this::load);};
    private volatile String idToken;
    private volatile boolean running;
    private volatile boolean freshSnapshot=false;
    private boolean loading;
    private final AtomicBoolean writeInFlight=new AtomicBoolean(false);
    private boolean reloadRequested=false;
    private final Runnable refreshLoop=() -> executor.execute(this::load);
    private final String dbRoot;
    private final String documentRoot = "projects/" + project + "/databases/(default)/documents";

    public FirebaseGateway(Context c, Listener l, String key) {
        context = c.getApplicationContext();
        listener = l;
        apiKey = BuildConfig.USE_EMULATORS?"demo-key":key;
        FirebaseOptions options=new FirebaseOptions.Builder().setProjectId(project)
                .setApiKey(apiKey).setApplicationId("1:570573461138:web:4414d0ce87282b91b2f84b").build();
        FirebaseApp firebase;boolean initialized=false;
        try { firebase=FirebaseApp.getInstance("three-patti-native"); }
        catch(IllegalStateException missing){firebase=FirebaseApp.initializeApp(context,options,"three-patti-native");initialized=true;}
        auth=FirebaseAuth.getInstance(firebase);
        firestore=FirebaseFirestore.getInstance(firebase);
        if(BuildConfig.USE_EMULATORS && initialized){
            auth.useEmulator("10.0.2.2",9099);
            firestore.useEmulator("10.0.2.2",8080);
        }
        // Legacy REST refresh tokens are discarded; Firebase Auth owns session persistence.
        context.getSharedPreferences("patti_native_auth",Context.MODE_PRIVATE).edit().clear().apply();
        dbRoot = (BuildConfig.USE_EMULATORS?"http://10.0.2.2:8080":"https://firestore.googleapis.com")+"/v1/projects/" + project + "/databases/(default)/documents";
    }
    public void start() {
        running = true;
        // A stored refresh token is not proof of a valid admin session.
        ui.post(() -> listener.authChanged(false));
        executor.execute(() -> { readCachedSnapshot(); load(); });
        for(String collection:Arrays.asList("players","rounds","sessions")){
            subscriptions.add(firestore.collection(collection).addSnapshotListener(MetadataChanges.INCLUDE,(snapshot,error)->{
                if(!running)return;
                liveRevision.incrementAndGet();freshSnapshot=false;listener.syncState(false);
                ui.removeCallbacks(liveRefresh);
                ui.postDelayed(liveRefresh,200);
            }));
        }
    }
    public void stop() { running=false;
        for(ListenerRegistration registration:subscriptions)registration.remove();
        subscriptions.clear();ui.removeCallbacksAndMessages(null);
        http.dispatcher().cancelAll();executor.shutdownNow();fetchers.shutdownNow();
    }
    private void notify(String msg) { ui.post(() -> { if (running) listener.notice(msg); }); }
    private void setBusy(boolean b) { ui.post(() -> { if (running) listener.busy(b); }); }
    private String encoded(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
    private String getError(JSONObject j) {
        JSONObject e = j.optJSONObject("error");
        if (e == null) return "Connection failed";
        return e.optString("message", "Connection failed");
    }
    private JSONObject network(String method, String url, String contentType, String body, String bearer) throws Exception {
        Request.Builder req = new Request.Builder().url(url).header("Accept", "application/json");
        if(bearer!=null)req.header("Authorization", "Bearer " + bearer);
        if(body!=null) {
            RequestBody rb=RequestBody.create(body, MediaType.parse(contentType));
            req.method(method,rb);
        } else if("GET".equals(method)) req.get();
        else req.method(method,null);
        try (Response response=http.newCall(req.build()).execute()) {
            String content=response.body()==null?"":response.body().string();
            JSONObject obj=content.isEmpty()?new JSONObject():new JSONObject(content);
            if(!response.isSuccessful())throw new IOException(getError(obj)+" (HTTP "+response.code()+")");
            return obj;
        }
    }
    private JSONObject jsonCall(String method,String endpoint,JSONObject body,String bearer) throws Exception {
        return network(method,endpoint,"application/json; charset=utf-8",body==null?null:body.toString(),bearer);
    }
    private void ensureIdToken() throws Exception {
        if(signingOut || auth.getCurrentUser()==null){idToken=null;return;}
        idToken=Tasks.await(auth.getCurrentUser().getIdToken(false),20,TimeUnit.SECONDS).getToken();
        ui.post(()->{if(running)listener.authChanged(loggedIn());});
    }
    public boolean loggedIn(){return !signingOut && auth.getCurrentUser()!=null && idToken!=null;}
    public void signIn(String email,String password){
        setBusy(true);
        executor.execute(()->{
            try{
                Tasks.await(auth.signInWithEmailAndPassword(email,password),25,TimeUnit.SECONDS);
                signingOut=false;ensureIdToken();load();notify("Admin login successful");
            }catch(Exception error){notify("Login failed. Check credentials and connection.");}
            finally{setBusy(false);}
        });
    }
    public void signOut(){
        // Block new mutations immediately, before the serialized work queue runs.
        signingOut=true;freshSnapshot=false;
        ui.post(()->{if(running)listener.authChanged(false);});
        executor.execute(()->{auth.signOut();idToken=null;signingOut=false;load();notify("Logged out");});
    }
    public void refresh() {
        ui.removeCallbacks(refreshLoop);
        executor.execute(this::load);
    }
    private void scheduleNext() {
        ui.removeCallbacks(refreshLoop);
        ui.postDelayed(refreshLoop, freshSnapshot?60000:12000);
    }
    private void load() {
        if (!running) return;
        if (loading) { reloadRequested=true; return; }
        loading=true;
        try {
            if (auth.getCurrentUser() != null) {
                try { ensureIdToken(); } catch (Exception ex) {
                    idToken=null;
                    ui.post(()->{if(running) listener.authChanged(false);});
                }
            }
            long revision=liveRevision.get();
            // Fetch independent collections in parallel rather than blocking the
            // admin action queue for the sum of three HTTP latencies.
            Future<CollectionSnapshot> playerFetch=fetchers.submit(()->fetchCollection("players"));
            Future<CollectionSnapshot> roundFetch=fetchers.submit(()->fetchCollection("rounds"));
            Future<CollectionSnapshot> sessionFetch=fetchers.submit(()->fetchCollection("sessions"));
            CollectionSnapshot ps=playerFetch.get(),rs=roundFetch.get(),ss=sessionFetch.get();
            List<Map<String,Object>> p=ps.rows,r=rs.rows,s=ss.rows;
            Map<String,String> nextVersions=new HashMap<>();Map<String,JSONObject> nextDocuments=new HashMap<>();
            for(CollectionSnapshot collection:Arrays.asList(ps,rs,ss)){nextVersions.putAll(collection.versions);nextDocuments.putAll(collection.documents);}
            versions=nextVersions;rawDocuments=nextDocuments;
            snapshotAt=System.currentTimeMillis();
            freshSnapshot=revision==liveRevision.get();
            final boolean coherent=freshSnapshot;
            saveCachedSnapshot(p,r,s);
            ui.post(() -> { if(running) { listener.syncState(coherent); listener.dataUpdated(p,r,s); } });
        } catch (Exception ex) {
            freshSnapshot=false;
            ui.post(()->{if(running)listener.syncState(false);});
            notify("Sync issue: " + ex.getMessage());
        } finally {
            loading=false;
            if (running && reloadRequested){reloadRequested=false;ui.removeCallbacks(refreshLoop);executor.execute(this::load);}
            else if(running) scheduleNext();
        }
    }
    // A private read-only offline snapshot. It NEVER queues or replays mutations.
    private File snapshotFile(){return new File(context.getFilesDir(),"3patti_snapshot.json");}
    private void saveCachedSnapshot(List<Map<String,Object>> players,
                                    List<Map<String,Object>> rounds,
                                    List<Map<String,Object>> sessions) {
        File temp=new File(context.getFilesDir(),"3patti_snapshot.tmp");
        try{
            JSONObject all=new JSONObject();
            all.put("players",new JSONArray(players));
            all.put("rounds",new JSONArray(rounds));
            all.put("sessions",new JSONArray(sessions));
            all.put("savedAt",System.currentTimeMillis());
            byte[] bytes=all.toString().getBytes(StandardCharsets.UTF_8);
            if(bytes.length>8_000_000)return; // keep app startup fast on very large histories
            try(FileOutputStream stream=new FileOutputStream(temp)){
                stream.write(bytes);
                stream.getFD().sync();
            }
            Files.move(temp.toPath(),snapshotFile().toPath(),StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception ex){
            // Cache failure never prevents a successful live Firebase load.
            temp.delete();
        }
    }
    private void readCachedSnapshot(){
        File file=snapshotFile();
        if(!file.isFile()||file.length()>8_000_000||file.length()==0)return;
        try{
            byte[] raw=Files.readAllBytes(file.toPath());
            JSONObject snapshot=new JSONObject(new String(raw,StandardCharsets.UTF_8));
            List<Map<String,Object>> p=decodeCachedCollection(snapshot.getJSONArray("players"));
            List<Map<String,Object>> r=decodeCachedCollection(snapshot.getJSONArray("rounds"));
            List<Map<String,Object>> ss=decodeCachedCollection(snapshot.getJSONArray("sessions"));
            ui.post(()->{
                if(running){
                    listener.syncState(false);
                    listener.dataUpdated(p,r,ss);
                    listener.notice("Showing saved scores. Checking live updates…");
                }
            });
        }catch(Exception ex){
            // A corrupt or incompatible snapshot is ignored, not interpreted as empty data.
            file.delete();
        }
    }
    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> decodeCachedCollection(JSONArray json) throws Exception{
        List<Map<String,Object>> out=new ArrayList<>();
        for(int i=0;i<json.length();i++){
            Object row=unwrapCached(json.get(i));
            if(!(row instanceof Map))throw new IOException("Invalid cached collection");
            out.add((Map<String,Object>)row);
        }
        return out;
    }
    private Object unwrapCached(Object value) throws Exception{
        if(value==null||value==JSONObject.NULL)return null;
        if(value instanceof JSONArray){
            JSONArray arr=(JSONArray)value;
            List<Object> decoded=new ArrayList<>();
            for(int i=0;i<arr.length();i++)decoded.add(unwrapCached(arr.get(i)));
            return decoded;
        }
        if(value instanceof JSONObject){
            JSONObject obj=(JSONObject)value;
            Map<String,Object> decoded=new LinkedHashMap<>();
            Iterator<String> keys=obj.keys();
            while(keys.hasNext()){
                String key=keys.next();
                decoded.put(key,unwrapCached(obj.get(key)));
            }
            return decoded;
        }
        return value;
    }
    private CollectionSnapshot fetchCollection(String name) throws Exception {
        CollectionSnapshot result=new CollectionSnapshot();
        String next=null;
        for(int page=0;page<20;page++) {
            String url=dbRoot + "/" + name + "?pageSize=500"
                    + (next==null?"":"&pageToken="+encoded(next));
            JSONObject data=jsonCall("GET",url,null,idToken);
            JSONArray docs=data.optJSONArray("documents");
            if(docs!=null) for(int i=0;i<docs.length();i++) {
                JSONObject doc=docs.getJSONObject(i);
                Map<String,Object> map=decodeFields(doc.optJSONObject("fields"));
                String full=doc.optString("name","");
                map.put("id",full.substring(full.lastIndexOf('/')+1));
                map.put("_updateTime",doc.optString("updateTime"));
                result.documents.put(name+"/"+map.get("id"),doc);
                result.versions.put(name+"/"+map.get("id"),doc.optString("updateTime"));
                result.rows.add(map);
            }
            next=data.optString("nextPageToken","");
            if(next.isEmpty()) break;
        }
        if(next!=null&&!next.isEmpty())throw new IOException("Firestore history exceeds page limit. Refusing to show incomplete records.");
        return result;
    }
    private Map<String,Object> decodeFields(JSONObject fields) throws Exception {
        Map<String,Object> result=new HashMap<>();
        if(fields==null) return result;
        Iterator<String> keys=fields.keys();
        while(keys.hasNext()) {
            String k=keys.next();
            result.put(k,decodeValue(fields.getJSONObject(k)));
        }
        return result;
    }
    private Object decodeValue(JSONObject field) throws Exception {
        if(field.has("nullValue")) return null;
        if(field.has("stringValue")) return field.optString("stringValue");
        if(field.has("referenceValue")) return field.optString("referenceValue");
        if(field.has("timestampValue")) return field.optString("timestampValue");
        if(field.has("integerValue")) return field.optLong("integerValue");
        if(field.has("doubleValue")) return field.optDouble("doubleValue");
        if(field.has("booleanValue")) return field.optBoolean("booleanValue");
        if(field.has("mapValue")) return decodeFields(field.getJSONObject("mapValue").optJSONObject("fields"));
        if(field.has("arrayValue")) {
            List<Object> data=new ArrayList<>();
            JSONArray values=field.getJSONObject("arrayValue").optJSONArray("values");
            if(values!=null) for(int i=0;i<values.length();i++) data.add(decodeValue(values.getJSONObject(i)));
            return data;
        }
        return null;
    }
    private JSONObject encodeFields(Map<String,Object> data) throws Exception {
        JSONObject fields=new JSONObject();
        for(Map.Entry<String,Object> e:data.entrySet()) fields.put(e.getKey(),encodeValue(e.getValue()));
        return fields;
    }
    private JSONObject encodeValue(Object value) throws Exception {
        JSONObject out=new JSONObject();
        if(value==null) out.put("nullValue",JSONObject.NULL);
        else if(value instanceof Stamp) out.put("timestampValue",((Stamp)value).value);
        else if(value instanceof Boolean) out.put("booleanValue",value);
        else if(value instanceof Float || value instanceof Double) out.put("doubleValue",value);
        else if(value instanceof Number) out.put("integerValue",String.valueOf(value));
        else if(value instanceof Map) {
            @SuppressWarnings("unchecked") Map<String,Object> m=(Map<String,Object>)value;
            JSONObject embedded=new JSONObject();
            embedded.put("fields",encodeFields(m));
            out.put("mapValue",embedded);
        } else if(value instanceof List) {
            JSONArray vals=new JSONArray();
            for(Object o:(List<?>)value) vals.put(encodeValue(o));
            JSONObject arr=new JSONObject();
            arr.put("values",vals);
            out.put("arrayValue",arr);
        } else out.put("stringValue",String.valueOf(value));
        return out;
    }
    /**
     * Atomic rename: changing the player and the historical embedded names
     * either ALL succeeds or NONE succeeds. Never partially rename Firebase.
     */
    public void renamePlayer(String playerId,String newName,List<Map<String,Object>> rounds,String playerVersion) {
        final Map<String,String> expected=new HashMap<>(versions);
        expected.put("players/"+playerId,playerVersion);
        for(Map<String,Object> round:rounds)expected.put("rounds/"+round.get("id"),String.valueOf(round.get("_updateTime")));
        write("Player renamed",token->{
            JSONArray operations=new JSONArray();
            JSONObject playerUpdate=new JSONObject();
            playerUpdate.put("name",documentRoot+"/players/"+playerId);
            playerUpdate.put("fields",encodeFields(map("name",newName)));
            operations.put(new JSONObject()
                    .put("update",playerUpdate)
                    .put("updateMask",new JSONObject().put("fieldPaths",new JSONArray().put("name")))
                    .put("currentDocument",precondition(expected,"players/"+playerId)));
            for(Map<String,Object> round:rounds){
                String rid=String.valueOf(round.get("id"));
                Map<String,Object> edits=new LinkedHashMap<>();
                if(playerId.equals(round.get("hukumId")))edits.put("hukumName",newName);
                List<?> partners=round.get("partnerIds") instanceof List?
                        (List<?>)round.get("partnerIds"):Collections.emptyList();
                if(partners.contains(playerId)){
                    List<String> names=new ArrayList<>();
                    for(Object item:partners)names.add(playerId.equals(item)?newName:
                            findRoundName(round,"partnerIds","partnerNames",item));
                    edits.put("partnerNames",names);
                }
                List<?> opponents=round.get("nonPartnerIds") instanceof List?
                        (List<?>)round.get("nonPartnerIds"):Collections.emptyList();
                if(opponents.contains(playerId)){
                    List<String> names=new ArrayList<>();
                    for(Object item:opponents)names.add(playerId.equals(item)?newName:
                            findRoundName(round,"nonPartnerIds","nonPartnerNames",item));
                    edits.put("nonPartnerNames",names);
                }
                if(edits.isEmpty())continue;
                if(operations.length()>=500)
                    throw new IOException("Too many rounds for an atomic rename. No records changed.");
                JSONObject update=new JSONObject();
                update.put("name",documentRoot+"/rounds/"+rid);
                update.put("fields",encodeFields(edits));
                JSONArray fields=new JSONArray();
                for(String field:edits.keySet())fields.put(field);
                operations.put(new JSONObject()
                        .put("update",update)
                        .put("updateMask",new JSONObject().put("fieldPaths",fields))
                        .put("currentDocument",precondition(expected,"rounds/"+rid)));
            }
            jsonCall("POST",dbRoot+":commit",new JSONObject().put("writes",operations),token);
        });
    }
    private String findRoundName(Map<String,Object> round,String idsKey,String namesKey,Object target){
        Object ids=round.get(idsKey),names=round.get(namesKey);
        if(ids instanceof List && names instanceof List){
            int i=((List<?>)ids).indexOf(target);
            if(i>=0 && i<((List<?>)names).size())return String.valueOf(((List<?>)names).get(i));
        }
        return "Unknown";
    }
    private JSONObject precondition(Map<String,String> expected,String path) throws Exception{
        String version=expected.get(path);
        if(version==null||version.isEmpty())throw new IOException("Refresh before changing this record");
        return new JSONObject().put("updateTime",version);
    }
    public void deleteDocument(String collection,String id,String success){deleteDocument(collection,id,success,versions.get(collection+"/"+id));}
    public void deleteDocument(String collection,String id,String success,String expectedVersion){
        final Map<String,String> expected=new HashMap<>(versions);expected.put(collection+"/"+id,expectedVersion);
        write(success,token->{
            String url=dbRoot+"/"+collection+"/"+encoded(id)+"?currentDocument.updateTime="+encoded(precondition(expected,collection+"/"+id).getString("updateTime"));
            preserveBackup();
            jsonCall("DELETE",url,null,token);
        });
    }
    public interface Action { void run(String token) throws Exception; }
    public void write(String success,Action action) {
        write(success,action,null,null);
    }
    public void write(String success,Action action,Runnable onSuccess,Runnable onFailure) {
        if(!loggedIn()){
            notify("Please log in before saving changes");
            onUi(onFailure);
            return;
        }
        if(!freshSnapshot){
            notify("Waiting for live Firebase sync. Offline data is read-only.");
            onUi(onFailure);
            return;
        }
        if(!writeInFlight.compareAndSet(false,true)){
            notify("Another save is already in progress");
            onUi(onFailure);
            return;
        }
        setBusy(true);
        executor.execute(() -> {
            try {
                ensureIdToken();
                if(idToken==null || signingOut)throw new IOException("Login expired");
                if(System.currentTimeMillis()-snapshotAt>60000)load();
                if(!freshSnapshot)throw new IOException("Data changed. Refresh and retry.");
                action.run(idToken);
                notify(success);
                onUi(onSuccess);
                load();
            }catch(Exception error){
                freshSnapshot=false;
                ui.post(()->{if(running)listener.syncState(false);});
                notify("Not saved: "+error.getMessage());
                if(running)ui.postDelayed(liveRefresh,200);
                onUi(onFailure);
            }finally{
                writeInFlight.set(false);
                setBusy(false);
            }
        });
    }
    private void onUi(Runnable callback){
        if(callback!=null)ui.post(()->{if(running)callback.run();});
    }
    public interface BackupCallback{void ready(byte[] bytes);}
    public void exportBackup(BackupCallback callback){
        setBusy(true);
        executor.execute(()->{
            try{
                load();if(!freshSnapshot)throw new IOException("A live snapshot is required for backup");
                JSONObject backup=new JSONObject().put("format","3patti-firestore-v1").put("project",project)
                    .put("exportedAt",Instant.now().toString()).put("documents",new JSONArray(rawDocuments.values()));
                byte[] bytes=backup.toString(2).getBytes(StandardCharsets.UTF_8);
                ui.post(()->{if(running)callback.ready(bytes);});
            }catch(Exception error){notify("Backup failed: "+error.getMessage());}
            finally{setBusy(false);}
        });
    }
    public void exportRecoveryBackup(BackupCallback callback){
        executor.execute(()->{
            try{
                File[] files=new File(context.getFilesDir(),"recovery").listFiles((dir,name)->name.endsWith(".json"));
                if(files==null||files.length==0)throw new IOException("No recovery backup on this device");
                Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());
                byte[] bytes=Files.readAllBytes(files[0].toPath());ui.post(()->{if(running)callback.ready(bytes);});
            }catch(Exception error){notify("Recovery export: "+error.getMessage());}
        });
    }
    private String fingerprint(){return new TreeMap<>(versions).toString();}
    public String backupFingerprint(){return fingerprint();}
    private void preserveBackup()throws Exception{
        JSONObject backup=new JSONObject().put("format","3patti-firestore-v1").put("project",project)
            .put("exportedAt",Instant.now().toString()).put("documents",new JSONArray(rawDocuments.values()));
        File folder=new File(context.getFilesDir(),"recovery");if(!folder.exists()&&!folder.mkdirs())throw new IOException("Cannot create recovery backup");
        File target=new File(folder,"backup-"+System.currentTimeMillis()+".json");
        try(FileOutputStream out=new FileOutputStream(target)){out.write(backup.toString().getBytes(StandardCharsets.UTF_8));out.getFD().sync();}
    }
    public void clearHistory(String exportedFingerprint){
        final Map<String,String> expected=new HashMap<>(versions);
        write("All rounds and sessions cleared",token->{
            if(!fingerprint().equals(exportedFingerprint))throw new IOException("Table changed since backup. Export a new backup first.");
            JSONArray writes=new JSONArray();
            for(String path:expected.keySet())if(path.startsWith("rounds/")||path.startsWith("sessions/")){
                writes.put(new JSONObject().put("delete",documentRoot+"/"+path).put("currentDocument",precondition(expected,path)));
            }
            if(writes.length()>500)throw new IOException("Atomic clear supports up to 500 rounds and sessions. Nothing deleted.");
            preserveBackup();
            if(writes.length()>0)jsonCall("POST",dbRoot+":commit",new JSONObject().put("writes",writes),token);
        });
    }
    public void restoreBackup(byte[] bytes){restoreBackup(bytes,false);}
    public void restoreBackup(byte[] bytes,boolean legacy){
        write("Backup restored; existing records preserved",token->{
            List<JSONObject> documents=BackupArchive.validate(bytes,project);JSONArray writes=new JSONArray();
            // Load live state first; conflicts fail before any mutation.
            load();if(!freshSnapshot)throw new IOException("Live data required");
            if(legacy)for(String path:rawDocuments.keySet())if(path.startsWith("players/"))throw new IOException("Legacy import requires an empty player collection");
            for(JSONObject doc:documents){
                String path=BackupArchive.path(doc,project);
                if(rawDocuments.containsKey(path)){
                    if(!equivalent(decodeFields(rawDocuments.get(path).getJSONObject("fields")),decodeFields(doc.getJSONObject("fields"))))
                        throw new IOException("Backup conflicts with existing "+path+". No records changed.");
                    continue;
                }
                writes.put(new JSONObject().put("update",new JSONObject().put("name",documentRoot+"/"+path).put("fields",doc.getJSONObject("fields")))
                    .put("currentDocument",new JSONObject().put("exists",false)));
            }
            preserveBackup();
            if(writes.length()>0)jsonCall("POST",dbRoot+":commit",new JSONObject().put("writes",writes),token);
        });
    }
    public static Map<String,Object> map(Object... v) {
        Map<String,Object> m=new LinkedHashMap<>();
        for(int i=0;i+1<v.length;i+=2) m.put(String.valueOf(v[i]),v[i+1]);
        return m;
    }
    public static Stamp now() { return new Stamp(Instant.now().toString()); }
    private JSONObject updateWrite(String collection,String id,Map<String,Object> fields,JSONObject condition,boolean masked)throws Exception{
        Map<String,Object> values=new LinkedHashMap<>(fields);JSONArray transforms=new JSONArray();
        for(Map.Entry<String,Object> entry:fields.entrySet())if(entry.getValue() instanceof Stamp){
            values.remove(entry.getKey());transforms.put(new JSONObject().put("fieldPath",entry.getKey()).put("setToServerValue","REQUEST_TIME"));
        }
        JSONObject write=new JSONObject().put("update",new JSONObject().put("name",documentRoot+"/"+collection+"/"+id).put("fields",encodeFields(values))).put("currentDocument",condition);
        if(masked)write.put("updateMask",new JSONObject().put("fieldPaths",new JSONArray(fields.keySet())));
        if(transforms.length()>0)write.put("updateTransforms",transforms);
        return write;
    }
    public void addDocument(String collection, Map<String,Object> fields,String success) {
        addDocument(collection,fields,success,null);
    }
    public void addDocument(String collection,Map<String,Object> fields,String success,Runnable onSaved){
        addDocument(collection,fields,success,onSaved,null);
    }
    public void addDocument(String collection,Map<String,Object> fields,String success,
                            Runnable onSaved,Runnable onFailed) {
        final String documentId=UUID.randomUUID().toString();
        write(success,token->{
            JSONObject update=updateWrite(collection,documentId,fields,new JSONObject().put("exists",false),false);
            jsonCall("POST",dbRoot+":commit",new JSONObject().put("writes",new JSONArray().put(update)),token);
        },onSaved,onFailed);
    }
    /**
     * Idempotent creation for Hukum rounds. The same stable document ID is
     * reused across an ambiguous network failure and a user retry.
     * No writes are generated by reads or unit tests.
     */
    public void addRoundWithId(String id,Map<String,Object> fields,
                               Runnable onSaved,Runnable onFailed) {
        write("Round saved successfully",token->{
            String path=dbRoot+"/rounds/"+encoded(id);
            JSONObject update=updateWrite("rounds",id,fields,new JSONObject().put("exists",false),false);
            try {
                jsonCall("POST",dbRoot+":commit",new JSONObject().put("writes",new JSONArray().put(update)),token);
            }catch(Exception failure){
                // A timeout might occur AFTER the backend committed the write.
                // Verify our unique UUID before offering a retry, to avoid
                // inserting the same round twice into the source of truth.
                try{
                    JSONObject existing=jsonCall("GET",path,null,token);
                    if(!sameRound(decodeFields(existing.optJSONObject("fields")),fields))
                        throw new IOException("Round ID already contains different data. Refresh before retrying.");
                }catch(Exception verificationFailure){
                    throw failure;
                }
            }
        },onSaved,onFailed);
    }
    static boolean sameRound(Map<String,Object> a,Map<String,Object> b){
        for(String key:Arrays.asList("type","bid","hukumId","partnerIds","nonPartnerIds","playerIds","result","scoreChanges")){
            if(!equivalent(a.get(key),b.get(key)))return false;
        }
        return true;
    }
    private static boolean equivalent(Object a,Object b){
        if(a instanceof Number && b instanceof Number)return Double.compare(((Number)a).doubleValue(),((Number)b).doubleValue())==0;
        if(a instanceof Map && b instanceof Map){
            Map<?,?> left=(Map<?,?>)a,right=(Map<?,?>)b;
            if(!left.keySet().equals(right.keySet()))return false;
            for(Object key:left.keySet())if(!equivalent(left.get(key),right.get(key)))return false;
            return true;
        }
        return Objects.equals(a,b);
    }
    public void patchDocument(String collection,String id,Map<String,Object> fields,String success){patchDocument(collection,id,fields,success,null);}
    public void patchDocument(String collection,String id,Map<String,Object> fields,String success,Runnable onSaved){patchDocument(collection,id,fields,success,versions.get(collection+"/"+id),onSaved);}
    public void patchDocument(String collection,String id,Map<String,Object> fields,String success,String expectedVersion,Runnable onSaved) {
        final Map<String,String> expected=new HashMap<>(versions);expected.put(collection+"/"+id,expectedVersion);
        write(success,token -> {
            JSONObject update=updateWrite(collection,id,fields,precondition(expected,collection+"/"+id),true);
            jsonCall("POST",dbRoot+":commit",new JSONObject().put("writes",new JSONArray().put(update)),token);
        },onSaved,null);
    }
}
