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
    private final AtomicReference<List<Map<String,Object>>> rounds=new AtomicReference<>(Collections.emptyList());
    private final AtomicReference<List<Map<String,Object>>> players=new AtomicReference<>(Collections.emptyList());
    private final AtomicLong syncEvent=new AtomicLong();
    private final AtomicReference<String> notice=new AtomicReference<>("");
    private void await(BooleanSupplier condition)throws Exception{long deadline=System.currentTimeMillis()+45000;while(!condition.getAsBoolean()&&System.currentTimeMillis()<deadline)Thread.sleep(100);assertTrue(notice.get(),condition.getAsBoolean());}
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
        CountDownLatch started=new CountDownLatch(1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            gateway=new FirebaseGateway(context,new FirebaseGateway.Listener(){
                public void dataUpdated(List<Map<String,Object>> p,List<Map<String,Object>> r,List<Map<String,Object>> s){players.set(p);rounds.set(r);}
                public void authChanged(boolean value){admin.set(value);}
                public void notice(String message){notice.set(message);android.util.Log.i("GatewayTest",message+"; live="+live.get()+" busy="+busy.get());}
                public void busy(boolean value){busy.set(value);}
                public void syncState(boolean value){syncEvent.set(System.currentTimeMillis());live.set(value);}
            },"demo-key");gateway.start();started.countDown();
        });assertTrue(started.await(5,TimeUnit.SECONDS));await(()->live.get()&&players.get().size()>=3);
        gateway.signIn("tester@example.test","test-password-123");await(()->admin.get()&&live.get()&&!busy.get());
    }
    @After public void cleanup()throws Exception{if(gateway!=null)InstrumentationRegistry.getInstrumentation().runOnMainSync(()->gateway.stop());}
    @Test public void roundRetryAndPayloadConflictAndVersionedDelete()throws Exception{
        String id="test-"+UUID.randomUUID();
        Map<String,Object> body=FirebaseGateway.map("type","hukum","timestamp",new FirebaseGateway.Stamp("2000-01-01T00:00:00Z"),"bid",155,"hukumId","a","hukumName","Player a","partnerIds",Arrays.asList("b"),"partnerNames",Arrays.asList("Player b"),"nonPartnerIds",Arrays.asList("c"),"nonPartnerNames",Arrays.asList("Player c"),"playerIds",Arrays.asList("a","b","c"),"result","win","scoreChanges",FirebaseGateway.map("a",310,"b",155,"c",0));
        CountDownLatch saved=new CountDownLatch(1);ready(()->gateway.addRoundWithId(id,body,saved::countDown,()->{}));completed(saved,"create");await(()->rounds.get().stream().anyMatch(r->id.equals(r.get("id")))&&live.get()&&!busy.get());
        assertFalse(String.valueOf(rounds.get().stream().filter(r->id.equals(r.get("id"))).findFirst().get().get("timestamp")).startsWith("2000-"));
        CountDownLatch retried=new CountDownLatch(1);ready(()->gateway.addRoundWithId(id,body,retried::countDown,()->{}));completed(retried,"retry");gateway.refresh();await(()->live.get()&&!busy.get());
        assertEquals(1,rounds.get().stream().filter(r->id.equals(r.get("id"))).count());
        CountDownLatch backup=new CountDownLatch(1);AtomicReference<byte[]> archive=new AtomicReference<>();gateway.exportBackup(bytes->{archive.set(bytes);backup.countDown();});assertTrue(backup.await(45,TimeUnit.SECONDS));assertTrue(BackupArchive.validate(archive.get(),"demo-three-patti").size()>=4);
        Map<String,Object> changed=new HashMap<>(body);changed.put("result","loss");CountDownLatch rejected=new CountDownLatch(1);AtomicBoolean incorrectlySaved=new AtomicBoolean();ready(()->gateway.addRoundWithId(id,changed,()->incorrectlySaved.set(true),rejected::countDown));completed(rejected,"reject changed payload");assertFalse(incorrectlySaved.get());
        gateway.refresh();await(()->live.get()&&!busy.get());ready(()->gateway.deleteDocument("rounds",id,"Deleted test round"));await(()->rounds.get().stream().noneMatch(r->id.equals(r.get("id")))&&live.get()&&!busy.get());
        ready(()->gateway.restoreBackup(archive.get()));await(()->rounds.get().stream().anyMatch(r->id.equals(r.get("id")))&&live.get());
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
}
