package com.hozefa.threepattipro.nativeapp;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.*;

/** Voice provider results are simulated; native UI, lifecycle and sandbox saves are real. */
public class NativeVoiceTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    @Before public void isolated(){Assume.assumeTrue(BuildConfig.USE_EMULATORS);assertTrue(inst.getTargetContext().getPackageName().endsWith(".sandbox"));}
    private Object field(MainActivity a,String name){try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}catch(Exception e){throw new AssertionError(e);}}
    private void field(MainActivity a,String name,Object value){try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,value);}catch(Exception e){throw new AssertionError(e);}}
    private void await(String label)throws Exception{long end=System.currentTimeMillis()+30000;Throwable last=null;while(System.currentTimeMillis()<end){try{onView(withText(label)).check(matches(isDisplayed()));return;}catch(RuntimeException|AssertionError e){last=e;Thread.sleep(150);}}throw new AssertionError(label,last);}
    private void idle(ActivityScenario<MainActivity> scenario)throws Exception{NativeTestUi.awaitMotionIdle(scenario);Thread.sleep(300);}
    private void open(ActivityScenario<MainActivity> scenario)throws Exception{
        await("Table Status");
        Tasks.await(FirebaseAuth.getInstance(FirebaseApp.getInstance("three-patti-native")).signInWithEmailAndPassword("tester@example.test","test-password-123"),20,TimeUnit.SECONDS);await("ADMIN");
        long end=System.currentTimeMillis()+30000;java.util.concurrent.atomic.AtomicBoolean ready=new java.util.concurrent.atomic.AtomicBoolean();
        while(System.currentTimeMillis()<end){scenario.onActivity(a->ready.set((Boolean)field(a,"liveConnected")&&!(Boolean)field(a,"busy")&&((List<?>)field(a,"players")).size()==3));if(ready.get())break;Thread.sleep(100);}assertTrue("Verified three-player voice fixture",ready.get());
        idle(scenario);onView(withContentDescription("Round")).perform(click());idle(scenario);
        onView(withText("🎙  Voice se round add karo")).perform(scrollTo(),click());await("Voice Round · PLAYERS");
    }
    private void input(String value,String next)throws Exception{
        onView(withContentDescription("Voice answer fallback")).inRoot(isDialog()).perform(scrollTo(),replaceText(value),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
        onView(withText("Use answer")).inRoot(isDialog()).perform(scrollTo(),click());await("Voice Round · "+next);
    }
    private void ready()throws Exception{input("all","HUKUM");input("Player a","BID");input("170","PARTNERS");input("Player b","RESULT");input("win","REVIEW");}
    private void capture(String label)throws Exception{Thread.sleep(600);inst.waitForIdleSync();android.graphics.Bitmap b=inst.getUiAutomation().takeScreenshot();assertNotNull(b);java.io.File dir=new java.io.File(inst.getTargetContext().getFilesDir(),"verification");assertTrue(dir.isDirectory()||dir.mkdirs());try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(dir,label+".png"))){assertTrue(b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{b.recycle();}}
    @Test public void cancelKeepsManualFormAndVoicePreviewUsesOriginalRules()throws Exception{
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            open(s);s.onActivity(a->{((FirebaseGateway)field(a,"api")).stop();field(a,"bid",200);});
            input("all","HUKUM");input("Player a","BID");input("155","PARTNERS");input("Player b","RESULT");input("loss","REVIEW");
            onView(withText("-78")).inRoot(isDialog()).perform(scrollTo()).check(matches(isDisplayed()));capture("41-voice-score-preview");
            s.onActivity(a->{assertEquals(200,field(a,"bid"));assertNull(field(a,"pendingRoundId"));});
            onView(withText("Cancel voice")).perform(click());
            s.onActivity(a->assertEquals(200,field(a,"bid")));
            onView(withContentDescription("Hukum Player a")).perform(scrollTo(),click());onView(withContentDescription("Partner Player b")).perform(scrollTo(),click());
            onView(withText("✓   Hukum Win")).perform(scrollTo(),click());onView(withText("+400")).perform(scrollTo()).check(matches(isDisplayed()));
        }
    }
    @Test public void recreationRestoresVoiceStepButNeverSaves()throws Exception{
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            open(s);input("all","HUKUM");input("Player a","BID");s.recreate();await("Voice Round · BID");
            input("200","PARTNERS");input("Player b","RESULT");input("win","REVIEW");
            onView(withText("+400")).inRoot(isDialog()).perform(scrollTo()).check(matches(isDisplayed()));capture("42-voice-restored-preview");
            s.onActivity(a->assertNull(field(a,"pendingRoundId")));onView(withText("Cancel voice")).perform(click());
        }
    }
    @Test public void recognizerResultAndCancelledRecognitionKeepStepSafe()throws Exception{
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            open(s);
            Intent result=new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,new ArrayList<>(Arrays.asList("all players")));
            Instrumentation.ActivityMonitor monitor=inst.addMonitor(new IntentFilter(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),new Instrumentation.ActivityResult(Activity.RESULT_OK,result),true);
            try{onView(withText("Speak answer")).inRoot(isDialog()).perform(scrollTo(),click());await("Voice Round · HUKUM");assertEquals(1,monitor.getHits());}finally{inst.removeMonitor(monitor);}
            Instrumentation.ActivityMonitor cancelled=inst.addMonitor(new IntentFilter(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),true);
            try{onView(withText("Speak answer")).inRoot(isDialog()).perform(scrollTo(),click());Thread.sleep(600);await("Voice Round · HUKUM");assertEquals(1,cancelled.getHits());}finally{inst.removeMonitor(cancelled);}
            input("not a real player","HUKUM");s.onActivity(a->assertNull(field(a,"pendingRoundId")));
            onView(withText("Cancel voice")).perform(click());
            s.onActivity(a->a.onActivityResult(VoiceRoundGuide.REQUEST,Activity.RESULT_OK,result));
            onView(withContentDescription("More options")).check(matches(isDisplayed()));
        }
    }
    @Test public void offlineConfirmationCannotChangeOrSaveManualDraft()throws Exception{
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            open(s);s.onActivity(a->{((FirebaseGateway)field(a,"api")).stop();field(a,"bid",200);a.syncState(false);});ready();
            onView(withText("Confirm Save")).inRoot(isDialog()).perform(scrollTo(),click());await("Voice Round · REVIEW");
            s.onActivity(a->{assertNull(field(a,"pendingRoundId"));assertEquals(200,field(a,"bid"));});
            onView(withText("Cancel voice")).perform(click());
        }
    }
    @Test public void confirmedVoiceRoundCreatesExactlyOneSandboxRecord()throws Exception{
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            open(s);FirebaseFirestore store=FirebaseFirestore.getInstance(FirebaseApp.getInstance("three-patti-native"));
            Set<String> before=new HashSet<>();for(DocumentSnapshot d:Tasks.await(store.collection("rounds").get(Source.SERVER),20,TimeUnit.SECONDS))before.add(d.getId());
            ready();capture("43-voice-confirmation");
            Intent confirmation=new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,new ArrayList<>(Arrays.asList("save karo")));
            Instrumentation.ActivityMonitor confirm=inst.addMonitor(new IntentFilter(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),new Instrumentation.ActivityResult(Activity.RESULT_OK,confirmation),true);
            try{onView(withText("Speak answer")).inRoot(isDialog()).perform(scrollTo(),click());await("Round saved successfully");assertEquals(1,confirm.getHits());}finally{inst.removeMonitor(confirm);}
            QuerySnapshot after=Tasks.await(store.collection("rounds").get(Source.SERVER),20,TimeUnit.SECONDS);
            List<DocumentSnapshot> added=new ArrayList<>();for(DocumentSnapshot d:after)if(!before.contains(d.getId()))added.add(d);
            assertEquals(1,added.size());DocumentSnapshot d=added.get(0);assertEquals(Long.valueOf(170),d.getLong("bid"));assertEquals("a",d.getString("hukumId"));assertEquals(Arrays.asList("b"),d.get("partnerIds"));assertEquals("win",d.getString("result"));
            Map<?,?> scores=(Map<?,?>)d.get("scoreChanges");assertEquals(340L,((Number)scores.get("a")).longValue());assertEquals(170L,((Number)scores.get("b")).longValue());
            Intent duplicate=new Intent().putStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS,new ArrayList<>(Arrays.asList("save karo")));s.onActivity(a->a.onActivityResult(VoiceRoundGuide.REQUEST,Activity.RESULT_OK,duplicate));
            assertEquals(after.size(),Tasks.await(store.collection("rounds").get(Source.SERVER),20,TimeUnit.SECONDS).size());
            onView(withText("Not Now")).perform(click());
            s.onActivity(a->{assertEquals(2,field(a,"tab"));assertEquals(new LinkedHashSet<>(Arrays.asList("a","b","c")),field(a,"playing"));assertEquals(155,field(a,"bid"));assertNull(field(a,"hukum"));assertNull(field(a,"outcome"));assertTrue(((Set<?>)field(a,"partners")).isEmpty());assertNull(field(a,"pendingRoundId"));});
            onView(withText("New Hukum Round")).check(matches(isDisplayed()));Tasks.await(d.getReference().delete(),20,TimeUnit.SECONDS);
        }
    }
}
