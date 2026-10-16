package com.hozefa.threepattipro.nativeapp;

import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.concurrent.atomic.AtomicBoolean;

public final class FirebaseGateway {
    public interface Listener {
        void dataUpdated(List<Map<String,Object>> players, List<Map<String,Object>> rounds, List<Map<String,Object>> sessions);
        void authChanged(boolean loggedIn);
        void notice(String message);
        void busy(boolean active);
    }
    public static final class Stamp {
        public final String value;
        public Stamp(String v) { value = v; }
    }
    private final String project = "patti-pro-a61d8";
    private final Context context;
    private final Listener listener;
    private final String apiKey;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final OkHttpClient http = new OkHttpClient.Builder().connectTimeout(14, TimeUnit.SECONDS).readTimeout(14, TimeUnit.SECONDS).build();
    private final SharedPreferences prefs;
    private String refreshToken, idToken;
    private long expireAt;
    private boolean running;
    private boolean loading;
    private final AtomicBoolean writeInFlight=new AtomicBoolean(false);
    private boolean reloadRequested=false;
    private final Runnable refreshLoop=() -> executor.execute(this::load);
    private final String dbRoot;

    public FirebaseGateway(Context c, Listener l, String key) {
        context = c.getApplicationContext();
        listener = l;
        apiKey = key;
        prefs = context.getSharedPreferences("patti_native_auth", Context.MODE_PRIVATE);
        refreshToken = prefs.getString("refresh_token", null);
        dbRoot = "https://firestore.googleapis.com/v1/projects/" + project + "/databases/(default)/documents";
    }
    public void start() {
        running = true;
        // A stored refresh token is not proof of a valid admin session.
        ui.post(() -> listener.authChanged(false));
        executor.execute(() -> {
            if (refreshToken != null) {
                try { ensureIdToken(); }
                catch (Exception ex) {
                    notify("Saved login could not be verified. Log in again if needed.");
                }
            }
            load();
        });
    }
    public void stop() { running = false; ui.removeCallbacksAndMessages(null); executor.shutdownNow(); }
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
        if (refreshToken == null) return;
        if (idToken != null && System.currentTimeMillis() < expireAt) return;
        String fields = "grant_type=refresh_token&refresh_token=" + encoded(refreshToken);
        JSONObject res = network("POST","https://securetoken.googleapis.com/v1/token?key="+encoded(apiKey),
                "application/x-www-form-urlencoded",fields,null);
        idToken=res.getString("id_token");
        refreshToken=res.getString("refresh_token");
        expireAt=System.currentTimeMillis() + Math.max(120, res.optLong("expires_in",3600)-90)*1000L;
        prefs.edit().putString("refresh_token",refreshToken).apply();
        ui.post(() -> { if (running) listener.authChanged(true); });
    }
    public boolean loggedIn() { return refreshToken != null && idToken != null; }
    public void signIn(String email,String password) {
        setBusy(true);
        executor.execute(() -> {
            try {
                JSONObject req=new JSONObject();
                req.put("email",email);
                req.put("password",password);
                req.put("returnSecureToken",true);
                JSONObject res=jsonCall("POST","https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key="+encoded(apiKey),req,null);
                refreshToken=res.getString("refreshToken");
                idToken=res.getString("idToken");
                expireAt=System.currentTimeMillis() + Math.max(120,res.optLong("expiresIn",3600)-90)*1000L;
                prefs.edit().putString("refresh_token",refreshToken).apply();
                ui.post(() -> { if(running) listener.authChanged(true); });
                notify("Admin login successful");
                load();
            } catch (Exception ex) { notify("Login failed: " + ex.getMessage()); }
            finally { setBusy(false); }
        });
    }
    public void signOut() {
        executor.execute(() -> {
            refreshToken=null;
            idToken=null;
            expireAt=0;
            prefs.edit().remove("refresh_token").apply();
            ui.post(() -> { if(running) listener.authChanged(false); });
            notify("Logged out");
            load();
        });
    }
    public void refresh() {
        ui.removeCallbacks(refreshLoop);
        executor.execute(this::load);
    }
    private void scheduleNext() {
        ui.removeCallbacks(refreshLoop);
        ui.postDelayed(refreshLoop, 12000);
    }
    private void load() {
        if (!running) return;
        if (loading) { reloadRequested=true; return; }
        loading=true;
        try {
            if (refreshToken != null) {
                try { ensureIdToken(); } catch (Exception ex) {
                    idToken=null;
                    ui.post(()->{if(running) listener.authChanged(false);});
                }
            }
            List<Map<String,Object>> p=fetchCollection("players");
            List<Map<String,Object>> r=fetchCollection("rounds");
            List<Map<String,Object>> s=fetchCollection("sessions");
            ui.post(() -> { if(running) listener.dataUpdated(p,r,s); });
        } catch (Exception ex) {
            notify("Sync issue: " + ex.getMessage());
        } finally {
            loading=false;
            if (running && reloadRequested){reloadRequested=false;ui.removeCallbacks(refreshLoop);executor.execute(this::load);}
            else if(running) scheduleNext();
        }
    }
    private List<Map<String,Object>> fetchCollection(String name) throws Exception {
        ArrayList<Map<String,Object>> result=new ArrayList<>();
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
                result.add(map);
            }
            next=data.optString("nextPageToken","");
            if(next.isEmpty()) break;
        }
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
    public void renamePlayer(String playerId,String newName,List<Map<String,Object>> rounds) {
        write("Player renamed",token->{
            JSONArray operations=new JSONArray();
            JSONObject playerUpdate=new JSONObject();
            playerUpdate.put("name",dbRoot+"/players/"+encoded(playerId));
            playerUpdate.put("fields",encodeFields(map("name",newName)));
            operations.put(new JSONObject()
                    .put("update",playerUpdate)
                    .put("updateMask",new JSONObject().put("fieldPaths",new JSONArray().put("name")))
                    .put("currentDocument",new JSONObject().put("exists",true)));
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
                update.put("name",dbRoot+"/rounds/"+encoded(rid));
                update.put("fields",encodeFields(edits));
                JSONArray fields=new JSONArray();
                for(String field:edits.keySet())fields.put(field);
                operations.put(new JSONObject()
                        .put("update",update)
                        .put("updateMask",new JSONObject().put("fieldPaths",fields))
                        .put("currentDocument",new JSONObject().put("exists",true)));
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
    public void deleteDocument(String collection,String id,String success){
        write(success,token->{
            String url=dbRoot+"/"+collection+"/"+encoded(id);
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
        if(!writeInFlight.compareAndSet(false,true)){
            notify("Another save is already in progress");
            onUi(onFailure);
            return;
        }
        setBusy(true);
        executor.execute(() -> {
            try {
                ensureIdToken();
                if(idToken==null)throw new IOException("Login expired");
                action.run(idToken);
                notify(success);
                onUi(onSuccess);
                load();
            }catch(Exception error){
                notify("Not saved: "+error.getMessage());
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
    public static Map<String,Object> map(Object... v) {
        Map<String,Object> m=new LinkedHashMap<>();
        for(int i=0;i+1<v.length;i+=2) m.put(String.valueOf(v[i]),v[i+1]);
        return m;
    }
    public static Stamp now() { return new Stamp(Instant.now().toString()); }
    public void addDocument(String collection, Map<String,Object> fields,String success) {
        addDocument(collection,fields,success,null);
    }
    public void addDocument(String collection,Map<String,Object> fields,String success,Runnable onSaved){
        addDocument(collection,fields,success,onSaved,null);
    }
    public void addDocument(String collection,Map<String,Object> fields,String success,
                            Runnable onSaved,Runnable onFailed) {
        write(success,token->{
            JSONObject body=new JSONObject();
            body.put("fields",encodeFields(fields));
            jsonCall("POST",dbRoot+"/"+collection,body,token);
        },onSaved,onFailed);
    }
    public void patchDocument(String collection,String id,Map<String,Object> fields,String success) {
        write(success,token -> {
            JSONObject body=new JSONObject();
            body.put("fields",encodeFields(fields));
            StringBuilder uri=new StringBuilder(dbRoot+"/"+collection+"/"+encoded(id)+"?");
            for(String key:fields.keySet()) uri.append("updateMask.fieldPaths=").append(encoded(key)).append("&");
            jsonCall("PATCH",uri.toString(),body,token);
        });
    }
}
