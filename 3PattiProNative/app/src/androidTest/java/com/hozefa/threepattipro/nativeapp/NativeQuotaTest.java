package com.hozefa.threepattipro.nativeapp;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.firebase.firestore.*;
import org.junit.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;

/** Synthetic SDK listener limits against the demo project only. No production credentials. */
public class NativeQuotaTest {
    private Context context;
    private byte[] savedCache;
    private Path cache;
    private FirebaseGateway gateway;
    private final AtomicBoolean live=new AtomicBoolean(),quota=new AtomicBoolean();
    private final AtomicReference<List<Map<String,Object>>> players=new AtomicReference<>(Collections.emptyList());
    private void await(BooleanSupplier condition)throws Exception{long end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){if(condition.getAsBoolean())return;Thread.sleep(100);}fail("Expected quota/recovery state did not arrive: live="+live.get()+", quota="+quota.get()+"");}
    private FirebaseGateway.Listener listener(){return new FirebaseGateway.Listener(){
        public void dataUpdated(List<Map<String,Object>> p,List<Map<String,Object>> r,List<Map<String,Object>> s){players.set(p);}
        public void authChanged(boolean a){}public void notice(String m){}public void busy(boolean b){}
        public void syncState(boolean online){live.set(online);}
        public void syncProblem(String message,long retryAt,boolean limit){quota.set(limit);}
    };}
    private void start(AtomicLong clock)throws Exception{
        live.set(false);quota.set(false);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{gateway=new FirebaseGateway(context,listener(),"demo-key",clock::get);gateway.start();});
    }
    private void stop(){if(gateway!=null){InstrumentationRegistry.getInstrumentation().runOnMainSync(()->gateway.stop());gateway=null;}}
    @Before public void setup()throws Exception{
        Assume.assumeTrue(BuildConfig.USE_EMULATORS);context=InstrumentationRegistry.getInstrumentation().getTargetContext();assertTrue(context.getPackageName().endsWith(".sandbox"));
        context.getSharedPreferences("firebase_sdk_retry_v1",0).edit().clear().commit();
        cache=context.getFilesDir().toPath().resolve("3patti_snapshot.json");savedCache=Files.isRegularFile(cache)?Files.readAllBytes(cache):null;
    }
    @After public void cleanup()throws Exception{stop();if(context!=null){context.getSharedPreferences("firebase_sdk_retry_v1",0).edit().clear().commit();if(savedCache==null)Files.deleteIfExists(cache);else Files.write(cache,savedCache);}}
    @Test public void quotaCooldownSurvivesRestartAndRefreshThenRecoversWithoutWriting()throws Exception{
        Files.write(cache,"{\"players\":[{\"id\":\"cached-only\",\"name\":\"Cached synthetic player\",\"active\":true}],\"rounds\":[],\"sessions\":[],\"savedAt\":1}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        AtomicLong clock=new AtomicLong(System.currentTimeMillis());
        start(clock);await(live::get);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->gateway.onListenFailure(new FirebaseFirestoreException("Synthetic SDK limit",FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED)));
        await(quota::get);assertFalse(live.get());Thread.sleep(300);
        long deadline=context.getSharedPreferences("firebase_sdk_retry_v1",0).getLong("quota_until",0);assertTrue(deadline>clock.get());
        for(int i=0;i<20;i++){assertFalse(gateway.refresh());InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{gateway.setForeground(false);gateway.setForeground(true);});}
        assertEquals(deadline,context.getSharedPreferences("firebase_sdk_retry_v1",0).getLong("quota_until",0));
        gateway.signIn("tester@example.test","test-password-123");await(gateway::loggedIn);
        stop();start(clock);await(quota::get);await(gateway::loggedIn);assertFalse(live.get());
        AtomicBoolean wrote=new AtomicBoolean();CountDownLatch rejected=new CountDownLatch(1);gateway.write("Must never save",token->wrote.set(true),()->{},rejected::countDown);assertTrue(rejected.await(5,TimeUnit.SECONDS));assertFalse(wrote.get());
        clock.addAndGet(121000);assertTrue(gateway.refresh());await(()->live.get()&&!quota.get());
        assertFalse(players.get().stream().anyMatch(p->"cached-only".equals(p.get("id"))));
    }
    @Test public void oldRestCooldownDoesNotBlockHealthySdkAndNoMinutePolling()throws Exception{
        context.getSharedPreferences("firebase_sync_retry",0).edit().putLong("quota_until",System.currentTimeMillis()+3600000).putInt("quota_failures",6).commit();
        start(new AtomicLong(System.currentTimeMillis()));await(live::get);
        assertEquals(0,context.getSharedPreferences("firebase_sync_retry",0).getAll().size());
        Thread.sleep(63000);assertTrue(live.get());assertFalse(quota.get());
    }
    @Test public void quotaScreenReplacesEndlessLoadingAndKeepsCachedTableReadable()throws Exception{
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            long end=System.currentTimeMillis()+15000;while(true){try{onView(withText("Table Status")).check(matches(isDisplayed()));break;}catch(RuntimeException|AssertionError e){if(System.currentTimeMillis()>end)throw e;Thread.sleep(100);}}
            scenario.onActivity(a->{
                try{java.lang.reflect.Field api=MainActivity.class.getDeclaredField("api");api.setAccessible(true);((FirebaseGateway)api.get(a)).stop();java.lang.reflect.Field data=MainActivity.class.getDeclaredField("hasData");data.setAccessible(true);data.setBoolean(a,false);}catch(Exception e){throw new AssertionError(e);}
                a.syncState(false);a.syncProblem("Firebase request limit reached. Sync is paused; retry is automatic.",System.currentTimeMillis()+120000,true);
            });
            onView(withText("Your table has not loaded yet")).check(matches(isDisplayed()));onView(withText("♠  Loading your table…")).check(doesNotExist());capture("22-quota-no-cache");
            scenario.onActivity(a->{a.dataUpdated(new ArrayList<>(Arrays.asList(FirebaseGateway.map("id","quota-demo","name","Offline synthetic player","active",true))),new ArrayList<>(),new ArrayList<>());a.syncProblem("Firebase request limit reached. Sync is paused; retry is automatic.",System.currentTimeMillis()+120000,true);});
            onView(withText("Showing the last saved table. Changes are disabled until live sync returns.")).check(matches(isDisplayed()));onView(withText("Table Status")).check(matches(isDisplayed()));capture("23-quota-cached-table");
            scenario.onActivity(a->{a.syncProblem(null,0,false);a.syncState(true);});onView(withText("Sync paused · Firebase limit")).check(doesNotExist());
        }
    }
    private void capture(String name)throws Exception{
        Thread.sleep(500);android.graphics.Bitmap b=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(b);java.io.File d=new java.io.File(context.getFilesDir(),"verification");assertTrue(d.isDirectory()||d.mkdirs());try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(d,name+".png"))){assertTrue(b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{b.recycle();}
    }
}
