package com.hozefa.threepattipro.nativeapp;
import org.junit.*;
import androidx.test.core.app.ActivityScenario;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;

public class NativeNavigationTest {
    private int galleryCount(){
        android.content.Context context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getTargetContext();
        try(android.database.Cursor cursor=context.getContentResolver().query(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,new String[]{"_id"},"relative_path = ? AND is_pending = 0",new String[]{"Pictures/3 Patti PRO/"},null)){return cursor==null?0:cursor.getCount();}
    }
    private void capture(String name)throws Exception{
        android.app.Instrumentation instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
        instrumentation.waitForIdleSync();android.graphics.Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();
        if(screenshot==null)throw new AssertionError("Screenshot unavailable");
        java.io.File directory=new java.io.File(instrumentation.getTargetContext().getExternalFilesDir(null),"verification");
        if(!directory.exists()&&!directory.mkdirs())throw new java.io.IOException("Cannot create screenshot directory");
        try(java.io.FileOutputStream output=new java.io.FileOutputStream(new java.io.File(directory,name+".png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,output);}finally{screenshot.recycle();}
    }
    private void waitFor(String text)throws Exception{
        long deadline=System.currentTimeMillis()+90000;Throwable last=null;
        while(System.currentTimeMillis()<deadline){try{onView(withText(text)).check(matches(isDisplayed()));return;}catch(RuntimeException|AssertionError e){last=e;Thread.sleep(500);}}
        throw new AssertionError("Missing "+text,last);
    }
    private void waitForDialog(String text)throws Exception{
        long deadline=System.currentTimeMillis()+90000;Throwable last=null;
        while(System.currentTimeMillis()<deadline){try{onView(withText(text)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(isDisplayed()));return;}catch(RuntimeException|AssertionError e){last=e;Thread.sleep(500);}}
        throw new AssertionError("Missing dialog "+text,last);
    }
    @Test public void nativeScreensSearchAndSettings()throws Exception{
        Assume.assumeTrue(BuildConfig.USE_EMULATORS);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitFor("Table Status");capture("01-home");
            for(String screen:new String[]{"Players","Ranks","History","Home"}){onView(withText(screen)).perform(click());capture("02-"+screen.toLowerCase(java.util.Locale.ROOT));}
            onView(withContentDescription("More options")).perform(click());onView(withText("Search")).perform(click());
            onView(withHint("Name, bid, result or session")).perform(typeText("Player"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());
            onView(withText("♣  Player a")).check(matches(isDisplayed()));capture("03-search");
            // Simulate a second client's realtime edit while the user is typing.
            org.junit.Assert.assertTrue(BuildConfig.USE_EMULATORS);
            com.google.firebase.firestore.FirebaseFirestore store=com.google.firebase.firestore.FirebaseFirestore.getInstance(com.google.firebase.FirebaseApp.getInstance("three-patti-native"));
            com.google.android.gms.tasks.Tasks.await(store.collection("players").document("a").update("name","Player a updated"),15,java.util.concurrent.TimeUnit.SECONDS);
            waitFor("♣  Player a updated");onView(withHint("Name, bid, result or session")).check(matches(hasFocus())).check(matches(withText("Player")));
            com.google.android.gms.tasks.Tasks.await(store.collection("players").document("a").update("name","Player a"),15,java.util.concurrent.TimeUnit.SECONDS);
            onView(withContentDescription("More options")).perform(click());onView(withText("Analytics")).perform(click());onView(withText("Table Analytics")).check(matches(isDisplayed()));capture("04-analytics");
            onView(withContentDescription("More options")).perform(click());onView(withText("Share Studio")).perform(click());onView(withText("CARD STYLE")).check(matches(isDisplayed()));capture("05-share-studio");
            onView(withContentDescription("More options")).perform(click());onView(withText("Settings")).perform(click());onView(withText("Compact round history")).check(matches(isDisplayed()));capture("06-settings");
            scenario.recreate();onView(withContentDescription("More options")).check(matches(isDisplayed()));
        }
    }
    @Test public void fullRoundFormSaveAndSharePreview()throws Exception{
        Assume.assumeTrue(BuildConfig.USE_EMULATORS);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitFor("Table Status");
            try{onView(withText("LOGIN")).perform(click());onView(withHint("Admin email")).perform(typeText("tester@example.test"));onView(withHint("Password")).perform(typeText("test-password-123"),androidx.test.espresso.action.ViewActions.closeSoftKeyboard());onView(withText("Login")).perform(click());waitFor("ADMIN");}catch(androidx.test.espresso.NoMatchingViewException signedIn){}
            onView(withText("Round")).perform(click());capture("07-round-form");
            onView(withContentDescription("Hukum Player a")).perform(scrollTo(),click());
            onView(withContentDescription("Partner Player b")).perform(scrollTo(),click());
            onView(withText("✓   Hukum Win")).perform(scrollTo(),click());
            onView(withText("+310")).perform(scrollTo()).check(matches(isDisplayed()));capture("08-score-preview");
            onView(withText("✓  Save Round to Firebase")).perform(scrollTo(),click());onView(withText("Save Round")).perform(click());
            waitForDialog("Round saved successfully");onView(withText("Share Result")).perform(click());waitForDialog("3 Patti PRO - Round Result");capture("09-share-round");onView(withText("Close")).perform(click());
            scenario.onActivity(activity->{java.util.List<NativeImageShare.RankRow> rows=new java.util.ArrayList<>();for(int i=0;i<75;i++)rows.add(new NativeImageShare.RankRow("Synthetic Player "+i,100-i,10,5,5,3,4));NativeImageShare.leaderboard(activity,"Synthetic pagination test",rows);});
            waitForDialog("3 Patti PRO - Rankings");onView(withText("1 / 3")).check(matches(isDisplayed()));onView(withText("Next")).perform(click());onView(withText("2 / 3")).check(matches(isDisplayed()));capture("10-share-ranks-page-2");
            if(android.os.Build.VERSION.SDK_INT>=29){
                int before=galleryCount();onView(withText("Save Images")).perform(click());long deadline=System.currentTimeMillis()+30000;
                while(galleryCount()<before+3&&System.currentTimeMillis()<deadline)Thread.sleep(100);
                org.junit.Assert.assertEquals("All three ranking pages must be saved to the gallery",before+3,galleryCount());
            }else onView(withText("Close")).perform(click());
        }
    }
}
