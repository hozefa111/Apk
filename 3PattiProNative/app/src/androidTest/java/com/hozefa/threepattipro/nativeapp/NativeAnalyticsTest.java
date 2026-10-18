package com.hozefa.threepattipro.nativeapp;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;
import static org.hamcrest.Matchers.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.*;

/** Synthetic native views only. No analytics fixture is written to Firebase. */
public class NativeAnalyticsTest {
    private static final String A="Alpha with a very long synthetic player name",B="Beta synthetic partner",C="Gamma synthetic opponent";
    private Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    private void awaitText(String text)throws Exception{long until=System.currentTimeMillis()+20000;Throwable last=null;while(System.currentTimeMillis()<until){try{onView(withText(text)).check(matches(isDisplayed()));return;}catch(RuntimeException|AssertionError e){last=e;Thread.sleep(150);}}throw new AssertionError(text,last);}
    private void field(MainActivity a,String name,Object value){try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);f.set(a,value);}catch(Exception e){throw new AssertionError(e);}}
    private List<Map<String,Object>> roster(){return new ArrayList<>(Arrays.asList(FirebaseGateway.map("id","a","name",A,"active",true),FirebaseGateway.map("id","b","name",B,"active",true),FirebaseGateway.map("id","c","name",C,"active",true)));}
    private List<Map<String,Object>> rounds(){List<Map<String,Object>> out=new ArrayList<>();for(int i=0;i<30;i++){String result=i%3==0?"loss":"win";int bid=155+i%4*5;out.add(FirebaseGateway.map("id","analytics-"+i,"type","hukum","timestamp",Instant.parse("2026-01-01T09:00:00Z").plusSeconds(i*900L).toString(),"sessionId",i<15?"old":"current","hukumId","a","hukumName",A,"partnerIds",Arrays.asList("b"),"partnerNames",Arrays.asList(B),"nonPartnerIds",Arrays.asList("c"),"nonPartnerNames",Arrays.asList(C),"playerIds",Arrays.asList("a","b","c"),"bid",bid,"result",result,"scoreChanges",ScoreRules.calculate(Arrays.asList("a","b","c"),bid,"a",Arrays.asList("b"),result)));}return out;}
    private List<Map<String,Object>> sessions(){return new ArrayList<>(Arrays.asList(FirebaseGateway.map("id","old","sessionName","Earlier table","startTime","2026-01-01T09:00:00Z","endTime","2026-01-01T12:45:00Z"),FirebaseGateway.map("id","current","sessionName","Latest table","startTime","2026-01-01T12:45:00Z","endTime",null)));}
    private void seed(MainActivity a,boolean empty){
        assertTrue(BuildConfig.USE_EMULATORS);assertTrue(a.getPackageName().endsWith(".sandbox"));
        try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField("api");f.setAccessible(true);((FirebaseGateway)f.get(a)).stop();}catch(Exception e){throw new AssertionError(e);}
        field(a,"tab",7);field(a,"rankFilter","all");a.snapshotContinuity(false);a.syncProblem(null,0,false);a.syncState(true);a.dataUpdated(roster(),empty?new ArrayList<>():rounds(),sessions());
    }
    private void view(int index){onView(withText(startsWith("View · "))).perform(scrollTo(),click());onData(equalTo(AdvancedAnalyticsPage.SECTIONS[index])).inRoot(isDialog()).perform(click());onView(withText("View · "+AdvancedAnalyticsPage.SECTIONS[index])).check(matches(isDisplayed()));}
    private void capture(String name)throws Exception{Thread.sleep(700);InstrumentationRegistry.getInstrumentation().waitForIdleSync();android.graphics.Bitmap b=InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();assertNotNull(b);java.io.File d=new java.io.File(context().getFilesDir(),"verification");assertTrue(d.isDirectory()||d.mkdirs());try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(d,name+".png"))){assertTrue(b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{b.recycle();}}
    @Test public void everyAdvancedViewRendersAndKeepsFiltersReachable()throws Exception{
        Assume.assumeTrue(BuildConfig.USE_EMULATORS);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            awaitText("Table Status");scenario.onActivity(a->seed(a,false));
            for(int i=0;i<AdvancedAnalyticsPage.SECTIONS.length;i++){
                view(i);onView(withText("Round scope · All sessions")).check(matches(isDisplayed()));
                if(i==1)onView(withText(startsWith("Compare with · "))).perform(scrollTo()).check(matches(isDisplayed()));
                if(i==2)onView(withText("Hukum performance")).perform(scrollTo()).check(matches(isDisplayed()));
                if(i==3)onView(withText("Your Hukum bids")).perform(scrollTo()).check(matches(isDisplayed()));
                if(i==4)onView(withText("Hukum-team partnerships")).perform(scrollTo()).check(matches(isDisplayed()));
                if(i==5)onView(withText("Recent form · Last 10 played rounds")).perform(scrollTo()).check(matches(isDisplayed()));
                if(i==6)onView(withText("Comeback & lead tracker")).perform(scrollTo()).check(matches(isDisplayed()));
                if(i==7)onView(withText("Session difference · First minus second")).perform(scrollTo()).check(matches(isDisplayed()));
                if(i==8)onView(withText("Rounds that moved the table")).perform(scrollTo()).check(matches(isDisplayed()));
                if(i==9)onView(withText("Personal records")).perform(scrollTo()).check(matches(isDisplayed()));
                if(i==10)onView(withText("Auto insights · "+A)).perform(scrollTo()).check(matches(isDisplayed()));
                capture(String.format(Locale.ROOT,"%02d-analytics-%s",24+i,new String[]{"overview","comparison","roles","bids","partnerships","form","leads","sessions","impact","records","insights"}[i]));
            }
            onView(withText(startsWith("View · "))).perform(scrollTo(),click());onView(withText("Cancel")).inRoot(isDialog()).perform(click());
        }
    }
    @Test public void playerChartAndSelectionsSurviveActivityRecreation()throws Exception{
        Assume.assumeTrue(BuildConfig.USE_EMULATORS);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            awaitText("Table Status");scenario.onActivity(a->seed(a,false));view(1);
            onView(withText(startsWith("Compare with · "))).perform(scrollTo(),click());onData(equalTo(C)).inRoot(isDialog()).perform(click());
            onView(withText("Compare with · "+C)).check(matches(isDisplayed()));
            onView(withContentDescription(startsWith("Score chart."))).perform(scrollTo(),click());
            onView(withContentDescription(startsWith("Score chart."))).check(matches(withContentDescription(containsString("Gamma"))));capture("38-analytics-comparison-chart");
            scenario.recreate();scenario.onActivity(a->seed(a,false));awaitText("View · Player comparison");onView(withText("Compare with · "+C)).check(matches(isDisplayed()));
            view(5);onView(withText("Last 5")).perform(scrollTo(),click());onView(withText("Recent form · Last 5 played rounds")).perform(scrollTo()).check(matches(isDisplayed()));
            scenario.recreate();scenario.onActivity(a->seed(a,false));awaitText("View · Form & consistency");onView(withText("Recent form · Last 5 played rounds")).perform(scrollTo()).check(matches(isDisplayed()));onView(withContentDescription(startsWith("Score chart."))).perform(scrollTo());capture("39-analytics-form-chart");
        }
    }
    @Test public void sessionScopeAndEmptyHistoryDoNotInventStats()throws Exception{
        Assume.assumeTrue(BuildConfig.USE_EMULATORS);
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            awaitText("Table Status");scenario.onActivity(a->seed(a,false));onView(withText("Round scope · All sessions")).perform(scrollTo(),click());onData(equalTo("Previous completed session")).inRoot(isDialog()).perform(click());
            onView(withText("Round scope · Previous completed session")).check(matches(isDisplayed()));onView(withText("15")).perform(scrollTo()).check(matches(isDisplayed()));
            scenario.onActivity(a->seed(a,true));awaitText("No rounds in this scope");capture("35-analytics-empty");
            onView(withText(startsWith("View · "))).perform(scrollTo(),click());onData(equalTo("Auto insights")).inRoot(isDialog()).perform(click());onView(withText("No rounds in this scope")).perform(scrollTo()).check(matches(isDisplayed()));
            scenario.onActivity(a->{field(a,"hasData",false);a.syncProblem("Synthetic offline connection",0,false);});awaitText("Analytics needs a loaded or saved table.");capture("40-analytics-no-cache");
        }
    }
    private String shell(String command)throws Exception{android.os.ParcelFileDescriptor f=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(command);try(java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(f);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] data=new byte[1024];int n;while((n=in.read(data))!=-1)out.write(data,0,n);return out.toString("UTF-8").trim();}}
    @Test public void analyticsControlsAndLongNamesRemainUsableAtLargeFontLandscape()throws Exception{
        Assume.assumeTrue(BuildConfig.USE_EMULATORS);String old=shell("settings get system font_scale");
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            awaitText("Table Status");
            try{
                shell("settings put system font_scale 2.0");long end=System.currentTimeMillis()+10000;while(Math.abs(context().getResources().getConfiguration().fontScale-2f)>.01f&&System.currentTimeMillis()<end)Thread.sleep(100);
                assertEquals(2f,context().getResources().getConfiguration().fontScale,.01f);scenario.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));AtomicBoolean rotated=new AtomicBoolean();end=System.currentTimeMillis()+15000;while(System.currentTimeMillis()<end){scenario.onActivity(a->rotated.set(a.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE));if(rotated.get())break;Thread.sleep(100);}assertTrue(rotated.get());
                scenario.onActivity(a->seed(a,false));view(1);onView(withText(startsWith("Compare with · "))).perform(scrollTo()).check(matches(isCompletelyDisplayed()));capture("36-analytics-large-comparison");
                view(5);onView(withText("Last 20")).perform(scrollTo(),click());onView(withText("Recent form · Last 20 played rounds")).perform(scrollTo()).check(matches(isDisplayed()));capture("37-analytics-large-form");
            }finally{shell("settings put system font_scale "+(old.matches("[0-9.]+")?old:"1.0"));scenario.onActivity(a->a.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));}
        }
    }
}
