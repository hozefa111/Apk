package com.hozefa.threepattipro.nativeapp;

import android.app.Instrumentation;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;

/** Runs real animation frames with system scales ON; all data is synthetic/local. */
public class NativeMotionTest {
    private final Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
    private final String[] scales={"animator_duration_scale","transition_animation_scale","window_animation_scale"};
    private final Map<String,String> previous=new HashMap<>();
    private String previousMotion;
    private Context context(){return instrumentation.getTargetContext();}
    private String shell(String command)throws Exception {
        android.os.ParcelFileDescriptor fd=instrumentation.getUiAutomation().executeShellCommand(command);
        try(java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] bytes=new byte[1024];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);return out.toString("UTF-8").trim();
        }
    }
    @Before public void enableRealMotion()throws Exception {
        Assume.assumeTrue(BuildConfig.USE_EMULATORS);
        previousMotion=context().getSharedPreferences("preferences",0).getString("motion",null);
        context().getSharedPreferences("preferences",0).edit().putString("motion","full").commit();
        for(String key:scales){previous.put(key,shell("settings get global "+key));shell("settings put global "+key+" 1.0");}
    }
    @After public void restoreMotion()throws Exception {
        if(previousMotion==null)context().getSharedPreferences("preferences",0).edit().remove("motion").commit();
        else context().getSharedPreferences("preferences",0).edit().putString("motion",previousMotion).commit();
        for(String key:previous.keySet())shell("null".equals(previous.get(key))?"settings delete global "+key:"settings put global "+key+" "+previous.get(key));
    }
    private void waitFor(String text)throws Exception {
        long deadline=System.currentTimeMillis()+90000;Throwable last=null;
        while(System.currentTimeMillis()<deadline){try{onView(withText(text)).check(matches(isDisplayed()));return;}catch(RuntimeException|AssertionError e){last=e;Thread.sleep(200);}}
        throw new AssertionError("Missing "+text,last);
    }
    private void capture(String name)throws Exception {
        android.graphics.Bitmap bitmap=instrumentation.getUiAutomation().takeScreenshot();assertNotNull(bitmap);
        java.io.File dir=new java.io.File(context().getFilesDir(),"verification");assertTrue(dir.isDirectory()||dir.mkdirs());
        try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name+".png"))){assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{bitmap.recycle();}
    }
    private View anchor(View view,String key) {
        if(view.getTag() instanceof MotionEngine.Anchor&&key.equals(((MotionEngine.Anchor)view.getTag()).key))return view;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View found=anchor(((ViewGroup)view).getChildAt(i),key);if(found!=null)return found;}
        return null;
    }
    private void stopData(MainActivity activity) {
        try{java.lang.reflect.Field field=MainActivity.class.getDeclaredField("api");field.setAccessible(true);((FirebaseGateway)field.get(activity)).stop();}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private Map<String,Object> player(String id) {return FirebaseGateway.map("id",id,"name","Motion player "+id,"active",true,"createdAt","2026-01-01T00:00:00Z");}
    private Map<String,Object> round(String id,String hukum,String partner,String opponent,int bid) {
        return FirebaseGateway.map("id",id,"type","hukum","timestamp",java.time.Instant.now().toString(),"hukumId",hukum,"hukumName","Motion player "+hukum,
                "partnerIds",Arrays.asList(partner),"partnerNames",Arrays.asList("Motion player "+partner),"nonPartnerIds",Arrays.asList(opponent),
                "playerIds",Arrays.asList(hukum,partner,opponent),"bid",bid,"result","win","scoreChanges",FirebaseGateway.map(hukum,bid*2,partner,bid,opponent,0));
    }
    @Test public void confirmedLiveSnapshotAnimatesScoresRanksAndNeverReplaysReconnect()throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitFor("Table Status");
            List<Map<String,Object>> players=new ArrayList<>(Arrays.asList(player("a"),player("b"),player("c")));
            Map<String,Object> first=round("motion-first","a","b","c",155);
            scenario.onActivity(a->{stopData(a);a.snapshotContinuity(false);a.syncState(true);a.dataUpdated(new ArrayList<>(players),new ArrayList<>(Arrays.asList(first)),new ArrayList<>());});
            onView(withContentDescription("Ranks")).perform(click());Thread.sleep(600);
            AtomicBoolean intermediate=new AtomicBoolean(),moved=new AtomicBoolean();CountDownLatch framesDone=new CountDownLatch(1),movingFrame=new CountDownLatch(1);
            Map<String,Object> second=round("motion-second","b","c","a",155);
            scenario.onActivity(a->{
                assertEquals(MotionPolicy.Mode.FULL,a.motionEngine().mode());
                a.dataUpdated(new ArrayList<>(players),new ArrayList<>(Arrays.asList(first,second)),new ArrayList<>());
                long end=android.os.SystemClock.uptimeMillis()+1000;
                a.getWindow().getDecorView().postOnAnimation(new Runnable(){public void run(){
                    View card=anchor(a.getWindow().getDecorView(),"rank:all:0:0:b");
                    TextView score=(TextView)anchor(a.getWindow().getDecorView(),"rank-score:all:0:0:b");
                    if(card!=null&&Math.abs(card.getTranslationY())>.1f)moved.set(true);
                    if(score!=null&&!"+155".contentEquals(score.getText())&&!"+465".contentEquals(score.getText()))intermediate.set(true);
                    if(moved.get()&&intermediate.get())movingFrame.countDown();
                    if(android.os.SystemClock.uptimeMillis()<end)a.getWindow().getDecorView().postOnAnimation(this);else framesDone.countDown();
                }});
            });
            assertTrue("Score and rank must produce actual intermediate frames",movingFrame.await(5,TimeUnit.SECONDS));capture("12-motion-live-ranks");
            assertTrue(framesDone.await(5,TimeUnit.SECONDS));assertTrue(intermediate.get());assertTrue(moved.get());
            scenario.onActivity(a->{
                TextView score=(TextView)anchor(a.getWindow().getDecorView(),"rank-score:all:0:0:b");assertEquals("+465",score.getText().toString());
                assertEquals(0,a.motionEngine().activeCount());assertTrue(a.motionEngine().deltaCount()>0);
                int before=a.motionEngine().deltaCount();
                a.snapshotContinuity(false);a.syncState(false);Map<String,Object> third=round("motion-reconnect","a","c","b",155);
                a.dataUpdated(new ArrayList<>(players),new ArrayList<>(Arrays.asList(first,second,third)),new ArrayList<>());a.syncState(true);
                a.dataUpdated(new ArrayList<>(players),new ArrayList<>(Arrays.asList(first,second,third)),new ArrayList<>());
                assertEquals("Reconnect must not replay point deltas",before,a.motionEngine().deltaCount());assertEquals(0,a.motionEngine().activeCount());
            });
        }
    }
    @Test public void motionPreferencePersistsThroughActivityRecreation()throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitFor("Table Status");NativeTestUi.awaitMotionIdle(scenario);onView(withContentDescription("More options")).perform(click());onView(withText("Settings")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());Thread.sleep(800);
            onView(withText("Animations · full")).perform(scrollTo(),click());onView(withText("Reduced")).perform(click());
            scenario.onActivity(a->assertEquals(MotionPolicy.Mode.REDUCED,a.motionEngine().mode()));
            onView(withText("Animations · reduced")).perform(scrollTo(),click());onView(withText("Off")).perform(click());
            scenario.recreate();onView(withText("Animations · off")).perform(scrollTo()).check(matches(isDisplayed()));capture("13-motion-settings-off");
            scenario.onActivity(a->{assertEquals(MotionPolicy.Mode.OFF,a.motionEngine().mode());assertEquals(0,a.motionEngine().activeCount());assertEquals(0,a.motionEngine().pendingCount());});
        }
    }
    @Test public void pauseCancelsMotionRestoresFinalScoreAndDoesNotReplayOnResume()throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitFor("Table Status");AtomicReference<TextView> target=new AtomicReference<>();
            scenario.onActivity(a->{
                stopData(a);a.motionEngine().cancelAll();
                TextView value=new TextView(a);value.setTextSize(30);value.setTextColor(android.graphics.Color.WHITE);value.setText("0");
                a.addContentView(value,new ViewGroup.LayoutParams(-1,150));target.set(value);
            });instrumentation.waitForIdleSync();
            scenario.onActivity(a->{a.motionEngine().number(target.get(),0,2000,true);a.motionEngine().highlight(target.get(),0xFF2ECD71);assertTrue(a.motionEngine().activeCount()>0);});
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.onActivity(a->{assertEquals("+2000",target.get().getText().toString());assertEquals(0,a.motionEngine().activeCount());assertEquals(0,a.motionEngine().pendingCount());});
            scenario.moveToState(Lifecycle.State.RESUMED);Thread.sleep(300);
            scenario.onActivity(a->{assertEquals("+2000",target.get().getText().toString());assertEquals(0,a.motionEngine().activeCount());});
        }
    }
    @Test public void systemAnimationOffOverridesFullAndReducedLeavesFinalReadableUi()throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitFor("Table Status");
            shell("settings put global animator_duration_scale 0");
            scenario.onActivity(a->{
                a.motionEngine().cancelAll();assertEquals(MotionPolicy.Mode.OFF,a.motionEngine().mode());
                TextView value=new TextView(a);value.setText("old");a.motionEngine().number(value,-155,310,true);assertEquals("+310",value.getText().toString());assertEquals(0,a.motionEngine().activeCount());
            });
            shell("settings put global animator_duration_scale 1");
            scenario.onActivity(a->{
                a.getSharedPreferences("preferences",0).edit().putString("motion","reduced").commit();assertEquals(MotionPolicy.Mode.REDUCED,a.motionEngine().mode());
                TextView value=new TextView(a);a.motionEngine().number(value,0,-155,true);assertEquals("-155",value.getText().toString());
                a.motionEngine().confetti((ViewGroup)a.getWindow().getDecorView());
            });instrumentation.waitForIdleSync();Thread.sleep(200);
            scenario.onActivity(a->assertEquals(0,a.motionEngine().activeCount()));
        }
    }
    @Test public void sessionCelebrationCanBeSkippedAndHistoryDoesNotReplayIt()throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitFor("Table Status");
            List<Map<String,Object>> players=Arrays.asList(player("a"),player("b"),player("c"));
            Map<String,Object> session=SessionSummary.compute(players,Arrays.asList(round("summary-motion","a","b","c",155)));
            session.put("sessionName","Motion verification");session.put("startTime",java.time.Instant.now().minusSeconds(60).toString());session.put("endTime",java.time.Instant.now().toString());session.put("duration",60000L);
            scenario.onActivity(a->{
                stopData(a);
                try{java.lang.reflect.Method show=MainActivity.class.getDeclaredMethod("showSessionSummary",Map.class,boolean.class);show.setAccessible(true);show.invoke(a,session,true);}
                catch(ReflectiveOperationException e){throw new AssertionError(e);}
            });
            waitFor("SESSION SUMMARY");onView(withText("Skip animation")).perform(click());
            scenario.onActivity(a->{assertEquals(0,a.motionEngine().activeCount());assertEquals(0,a.motionEngine().pendingCount());});
            capture("14-motion-session-summary");onView(withText("Close")).perform(click());
            scenario.onActivity(a->{
                try{java.lang.reflect.Method show=MainActivity.class.getDeclaredMethod("showSessionSummary",Map.class,boolean.class);show.setAccessible(true);show.invoke(a,session,false);}
                catch(ReflectiveOperationException e){throw new AssertionError(e);}
            });
            waitFor("SESSION SUMMARY");onView(withText("Skip animation")).check(doesNotExist());onView(withText("Close")).perform(click());
        }
    }
}
