package com.hozefa.threepattipro.nativeapp;
import android.view.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;

/** Local fixtures; production scoring and data are untouched. */
public class NativeScoreboardTest {
    private Object get(MainActivity a,String n){try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField(n);f.setAccessible(true);return f.get(a);}catch(Exception e){throw new AssertionError(e);}}
    private void set(MainActivity a,String n,Object v){try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField(n);f.setAccessible(true);f.set(a,v);}catch(Exception e){throw new AssertionError(e);}}
    private void await(String text)throws Exception{long end=System.currentTimeMillis()+30000;Throwable last=null;while(System.currentTimeMillis()<end){try{onView(withText(text)).check(matches(isDisplayed()));return;}catch(RuntimeException|AssertionError e){last=e;Thread.sleep(150);}}throw new AssertionError(text,last);}
    private void see(String text)throws Exception{Thread.sleep(700);onView(withText(text)).perform(scrollTo()).check(matches(isDisplayed()));}
    private void seed(MainActivity a,boolean tie,boolean session){assertTrue(BuildConfig.USE_EMULATORS);((FirebaseGateway)get(a,"api")).stop();set(a,"tab",3);set(a,"rankFilter","all");set(a,"scoreboardScope","all");a.snapshotContinuity(false);a.syncState(true);
        List<Map<String,Object>> p=new ArrayList<>();for(String id:Arrays.asList("a","b","c","d"))p.add(FirebaseGateway.map("id",id,"name","Score player "+id,"active",true));
        Map<String,Object> r=FirebaseGateway.map("id","score-fixture","type","hukum","timestamp","2026-10-20T10:00:00Z","sessionId","current","playerIds",Arrays.asList("a","b","c","d"),"hukumId","a","partnerIds",Arrays.asList("b"),"result","win","scoreChanges",FirebaseGateway.map("a",170,"b",tie?170:100,"c",tie?170:50,"d",tie?170:0));
        List<Map<String,Object>> sessions=session?new ArrayList<>(Arrays.asList(FirebaseGateway.map("id","current","startTime","2026-10-20T00:00:00Z","endTime",null,"sessionName","Fixture session"))):new ArrayList<>();a.dataUpdated(p,new ArrayList<>(Arrays.asList(r)),sessions);
    }
    private void capture(String name)throws Exception{Thread.sleep(600);android.app.Instrumentation i=InstrumentationRegistry.getInstrumentation();i.waitForIdleSync();android.graphics.Bitmap b=i.getUiAutomation().takeScreenshot();assertNotNull(b);java.io.File d=new java.io.File(i.getTargetContext().getFilesDir(),"verification");assertTrue(d.isDirectory()||d.mkdirs());try(java.io.OutputStream out=new java.io.FileOutputStream(new java.io.File(d,name+".png"))){assertTrue(b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out));}finally{b.recycle();}}
    @Before public void isolated(){Assume.assumeTrue(BuildConfig.USE_EMULATORS);}
    @Test public void tiesHaveJointCrownsAndTheFourthPlayerRemainsReachable()throws Exception{
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){await("Table Status");s.onActivity(a->seed(a,true,true));see("ROYAL TOP 3");see("+1 more tied at #1 · View full ranking");capture("44-royal-podium-ties");
            onView(withContentDescription("Scorecard Score player d")).perform(scrollTo()).check(matches(isDisplayed()));
            onView(withContentDescription("Home")).perform(click());see("ROYAL TOP 3");
        }
    }
    @Test public void bigScoreboardKeepsScopeAcrossRotationAndClearsWakeOnExit()throws Exception{
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){await("Table Status");s.onActivity(a->seed(a,false,true));see("Big Scoreboard");onView(withText("Big Scoreboard")).perform(click());await("Big Scoreboard");
            see("Screen on · Off");onView(withText("Screen on · Off")).perform(click());see("Screen on · On");
            s.onActivity(a->assertTrue((a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0));capture("45-big-scoreboard");s.recreate();await("Big Scoreboard");see("Screen on · On");
            s.onActivity(a->a.onBackPressed());Thread.sleep(700);s.onActivity(a->assertEquals(get(a,"bigOriginScroll"),((android.widget.ScrollView)get(a,"scroll")).getScrollY()));see("Leaderboard");s.onActivity(a->{assertEquals(3,get(a,"tab"));assertFalse((a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0);});
        }
    }
    @Test public void currentSessionWithoutActiveSessionShowsNoInventedScores()throws Exception{
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){await("Table Status");s.onActivity(a->seed(a,false,false));see("Current Session");onView(withText("Current Session")).perform(click());see("No active session");see("No players with rounds in this scope.");capture("46-scoreboard-empty-session");}
    }
    @Test public void scoreCardsShowPersonalChangeAndSessionAndOfflineBigState()throws Exception{
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){await("Table Status");s.onActivity(a->seed(a,false,true));see("Last played round  +100");see("70 points behind leader");
            see("Big Scoreboard");onView(withText("Big Scoreboard")).perform(click());await("Big Scoreboard");s.onActivity(a->a.syncState(false));see("Offline · saved scores");capture("47-scoreboard-offline");
            s.onActivity(a->a.onBackPressed());Thread.sleep(700);s.onActivity(a->assertEquals(get(a,"bigOriginScroll"),((android.widget.ScrollView)get(a,"scroll")).getScrollY()));see("Leaderboard");
        }
    }
    private View find(View v,String description){if(description.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=find(((ViewGroup)v).getChildAt(i),description);if(found!=null)return found;}return null;}
    private void textFits(View v){
        if(v instanceof android.widget.TextView){android.widget.TextView t=(android.widget.TextView)v;android.text.Layout l=t.getLayout();assertNotNull(l);assertEquals("No hidden lines: "+t.getText(),0,l.getEllipsisCount(l.getLineCount()-1));
            assertTrue("Text height: "+t.getText(),l.getHeight()<=t.getHeight()-t.getCompoundPaddingTop()-t.getCompoundPaddingBottom()+1);
            for(int n=0;n<l.getLineCount();n++)assertTrue("Text width: "+t.getText(),l.getLineWidth(n)<=t.getWidth()-t.getCompoundPaddingLeft()-t.getCompoundPaddingRight()+1);
        }
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++)textFits(((ViewGroup)v).getChildAt(i));
    }
    @Test public void compactPodiumHasNoClippedNamesOrScoresAndOpensProfiles()throws Exception{
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){await("Table Status");s.onActivity(a->{seed(a,false,true);
            List<Map<String,Object>> p=Arrays.asList(FirebaseGateway.map("id","a","name","Abbas","active",true),FirebaseGateway.map("id","b","name","Shabbir","active",true),FirebaseGateway.map("id","c","name","Fatema","active",true));
            Map<String,Object> r=FirebaseGateway.map("id","podium-preview","timestamp","2026-10-20T10:00:00Z","sessionId","current","type","hukum","playerIds",Arrays.asList("a","b","c"),"scoreChanges",FirebaseGateway.map("a",40836,"b",31481,"c",24033));
            a.dataUpdated(p,new ArrayList<>(Arrays.asList(r)),new ArrayList<>((List<Map<String,Object>>)get(a,"sessions")));
        });see("ROYAL TOP 3");capture("49-polished-podium");
            s.onActivity(a->{for(String name:Arrays.asList("Abbas","Shabbir","Fatema")){View card=find(a.getWindow().getDecorView(),"Podium "+name);assertNotNull(card);textFits(card);assertTrue("Compact podium",card.getHeight()<DialogUi.dp(a,225));}});
            onView(withContentDescription("Podium Shabbir")).perform(scrollTo(),click());s.onActivity(a->assertEquals("b",get(a,"profileId")));
        }
    }
    @Test public void longNamesAndLongSignedScoresRemainReadable()throws Exception{
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){await("Table Status");s.onActivity(a->{seed(a,false,true);
            List<Map<String,Object>> p=Arrays.asList(FirebaseGateway.map("id","a","name","A very long player name","active",true),FirebaseGateway.map("id","b","name","Second long player name","active",true),FirebaseGateway.map("id","c","name","Negative score player","active",true));
            Map<String,Object> r=FirebaseGateway.map("id","long-preview","timestamp","2026-10-20T10:00:00Z","sessionId","current","type","hukum","playerIds",Arrays.asList("a","b","c"),"scoreChanges",FirebaseGateway.map("a",4000000000L,"b",3000000000L,"c",-4000000000L));
            a.dataUpdated(p,new ArrayList<>(Arrays.asList(r)),new ArrayList<>((List<Map<String,Object>>)get(a,"sessions")));
        });see("ROYAL TOP 3");capture("50-polished-long-scores");
            s.onActivity(a->{for(String name:Arrays.asList("A very long player name","Second long player name","Negative score player"))textFits(find(a.getWindow().getDecorView(),"Podium "+name));});
        }
    }
    private String shell(String cmd)throws Exception{android.os.ParcelFileDescriptor fd=InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(cmd);try(java.io.InputStream in=new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8").trim();}}
    @Test public void largeTextLandscapeKeepsScoreboardControlsReachable()throws Exception{
        String original=shell("settings get system font_scale");
        try{
            shell("settings put system font_scale 2.0");Thread.sleep(1000);
            try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
                await("Table Status");s.onActivity(a->seed(a,false,true));see("ROYAL TOP 3");capture("51-polished-large-text-podium");
                s.onActivity(a->{for(String name:Arrays.asList("Score player a","Score player b","Score player c"))textFits(find(a.getWindow().getDecorView(),"Podium "+name));});see("Big Scoreboard");onView(withText("Big Scoreboard")).perform(click());await("Big Scoreboard");
                s.onActivity(a->a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));Thread.sleep(1500);see("Screen on · Off");capture("48-big-scoreboard-large-text");
                see("Close Big Scoreboard");onView(withText("Close Big Scoreboard")).perform(click());see("Leaderboard");
                s.onActivity(a->a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));Thread.sleep(1200);
            }
        }finally{shell("null".equals(original)?"settings delete system font_scale":"settings put system font_scale "+original);Thread.sleep(1000);}
    }
}
