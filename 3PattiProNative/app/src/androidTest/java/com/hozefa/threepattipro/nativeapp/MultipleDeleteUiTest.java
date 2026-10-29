package com.hozefa.threepattipro.nativeapp;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;

/** Selection/confirmation only. Production writes are impossible in these fixtures. */
public class MultipleDeleteUiTest {
    private void set(MainActivity a,String name,Object value){try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,value);}catch(Exception e){throw new AssertionError(e);}}
    private Object get(MainActivity a,String name){try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}catch(Exception e){throw new AssertionError(e);}}
    private List<Map<String,Object>> list(Map<String,Object>...rows){return new ArrayList<>(Arrays.asList(rows));}
    private void seed(MainActivity a,int tab,int history){
        ((FirebaseGateway)get(a,"api")).stop();set(a,"tab",tab);set(a,"historyTab",history);set(a,"loggedIn",true);set(a,"busy",false);
        a.dataUpdated(list(FirebaseGateway.map("id","a","name","Alpha"),FirebaseGateway.map("id","b","name","Beta")),
            list(FirebaseGateway.map("id","r1","timestamp","2026-10-20T10:00:00Z","type","hukum","hukumName","Alpha","bid",155,"scoreChanges",FirebaseGateway.map("a",310)),FirebaseGateway.map("id","r2","timestamp","2026-10-20T11:00:00Z","type","hukum","hukumName","Beta","bid",180,"scoreChanges",FirebaseGateway.map("a",-180)),FirebaseGateway.map("id","r3","source","online","timestamp","2026-10-20T12:00:00Z","type","hukum","hukumName","Immutable online","bid",200,"scoreChanges",FirebaseGateway.map("a",400))),
            list(FirebaseGateway.map("id","s1","sessionName","First session","startTime","2026-10-20T10:00:00Z","endTime","2026-10-20T11:00:00Z"),FirebaseGateway.map("id","s2","sessionName","Second session","startTime","2026-10-21T10:00:00Z","endTime","2026-10-21T11:00:00Z"),FirebaseGateway.map("id","s3","sessionName","Active session","startTime","2026-10-22T10:00:00Z")));
    }
    @Before public void isolated(){Assume.assumeTrue(BuildConfig.USE_EMULATORS);InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("preferences",0).edit().putString("motion","off").apply();}
    @Test public void roundsSelectAllReviewExactPointEffectAndCancelWithoutDeletion()throws Exception{
        try(ActivityScenario<MainActivity>s=ActivityScenario.launch(MainActivity.class)){
            NativeTestUi.awaitMotionIdle(s);s.onActivity(a->seed(a,4,0));
            onView(withText("Delete Multiple Rounds")).perform(scrollTo(),click());
            onView(withText("Review")).check(matches(org.hamcrest.Matchers.not(isEnabled())));
            onView(withText("Select all")).perform(click());onView(withText("Review (2)")).perform(click());
            onView(withText(org.hamcrest.Matchers.containsString("Alpha: -130"))).check(matches(isDisplayed()));
            onView(withText("Keep")).perform(click());s.onActivity(a->assertEquals(3,((List<?>)get(a,"rounds")).size()));
        }
    }
    @Test public void activeSessionsExcludedAndPlayerSelectionCanBeCancelled()throws Exception{
        try(ActivityScenario<MainActivity>s=ActivityScenario.launch(MainActivity.class)){
            NativeTestUi.awaitMotionIdle(s);s.onActivity(a->seed(a,4,1));onView(withText("Delete Multiple Sessions")).perform(scrollTo(),click());
            onView(withText("Select all")).perform(click());onView(withText("Review (2)")).check(matches(isEnabled()));onView(withText("Cancel")).perform(click());
            s.onActivity(a->seed(a,1,0));onView(withText("Delete Multiple Players")).perform(scrollTo(),click());onView(withText("Select all")).perform(click());onView(withText("Review (2)")).perform(click());
            onView(withText(org.hamcrest.Matchers.containsString("Saved rounds remain unchanged"))).check(matches(isDisplayed()));onView(withText("Continue")).perform(click());onView(withText("Final confirmation")).check(matches(isDisplayed()));onView(withText("Cancel")).perform(click());
            s.onActivity(a->assertEquals(2,((List<?>)get(a,"players")).size()));
        }
    }
}
