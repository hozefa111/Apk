package com.hozefa.threepattipro.nativeapp;

import android.view.*;
import android.widget.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

/** UI-only callbacks with isolated synthetic data; no production Firebase operations. */
public class SyncBannerTest {
    private Object get(MainActivity a,String name){try{Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);return f.get(a);}catch(Exception e){throw new AssertionError(e);}}
    private View find(View root,String text){if(root instanceof TextView&&((TextView)root).getText().toString().equals(text))return root;if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){View found=find(g.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private void shown(MainActivity a,String text){View v=find(a.getWindow().getDecorView(),text);assertNotNull(text,v);assertEquals(text,View.VISIBLE,v.getVisibility());}
    private void absent(MainActivity a,String text){assertNull(text,find(a.getWindow().getDecorView(),text));}
    private void seed(MainActivity a){
        assertTrue(BuildConfig.USE_EMULATORS);((FirebaseGateway)get(a,"api")).stop();a.syncProblem(null,0,false);a.syncState(false);
        List<Map<String,Object>> p=new ArrayList<>();for(String id:Arrays.asList("a","b","c","d"))p.add(FirebaseGateway.map("id",id,"name","Banner player "+id,"active",true));
        List<Map<String,Object>> r=new ArrayList<>();r.add(FirebaseGateway.map("id","banner-round","type","hukum","timestamp","2026-10-20T10:00:00Z","playerIds",Arrays.asList("a","b","c","d"),"hukumId","a","partnerIds",Arrays.asList("b"),"result","win","scoreChanges",FirebaseGateway.map("a",360,"b",180,"c",0,"d",0)));
        a.dataUpdated(p,r,new ArrayList<>());a.syncTimestamp(1000);
    }
    @Test public void verifiedSyncClearsStaleBannerWithoutRebuildingHomeOrMovingScroll()throws Exception{
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            s.onActivity(this::seed);InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            final View[] page=new View[1],slot=new View[1];final int[] y=new int[1];
            s.onActivity(a->{shown(a,"Saved scores · synchronizing");LinearLayout c=(LinearLayout)get(a,"content");slot[0]=c.getChildAt(0);page[0]=c.getChildAt(1);ScrollView scroll=(ScrollView)get(a,"scroll");scroll.scrollTo(0,150);y[0]=scroll.getScrollY();a.syncTimestamp(2000);a.syncState(true);shown(a,"●  LIVE SYNC");absent(a,"Saved scores · synchronizing");assertEquals(View.GONE,slot[0].getVisibility());assertSame(page[0],c.getChildAt(1));});
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            s.onActivity(a->{assertEquals(y[0],((ScrollView)get(a,"scroll")).getScrollY());LinearLayout c=(LinearLayout)get(a,"content");a.syncState(true);a.syncTimestamp(3000);assertSame(page[0],c.getChildAt(1));assertSame(slot[0],c.getChildAt(0));assertEquals(0,((ViewGroup)slot[0]).getChildCount());assertEquals(3000L,get(a,"lastSyncedAt"));});
        }
    }
    @Test public void offlineQuotaAndReconnectKeepOneAccurateWarningAndCachedScores(){
        try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
            s.onActivity(a->{seed(a);a.syncState(true);View page=((LinearLayout)get(a,"content")).getChildAt(1);a.syncState(false);shown(a,"Saved scores · synchronizing");absent(a,"●  LIVE SYNC");a.syncProblem("Synthetic offline",0,false);shown(a,"Connection unavailable");absent(a,"Saved scores · synchronizing");shown(a,"Showing the last saved table. Changes are disabled until live sync returns.");a.syncProblem("Synthetic quota",System.currentTimeMillis()+300000,true);shown(a,"Sync paused · Firebase limit");shown(a,"○  SYNC PAUSED · LIMIT REACHED");a.syncState(true);absent(a,"●  LIVE SYNC");shown(a,"Sync paused · Firebase limit");a.syncProblem(null,0,false);shown(a,"●  LIVE SYNC");absent(a,"Sync paused · Firebase limit");absent(a,"Saved scores · synchronizing");assertSame(page,((LinearLayout)get(a,"content")).getChildAt(1));assertEquals(360,((Map<?,?>)get(a,"cachedScores")).get("a"));a.syncState(false);shown(a,"Saved scores · synchronizing");a.syncTimestamp(4000);a.syncState(true);absent(a,"Saved scores · synchronizing");shown(a,"●  LIVE SYNC");});
        }
    }
}
