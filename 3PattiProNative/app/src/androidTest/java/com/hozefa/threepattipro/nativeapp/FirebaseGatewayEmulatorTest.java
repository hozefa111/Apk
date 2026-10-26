package com.hozefa.threepattipro.nativeapp;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import okhttp3.*;
import org.json.*;
import static org.junit.Assert.*;

/** These tests are deliberately incapable of running writes in a production build. */
public class FirebaseGatewayEmulatorTest {
    private FirebaseGateway gateway;
    private int batchSize=BuildConfig.FIRESTORE_PAGE_SIZE;
    private final AtomicBoolean live=new AtomicBoolean(),admin=new AtomicBoolean(),busy=new AtomicBoolean();
    private final AtomicReference<List<Map<String,Object>>> sessions=new AtomicReference<>(Collections.emptyList());
    private final AtomicReference<List<Map<String,Object>>> rounds=new AtomicReference<>(Collections.emptyList());
    private final AtomicReference<List<Map<String,Object>>> players=new AtomicReference<>(Collections.emptyList());
    private final AtomicLong syncEvent=new AtomicLong();
    private final AtomicReference<String> notice=new AtomicReference<>("");
    private void await(BooleanSupplier condition)throws Exception{
        long deadline=System.currentTimeMillis()+45000;
        while(System.currentTimeMillis()<deadline){if(condition.getAsBoolean())return;Thread.sleep(100);}
        fail(notice.get());
    }
    private void ready(Runnable operation)throws Exception{
        await(()->live.get()&&!busy.get()&&System.currentTimeMillis()-syncEvent.get()>500);
        AtomicBoolean sent=new AtomicBoolean();
        while(!sent.get()){
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{if(live.get()&&!busy.get()){operation.run();sent.set(true);}});
            if(!sent.get())await(()->live.get()&&!busy.get()&&System.currentTimeMillis()-syncEvent.get()>500);
        }
    }
    private void completed(CountDownLatch latch,String operation)throws Exception{
        boolean done=latch.await(45,TimeUnit.SECONDS);
        assertTrue(operation+": "+notice.get()+"; live="+live.get()+" busy="+busy.get(),done);
    }
    private JSONObject request(String method,String path,JSONObject body)throws Exception{
        assertTrue(BuildConfig.USE_EMULATORS);
        String root="http://10.0.2.2:8080/v1/projects/demo-three-patti/databases/(default)/documents";
        Request.Builder b=new Request.Builder().url(root+path).header("Authorization","Bearer owner");
        b.method(method,body==null?null:RequestBody.create(body.toString(),MediaType.get("application/json")));
        try(Response r=new OkHttpClient().newCall(b.build()).execute()){assertTrue("Emulator HTTP "+r.code(),r.isSuccessful());String text=r.body().string();return text.isEmpty()?new JSONObject():new JSONObject(text);}
    }
    @Before public void setup()throws Exception{
        Assume.assumeTrue(BuildConfig.USE_EMULATORS);
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();assertTrue(context.getPackageName().endsWith(".sandbox"));
        for(String id:Arrays.asList("a","b","c"))request("PATCH","/players/"+id,new JSONObject().put("fields",new JSONObject().put("name",new JSONObject().put("stringValue","Player "+id)).put("active",new JSONObject().put("booleanValue",true))));
        startGateway();
    }
    private void startGateway()throws Exception{
        live.set(false);busy.set(false);admin.set(false);
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        CountDownLatch started=new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            gateway=new FirebaseGateway(context,new FirebaseGateway.Listener(){
                public void dataUpdated(List<Map<String,Object>> p,List<Map<String,Object>> r,List<Map<String,Object>> s){players.set(p);rounds.set(r);sessions.set(s);}
                public void authChanged(boolean value){admin.set(value);}
                public void notice(String message){notice.set(message);android.util.Log.i("GatewayTest",message+"; live="+live.get()+" busy="+busy.get());}
                public void busy(boolean value){busy.set(value);}
                public void syncState(boolean value){syncEvent.set(System.currentTimeMillis());live.set(value);}
            },"demo-key",System::currentTimeMillis,batchSize);gateway.start();gateway.refresh();started.countDown();
        });assertTrue(started.await(5,TimeUnit.SECONDS));await(()->live.get()&&players.get().size()>=3);
        gateway.signIn("tester@example.test","test-password-123");await(()->admin.get()&&live.get()&&!busy.get());ready(()->{});
    }
    @After public void cleanup()throws Exception{if(gateway!=null)InstrumentationRegistry.getInstrumentation().runOnMainSync(()->gateway.stop());}
    @Test public void delayedCacheCallbackAfterShutdownCannotCrashOrRestartQueries()throws Exception{
        java.lang.reflect.Field field=FirebaseGateway.class.getDeclaredField("cacheFlush");field.setAccessible(true);
        Runnable delayed=(Runnable)field.get(gateway);long queries=gateway.performanceCounters()[3];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            gateway.stop();
            // Reproduce a callback posted by a worker after Activity destruction
            // removed older UI callbacks. Its executor is already shutting down.
            for(int i=0;i<10;i++)delayed.run();
            gateway.setForeground(true);assertFalse(gateway.refresh());gateway.stop();
        });
        Thread.sleep(300);assertEquals(queries,gateway.performanceCounters()[3]);
    }
    @Test public void boundedCursorsPreserveScoresAcrossBoundaryInsertDeleteAndSingleEdit()throws Exception{
        final String prefix="paged-"+UUID.randomUUID()+"-";
        List<String> created=new ArrayList<>();
        try{
            JSONArray writes=new JSONArray();
            for(int i=0;i<52;i++){
                String id=prefix+String.format(java.util.Locale.ROOT,"%03d",i);created.add(id);
                JSONObject fields=SdkDocuments.fields(FirebaseGateway.map("type","hukum","timestamp",new com.google.firebase.Timestamp(1000+i,0),"scoreChanges",FirebaseGateway.map("a",i),"playerIds",Arrays.asList("a","b","c"),"bid",155,"hukumId","a","result","win"));
                writes.put(new JSONObject().put("update",new JSONObject().put("name","projects/demo-three-patti/databases/(default)/documents/rounds/"+id).put("fields",fields)));
            }
            request("POST",":commit",new JSONObject().put("writes",writes));
            await(()->live.get()&&rounds.get().stream().filter(r->String.valueOf(r.get("id")).startsWith(prefix)).count()==52);
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->gateway.stop());batchSize=7;startGateway();
            await(()->live.get()&&rounds.get().stream().filter(r->String.valueOf(r.get("id")).startsWith(prefix)).count()==52);
            assertTrue("Every query bounded",gateway.performanceCounters()[4]<=7);
            assertTrue("Dataset spanned multiple cursor queries",gateway.performanceCounters()[3]>3);
            Thread.sleep(1500);long decodedBefore=gateway.performanceCounters()[0],queriesBefore=gateway.performanceCounters()[3],cacheRowsBefore=gateway.performanceCounters()[6];
            String edited=created.get(15);
            JSONObject fields=request("GET","/rounds/"+edited,null).getJSONObject("fields");
            fields.put("scoreChanges",new JSONObject().put("mapValue",new JSONObject().put("fields",new JSONObject().put("a",new JSONObject().put("integerValue","999")))));
            request("PATCH","/rounds/"+edited,new JSONObject().put("fields",fields));
            await(()->live.get()&&rounds.get().stream().anyMatch(r->edited.equals(r.get("id"))&&((Number)((Map<?,?>)r.get("scoreChanges")).get("a")).intValue()==999));
            assertEquals("Single edit decodes one document",1,gateway.performanceCounters()[0]-decodedBefore);
            assertEquals("Non-boundary edit reuses subscriptions",queriesBefore,gateway.performanceCounters()[3]);
            Thread.sleep(1600);
            assertEquals("Single edit writes one cached row",1,gateway.performanceCounters()[6]-cacheRowsBefore);
            String boundary=null;for(String candidate:gateway.rangeBoundaries("rounds"))if(candidate.startsWith(prefix)&&!candidate.equals(edited)){boundary=candidate;break;}
            assertNotNull("Fixture spans a real cursor boundary",boundary);final String removed=boundary;
            request("DELETE","/rounds/"+removed,null);
            await(()->live.get()&&rounds.get().stream().noneMatch(r->removed.equals(r.get("id")))&&rounds.get().stream().filter(r->String.valueOf(r.get("id")).startsWith(prefix)).count()==51);
            long beforeInsertQueries=gateway.performanceCounters()[3];
            String inserted=prefix+"000a";created.add(inserted);
            request("PATCH","/rounds/"+inserted,new JSONObject().put("fields",SdkDocuments.fields(FirebaseGateway.map("type","hukum","scoreChanges",FirebaseGateway.map("a",123),"playerIds",Arrays.asList("a")))));
            // Missing timestamp must not exclude a legacy record from score totals.
            await(()->live.get()&&rounds.get().stream().filter(r->String.valueOf(r.get("id")).startsWith(prefix)).count()==52);
            assertTrue("An insert splits only its affected range, not every later page",gateway.performanceCounters()[3]-beforeInsertQueries<=2);
            Set<String> unique=new HashSet<>();long actual=0;
            for(Map<String,Object> row:rounds.get()){
                assertTrue("No duplicate records across cursors",unique.add(String.valueOf(row.get("id"))));
                if(String.valueOf(row.get("id")).startsWith(prefix))actual+=((Number)((Map<?,?>)row.get("scoreChanges")).get("a")).intValue();
            }
            int removedIndex=Integer.parseInt(removed.substring(prefix.length()));
            assertEquals("Exact score sum after edit/delete/insert",(51*52/2)-15+999-removedIndex+123,actual);
            Thread.sleep(1600);long[] beforeNavigation=gateway.performanceCounters();
            for(int i=0;i<4;i++)InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{gateway.setForeground(false);gateway.setForeground(true);});
            Thread.sleep(1600);long[] afterNavigation=gateway.performanceCounters();
            assertEquals("Short background/navigation reuses live queries",beforeNavigation[3],afterNavigation[3]);
            assertEquals("Unchanged data is not decoded again",beforeNavigation[0],afterNavigation[0]);
            assertEquals("Unchanged data is not rewritten to cache",beforeNavigation[2],afterNavigation[2]);
            android.util.Log.i("PagingPerformance",gateway.performanceSummary());
            java.io.File evidence=new java.io.File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(),"verification");
            assertTrue(evidence.isDirectory()||evidence.mkdirs());
            JSONObject measured=new JSONObject().put("scope","demo Firestore emulator; not production WAN latency")
                .put("boundedBatch",7).put("singleEditDecodedRecords",1).put("singleEditCacheRowsWritten",1)
                .put("summary",gateway.performanceSummary()).put("duplicateRecords",0).put("exactScoresVerified",true);
            try(java.io.FileOutputStream file=new java.io.FileOutputStream(new java.io.File(evidence,"firestore-performance.json"))){file.write(measured.toString(2).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        }finally{
            JSONArray deletes=new JSONArray();for(String id:created)deletes.put(new JSONObject().put("delete","projects/demo-three-patti/databases/(default)/documents/rounds/"+id));
            request("POST",":commit",new JSONObject().put("writes",deletes));
        }
    }
    @Test public void onlineSessionCascadeRemovesLinkedRoundsAndKeepsOtherScores()throws Exception{
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("kaliOnlineBackend")));
        String id="online-meta-"+UUID.randomUUID();
        Map<String,Object> fields=FirebaseGateway.map("source","online","sessionName","Online original","endTime",new com.google.firebase.Timestamp(1000,0),"startTime",new com.google.firebase.Timestamp(500,0),"totalRounds",0);
        request("PATCH","/sessions/"+id,new JSONObject().put("fields",SdkDocuments.fields(fields)));
        await(()->sessions.get().stream().anyMatch(s->id.equals(s.get("id")))&&live.get()&&!busy.get());
        JSONObject baseline=request("GET","/rounds/fixture-round",null).getJSONObject("fields");
        Map<String,Object> original=sessions.get().stream().filter(s->id.equals(s.get("id"))).findFirst().get();
        ready(()->gateway.onlineSessionHistory(original,"rename","Family Friday"));
        await(()->sessions.get().stream().anyMatch(s->id.equals(s.get("id"))&&"Family Friday".equals(s.get("sessionName")))&&live.get()&&!busy.get());
        Map<String,Object> renamed=sessions.get().stream().filter(s->id.equals(s.get("id"))).findFirst().get();
        String linkedId="cascade-"+UUID.randomUUID();
        request("PATCH","/rounds/"+linkedId,new JSONObject().put("fields",SdkDocuments.fields(FirebaseGateway.map("source","online","sessionId",id,"scoreChanges",FirebaseGateway.map("a",-155),"timestamp",new com.google.firebase.Timestamp(1500,0)))));
        CountDownLatch inspected=new CountDownLatch(1);AtomicReference<JSONObject> preview=new AtomicReference<>();
        ready(()->gateway.previewSessionDeletion(renamed,p->{preview.set(p);inspected.countDown();}));completed(inspected,"session deletion preview");assertEquals(1,preview.get().getInt("roundCount"));
        ready(()->gateway.deleteSessionRounds(renamed,preview.get()));
        await(()->sessions.get().stream().noneMatch(s->id.equals(s.get("id")))&&live.get()&&!busy.get());
        assertTrue(BulkWritePlan.sameJson(baseline,request("GET","/rounds/fixture-round",null).getJSONObject("fields")));
        assertTrue(rounds.get().stream().noneMatch(r->linkedId.equals(r.get("id"))));
    }
    @Test public void roundRetryAndPayloadConflictAndVersionedDelete()throws Exception{
        String id="test-"+UUID.randomUUID();
        Map<String,Object> body=FirebaseGateway.map("type","hukum","timestamp",new FirebaseGateway.Stamp("2000-01-01T00:00:00Z"),"bid",155,"hukumId","a","hukumName","Player a","partnerIds",Arrays.asList("b"),"partnerNames",Arrays.asList("Player b"),"nonPartnerIds",Arrays.asList("c"),"nonPartnerNames",Arrays.asList("Player c"),"playerIds",Arrays.asList("a","b","c"),"result","win","scoreChanges",FirebaseGateway.map("a",310,"b",155,"c",0));
        CountDownLatch saved=new CountDownLatch(1);ready(()->gateway.addRoundWithId(id,body,saved::countDown,()->{}));completed(saved,"create");await(()->rounds.get().stream().anyMatch(r->id.equals(r.get("id")))&&live.get()&&!busy.get());
        assertFalse(String.valueOf(rounds.get().stream().filter(r->id.equals(r.get("id"))).findFirst().get().get("timestamp")).startsWith("2000-"));
        CountDownLatch retried=new CountDownLatch(1);ready(()->gateway.addRoundWithId(id,body,retried::countDown,()->{}));completed(retried,"retry");gateway.refresh();await(()->live.get()&&!busy.get());
        assertEquals(1,rounds.get().stream().filter(r->id.equals(r.get("id"))).count());
        CountDownLatch backup=new CountDownLatch(1);AtomicReference<byte[]> archive=new AtomicReference<>();gateway.exportBackup(bytes->{archive.set(bytes);backup.countDown();});assertTrue(backup.await(45,TimeUnit.SECONDS));assertTrue(BackupArchive.validate(archive.get(),"demo-three-patti").size()>=4);
        // An older REST archive can spell the same timestamp with different fractional digits.
        JSONObject oldArchive=new JSONObject(new String(archive.get(),java.nio.charset.StandardCharsets.UTF_8));
        JSONArray docs=oldArchive.getJSONArray("documents");
        for(int i=0;i<docs.length();i++){
            JSONObject oldFields=docs.getJSONObject(i).getJSONObject("fields");
            if(oldFields.has("timestamp")){
                JSONObject t=oldFields.getJSONObject("timestamp");
                if(t.has("timestampValue"))t.put("timestampValue",new java.time.format.DateTimeFormatterBuilder().appendInstant(9).toFormatter().format(java.time.Instant.parse(t.getString("timestampValue"))));
            }
        }
        notice.set("");ready(()->gateway.restoreBackup(oldArchive.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        await(()->notice.get().startsWith("Backup restored")&&live.get()&&!busy.get());
        assertFalse(gateway.hasPendingBulkOperation());
        Map<String,Object> changed=new HashMap<>(body);changed.put("result","loss");CountDownLatch rejected=new CountDownLatch(1);AtomicBoolean incorrectlySaved=new AtomicBoolean();ready(()->gateway.addRoundWithId(id,changed,()->incorrectlySaved.set(true),rejected::countDown));completed(rejected,"reject changed payload");assertFalse(incorrectlySaved.get());
        gateway.refresh();await(()->live.get()&&!busy.get());ready(()->gateway.deleteDocument("rounds",id,"Deleted test round"));await(()->rounds.get().stream().noneMatch(r->id.equals(r.get("id")))&&live.get()&&!busy.get());
        ready(()->gateway.restoreBackup(archive.get()));await(()->rounds.get().stream().anyMatch(r->id.equals(r.get("id")))&&live.get());
    }
    @Test public void sdkCodecPreservesAllArchiveTypesAndLiteralFieldNames()throws Exception{
        com.google.firebase.firestore.FirebaseFirestore db=com.google.firebase.firestore.FirebaseFirestore.getInstance(com.google.firebase.FirebaseApp.getInstance("three-patti-native"));
        Map<String,Object> values=FirebaseGateway.map("stamp",new com.google.firebase.Timestamp(1700000000,123456789),"bytes",com.google.firebase.firestore.Blob.fromBytes(new byte[]{0,1,-1}),"point",new com.google.firebase.firestore.GeoPoint(12.4,-77.2),"reference",db.document("players/a"),"null",null,"integer",Long.MAX_VALUE,"double",1.5d,"nan",Double.NaN,"infinity",Double.POSITIVE_INFINITY,"literal.dot",FirebaseGateway.map("nested",Arrays.asList(true,"value",4L)));
        JSONObject fields=SdkDocuments.fields(values);
        assertTrue(BulkWritePlan.sameJson(fields,SdkDocuments.fields(SdkDocuments.decode(fields,db))));
        CountDownLatch saved=new CountDownLatch(1);
        ready(()->gateway.patchDocument("players","a",FirebaseGateway.map("literal.dot","literal value"),"Literal edit",saved::countDown));
        completed(saved,"literal field edit");
        JSONObject actual=request("GET","/players/a",null).getJSONObject("fields");
        assertEquals("literal value",actual.getJSONObject("literal.dot").getString("stringValue"));
        assertFalse(actual.has("literal"));
    }
    @Test public void externalEditArrivesDirectlyAndOldDialogCannotOverwriteIt()throws Exception{
        String oldVersion=String.valueOf(players.get().stream().filter(p->"a".equals(p.get("id"))).findFirst().get().get("_updateTime"));
        JSONObject fields=request("GET","/players/a",null).getJSONObject("fields");
        fields.put("externalMarker",new JSONObject().put("stringValue","other-client"));
        request("PATCH","/players/a",new JSONObject().put("fields",fields));
        await(()->live.get()&&players.get().stream().anyMatch(p->"other-client".equals(p.get("externalMarker"))));notice.set("");
        ready(()->gateway.patchDocument("players","a",FirebaseGateway.map("externalMarker","stale-dialog"),"Must not save",oldVersion,null));
        await(()->notice.get().startsWith("Not saved:"));
        assertEquals("other-client",request("GET","/players/a",null).getJSONObject("fields").getJSONObject("externalMarker").getString("stringValue"));
    }
    @Test public void staleVersionCannotOverwriteNewerEdit()throws Exception{
        Map<String,Object> player=players.get().stream().filter(p->"a".equals(p.get("id"))).findFirst().get();String oldVersion=String.valueOf(player.get("_updateTime"));
        CountDownLatch first=new CountDownLatch(1);
        ready(()->gateway.patchDocument("players","a",FirebaseGateway.map("testNote","first"),"First edit",oldVersion,first::countDown));
        completed(first,"versioned first edit");await(()->live.get()&&!busy.get());notice.set("");
        ready(()->gateway.patchDocument("players","a",FirebaseGateway.map("testNote","second"),"Wrongly overwritten",oldVersion,null));
        await(()->notice.get().startsWith("Not saved:"));
        assertEquals("first",request("GET","/players/a",null).getJSONObject("fields").getJSONObject("testNote").getString("stringValue"));
    }
    @Test public void clearRequiresCurrentBackupAndCanRestoreEverything()throws Exception{
        int originalRounds=rounds.get().size();assertTrue("Synthetic fixture must have rounds",originalRounds>0);
        CountDownLatch firstBackup=new CountDownLatch(1);AtomicReference<byte[]> firstArchive=new AtomicReference<>();
        ready(()->gateway.exportBackup(bytes->{firstArchive.set(bytes);firstBackup.countDown();}));completed(firstBackup,"first backup");
        String stale=BackupArchive.fingerprint(firstArchive.get(),"demo-three-patti");
        JSONObject updatedFields=request("GET","/players/a",null).getJSONObject("fields");updatedFields.put("testBackupMarker",new JSONObject().put("stringValue","newer"));
        request("PATCH","/players/a",new JSONObject().put("fields",updatedFields));
        await(()->players.get().stream().anyMatch(p->"newer".equals(p.get("testBackupMarker")))&&live.get()&&!busy.get());notice.set("");
        ready(()->gateway.clearHistory(stale));await(()->notice.get().startsWith("Not saved:"));assertEquals("Stale backup must never clear records",originalRounds,rounds.get().size());
        CountDownLatch currentBackup=new CountDownLatch(1);AtomicReference<byte[]> currentArchive=new AtomicReference<>();
        ready(()->gateway.exportBackup(bytes->{currentArchive.set(bytes);currentBackup.countDown();}));completed(currentBackup,"current backup");
        String current=BackupArchive.fingerprint(currentArchive.get(),"demo-three-patti");
        ready(()->gateway.clearHistory(current));await(()->rounds.get().isEmpty()&&live.get()&&!busy.get());assertEquals(3,players.get().size());
        ready(()->gateway.restoreBackup(currentArchive.get()));await(()->rounds.get().size()==originalRounds&&live.get()&&!busy.get());
        assertTrue("Session history must also be restored",request("GET","/sessions/fixture-session",null).has("fields"));
    }
    @Test public void largeClearRestoreAndInterruptedCommitRecoverAfterRestart()throws Exception{
        String root="projects/demo-three-patti/databases/(default)/documents/";
        JSONObject fields=request("GET","/rounds/fixture-round",null).getJSONObject("fields");
        JSONArray seed=new JSONArray();
        for(int i=0;i<501;i++){
            seed.put(new JSONObject().put("update",new JSONObject().put("name",root+"rounds/bulk-"+i).put("fields",fields)).put("currentDocument",new JSONObject().put("exists",false)));
            if(seed.length()==400||i==500){request("POST",":commit",new JSONObject().put("writes",seed));seed=new JSONArray();}
        }
        await(()->rounds.get().stream().filter(r->String.valueOf(r.get("id")).startsWith("bulk-")).count()==501&&live.get()&&!busy.get());
        Map<String,Object> player=players.get().stream().filter(p->"a".equals(p.get("id"))).findFirst().get();
        ready(()->gateway.renamePlayer("a","Bulk renamed",new ArrayList<>(rounds.get()),String.valueOf(player.get("_updateTime"))));
        await(()->rounds.get().stream().filter(r->String.valueOf(r.get("id")).startsWith("bulk-")&&"Bulk renamed".equals(r.get("hukumName"))).count()==501&&live.get()&&!busy.get());
        Map<String,Object> renamed=players.get().stream().filter(p->"a".equals(p.get("id"))).findFirst().get();
        ready(()->gateway.renamePlayer("a","Player a",new ArrayList<>(rounds.get()),String.valueOf(renamed.get("_updateTime"))));
        await(()->rounds.get().stream().filter(r->String.valueOf(r.get("id")).startsWith("bulk-")&&"Player a".equals(r.get("hukumName"))).count()==501&&live.get()&&!busy.get());
        int original=rounds.get().size();AtomicReference<byte[]> archive=new AtomicReference<>();CountDownLatch exported=new CountDownLatch(1);
        ready(()->gateway.exportBackup(bytes->{archive.set(bytes);exported.countDown();}));completed(exported,"large export");
        assertTrue(BackupArchive.validate(archive.get(),"demo-three-patti").size()>500);
        JSONObject invalid=new JSONObject(new String(archive.get(),java.nio.charset.StandardCharsets.UTF_8));
        JSONArray invalidDocs=invalid.getJSONArray("documents");
        invalidDocs.getJSONObject(invalidDocs.length()-1).getJSONObject("fields").put("invalidInteger",new JSONObject().put("integerValue","not a number"));
        byte[] invalidArchive=invalid.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);notice.set("");
        ready(()->gateway.restoreBackup(invalidArchive));await(()->notice.get().startsWith("Not saved:"));
        assertEquals(original,rounds.get().size());assertFalse(gateway.hasPendingBulkOperation());
        ready(()->{try{gateway.clearHistory(BackupArchive.fingerprint(archive.get(),"demo-three-patti"));}catch(Exception e){throw new RuntimeException(e);}});
        await(()->rounds.get().isEmpty()&&live.get()&&!busy.get());assertFalse(gateway.hasPendingBulkOperation());assertEquals(3,players.get().size());
        ready(()->gateway.restoreBackup(archive.get()));await(()->rounds.get().size()==original&&live.get()&&!busy.get());assertFalse(gateway.hasPendingBulkOperation());
        // Simulate process loss after a remote commit but before its local acknowledgement.
        JSONArray deletes=new JSONArray();
        for(Map<String,Object> r:rounds.get())if(String.valueOf(r.get("id")).startsWith("bulk-"))deletes.put(new JSONObject().put("delete",root+"rounds/"+r.get("id")).put("currentDocument",new JSONObject().put("updateTime",r.get("_updateTime"))));
        String owner=com.google.firebase.auth.FirebaseAuth.getInstance(com.google.firebase.FirebaseApp.getInstance("three-patti-native")).getCurrentUser().getUid();
        BulkWritePlan plan=BulkWritePlan.create("demo-three-patti",owner,"clear",deletes);
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        android.util.AtomicFile journal=new android.util.AtomicFile(new java.io.File(context.getFilesDir(),"pending-data-operation.json"));
        java.io.FileOutputStream out=journal.startWrite();out.write(plan.bytes());out.getFD().sync();journal.finishWrite(out);
        // Simulate an already committed batch through emulator REST admin setup only.
        JSONArray committed=plan.batch();
        for(int i=0;i<committed.length();i++)committed.getJSONObject(i).put("currentDocument",new JSONObject().put("exists",true));
        request("POST",":commit",new JSONObject().put("writes",committed));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->gateway.stop());startGateway();assertTrue(gateway.hasPendingBulkOperation());
        notice.set("");ready(()->gateway.patchDocument("players","a",FirebaseGateway.map("blockedBulkWrite",true),"Must not save"));
        await(()->notice.get().contains("interrupted data operation"));
        assertFalse(request("GET","/players/a",null).getJSONObject("fields").has("blockedBulkWrite"));
        ready(()->gateway.resumeBulkOperation());
        await(()->!gateway.hasPendingBulkOperation()&&rounds.get().stream().noneMatch(r->String.valueOf(r.get("id")).startsWith("bulk-"))&&live.get()&&!busy.get());
        assertEquals(original-501,rounds.get().size());assertEquals(3,players.get().size());
    }
    @Test public void startupAutomaticallyLoadsCompleteHistoryWithoutManualAction()throws Exception{
        List<String> seeded=new ArrayList<>();
        try{
            for(int n=0;n<31;n++){
                String key="deferred_history_"+n;seeded.add(key);
                JSONObject fields=new JSONObject().put("type",new JSONObject().put("stringValue","hukum"))
                    .put("timestamp",new JSONObject().put("timestampValue",n==0?"2020-01-01T00:00:00Z":String.format(java.util.Locale.ROOT,"2026-01-01T00:00:%02dZ",n)))
                    .put("scoreChanges",new JSONObject().put("mapValue",new JSONObject().put("fields",new JSONObject().put("a",new JSONObject().put("integerValue",n==0?"360":"0")))));
                request("PATCH","/rounds/"+key,new JSONObject().put("fields",fields));
            }
            AtomicBoolean deferred=new AtomicBoolean();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                gateway.stop();live.set(false);
                gateway=new FirebaseGateway(InstrumentationRegistry.getInstrumentation().getTargetContext(),new FirebaseGateway.Listener(){
                    public void dataUpdated(List<Map<String,Object>> p,List<Map<String,Object>> r,List<Map<String,Object>> s){rounds.set(r);}
                    public void authChanged(boolean a){} public void notice(String m){} public void busy(boolean b){}
                    public void syncState(boolean value){live.set(value);}
                    public void historyDeferred(boolean value){deferred.set(value);}
                },"demo-key");gateway.start();
            });
            await(live::get);assertFalse(deferred.get());assertTrue("Automatic complete verification uses the existing page streams",gateway.performanceCounters()[3]>1);
            assertTrue(rounds.get().stream().anyMatch(r->"deferred_history_0".equals(r.get("id"))));
            Map<String,Object> old=rounds.get().stream().filter(r->"deferred_history_0".equals(r.get("id"))).findFirst().get();
            assertEquals(360,((Number)((Map<?,?>)old.get("scoreChanges")).get("a")).intValue());
        }finally{for(String key:seeded)request("DELETE","/rounds/"+key,null);}
    }

    @Test public void incrementalJournalSurvivesReopenAndTracksHistoricalEditDelete()throws Exception{
        String id="journal-native-"+UUID.randomUUID();JSONObject head;
        try{head=request("GET","/trackerSync/state",null).getJSONObject("fields");}catch(Exception|AssertionError missing){head=SdkDocuments.fields(FirebaseGateway.map("revision",0L,"epoch",1L,"mode","prepare","eventId","seed","paths",new ArrayList<>()));}
        long epoch=head.getJSONObject("epoch").getLong("integerValue")+1;
        head.put("enforced",new JSONObject().put("booleanValue",true));
        head.put("mode",new JSONObject().put("stringValue","incremental")).put("epoch",new JSONObject().put("integerValue",String.valueOf(epoch)));
        request("PATCH","/trackerSync/state",new JSONObject().put("fields",head));
        try{
            await(()->gateway.trackerCheckpoint()[0]==epoch&&live.get()&&!busy.get());
            Map<String,Object> body=FirebaseGateway.map("type","hukum","timestamp",new FirebaseGateway.Stamp("2000-01-01T00:00:00Z"),"scoreChanges",FirebaseGateway.map("a",360));
            CountDownLatch saved=new CountDownLatch(1);ready(()->gateway.addRoundWithId(id,body,saved::countDown,()->{}));completed(saved,"journal create");await(()->rounds.get().stream().anyMatch(r->id.equals(r.get("id")))&&live.get()&&!busy.get());
            CountDownLatch edited=new CountDownLatch(1);ready(()->gateway.patchDocument("rounds",id,FirebaseGateway.map("scoreChanges",FirebaseGateway.map("a",700)),"Edited",edited::countDown));completed(edited,"journal edit");
            await(()->rounds.get().stream().anyMatch(r->id.equals(r.get("id"))&&((Number)((Map<?,?>)r.get("scoreChanges")).get("a")).intValue()==700)&&live.get()&&!busy.get());
            long cursor=gateway.trackerCheckpoint()[1];InstrumentationRegistry.getInstrumentation().runOnMainSync(()->gateway.stop());startGateway();
            assertEquals(epoch,gateway.trackerCheckpoint()[0]);assertEquals(cursor,gateway.trackerCheckpoint()[1]);assertEquals("Unchanged reopen performs no full ledger query",0,gateway.performanceCounters()[3]);
            ready(()->gateway.deleteDocument("rounds",id,"Deleted"));await(()->rounds.get().stream().noneMatch(r->id.equals(r.get("id")))&&live.get()&&!busy.get());assertTrue(gateway.trackerCheckpoint()[1]>cursor);
        }finally{
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->gateway.stop());
            JSONObject latest=request("GET","/trackerSync/state",null).getJSONObject("fields");latest.put("mode",new JSONObject().put("stringValue","prepare")).put("enforced",new JSONObject().put("booleanValue",false));request("PATCH","/trackerSync/state",new JSONObject().put("fields",latest));
            try{request("DELETE","/rounds/"+id,null);}catch(Exception removed){}
        }
    }

    @Test public void sixConcurrentNativeCommandsCommitOnceAndReplayReceipts()throws Exception{
        JSONObject h;try{h=request("GET","/trackerSync/state",null).getJSONObject("fields");}catch(Exception|AssertionError missing){h=SdkDocuments.fields(FirebaseGateway.map("revision",0L,"epoch",1L,"mode","prepare","enforced",false,"eventId","seed","paths",new ArrayList<>()));}
        long epoch=h.getJSONObject("epoch").getLong("integerValue")+1;h.put("epoch",new JSONObject().put("integerValue",String.valueOf(epoch))).put("mode",new JSONObject().put("stringValue","incremental")).put("enforced",new JSONObject().put("booleanValue",true));request("PATCH","/trackerSync/state",new JSONObject().put("fields",h));
        ExecutorService workers=Executors.newFixedThreadPool(6);try{await(()->gateway.trackerCheckpoint()[0]==epoch&&live.get());long before=request("GET","/trackerSync/state",null).getJSONObject("fields").getJSONObject("revision").getLong("integerValue");java.lang.reflect.Method commit=FirebaseGateway.class.getDeclaredMethod("commit",JSONArray.class);commit.setAccessible(true);List<JSONArray> commands=new ArrayList<>();String id="six-native-"+UUID.randomUUID();for(int n=0;n<6;n++)commands.add(new JSONArray().put(new JSONObject().put("update",new JSONObject().put("name","projects/demo-three-patti/databases/(default)/documents/rounds/"+id+n).put("fields",SdkDocuments.fields(FirebaseGateway.map("type","hukum","scoreChanges",FirebaseGateway.map("a",155))))).put("currentDocument",new JSONObject().put("exists",false))));List<Future<?>> results=new ArrayList<>();for(JSONArray command:commands)results.add(workers.submit(()->{try{commit.invoke(gateway,command);return null;}catch(Exception e){throw new RuntimeException(e);}}));for(Future<?> result:results)result.get(90,TimeUnit.SECONDS);long after=request("GET","/trackerSync/state",null).getJSONObject("fields").getJSONObject("revision").getLong("integerValue");assertEquals(before+6,after);for(JSONArray command:commands)commit.invoke(gateway,command);assertEquals(after,request("GET","/trackerSync/state",null).getJSONObject("fields").getJSONObject("revision").getLong("integerValue"));for(int n=0;n<6;n++)assertNotNull(request("GET","/rounds/"+id+n,null));
            JSONObject fields=request("GET","/rounds/"+id+0,null).getJSONObject("fields");String token=DocumentVersion.of(fields);List<Future<Boolean>> conflicting=new ArrayList<>();for(int n=0;n<2;n++){final int score=180+n;conflicting.add(workers.submit(()->{JSONArray mutation=new JSONArray().put(new JSONObject().put("update",new JSONObject().put("name","projects/demo-three-patti/databases/(default)/documents/rounds/"+id+0).put("fields",SdkDocuments.fields(FirebaseGateway.map("type","hukum","scoreChanges",FirebaseGateway.map("a",score))))).put("currentDocument",new JSONObject().put("updateTime",token)));try{commit.invoke(gateway,mutation);return true;}catch(java.lang.reflect.InvocationTargetException expected){assertTrue(expected.getCause().getMessage().contains("Record changed"));return false;}}));}int winners=0;for(Future<Boolean> f:conflicting)if(f.get(90,TimeUnit.SECONDS))winners++;assertEquals(1,winners);
            com.google.firebase.firestore.FirebaseFirestore db=com.google.firebase.firestore.FirebaseFirestore.getInstance(com.google.firebase.FirebaseApp.getInstance("three-patti-native"));JSONArray offline=new JSONArray(commands.get(5).toString().replace(id+5,id+"offline"));com.google.android.gms.tasks.Tasks.await(db.disableNetwork());try{commit.invoke(gateway,offline);fail("Offline transaction cannot queue a save");}catch(java.lang.reflect.InvocationTargetException expected){}finally{com.google.android.gms.tasks.Tasks.await(db.enableNetwork());}await(()->live.get());commit.invoke(gateway,offline);commit.invoke(gateway,offline);assertEquals(after+2,request("GET","/trackerSync/state",null).getJSONObject("fields").getJSONObject("revision").getLong("integerValue"));

        }finally{workers.shutdownNow();JSONObject latest=request("GET","/trackerSync/state",null).getJSONObject("fields");latest.put("mode",new JSONObject().put("stringValue","prepare")).put("enforced",new JSONObject().put("booleanValue",false));request("PATCH","/trackerSync/state",new JSONObject().put("fields",latest));}
    }
}
