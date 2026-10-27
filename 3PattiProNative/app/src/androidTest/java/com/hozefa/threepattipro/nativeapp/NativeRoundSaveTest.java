package com.hozefa.threepattipro.nativeapp;

import android.content.Context;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;

/** Real native taps and Firestore emulator saves. Never runs against production. */
public class NativeRoundSaveTest {
    private static final String EXTRA="round-save-extra";
    private Context context;
    private String originalDraft;
    private FirebaseFirestore store;
    private Set<String> before;
    private Object field(MainActivity a,String name){try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}catch(Exception e){throw new AssertionError(e);}}
    private void await(ActivityScenario<MainActivity> s,java.util.function.Predicate<MainActivity> condition)throws Exception{
        long end=System.currentTimeMillis()+30000;AtomicBoolean ok=new AtomicBoolean();
        while(System.currentTimeMillis()<end){s.onActivity(a->ok.set(condition.test(a)));if(ok.get())return;Thread.sleep(100);}fail("Native round state did not settle");
    }
    private Set<String> ids()throws Exception{Set<String> ids=new HashSet<>();for(DocumentSnapshot d:Tasks.await(store.collection("rounds").get(Source.SERVER),20,TimeUnit.SECONDS))ids.add(d.getId());return ids;}
    @Before public void isolated(){
        Assume.assumeTrue(BuildConfig.USE_EMULATORS);context=InstrumentationRegistry.getInstrumentation().getTargetContext();assertTrue(context.getPackageName().endsWith(".sandbox"));
        originalDraft=context.getSharedPreferences("preferences",0).getString("draft",null);assertTrue(context.getSharedPreferences("preferences",0).edit().remove("draft").commit());
    }
    @After public void cleanup()throws Exception{
        if(store!=null&&before!=null){for(String id:ids())if(!before.contains(id))Tasks.await(store.collection("rounds").document(id).delete(),20,TimeUnit.SECONDS);Tasks.await(store.collection("players").document(EXTRA).delete(),20,TimeUnit.SECONDS);}
        if(context!=null){android.content.SharedPreferences.Editor e=context.getSharedPreferences("preferences",0).edit();if(originalDraft==null)e.remove("draft");else e.putString("draft",originalDraft);assertTrue(e.commit());}
    }
    private void open(ActivityScenario<MainActivity> s)throws Exception{
        FirebaseApp app=FirebaseApp.getInstance("three-patti-native");Tasks.await(FirebaseAuth.getInstance(app).signInWithEmailAndPassword("tester@example.test","test-password-123"),20,TimeUnit.SECONDS);
        store=FirebaseFirestore.getInstance(app);before=ids();assertFalse(Tasks.await(store.collection("players").document(EXTRA).get(Source.SERVER),20,TimeUnit.SECONDS).exists());
        Tasks.await(store.collection("players").document(EXTRA).set(FirebaseGateway.map("name","Round save extra","active",true)),20,TimeUnit.SECONDS);
        ready(s);await(s,a->((List<?>)field(a,"players")).size()==4);NativeTestUi.awaitMotionIdle(s);
        onView(withContentDescription("Round")).perform(click());NativeTestUi.awaitMotionIdle(s);
        onView(withContentDescription("Playing Round save extra")).perform(scrollTo(),click());
        s.onActivity(a->assertEquals(new LinkedHashSet<>(Arrays.asList("a","b","c")),field(a,"playing")));
    }
    private void ready(ActivityScenario<MainActivity> s)throws Exception{await(s,a->(Boolean)field(a,"liveConnected")&&!(Boolean)field(a,"busy")&&(Boolean)field(a,"loggedIn"));}
    private void fill(String hukum,String partner,boolean win)throws Exception{
        onView(withContentDescription("Hukum "+hukum)).perform(scrollTo(),click());onView(withContentDescription("Partner "+partner)).perform(scrollTo(),click());
        onView(withText(win?"✓   Hukum Win":"✕   Hukum Lose")).perform(scrollTo(),click());
    }
    private void bid180(){onView(withContentDescription("Choose bid amount")).perform(scrollTo(),click());onView(withText("180")).inRoot(isDialog()).perform(click());}
    private void submit(){onView(withText("✓  Save Round to Firebase")).perform(scrollTo(),click());onView(withText("Save Round")).inRoot(isDialog()).perform(click());}
    private void saved(ActivityScenario<MainActivity> s)throws Exception{
        await(s,a->field(a,"pendingRoundId")==null&&!(Boolean)field(a,"roundSaving"));
        onView(withText("Round saved successfully")).inRoot(isDialog()).check(matches(isDisplayed()));
        s.onActivity(a->{assertEquals(2,field(a,"tab"));assertEquals(new LinkedHashSet<>(Arrays.asList("a","b","c")),field(a,"playing"));assertEquals(155,field(a,"bid"));assertNull(field(a,"hukum"));assertNull(field(a,"outcome"));assertTrue(((Set<?>)field(a,"partners")).isEmpty());assertNull(field(a,"pendingRoundJson"));assertNull(field(a,"pendingRoundKey"));});
        onView(withText("Not Now")).inRoot(isDialog()).perform(click());onView(withText("New Hukum Round")).check(matches(isDisplayed()));
        onView(withContentDescription("Playing Round save extra")).check(matches(withText("○   Round save extra")));
    }
    @Test public void manualSaveRetainsPlayersResetsFieldsAndAcceptsNextRound()throws Exception{
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            open(s);bid180();fill("Player a","Player b",true);submit();saved(s);assertEquals(before.size()+1,ids().size());
            ready(s);fill("Player c","Player a",false);submit();saved(s);assertEquals(before.size()+2,ids().size());
            onView(withContentDescription("More options")).perform(click());onView(withText("Settings")).inRoot(isDialog()).perform(click());
            onView(withText("Discard local round draft")).perform(scrollTo(),click());onView(withText("Discard draft")).inRoot(isDialog()).perform(click());s.onActivity(a->assertNull(field(a,"playing")));
        }
    }
    private void failedThenRetry(boolean serverAlreadySaved)throws Exception{
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            open(s);bid180();fill("Player a","Player b",true);
            s.onActivity(a->((FirebaseGateway)field(a,"api")).tokenLookup=(user,force)->force?Tasks.forException(new java.io.IOException("Synthetic failed confirmation")):user.getIdToken(false));
            submit();await(s,a->field(a,"pendingRoundId")!=null&&!(Boolean)field(a,"roundSaving")&&!(Boolean)field(a,"busy"));
            final String[] draft=new String[3];s.onActivity(a->{draft[0]=(String)field(a,"pendingRoundId");draft[1]=(String)field(a,"pendingRoundJson");draft[2]=(String)field(a,"pendingRoundTimestamp");assertEquals(2,field(a,"tab"));assertEquals(180,field(a,"bid"));assertEquals("a",field(a,"hukum"));assertEquals("win",field(a,"outcome"));assertEquals(new LinkedHashSet<>(Arrays.asList("b")),field(a,"partners"));});
            assertEquals(before,ids());onView(withContentDescription("Hukum Player c")).perform(scrollTo(),click());s.onActivity(a->assertEquals("a",field(a,"hukum")));
            if(serverAlreadySaved){Map<String,Object> committed=JsonValues.object(new org.json.JSONObject(draft[1]));committed.put("source","manual");Tasks.await(store.collection("rounds").document(draft[0]).set(committed),20,TimeUnit.SECONDS);}
            s.recreate();ready(s);s.onActivity(a->{assertEquals(draft[0],field(a,"pendingRoundId"));assertEquals(draft[1],field(a,"pendingRoundJson"));assertEquals(draft[2],field(a,"pendingRoundTimestamp"));});
            onView(withContentDescription("More options")).perform(click());onView(withText("Settings")).inRoot(isDialog()).perform(click());onView(withText("Retry unconfirmed round")).perform(scrollTo(),click());onView(withText("Retry")).inRoot(isDialog()).perform(click());saved(s);
            assertEquals(before.size()+1,ids().size());assertTrue(ids().contains(draft[0]));assertEquals(Long.valueOf(180),Tasks.await(store.collection("rounds").document(draft[0]).get(Source.SERVER),20,TimeUnit.SECONDS).getLong("bid"));
            s.recreate();ready(s);assertEquals(before.size()+1,ids().size());s.onActivity(a->{assertNull(field(a,"pendingRoundId"));assertEquals(2,field(a,"tab"));});
        }
    }
    @Test public void failedSaveRetainsOriginalDraftAndRetriesOnce()throws Exception{failedThenRetry(false);}
    @Test public void lostConfirmationRetryUsesAlreadyCommittedIdWithoutDuplicate()throws Exception{failedThenRetry(true);}
}
