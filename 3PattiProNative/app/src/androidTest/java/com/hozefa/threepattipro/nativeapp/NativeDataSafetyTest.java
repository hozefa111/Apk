package com.hozefa.threepattipro.nativeapp;
import android.os.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;

public class NativeDataSafetyTest {
 private String oldMotion;
 private Object get(MainActivity a,String key){try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);return f.get(a);}catch(Exception e){throw new AssertionError(e);}}
 private void set(MainActivity a,String key,Object value){try{java.lang.reflect.Field f=MainActivity.class.getDeclaredField(key);f.setAccessible(true);f.set(a,value);}catch(Exception e){throw new AssertionError(e);}}
 private Object invoke(MainActivity a,String name,Class<?>[] types,Object...args){try{java.lang.reflect.Method m=MainActivity.class.getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(a,args);}catch(Exception e){throw new AssertionError(e);}}
 private void onUi(Runnable action)throws Exception{AtomicReference<Throwable> failure=new AtomicReference<>();CountDownLatch done=new CountDownLatch(1);new Handler(Looper.getMainLooper()).post(()->{try{action.run();}catch(Throwable e){failure.set(e);}finally{done.countDown();}});assertTrue(done.await(10,TimeUnit.SECONDS));if(failure.get()!=null)throw new AssertionError(failure.get());}
 private Map<String,Object> m(Object...v){return FirebaseGateway.map(v);}
 private List<Map<String,Object>> players(){return new ArrayList<>(Arrays.asList(m("id","a","name","Alice","active",true),m("id","b","name","Bob","active",true),m("id","c","name","Cara","active",true)));}
 private Map<String,Object> round(String id,String hukum,String partner,int bid,String result){List<String> ids=Arrays.asList("a","b","c"),other=new ArrayList<>(ids);other.remove(hukum);other.remove(partner);return m("id",id,"source","manual","type","hukum","sessionId","s","timestamp","2026-10-20T01:00:00Z","bid",bid,"hukumId",hukum,"partnerIds",Arrays.asList(partner),"nonPartnerIds",other,"playerIds",ids,"result",result,"scoreChanges",ScoreRules.calculate(ids,bid,hukum,Arrays.asList(partner),result));}
 private List<Map<String,Object>> rounds(){return new ArrayList<>(Arrays.asList(round("loss","c","a",155,"loss"),round("win","a","b",180,"win")));}
 private Map<String,Object> session(){Map<String,Object> s=SessionSummary.compute(players(),rounds());s.putAll(m("id","s","sessionName","Corrected session","startTime","2026-10-20T00:00:00Z","endTime","2026-10-20T02:00:00Z"));return s;}
 private void seed(MainActivity a,List<Map<String,Object>> rows,Map<String,Object> session){((FirebaseGateway)get(a,"api")).stop();set(a,"loggedIn",true);set(a,"ranksSessions",false);set(a,"profileId","a");a.historyDeferred(false);a.syncState(true);a.dataUpdated(players(),rows,new ArrayList<>(Arrays.asList(session)));}
 @Before public void isolate(){Assume.assumeTrue(BuildConfig.USE_EMULATORS);android.content.Context c=InstrumentationRegistry.getInstrumentation().getTargetContext();assertTrue(c.getPackageName().endsWith(".sandbox"));oldMotion=c.getSharedPreferences("preferences",0).getString("motion","full");c.getSharedPreferences("preferences",0).edit().putString("motion","off").commit();}
 @After public void restore(){if(oldMotion!=null)InstrumentationRegistry.getInstrumentation().getTargetContext().getSharedPreferences("preferences",0).edit().putString("motion",oldMotion).commit();}
 @Test public void correctedManualSummaryAndSavedModelsChangeTogetherAfterDeletion()throws Exception{
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){Map<String,Object> archive=session();AtomicReference<ScoreboardModel> before=new AtomicReference<>();
   s.onActivity(a->{seed(a,rounds(),archive);ScoreboardModel first=(ScoreboardModel)invoke(a,"scoreboardModel",new Class<?>[]{boolean.class},false);before.set(first);assertSame(first,invoke(a,"scoreboardModel",new Class<?>[]{boolean.class},true));assertSame(first,invoke(a,"scoreboardModel",new Class<?>[]{boolean.class},false));assertEquals("b",first.rows.get(0).id);});
   s.onActivity(a->{a.dataUpdated(players(),new ArrayList<>(Arrays.asList(rounds().get(1))),new ArrayList<>(Arrays.asList(archive)));ScoreboardModel after=(ScoreboardModel)invoke(a,"scoreboardModel",new Class<?>[]{boolean.class},false);assertNotSame(before.get(),after);assertEquals("a",after.rows.get(0).id);assertEquals(360,after.rows.get(0).points);Map<?,?> corrected=(Map<?,?>)invoke(a,"effectiveSession",new Class<?>[]{Map.class},archive);assertEquals(1,corrected.get("totalRounds"));assertEquals("Alice",((Map<?,?>)corrected.get("mvp")).get("name"));assertEquals(2,archive.get("totalRounds"));});
  }
 }
 @Test public void analyticsDropsStaleWorkAndFinishesAfterBackgroundResume()throws Exception{
  CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicReference<MainActivity> activity=new AtomicReference<>();
  try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){
   Map<String,Object> archive=session();s.onActivity(a->{activity.set(a);seed(a,rounds(),archive);((ThreadPoolExecutor)get(a,"analyticsWorker")).execute(()->{entered.countDown();try{release.await(20,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}});});assertTrue(entered.await(10,TimeUnit.SECONDS));
   s.onActivity(a->{set(a,"tab",7);invoke(a,"render",new Class<?>[]{boolean.class},true);a.dataUpdated(players(),new ArrayList<>(Arrays.asList(rounds().get(1))),new ArrayList<>(Arrays.asList(archive)));});
   onView(withText("Loading analytics…")).check(matches(isDisplayed()));s.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);release.countDown();
   long until=System.currentTimeMillis()+20000;AtomicBoolean ready=new AtomicBoolean();while(System.currentTimeMillis()<until&&!ready.get()){onUi(()->ready.set(get(activity.get(),"analyticsCache")!=null));Thread.sleep(50);}assertTrue(ready.get());onUi(()->assertEquals(1,((AdvancedAnalytics.Report)get(activity.get(),"analyticsCache")).rounds.size()));
   s.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);onView(withText("Round scope · All sessions")).check(matches(isDisplayed()));onUi(()->assertNotNull(get(activity.get(),"analyticsRenderedKey")));
  }finally{release.countDown();}
 }
}
