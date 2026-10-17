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
        Thread.sleep(3800); // Allow transient Android toasts to leave the captured frame.
        instrumentation.waitForIdleSync();android.graphics.Bitmap screenshot=instrumentation.getUiAutomation().takeScreenshot();
        if(screenshot==null)throw new AssertionError("Screenshot unavailable");
        java.io.File directory=new java.io.File(instrumentation.getTargetContext().getFilesDir(),"verification");
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
            onView(withText("+310")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("0")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withText("✓  Save Round to Firebase")).perform(scrollTo());capture("08-score-preview");
            onView(withText("✓  Save Round to Firebase")).perform(click());onView(withText("Save Round")).perform(click());
            waitForDialog("Round saved successfully");onView(withText("Share Result")).perform(click());waitForDialog("3 Patti PRO - Round Result");capture("09-share-round");onView(withText("Close")).perform(click());
            scenario.onActivity(activity->{java.util.List<NativeImageShare.RankRow> rows=new java.util.ArrayList<>();for(int i=0;i<75;i++)rows.add(new NativeImageShare.RankRow(i%3==0?"Synthetic player with a forty-letter name "+i:"Synthetic Player "+i,100-i,10,5,5,3,4));NativeImageShare.leaderboard(activity,"Synthetic pagination test",rows);});
            waitForDialog("3 Patti PRO - Rankings");onView(withText("1 / 3")).check(matches(isDisplayed()));onView(withText("Next")).perform(click());onView(withText("2 / 3")).check(matches(isDisplayed()));
            onView(withContentDescription("Scroll full share image")).perform(swipeUp()).check((view,error)->{if(error!=null)throw error;org.junit.Assert.assertTrue("Long image must scroll at full preview width",view.getScrollY()>0);});
            capture("10-share-ranks-page-2");
            if(android.os.Build.VERSION.SDK_INT>=29){
                int before=galleryCount();onView(withText("Save Images")).perform(click());long deadline=System.currentTimeMillis()+30000;
                while(galleryCount()<before+3&&System.currentTimeMillis()<deadline)Thread.sleep(100);
                org.junit.Assert.assertEquals("All three ranking pages must be saved to the gallery",before+3,galleryCount());
            }else onView(withText("Close")).perform(click());
        }
    }
    private android.view.View findText(android.view.View view,String label){
        if(view instanceof android.widget.TextView&&label.contentEquals(((android.widget.TextView)view).getText()))return view;
        if(view instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)view;for(int i=0;i<group.getChildCount();i++){android.view.View found=findText(group.getChildAt(i),label);if(found!=null)return found;}}
        return null;
    }
    @Test public void restoredLoginDuringDialogShowDoesNotCrash()throws Exception{
        Assume.assumeTrue(BuildConfig.USE_EMULATORS);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            scenario.onActivity(activity->{
                activity.authChanged(false);
                android.view.View login=findText(activity.getWindow().getDecorView(),"LOGIN");org.junit.Assert.assertNotNull(login);
                login.performClick();
                // Auth restoration can arrive before the dialog's queued onShow callback.
                activity.authChanged(true);
            });
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync();waitFor("ADMIN");
        }
    }
    private String shell(String command)throws Exception{
        android.os.ParcelFileDescriptor descriptor=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
        try(java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] buffer=new byte[1024];int count;while((count=in.read(buffer))!=-1)out.write(buffer,0,count);return out.toString("UTF-8").trim();
        }
    }
    @Test public void navigationAndSettingsRemainUsableWithLargeText()throws Exception{
        Assume.assumeTrue(BuildConfig.USE_EMULATORS);String original=shell("settings get system font_scale");
        try{
            shell("settings put system font_scale 2.0");
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
                waitFor("Table Status");onView(withContentDescription("Players")).perform(click());
                onView(org.hamcrest.Matchers.allOf(withText("Players"),withParent(withContentDescription("Players")))).check((view,error)->{if(error!=null)throw error;android.widget.TextView label=(android.widget.TextView)view;org.junit.Assert.assertEquals("Navigation label must not break inside its word",1,label.getLineCount());org.junit.Assert.assertEquals("Navigation label must remain complete",label.length(),label.getLayout().getLineEnd(0));});
                onView(withContentDescription("More options")).perform(click());onView(withText("Settings")).perform(click());
                onView(withText("Compact round history")).perform(scrollTo()).check(matches(isDisplayed()));capture("11-settings-large-text");
                scenario.recreate();onView(withContentDescription("More options")).check(matches(isDisplayed()));
                onView(withText("Export JSON backup")).perform(scrollTo()).check(matches(isDisplayed()));
            }
        }finally{shell("null".equals(original)?"settings delete system font_scale":"settings put system font_scale "+original);}
    }
}
