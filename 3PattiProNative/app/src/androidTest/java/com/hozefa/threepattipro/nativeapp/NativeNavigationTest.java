package com.hozefa.threepattipro.nativeapp;
import org.junit.*;
import androidx.test.core.app.ActivityScenario;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;

public class NativeNavigationTest {
    private void waitFor(String text)throws Exception{
        long deadline=System.currentTimeMillis()+90000;Throwable last=null;
        while(System.currentTimeMillis()<deadline){try{onView(withText(text)).check(matches(isDisplayed()));return;}catch(androidx.test.espresso.NoMatchingViewException|AssertionError e){last=e;Thread.sleep(500);}}
        throw new AssertionError("Missing "+text,last);
    }
    @Test public void nativeScreensSearchAndSettings()throws Exception{
        Assume.assumeTrue(BuildConfig.USE_EMULATORS);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitFor("Table Status");
            for(String screen:new String[]{"Players","Ranks","History","Home"})onView(withText(screen)).perform(click());
            onView(withContentDescription("More options")).perform(click());onView(withText("Search")).perform(click());
            onView(withHint("Name, bid, result or session")).perform(typeText("Player"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            onView(withText("♣  Player a")).check(matches(isDisplayed()));
            onView(withContentDescription("More options")).perform(click());onView(withText("Analytics")).perform(click());onView(withText("Table Analytics")).check(matches(isDisplayed()));
            onView(withContentDescription("More options")).perform(click());onView(withText("Share Studio")).perform(click());onView(withText("CARD STYLE")).check(matches(isDisplayed()));
            onView(withContentDescription("More options")).perform(click());onView(withText("Settings")).perform(click());onView(withText("Compact round history")).check(matches(isDisplayed()));
            scenario.recreate();onView(withContentDescription("More options")).check(matches(isDisplayed()));
        }
    }
    @Test public void fullRoundFormSaveAndSharePreview()throws Exception{
        Assume.assumeTrue(BuildConfig.USE_EMULATORS);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitFor("Table Status");
            try{onView(withText("LOGIN")).perform(click());onView(withHint("Admin email")).perform(typeText("tester@example.test"));onView(withHint("Password")).perform(typeText("test-password-123"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());onView(withText("Login")).perform(click());waitFor("ADMIN");}catch(androidx.test.espresso.NoMatchingViewException signedIn){}
            onView(withText("Round")).perform(click());
            onView(withContentDescription("Hukum Player a")).perform(scrollTo(),click());
            onView(withContentDescription("Partner Player b")).perform(scrollTo(),click());
            onView(withText("✓   Hukum Win")).perform(scrollTo(),click());
            onView(withText("+310")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("✓  Save Round to Firebase")).perform(scrollTo(),click());onView(withText("Save Round")).perform(click());
            waitFor("Round saved successfully");onView(withText("Share Result")).perform(click());waitFor("3 Patti PRO - Round Result");onView(withText("Close")).perform(click());
            scenario.onActivity(activity->{java.util.List<NativeImageShare.RankRow> rows=new java.util.ArrayList<>();for(int i=0;i<75;i++)rows.add(new NativeImageShare.RankRow("Synthetic Player "+i,100-i,10,5,5,3,4));NativeImageShare.leaderboard(activity,"Synthetic pagination test",rows);});
            waitFor("3 Patti PRO - Rankings");onView(withText("1 / 3")).check(matches(isDisplayed()));onView(withText("Next")).perform(click());onView(withText("2 / 3")).check(matches(isDisplayed()));onView(withText("Close")).perform(click());
        }
    }
}
