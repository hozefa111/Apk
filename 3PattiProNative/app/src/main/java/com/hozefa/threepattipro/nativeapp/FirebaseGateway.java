package com.hozefa.threepattipro.nativeapp;

import android.content.Context;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.android.gms.tasks.Tasks;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.util.concurrent.TimeUnit;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import com.google.firebase.firestore.FirebaseFirestoreException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class FirebaseGateway {
    public interface Listener {
        void dataUpdated(List<Map<String,Object>> players, List<Map<String,Object>> rounds, List<Map<String,Object>> sessions);
        void authChanged(boolean loggedIn);
        void notice(String message);
        void busy(boolean active);
        void syncState(boolean online);
        /** Presentation only: false seeds a new animation baseline after cache/reconnect. */
        default void snapshotContinuity(boolean continuous) {}
        /** Bounded initial sync progress; partial history never authorizes scores/writes. */
        default void dataLoading(String collection,int loaded) {}
        default void historyDeferred(boolean deferred) {}
        default void syncTimestamp(long timestamp) {}
        /** A read-only connection problem; retryAt is a wall-clock deadline, not a write queue. */
        default void syncProblem(String message,long retryAt,boolean quota) {}
    }
    public static final class Stamp {
        public final String value;
        public Stamp(String v) { value = v; }
    }
    private final String project = BuildConfig.USE_EMULATORS?"demo-three-patti":"patti-pro-a61d8";
    private final Context context;
    private final Listener listener;
    private final Object snapshotLock=new Object();
    private final Map<String,List<PagedFirestoreCollection.Snapshot>> liveCollections=new HashMap<>();
    private final Map<String,Boolean> collectionComplete=new HashMap<>();
    private final Map<String,List<String>> liveBoundaries=new HashMap<>();
    private volatile Map<String,List<String>> cacheBoundaries=Collections.emptyMap();
    private final Map<String,Map<String,CachedDocument>> decodedDocuments=new HashMap<>();
    private final ExecutorService snapshotExecutor=Executors.newSingleThreadExecutor();
    private final java.util.concurrent.Executor snapshotDispatch=task->{
        try{snapshotExecutor.execute(task);}catch(java.util.concurrent.RejectedExecutionException stopped){/* Removed SDK listeners can race lifecycle shutdown. */}
    };
    private final int pageSize;
    private long lastPublishedRevision=-1,cacheRevision=-1;
    private long lastDeliveredContent=-1;
    private long contentRevision;
    private final Runnable cacheFlush=()->dispatch(this::flushCache);
    private volatile Map<String,String> cacheRows;
    private long cacheSnapshotAt;
    private final TrackerCache trackerCache;
    private volatile long decodedCount,metadataReuses,cacheWrites,queryStarts,maxPageDocuments,cacheRecordWrites;
    private volatile long decodeNanos;
    private final ArrayDeque<Long> pageLatencies=new ArrayDeque<>();
    private static final class CachedDocument {
        final DocumentSnapshot source;final Map<String,Object> row;final JSONObject raw;final String token;final String cachedRow;
        CachedDocument(DocumentSnapshot source,Map<String,Object> row,JSONObject raw,String token){this.source=source;this.row=row;this.raw=raw;this.token=token;this.cachedRow=new JSONObject(row).toString();}
    }
    private int subscriptionGeneration,legacyEpoch;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final LongSupplier retryClock;
    private final SyncRetryPolicy retry;
    private final android.content.SharedPreferences retryStore;
    private final AtomicBoolean readQueued=new AtomicBoolean(),readAgain=new AtomicBoolean();
    private volatile boolean foreground=true;
    private volatile long lastRefreshRequestAt;
    private final Runnable backgroundStop=this::stopInBackground;
    private void stopInBackground(){if(!foreground){removeSubscriptions();animationContinuity=false;freshSnapshot=false;if(running)listener.syncState(false);}}
    private final FirebaseAuth auth;
    private FirebaseAuth.AuthStateListener authObserver;
    private final FirebaseFirestore firestore;
    private final List<PagedFirestoreCollection> subscriptions=new ArrayList<>();
    private volatile Map<String,String> versions=Collections.emptyMap();
    private volatile Map<String,JSONObject> rawDocuments=Collections.emptyMap();
    private static final class CollectionSnapshot {
        final List<Map<String,Object>> rows=new ArrayList<>();
        final Map<String,JSONObject> documents=new HashMap<>();
        final Map<String,String> versions=new HashMap<>();
    }
    private final java.util.concurrent.atomic.AtomicLong liveRevision=new java.util.concurrent.atomic.AtomicLong();
    private volatile long snapshotAt=0;
    private volatile boolean animationContinuity;
    private long animationSnapshotAt;
    private volatile boolean signingOut=false;
    private final Runnable liveRefresh=()->{if(!this.freshSnapshot)queueLoad(true);};
    private volatile String idToken;
    private volatile boolean administrator;
    private volatile boolean running;
    private final AtomicBoolean stopped=new AtomicBoolean();
    private volatile boolean freshSnapshot=false;
    private volatile boolean historyRequested=true;
    private ListenerRegistration journalHead;
    private volatile boolean journalHeadKnown=false,journalActive=false;
    private Map<String,Map<String,Object>> journalDocuments=new LinkedHashMap<>();
    private volatile long journalEpoch=-1,journalRevision=-1;
    long[] trackerCheckpoint(){return new long[]{journalEpoch,journalRevision};}
    private volatile Map<String,Object> journalLatestHead;
    private ListenerRegistration recentPreview;
    private final AtomicBoolean writeInFlight=new AtomicBoolean(false);
    private final Runnable refreshLoop=()->{startSubscriptions();queueLoad(true);};
    private final String documentRoot = "projects/" + project + "/databases/(default)/documents";

    public FirebaseGateway(Context c, Listener l, String key) {
        this(c,l,key,System::currentTimeMillis);
    }
    FirebaseGateway(Context c,Listener l,String key,LongSupplier clock) {
        this(c,l,key,clock,BuildConfig.FIRESTORE_PAGE_SIZE);
    }
    FirebaseGateway(Context c,Listener l,String key,LongSupplier clock,int batchSize) {
        if(batchSize<2||batchSize>500)throw new IllegalArgumentException("Invalid Firestore page size");
        pageSize=batchSize;
        retryClock=clock;
        context = c.getApplicationContext();
        trackerCache=new TrackerCache(context);
        listener = l;
        retryStore=context.getSharedPreferences("firebase_sdk_retry_v1",Context.MODE_PRIVATE);
        retry=new SyncRetryPolicy(retryClock.getAsLong(),retryStore.getLong("quota_until",0),retryStore.getInt("quota_failures",0));
        String apiKey = BuildConfig.USE_EMULATORS?"demo-key":key;
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
        // Old REST-only cooldowns must not prevent a healthy SDK connection.
        context.getSharedPreferences("firebase_sync_retry",Context.MODE_PRIVATE).edit().clear().apply();
    }
    public void start() {
        if(running||stopped.get())return;
        running=true;lastRefreshRequestAt=retryClock.getAsLong();
        ui.post(() -> listener.authChanged(false));
        readQueued.set(true);
        dispatch(()->{try{readCachedSnapshot();load();}finally{finishQueuedRead();}});
        authObserver=state->{
            if(!running)return;
            animationContinuity=false;liveRevision.incrementAndGet();freshSnapshot=false;listener.syncState(false);
            if(state.getCurrentUser()==null){idToken=null;listener.authChanged(false);}
            else dispatch(()->{try{ensureIdToken();}catch(Exception expired){idToken=null;ui.post(()->{if(running)listener.authChanged(false);});}});
            removeSubscriptions();startSubscriptions();scheduleNext();
        };
        auth.addAuthStateListener(authObserver);
        // AuthStateListener always emits its initial state; it owns initial attach,
        // avoiding an attach/remove/attach race and duplicate cold-start queries.
    }
    private void startJournalHead(){
        if(journalHead!=null||!running||!foreground||retry.remaining(retryClock.getAsLong())>0)return;
        final int generation=subscriptionGeneration;
        journalHead=firestore.document("trackerSync/state").addSnapshotListener(MetadataChanges.INCLUDE,(head,error)->{
            if(!running||generation!=subscriptionGeneration)return;
            if(error!=null){onListenFailure(error);return;}
            if(head.getMetadata().isFromCache()||head.getMetadata().hasPendingWrites()){freshSnapshot=false;listener.syncState(false);return;}
            journalLatestHead=TrackerJournalSync.head(head);
            journalHeadKnown=true;boolean active="incremental".equals(head.exists()?head.getString("mode"):"prepare");
            if(active!=journalActive){journalActive=active;legacyEpoch++;synchronized(snapshotLock){liveCollections.clear();collectionComplete.clear();liveBoundaries.clear();}listener.syncState(false);for(PagedFirestoreCollection p:subscriptions)snapshotDispatch.execute(p::close);subscriptions.clear();freshSnapshot=false;}
            listener.historyDeferred(false);
            if(active){freshSnapshot=false;listener.syncState(false);queueLoad(true);}else{startSubscriptions();queueLoad(true);}
        });
    }
    private void startSubscriptions(){
        startJournalHead();if(!journalHeadKnown||journalActive)return;
        if(!running||!foreground||!subscriptions.isEmpty()||retry.remaining(retryClock.getAsLong())>0)return;
        if(!historyRequested){
            listener.historyDeferred(true);listener.syncState(false);
            if(recentPreview==null){queryStarts++;recentPreview=firestore.collection("rounds").orderBy("timestamp",Query.Direction.DESCENDING).limit(25).addSnapshotListener(snapshotDispatch,(snapshot,error)->{
                if(!running||historyRequested)return;
                if(error!=null){ui.post(()->onListenFailure(error));return;}
                // This bounded preview only signals freshness. It never replaces the
                // complete ledger cache or supplies partial lifetime totals.
                ui.post(()->{if(running&&!historyRequested){listener.historyDeferred(true);listener.syncState(false);}});
            });}
            return;
        }
        listener.historyDeferred(false);
        if(recentPreview!=null){recentPreview.remove();recentPreview=null;}
        final int generation=subscriptionGeneration,streamEpoch=legacyEpoch;
        for(String collection:Arrays.asList("players","rounds","sessions")){
            PagedFirestoreCollection stream=new PagedFirestoreCollection(firestore.collection(collection),pageSize,snapshotDispatch,new PagedFirestoreCollection.Observer(){
                public void requested(){queryStarts++;}
                public void response(long nanos){synchronized(snapshotLock){if(pageLatencies.size()==200)pageLatencies.removeFirst();pageLatencies.addLast(nanos/1_000_000);}}
                public void changed(List<PagedFirestoreCollection.Snapshot> pages,boolean complete,int loaded,List<String> boundaries){
                    for(PagedFirestoreCollection.Snapshot page:pages)maxPageDocuments=Math.max(maxPageDocuments,page.size());
                    final boolean invalidates;
                    synchronized(snapshotLock){
                        if(!running||generation!=subscriptionGeneration||streamEpoch!=legacyEpoch||journalActive)return;
                        List<PagedFirestoreCollection.Snapshot> previous=liveCollections.get(collection);
                        boolean dataChanged=previous==null||previous.size()!=pages.size();
                        if(!dataChanged)for(int i=0;i<pages.size();i++)if(previous.get(i)!=pages.get(i)&&!pages.get(i).getDocumentChanges().isEmpty()){dataChanged=true;break;}
                        invalidates=!complete||dataChanged;
                        liveCollections.put(collection,pages);collectionComplete.put(collection,complete);liveBoundaries.put(collection,boundaries);
                        if(invalidates){liveRevision.incrementAndGet();freshSnapshot=false;}
                    }
                    ui.post(()->{
                        if(!running||generation!=subscriptionGeneration)return;
                        if(invalidates)listener.syncState(false);
                        if(!complete)listener.dataLoading(collection,loaded);
                        ui.removeCallbacks(liveRefresh);ui.postDelayed(liveRefresh,150);
                    });
                }
                public void failed(FirebaseFirestoreException error){ui.post(()->{if(running&&generation==subscriptionGeneration&&streamEpoch==legacyEpoch&&!journalActive)onListenFailure(error);});}
            });
            subscriptions.add(stream);dispatch(()->{if(running&&generation==subscriptionGeneration)snapshotDispatch.execute(()->stream.start(cacheBoundaries.get(collection)));});
        }
        scheduleNext();
    }
    /** Shared SDK listener error path; package visibility permits emulator-only fault injection. */
    void onListenFailure(FirebaseFirestoreException error){
        synchronized(snapshotLock){animationContinuity=false;freshSnapshot=false;liveRevision.incrementAndGet();}
        listener.syncState(false);
        if(error.getCode()==FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED){recordQuota(null);return;}
        synchronized(snapshotLock){retry.unavailable(retryClock.getAsLong());}
        String message=error.getCode()==FirebaseFirestoreException.Code.PERMISSION_DENIED?
                "Firebase access denied. Check the signed-in account and project rules.":
                "Connection interrupted ("+error.getCode().name()+"). Saved data remains available.";
        publishProblem(message);removeSubscriptions();scheduleNext();
    }
    private void removeSubscriptions(){
        if(journalHead!=null){journalHead.remove();journalHead=null;}journalHeadKnown=false;
        if(recentPreview!=null){recentPreview.remove();recentPreview=null;}
        subscriptionGeneration++;
        for(PagedFirestoreCollection registration:subscriptions)snapshotDispatch.execute(registration::close);
        subscriptions.clear();
        synchronized(snapshotLock){liveCollections.clear();collectionComplete.clear();liveBoundaries.clear();liveRevision.incrementAndGet();}
        freshSnapshot=false;
    }
    public void setForeground(boolean visible){
        foreground=visible;ui.removeCallbacks(backgroundStop);
        if(!running)return;
        if(!visible){ui.removeCallbacks(refreshLoop);ui.removeCallbacks(liveRefresh);ui.postDelayed(backgroundStop,60000);return;}
        startSubscriptions();
        if(!freshSnapshot||readAgain.get())queueLoad(false);
        else scheduleNext();
    }
    private void queueLoad(boolean followUp){
        if(!running||!foreground)return;
        if(!readQueued.compareAndSet(false,true)){if(followUp)readAgain.set(true);return;}
        dispatch(()->{try{if(running&&foreground)load();}finally{finishQueuedRead();}});
    }
    private void finishQueuedRead(){
        readQueued.set(false);
        if(readAgain.getAndSet(false)&&running&&foreground&&retry.remaining(retryClock.getAsLong())==0)queueLoad(false);
    }
    public void stop() { if(!stopped.compareAndSet(false,true))return;running=false;
        if(authObserver!=null)auth.removeAuthStateListener(authObserver);
        removeSubscriptions();ui.removeCallbacksAndMessages(null);
        snapshotExecutor.shutdown();executor.execute(()->{flushCache();trackerCache.close();});executor.shutdown();
    }
    private boolean dispatch(Runnable task){
        if(!running||stopped.get())return false;
        try{executor.execute(()->{if(running&&!stopped.get())task.run();});return true;}
        catch(java.util.concurrent.RejectedExecutionException closing){return false;}
    }
    private void notify(String msg) { ui.post(() -> { if (running) listener.notice(msg); }); }
    private void setBusy(boolean b) { ui.post(() -> { if (running) listener.busy(b); }); }
    /** All preconditions are read and checked before any write in this SDK transaction. */
    private void commit(JSONArray writes)throws Exception {
        if(retry.remaining(retryClock.getAsLong())>0)throw new IOException("Sync retry is pending");
        final String owner=bulkOwner();
        final DocumentReference journalEvent=firestore.collection("trackerChanges").document();
        Tasks.await(firestore.runTransaction(transaction->{
            try{
                if(signingOut||!owner.equals(bulkOwner()))throw new IOException("Login changed");
                if(transaction.get(journalEvent).exists())return null;
                DocumentReference syncRef=firestore.document("trackerSync/state");DocumentSnapshot syncSnapshot=transaction.get(syncRef);Map<String,Object> sync=TrackerJournalSync.head(syncSnapshot);
                List<DocumentReference> refs=new ArrayList<>();
                List<Map<String,Object>> edits=new ArrayList<>();
                for(int i=0;i<writes.length();i++){
                    JSONObject write=writes.getJSONObject(i);
                    String name=write.has("delete")?write.getString("delete"):write.getJSONObject("update").getString("name");
                    String path=BackupArchive.path(new JSONObject().put("name",name),project);
                    DocumentReference ref=firestore.document(path);refs.add(ref);
                    DocumentSnapshot current=transaction.get(ref);
                    JSONObject expected=write.getJSONObject("currentDocument");
                    if(expected.has("exists")){
                        if(current.exists()!=expected.getBoolean("exists"))throw new IOException("Record already exists or was removed. Refresh and retry.");
                    }else{
                        String token=expected.getString("updateTime");
                        if(!token.startsWith("sdk1:"))throw new IOException("This operation uses an older sync version. Export its recovery backup before stopping it in Settings.");
                        if(!current.exists()||!token.equals(DocumentVersion.of(SdkDocuments.fields(current.getData()))))
                            throw new IOException("Record changed. Refresh before saving; newer data was kept.");
                    }
                    if(write.has("delete")){edits.add(null);continue;}
                    Map<String,Object> values=SdkDocuments.decode(write.getJSONObject("update").getJSONObject("fields"),firestore);
                    JSONArray transforms=write.optJSONArray("updateTransforms");
                    if(transforms!=null)for(int j=0;j<transforms.length();j++)values.put(transforms.getJSONObject(j).getString("fieldPath"),FieldValue.serverTimestamp());
                    edits.add(values);
                }
                // SDK transactions fail offline; unlike batches they cannot queue an offline save.
                for(int i=0;i<writes.length();i++){
                    JSONObject write=writes.getJSONObject(i);DocumentReference ref=refs.get(i);
                    if(write.has("delete"))transaction.delete(ref);
                    else if(write.has("updateMask")){
                        Map<FieldPath,Object> updates=new HashMap<>();
                        for(Map.Entry<String,Object> edit:edits.get(i).entrySet())updates.put(FieldPath.of(edit.getKey()),edit.getValue());
                        // set with explicit FieldPaths preserves literal keys containing dots.
                        transaction.set(ref,edits.get(i),SetOptions.mergeFieldPaths(new ArrayList<>(updates.keySet())));
                    }else transaction.set(ref,edits.get(i));
                }
                List<String> paths=new ArrayList<>();for(DocumentReference ref:refs)if(!paths.contains(ref.getPath()))paths.add(ref.getPath());Collections.sort(paths);
                if(paths.size()>450)throw new IOException("Split tracker changes into smaller batches");
                long revision=TrackerJournalSync.number(sync,"revision")+1;
                transaction.set(journalEvent,TrackerJournalSync.values("epoch",sync.get("epoch"),"revision",revision,"paths",paths));
                Map<String,Object> next=new HashMap<>(sync);next.put("revision",revision);next.put("eventId",journalEvent.getId());next.put("paths",paths);transaction.set(syncRef,next);
                return null;
            }catch(FirebaseFirestoreException error){throw error;}
            catch(Exception conflict){
                // A stale dialog/payload is final; only the SDK's real concurrency/network
                // failures should trigger its automatic transaction retries.
                throw new IllegalStateException(conflict.getMessage(),conflict);
            }
        }),60,TimeUnit.SECONDS);
    }
    private JSONObject readDocument(String collection,String id)throws Exception{
        DocumentSnapshot doc=Tasks.await(firestore.collection(collection).document(id).get(Source.SERVER),25,TimeUnit.SECONDS);
        if(!doc.exists())throw new IOException("Record not found");
        return new JSONObject().put("fields",SdkDocuments.fields(doc.getData()));
    }
    private void ensureIdToken() throws Exception {
        if(signingOut || auth.getCurrentUser()==null){idToken=null;return;}
        com.google.firebase.auth.GetTokenResult result=Tasks.await(auth.getCurrentUser().getIdToken(false),20,TimeUnit.SECONDS);
        idToken=result.getToken();
        administrator=false;
        if(!auth.getCurrentUser().isAnonymous()) {
            administrator=Boolean.TRUE.equals(result.getClaims().get("admin"));
            if(!administrator){
                // Restore the previously server-verified role when reopening offline.
                // Pending local role writes are never trusted; mutations additionally
                // require a fresh live snapshot and server rules check authorization.
                DocumentSnapshot role=Tasks.await(firestore.collection("admins").document(auth.getCurrentUser().getUid()).get(Source.DEFAULT),20,TimeUnit.SECONDS);
                administrator=role.exists()&&!role.getMetadata().hasPendingWrites();
            }
        }
        ui.post(()->{if(running)listener.authChanged(loggedIn());});
    }
    public boolean loggedIn(){return !signingOut && auth.getCurrentUser()!=null && idToken!=null && administrator;}
    public void signIn(String email,String password){
        setBusy(true);
        dispatch(()->{
            try{
                Tasks.await(auth.signInWithEmailAndPassword(email,password),25,TimeUnit.SECONDS);
                signingOut=false;ensureIdToken();if(!loggedIn())throw new IOException("This account has no administrator role. Contact the existing app administrator.");load();notify("Admin login successful");
            }catch(Exception error){notify("Login failed. Check credentials and connection.");}
            finally{setBusy(false);}
        });
    }
    public void signOut(){
        // Block new mutations immediately, before the serialized work queue runs.
        signingOut=true;freshSnapshot=false;
        ui.post(()->{if(running)listener.authChanged(false);});
        dispatch(()->{auth.signOut();idToken=null;signingOut=false;load();notify("Logged out");});
    }
    public boolean refresh() {
        if(!running)return false;
        long now=retryClock.getAsLong();
        if(retry.remaining(now)>0){publishProblem(retry.quota()?quotaMessage():"Connection unavailable. Automatic retry is scheduled.");scheduleNext();return false;}
        synchronized(this){if(historyRequested&&now-lastRefreshRequestAt<10000)return false;lastRefreshRequestAt=now;historyRequested=true;}
        ui.removeCallbacks(refreshLoop);ui.post(()->{startSubscriptions();queueLoad(false);});return true;
    }
    private void scheduleNext() {
        ui.removeCallbacks(refreshLoop);
        if(!running||!foreground||!historyRequested)return;
        long delay=retry.remaining(retryClock.getAsLong());
        // A healthy listener already delivers real changes. No minute-by-minute full scans.
        if(delay==0&&freshSnapshot)return;
        ui.postDelayed(refreshLoop,delay>0?delay:30000);
    }
    private String quotaMessage(){return "Firebase request limit reached. Sync is paused; saved data is read-only. Retry is automatic. The exact project limit must be checked if this persists.";}
    private void publishProblem(String message){
        long deadline=retry.deadline();boolean quota=retry.quota();
        ui.post(()->{if(running){listener.snapshotContinuity(false);listener.syncState(false);listener.syncProblem(message,deadline,quota);}});
    }
    private void recordQuota(String retryAfter){
        synchronized(snapshotLock){
            liveRevision.incrementAndGet();retry.rateLimited(retryClock.getAsLong(),retryAfter);
            // Serialize cooldown persistence against successful snapshot publication.
            retryStore.edit().putLong("quota_until",retry.deadline()).putInt("quota_failures",retry.quotaFailures()).commit();
            freshSnapshot=false;animationContinuity=false;
        }
        publishProblem(quotaMessage());
        ui.post(()->{if(running){removeSubscriptions();ui.removeCallbacks(liveRefresh);scheduleNext();}});
    }
    private void load() {
        if(!running)return;
        if(!journalHeadKnown)return;
        if(journalActive){loadJournal();return;}
        if(!historyRequested&&retry.remaining(retryClock.getAsLong())==0){ui.post(()->{if(running){listener.historyDeferred(true);listener.syncState(false);}});return;}
        if(retry.remaining(retryClock.getAsLong())>0){publishProblem(retry.quota()?quotaMessage():"Connection unavailable. Automatic retry is scheduled.");scheduleNext();return;}
        try{
            Map<String,List<PagedFirestoreCollection.Snapshot>> collections;Map<String,Boolean> complete;Map<String,List<String>> bounds;long revision;
            synchronized(snapshotLock){collections=new HashMap<>(liveCollections);complete=new HashMap<>(collectionComplete);bounds=new HashMap<>(liveBoundaries);revision=liveRevision.get();}
            if(collections.size()!=3||complete.size()!=3||complete.containsValue(false)){scheduleNext();return;}
            boolean server=true;
            List<CollectionSnapshot> decoded=new ArrayList<>();
            for(String name:Arrays.asList("players","rounds","sessions")){
                List<PagedFirestoreCollection.Snapshot> pages=collections.get(name);
                for(PagedFirestoreCollection.Snapshot snapshot:pages)server&=!snapshot.getMetadata().isFromCache()&&!snapshot.getMetadata().hasPendingWrites();
                decoded.add(decodeSnapshots(name,pages));
            }
            if(!server){
                freshSnapshot=false;animationContinuity=false;
                publishProblem("Waiting for a server connection. The last saved table remains read-only; retry is automatic.");
                scheduleNext();return;
            }
            CollectionSnapshot ps=decoded.get(0),rs=decoded.get(1),ss=decoded.get(2);
            Map<String,String> nextVersions=new HashMap<>();Map<String,JSONObject> nextDocuments=new HashMap<>();
            for(CollectionSnapshot collection:decoded){nextVersions.putAll(collection.versions);nextDocuments.putAll(collection.documents);}
            final boolean continuous;
            synchronized(snapshotLock){
                // A listener error or newer snapshot must never be overwritten by
                // decoding an older snapshot that was already on the worker queue.
                if(revision!=liveRevision.get()||retry.remaining(retryClock.getAsLong())>0)return;
                versions=nextVersions;rawDocuments=nextDocuments;snapshotAt=System.currentTimeMillis();freshSnapshot=true;
                continuous=animationContinuity&&snapshotAt-animationSnapshotAt<75000;
                animationContinuity=true;animationSnapshotAt=snapshotAt;
                retry.succeeded();retryStore.edit().clear().apply();
            }
            if(contentRevision!=lastPublishedRevision){
                lastPublishedRevision=contentRevision;
                Map<String,String> pending=new LinkedHashMap<>();
                for(Map.Entry<String,Map<String,CachedDocument>> collection:decodedDocuments.entrySet())
                    for(Map.Entry<String,CachedDocument> doc:collection.getValue().entrySet())pending.put(collection.getKey()+"/"+doc.getKey(),doc.getValue().cachedRow);
                cacheRows=pending;cacheBoundaries=bounds;cacheSnapshotAt=snapshotAt;
                ui.removeCallbacks(cacheFlush);ui.postDelayed(cacheFlush,1000);
            }
            final boolean online=true;
            final long deliveredContent=contentRevision;
            ui.post(()->{
                if(!running||revision!=liveRevision.get())return;
                if(online)listener.syncProblem(null,0,false);
                listener.snapshotContinuity(continuous);
                if(lastDeliveredContent!=deliveredContent||!continuous){
                    lastDeliveredContent=deliveredContent;listener.dataUpdated(ps.rows,rs.rows,ss.rows);
                }
                listener.syncTimestamp(snapshotAt);listener.syncState(online);
            });
            scheduleNext();
        }catch(Exception error){freshSnapshot=false;animationContinuity=false;retry.unavailable(retryClock.getAsLong());publishProblem("Cannot read the shared table. Saved data remains available; retry is automatic.");scheduleNext();}
    }
    private void loadJournal(){
        if(!foreground||retry.remaining(retryClock.getAsLong())>0){scheduleNext();return;}
        final int generation=subscriptionGeneration;
        try{
            TrackerJournalSync engine=new TrackerJournalSync(firestore,pageSize,25);
            Map<String,Object> supplied=journalLatestHead;if(supplied!=null&&TrackerJournalSync.number(supplied,"revision")<journalRevision)supplied=null;
            TrackerJournalSync.Result result=engine.pull(journalDocuments,journalEpoch,journalRevision,supplied,()->!running||!foreground||generation!=subscriptionGeneration);
            if(result==null){journalActive=false;ui.post(this::startSubscriptions);return;}
            if(!running||!foreground||generation!=subscriptionGeneration||!journalActive)return;
            Map<String,List<Map<String,Object>>> groups=new HashMap<>();for(String col:TrackerJournalSync.COLLECTIONS)groups.put(col,new ArrayList<>());
            Map<String,String> rows=new LinkedHashMap<>(),newVersions=new HashMap<>();Map<String,JSONObject> documents=new HashMap<>();
            for(Map.Entry<String,Map<String,Object>> item:result.documents.entrySet()){
                String path=item.getKey();JSONObject fields=SdkDocuments.fields(item.getValue());String token=DocumentVersion.of(fields);Map<String,Object> row=decodeFields(fields);row.put("id",path.substring(path.indexOf('/')+1));row.put("_updateTime",token);
                groups.get(path.substring(0,path.indexOf('/'))).add(row);JSONObject saved=new JSONObject(row);saved.put("_journalFields",fields.toString());rows.put(path,saved.toString());newVersions.put(path,token);documents.put(path,new JSONObject().put("name",documentRoot+"/"+path).put("fields",fields).put("updateTime",token));
            }
            long now=System.currentTimeMillis();ui.removeCallbacks(cacheFlush);cacheRows=null;
            cacheRecordWrites+=trackerCache.write(rows,Collections.emptyMap(),now,result.epoch,result.revision);
            if(!running||generation!=subscriptionGeneration||!journalActive)return;
            journalDocuments=result.documents;journalEpoch=result.epoch;journalRevision=result.revision;
            if(journalLatestHead==null||TrackerJournalSync.number(journalLatestHead,"revision")<result.revision)journalLatestHead=TrackerJournalSync.values("mode","incremental","epoch",result.epoch,"revision",result.revision);versions=newVersions;rawDocuments=documents;snapshotAt=now;freshSnapshot=true;retry.succeeded();retryStore.edit().clear().apply();queryStarts+=engine.queries;
            ui.post(()->{if(!running||generation!=subscriptionGeneration)return;listener.historyDeferred(false);listener.syncProblem(null,0,false);listener.dataUpdated(groups.get("players"),groups.get("rounds"),groups.get("sessions"));listener.syncTimestamp(snapshotAt);listener.syncState(true);});
        }catch(java.util.concurrent.CancellationException cancelled){/* Resume keeps the durable checkpoint. */}
        catch(Exception failure){freshSnapshot=false;if(failure.getMessage()!=null&&failure.getMessage().contains("journal gap")){journalEpoch=-1;journalRevision=-1;}Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();if(cause instanceof FirebaseFirestoreException&&((FirebaseFirestoreException)cause).getCode()==FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED){recordQuota(null);return;}retry.unavailable(retryClock.getAsLong());publishProblem("Synchronization interrupted. Saved scores remain available; retry is automatic.");scheduleNext();}
    }
    // A private read-only offline snapshot. It NEVER queues or replays mutations.
    private File snapshotFile(){return new File(context.getFilesDir(),"3patti_snapshot.json");}
    private void readCachedSnapshot(){
        try{
            Map<String,String> cached=trackerCache.read();cacheBoundaries=trackerCache.boundaries();
            if(cached!=null){
                final long cachedAt=trackerCache.lastSyncedAt();ui.post(()->listener.syncTimestamp(cachedAt));
                journalEpoch=trackerCache.journalEpoch();journalRevision=trackerCache.journalRevision();journalDocuments.clear();
                for(Map.Entry<String,String> entry:cached.entrySet()){JSONObject value=new JSONObject(entry.getValue());if(value.has("_journalFields"))journalDocuments.put(entry.getKey(),SdkDocuments.decode(new JSONObject(value.getString("_journalFields")),firestore));else{journalEpoch=-1;journalRevision=-1;}}
                Map<String,List<Map<String,Object>>> rows=new HashMap<>();
                for(String name:Arrays.asList("players","rounds","sessions"))rows.put(name,new ArrayList<>());
                for(Map.Entry<String,String> item:cached.entrySet()){
                    String name=item.getKey().substring(0,item.getKey().indexOf('/'));
                    if(!rows.containsKey(name))throw new IOException("Invalid local cache collection");
                    Object value=unwrapCached(new JSONObject(item.getValue()));
                    if(!(value instanceof Map))throw new IOException("Invalid local cache row");
                    @SuppressWarnings("unchecked") Map<String,Object> row=(Map<String,Object>)value;rows.get(name).add(row);
                }
                ui.post(()->{if(running){listener.syncState(false);listener.dataUpdated(rows.get("players"),rows.get("rounds"),rows.get("sessions"));}});
                return;
            }
        }catch(Exception ignored){/* A disposable cache failure never changes server data. */}
        // Existing installs can show their prior JSON cache until a full live sync
        // commits the new SQLite cache. No remote migration or write replay occurs.
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
                    // The connection label already identifies cached/offline state.
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
    private CollectionSnapshot decodeSnapshots(String name,List<PagedFirestoreCollection.Snapshot> pages)throws Exception {
        long started=System.nanoTime();
        CollectionSnapshot out=new CollectionSnapshot();
        Map<String,CachedDocument> previous=decodedDocuments.getOrDefault(name,Collections.emptyMap());
        Map<String,CachedDocument> next=new LinkedHashMap<>();
        for(PagedFirestoreCollection.Snapshot snapshot:pages)for(DocumentSnapshot doc:snapshot.getDocuments()){
            CachedDocument entry=previous.get(doc.getId());
            // SDK equality includes data + metadata, so compare immutable data to reuse
            // both server-confirmation events and unchanged documents in a changed page.
            if(entry==null||!Objects.equals(entry.source.getData(),doc.getData())){
                JSONObject fields=SdkDocuments.fields(doc.getData());String token=DocumentVersion.of(fields);
                Map<String,Object> row=decodeFields(fields);row.put("id",doc.getId());row.put("_updateTime",token);
                String path=name+"/"+doc.getId();
                entry=new CachedDocument(doc,row,new JSONObject().put("name",documentRoot+"/"+path).put("fields",fields).put("updateTime",token),token);
                decodedCount++;contentRevision++;
            }else metadataReuses++;
            next.put(doc.getId(),entry);
        }
        if(!previous.keySet().equals(next.keySet()))contentRevision++;
        decodedDocuments.put(name,next);
        for(Map.Entry<String,CachedDocument> item:next.entrySet()){
            String path=name+"/"+item.getKey();CachedDocument entry=item.getValue();
            out.rows.add(entry.row);out.versions.put(path,entry.token);out.documents.put(path,entry.raw);
        }
        decodeNanos+=System.nanoTime()-started;
        return out;
    }
    private void flushCache(){
        if(cacheRows==null)return;
        long revision=lastPublishedRevision;
        if(cacheRevision==revision)return;
        try{
            cacheRecordWrites+=trackerCache.write(cacheRows,cacheBoundaries,cacheSnapshotAt);cacheRevision=revision;cacheWrites++;
        }catch(Exception ignored){/* Cache remains disposable; never block a live save. */}
    }
    /** Numeric, local-only counters for emulator validation; contains no user data. */
    long[] performanceCounters(){return new long[]{decodedCount,metadataReuses,cacheWrites,queryStarts,maxPageDocuments,decodeNanos,cacheRecordWrites};}
    List<String> rangeBoundaries(String collection){synchronized(snapshotLock){return new ArrayList<>(liveBoundaries.getOrDefault(collection,Collections.emptyList()));}}
    public String performanceSummary(){
        List<Long> samples;synchronized(snapshotLock){samples=new ArrayList<>(pageLatencies);}Collections.sort(samples);
        String timing=samples.isEmpty()?"Waiting for a confirmed page response":
            "Median "+samples.get((samples.size()-1)/2)+" ms · p95 "+samples.get((int)Math.ceil(samples.size()*.95)-1)+" ms ("+samples.size()+" samples)";
        return "Cloud Firestore · "+(freshSnapshot?"Live and complete":"Loading / read-only")+
            "\nConfigured batch: "+pageSize+" documents\nLargest received batch: "+maxPageDocuments+
            "\nPage queries attached: "+queryStarts+"\nDecoded records: "+decodedCount+
            "\nUnchanged record reuse: "+metadataReuses+"\nOffline cache transactions: "+cacheWrites+"\nIndividual cached rows changed: "+cacheRecordWrites+
            "\nDecode work: "+(decodeNanos/1_000_000)+" ms\n\nFirst confirmed page response\n"+timing+
            "\n\nIncludes Firebase SDK, cache confirmation and network time; not database execution time alone. Up to 200 numeric samples stay in this app process. No names, IDs, cards or tokens are recorded. Complete history is required for lifetime totals; it loads in bounded pages before changes are enabled.";
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
     * Version-checked rename. Large histories use the same recoverable journal as restore.
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
                if("online".equals(round.get("source")))continue;
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
                JSONObject update=new JSONObject();
                update.put("name",documentRoot+"/rounds/"+rid);
                update.put("fields",encodeFields(edits));
                JSONArray fields=new JSONArray();
                for(String field:edits.keySet()){fields.put(field);}
                operations.put(new JSONObject()
                        .put("update",update)
                        .put("updateMask",new JSONObject().put("fieldPaths",fields))
                        .put("currentDocument",precondition(expected,"rounds/"+rid)));
            }
            startBulk("rename",operations,token);
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
            JSONObject deletion=new JSONObject().put("delete",documentRoot+"/"+collection+"/"+id)
                .put("currentDocument",precondition(expected,collection+"/"+id));
            preserveBackup();
            commit(new JSONArray().put(deletion));
        });
    }
    /** Metadata and destructive session actions share server-side authority. */
    private JSONObject sessionRequest(Map<String,Object> session,String action,String name,String token,JSONObject preview)throws Exception{
        String endpoint;
        if(BuildConfig.USE_EMULATORS)endpoint="http://10.0.2.2:5001/demo-three-patti/asia-south1/kaliApi";
        else try(Reader reader=new InputStreamReader(context.getAssets().open("kali/config.js"),StandardCharsets.UTF_8)){
            StringBuilder config=new StringBuilder();char[] buffer=new char[1024];int count;
            while((count=reader.read(buffer))!=-1)config.append(buffer,0,count);endpoint=OnlineEndpoint.api(config.toString());
        }
        JSONObject payload=new JSONObject().put("clientProtocol",2).put("operation","sessionHistory").put("sessionId",String.valueOf(session.get("id")))
                .put("action",action).put("name",name==null?JSONObject.NULL:name).put("expectedName",session.get("sessionName")).put("commandId",UUID.randomUUID().toString());
        if(preview!=null)payload.put("deleteRounds",true).put("previewToken",preview.getString("previewToken"));
        okhttp3.OkHttpClient http=new okhttp3.OkHttpClient.Builder().callTimeout(25,TimeUnit.SECONDS).build();
        okhttp3.Request request=new okhttp3.Request.Builder().url(endpoint).header("Authorization","Bearer "+token)
                .post(okhttp3.RequestBody.create(payload.toString(),okhttp3.MediaType.get("application/json"))).build();
        try(okhttp3.Response response=http.newCall(request).execute()){
            JSONObject result=new JSONObject(response.body().string());if(!response.isSuccessful())throw new IOException(result.optString("error","Session update failed"));return result;
        }
    }
    public void onlineSessionHistory(Map<String,Object> session,String action,String name){
        write("Session updated",token->sessionRequest(session,action,name,token,null));
    }
    public void previewSessionDeletion(Map<String,Object> session,java.util.function.Consumer<JSONObject> ready){
        write(null,token->{JSONObject preview=sessionRequest(session,"previewDelete",null,token,null);ui.post(()->{if(running)ready.accept(preview);});});
    }
    public void deleteSessionRounds(Map<String,Object> session,JSONObject preview){
        write("Session, rounds and their points deleted",token->{
            preserveBackup();JSONObject result;
            do{ensureIdToken();result=sessionRequest(session,"delete",null,idToken,preview);if(result.optBoolean("pending"))Thread.sleep(1000);}while(result.optBoolean("pending"));
        });
    }
    public interface Action { void run(String token) throws Exception; }
    public void write(String success,Action action) {
        write(success,action,null,null);
    }
    public void write(String success,Action action,Runnable onSuccess,Runnable onFailure) {
        write(success,action,onSuccess,onFailure,false);
    }
    private void write(String success,Action action,Runnable onSuccess,Runnable onFailure,boolean resumeBulk) {
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
        dispatch(() -> {
            try {
                ensureIdToken();
                if(idToken==null || signingOut){throw new IOException("Login expired");}
                if(!freshSnapshot || System.currentTimeMillis()-snapshotAt>60000){load();}
                if(!freshSnapshot)throw new IOException("Data changed. Refresh and retry.");
                if(hasPendingBulkOperation()&&!resumeBulk)throw new IOException("An interrupted data operation needs attention in Settings before other changes");
                action.run(idToken);journalLatestHead=null;
                if(success!=null&&!success.isEmpty())notify(success);
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
        dispatch(()->{
            try{
                load();if(!freshSnapshot)throw new IOException("A live snapshot is required for backup");
                JSONObject backup=new JSONObject().put("format","3patti-firestore-v1").put("project",project)
                    .put("exportedAt",Instant.now().toString()).put("documents",new JSONArray(rawDocuments.values()));
                byte[] bytes=backup.toString(2).getBytes(StandardCharsets.UTF_8);
                BackupArchive.validate(bytes,project);
                ui.post(()->{if(running)callback.ready(bytes);});
            }catch(Exception error){notify("Backup failed: "+error.getMessage());}
            finally{setBusy(false);}
        });
    }
    public void exportRecoveryBackup(BackupCallback callback){
        dispatch(()->{
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
        File target=new File(folder,"backup-"+System.currentTimeMillis()+"-"+UUID.randomUUID()+".json");
        byte[] bytes=backup.toString().getBytes(StandardCharsets.UTF_8);BackupArchive.validate(bytes,project);
        atomicSave(new android.util.AtomicFile(target),bytes);
    }
    private android.util.AtomicFile bulkFile(){return new android.util.AtomicFile(new File(context.getFilesDir(),"pending-data-operation.json"));}
    private static void atomicSave(android.util.AtomicFile file,byte[] bytes)throws IOException{
        FileOutputStream out=null;
        try{out=file.startWrite();out.write(bytes);out.getFD().sync();file.finishWrite(out);}
        catch(IOException error){if(out!=null)file.failWrite(out);throw error;}
    }
    public boolean hasPendingBulkOperation(){
        File file=bulkFile().getBaseFile();return file.exists()||new File(file.getPath()+".bak").exists();
    }
    private String bulkOwner()throws IOException{
        if(auth.getCurrentUser()==null)throw new IOException("Login required");return auth.getCurrentUser().getUid();
    }
    private void startBulk(String kind,JSONArray writes,String token)throws Exception{
        BulkWritePlan plan=BulkWritePlan.create(project,bulkOwner(),kind,writes);
        // Validate the complete plan before any remote write, including batch size limits.
        plan=plan.reconcile(rawDocuments);
        preserveBackup();atomicSave(bulkFile(),plan.bytes());runBulk(plan,token);
    }
    private void runBulk(BulkWritePlan plan,String token)throws Exception{
        try{
            while(plan.remaining.length()>0){
                if(signingOut||auth.getCurrentUser()==null||!plan.owner.equals(bulkOwner()))throw new IOException("Login changed");
                JSONArray batch=plan.batch();
                commit(batch);
                plan=plan.acknowledged(batch.length());
                // If this save fails, replay reconciles already committed document contents.
                atomicSave(bulkFile(),plan.bytes());
            }
            bulkFile().delete();
            if(hasPendingBulkOperation())throw new IOException("Cannot finish local recovery journal");
        }catch(Exception error){
            throw new IOException("Data operation paused ("+(plan.total-plan.remaining.length())+"/"+plan.total+" confirmed). Retry in Settings. Recovery backup is saved. "+error.getMessage(),error);
        }
    }
    public void resumeBulkOperation(){
        write("Data operation completed",token->{
            if(!hasPendingBulkOperation())throw new IOException("No interrupted operation");
            byte[] bytes;try(FileInputStream in=bulkFile().openRead()){bytes=readAll(in);}
            BulkWritePlan plan=BulkWritePlan.parse(bytes,project,bulkOwner());
            load();if(!freshSnapshot)throw new IOException("Live data required");
            plan=plan.reconcile(rawDocuments);atomicSave(bulkFile(),plan.bytes());runBulk(plan,token);
        },null,null,true);
    }
    private static byte[] readAll(InputStream in)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int count;
        while((count=in.read(buffer))!=-1)out.write(buffer,0,count);return out.toByteArray();
    }
    /** Explicitly stops further batches; completed remote batches remain in place. */
    public void abandonBulkOperation(){
        write("Pending operation stopped. Completed batches remain; use the recovery backup if needed.",token->{
            byte[] bytes;try(FileInputStream in=bulkFile().openRead()){bytes=readAll(in);}
            BulkWritePlan.parse(bytes,project,bulkOwner());bulkFile().delete();
            if(hasPendingBulkOperation())throw new IOException("Cannot remove recovery journal");
        },null,null,true);
    }
    public void clearHistory(String exportedFingerprint){
        write("All rounds and sessions cleared",token->{
            if(!fingerprint().equals(exportedFingerprint))throw new IOException("Table changed since backup. Export a new backup first.");
            Map<String,String> expected=new TreeMap<>(versions);
            JSONArray writes=new JSONArray();
            for(String path:expected.keySet())if(path.startsWith("rounds/")||path.startsWith("sessions/")){
                JSONObject raw=rawDocuments.get(path);
                if(raw!=null&&"online".equals(decodeFields(raw.optJSONObject("fields")).get("source")))continue;
                writes.put(new JSONObject().put("delete",documentRoot+"/"+path).put("currentDocument",precondition(expected,path)));
            }
            startBulk("clear",writes,token);
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
                JSONObject normalized=SdkDocuments.fields(SdkDocuments.decode(doc.getJSONObject("fields"),firestore));
                if("online".equals(decodeFields(normalized).get("source")))continue;
                if(rawDocuments.containsKey(path)){
                    if(!BulkWritePlan.sameJson(rawDocuments.get(path).getJSONObject("fields"),normalized))
                        throw new IOException("Backup conflicts with existing "+path+". No records changed.");
                    continue;
                }
                writes.put(new JSONObject().put("update",new JSONObject().put("name",documentRoot+"/"+path).put("fields",normalized))
                    .put("currentDocument",new JSONObject().put("exists",false)));
            }
            startBulk("restore",writes,token);
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
            commit(new JSONArray().put(update));
        },onSaved,onFailed);
    }
    /**
     * Idempotent creation for Hukum rounds. The same stable document ID is
     * reused across an ambiguous network failure and a user retry.
     * No writes are generated by reads or unit tests.
     */
    public void addRoundWithId(String id,Map<String,Object> fields,
                               Runnable onSaved,Runnable onFailed) {
        Map<String,Object> tagged=new HashMap<>(fields);tagged.put("source","manual");
        if(!tagged.containsKey("sessionId")) {
            for(Map<String,Object> session:cachedSessionRows())if(session.get("endTime")==null&&!"online".equals(session.get("source"))){tagged.put("sessionId",session.get("id"));break;}
        }
        write("Round saved successfully",token->{
            JSONObject update=updateWrite("rounds",id,tagged,new JSONObject().put("exists",false),false);
            try {
                commit(new JSONArray().put(update));
            }catch(Exception failure){
                // A timeout might occur AFTER the backend committed the write.
                // Verify our unique UUID before offering a retry, to avoid
                // inserting the same round twice into the source of truth.
                try{
                    JSONObject existing=readDocument("rounds",id);
                    if(!sameRound(decodeFields(existing.optJSONObject("fields")),fields))
                        throw new IOException("Round ID already contains different data. Refresh before retrying.");
                }catch(Exception verificationFailure){
                    throw failure;
                }
            }
        },onSaved,onFailed);
    }
    private List<Map<String,Object>> cachedSessionRows(){
        List<Map<String,Object>> rows=new ArrayList<>();
        for(Map.Entry<String,JSONObject> entry:rawDocuments.entrySet())if(entry.getKey().startsWith("sessions/")){
            try{Map<String,Object> data=decodeFields(entry.getValue().optJSONObject("fields"));data.put("id",entry.getKey().substring(9));rows.add(data);}catch(Exception malformed){throw new IllegalStateException("Invalid cached session",malformed);}
        }
        rows.sort((a,b)->String.valueOf(b.get("startTime")).compareTo(String.valueOf(a.get("startTime"))));return rows;
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
            commit(new JSONArray().put(update));
        },onSaved,null);
    }
}
