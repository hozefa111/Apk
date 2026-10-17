package com.hozefa.threepattipro.nativeapp;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static org.hamcrest.Matchers.*;

public class NativeDialogTest {
    private static final String LONG_NAME="Synthetic player with a forty-letter name";
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private void waitFor(String text)throws Exception{
        long end=System.currentTimeMillis()+90000;Throwable last=null;
        while(System.currentTimeMillis()<end){try{onView(withText(text)).check(matches(isDisplayed()));return;}catch(RuntimeException|AssertionError e){last=e;Thread.sleep(200);}}
        throw new AssertionError("Missing "+text,last);
    }
    private void invoke(MainActivity a,String name,Class<?>[] types,Object... args){
        try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(a,args);}catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private List<Map<String,Object>> players(){return new ArrayList<>(Arrays.asList(FirebaseGateway.map("id","a","name",LONG_NAME,"active",true),FirebaseGateway.map("id","b","name","Partner with a very long synthetic name","active",true),FirebaseGateway.map("id","c","name","Opponent with a very long synthetic name","active",true)));}
    private Map<String,Object> round(int i){
        boolean win=i%2==0;
        return FirebaseGateway.map("id",String.format(java.util.Locale.ROOT,"dialog-%03d",i),"type","hukum","timestamp",java.time.Instant.now().minusSeconds(i*3600L).toString(),
                "playerIds",Arrays.asList("a","b","c"),"hukumId","a","hukumName",LONG_NAME,"partnerIds",Arrays.asList("b"),"partnerNames",Arrays.asList("Partner with a very long synthetic name"),
                "nonPartnerIds",Arrays.asList("c"),"nonPartnerNames",Arrays.asList("Opponent with a very long synthetic name"),"bid",155,"result",win?"win":"loss","scoreChanges",FirebaseGateway.map("a",win?310:-155,"b",win?155:-78,"c",win?0:155));
    }
    private List<Map<String,Object>> fixture(){List<Map<String,Object>> rows=new ArrayList<>();for(int i=0;i<47;i++)rows.add(round(i));return rows;}
    private void seed(MainActivity a,List<Map<String,Object>> rounds){
        assertTrue(BuildConfig.USE_EMULATORS);
        try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField("api");f.setAccessible(true);((FirebaseGateway)f.get(a)).stop();}catch(ReflectiveOperationException e){throw new AssertionError(e);}
        a.snapshotContinuity(false);a.syncState(true);a.dataUpdated(players(),new ArrayList<>(rounds),new ArrayList<>());
    }
    private void role(MainActivity a,List<Map<String,Object>> rows){invoke(a,"showRoleRounds",new Class<?>[]{String.class,String.class,List.class},"a","hukum",rows);}
    private void capture(String name)throws Exception{
        Thread.sleep(700);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        android.graphics.Bitmap b=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(b);
        java.io.File d=new java.io.File(context().getFilesDir(),"verification");assertTrue(d.isDirectory()||d.mkdirs());
        try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(d,name+".png"))){assertTrue(b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{b.recycle();}
    }
    @Test public void fortySevenRoundHistoryUsesCardsAndKeepsEveryRoundReachable()throws Exception{
        Assume.assumeTrue(BuildConfig.USE_EMULATORS);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitFor("Table Status");List<Map<String,Object>> rows=fixture();scenario.onActivity(a->{seed(a,rows);role(a,rows);});
            waitFor("Hukum · 47 rounds");onView(withText("Close")).check(matches(isCompletelyDisplayed()));
            onView(withContentDescription("Role round history")).check((view,error)->{if(error!=null)throw error;ListView list=(ListView)view;assertEquals("Header plus every round",48,list.getAdapter().getCount());});capture("15-role-history-cards");
            onData(anything()).inAdapterView(withContentDescription("Role round history")).atPosition(47).check(matches(isDisplayed()));
            onView(withText("Close")).check(matches(isCompletelyDisplayed()));capture("16-role-history-last");
            onData(anything()).inAdapterView(withContentDescription("Role round history")).atPosition(47).perform(click());waitFor("Round details");
            onView(withText("+310")).perform(scrollTo()).check((view,error)->{if(error!=null)throw error;assertEquals(DialogUi.GREEN,((TextView)view).getCurrentTextColor());});capture("17-round-detail-cards");
            onView(withText("Close")).check(matches(isCompletelyDisplayed())).perform(click());waitFor("Hukum · 47 rounds");onView(withText("Close")).perform(click());
        }
    }
    @Test public void detailsKeepNegativeAndZeroScoresDistinctWithLongNames()throws Exception{
        Assume.assumeTrue(BuildConfig.USE_EMULATORS);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            waitFor("Table Status");scenario.onActivity(a->{seed(a,fixture());invoke(a,"roundDetails",new Class<?>[]{Map.class},round(1));});
            waitFor("Round details");onView(withText("-78")).perform(scrollTo()).check((view,error)->{if(error!=null)throw error;assertEquals(DialogUi.RED,((TextView)view).getCurrentTextColor());});
            onView(withText("Close")).check(matches(isCompletelyDisplayed())).perform(click());
            scenario.onActivity(a->invoke(a,"roundDetails",new Class<?>[]{Map.class},round(0)));
            onView(withText("0")).perform(scrollTo()).check((view,error)->{if(error!=null)throw error;assertEquals(DialogUi.MUTED,((TextView)view).getCurrentTextColor());});capture("18-round-detail-zero");onView(withText("Close")).perform(click());
            onView(withContentDescription("Ranks")).perform(click());onView(withText("Custom")).perform(scrollTo(),click());
            onView(withText("Start Date")).perform(scrollTo(),click());onView(withText("Cancel")).check(matches(isCompletelyDisplayed()));capture("21-datepicker-themed");onView(withText("Cancel")).perform(click());
        }
    }
    private String shell(String command)throws Exception{
        android.os.ParcelFileDescriptor fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);
        try(java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] bytes=new byte[1024];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);return out.toString("UTF-8").trim();}
    }
    @Test public void largeFontLandscapeKeepsHistoryAndShareActionsOnScreen()throws Exception{
        Assume.assumeTrue(BuildConfig.USE_EMULATORS);String old=shell("settings get system font_scale");
        try{
            shell("settings put system font_scale 2.0");long end=System.currentTimeMillis()+10000;while(Math.abs(context().getResources().getConfiguration().fontScale-2f)>.01f&&System.currentTimeMillis()<end)Thread.sleep(100);
            assertEquals(2f,context().getResources().getConfiguration().fontScale,.01f);
            try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
                try{
                scenario.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
                AtomicBoolean rotated=new AtomicBoolean();end=System.currentTimeMillis()+15000;
                while(!rotated.get()&&System.currentTimeMillis()<end){scenario.onActivity(a->rotated.set(a.getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE));Thread.sleep(100);}assertTrue(rotated.get());
                List<Map<String,Object>> rows=fixture();scenario.onActivity(a->{seed(a,rows);role(a,rows);});waitFor("Hukum · 47 rounds");
                onData(anything()).inAdapterView(withContentDescription("Role round history")).atPosition(47).check(matches(isDisplayed()));onView(withText("Close")).check(matches(isCompletelyDisplayed()));capture("19-role-landscape-large-font");onView(withText("Close")).perform(click());
                scenario.onActivity(a->{List<NativeImageShare.RankRow> ranks=new ArrayList<>();for(int i=0;i<75;i++)ranks.add(new NativeImageShare.RankRow(LONG_NAME+i,100-i,10,5,5,3,4));NativeImageShare.leaderboard(a,"Landscape large text",ranks);});
                waitFor("3 Patti PRO - Rankings");onView(withText("Close")).check(matches(isCompletelyDisplayed()));onView(withText("Save Images")).check(matches(isCompletelyDisplayed()));onView(withText("Next")).check(matches(isCompletelyDisplayed())).perform(click());onView(withText("2 / 3")).check(matches(isDisplayed()));capture("20-share-landscape-large-font");onView(withText("Close")).perform(click());
                }finally{
                    scenario.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));AtomicBoolean restored=new AtomicBoolean();long resetEnd=System.currentTimeMillis()+15000;
                    while(!restored.get()&&System.currentTimeMillis()<resetEnd){scenario.onActivity(a->restored.set(a.getResources().getConfiguration().orientation==Configuration.ORIENTATION_PORTRAIT));Thread.sleep(100);}assertTrue(restored.get());
                }
            }
        }finally{shell("null".equals(old)?"settings delete system font_scale":"settings put system font_scale "+old);}
    }
}
