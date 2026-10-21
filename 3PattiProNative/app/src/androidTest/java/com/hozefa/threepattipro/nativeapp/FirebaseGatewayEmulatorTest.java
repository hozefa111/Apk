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
            },"demo-key");gateway.start();started.countDown();
        });assertTrue(started.await(5,TimeUnit.SECONDS));await(()->live.get()&&players.get().size()>=3);
        gateway.signIn("tester@example.test","test-password-123");await(()->admin.get()&&live.get()&&!busy.get());ready(()->{});
    }
    @After public void cleanup()throws Exception{if(gateway!=null)InstrumentationRegistry.getInstrumentation().runOnMainSync(()->gateway.stop());}
    @Test public void onlineSessionRenameAndDeleteUseAuthorityAndKeepRounds()throws Exception{
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
        ready(()->gateway.onlineSessionHistory(renamed,"delete",null));
        await(()->sessions.get().stream().noneMatch(s->id.equals(s.get("id")))&&live.get()&&!busy.get());
        assertTrue(BulkWritePlan.sameJson(baseline,request("GET","/rounds/fixture-round",null).getJSONObject("fields")));
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
}
