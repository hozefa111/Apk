package com.hozefa.threepattipro.nativeapp;

import android.content.Context;import android.os.SystemClock;import android.view.*;import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;import androidx.test.platform.app.InstrumentationRegistry;
import com.google.firebase.*;import com.google.firebase.auth.FirebaseAuth;import com.google.android.gms.tasks.Tasks;
import org.json.*;import org.junit.*;import static org.junit.Assert.*;
import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicReference;

/** Real injected finger gestures against the bundled UI and demo backend only. */
public class LobbyScrollTest {
 private WebView find(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){WebView w=find(((ViewGroup)v).getChildAt(i));if(w!=null)return w;}return null;}
 private String js(ActivityScenario<OnlineGameActivity> s,String code)throws Exception{CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> out=new AtomicReference<>();s.onActivity(a->find(a.getWindow().getDecorView()).evaluateJavascript(code,v->{out.set(v);latch.countDown();}));assertTrue(latch.await(15,TimeUnit.SECONDS));return out.get();}
 private void await(ActivityScenario<OnlineGameActivity> s,String condition)throws Exception{long end=System.currentTimeMillis()+60000;while(System.currentTimeMillis()<end){if("true".equals(js(s,condition)))return;Thread.sleep(150);}fail("Condition: "+condition+"; "+js(s,"JSON.stringify({error,body:document.body.className,scrollY,viewport:innerHeight,content:document.documentElement.scrollHeight})"));}
 @Before public void signInDemoAdmin()throws Exception{
  assertTrue(BuildConfig.USE_EMULATORS);Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("kaliOnlineBackend")));
  Context ctx=InstrumentationRegistry.getInstrumentation().getTargetContext();FirebaseApp app;
  try{app=FirebaseApp.getInstance("three-patti-native");}catch(IllegalStateException e){app=FirebaseApp.initializeApp(ctx,new FirebaseOptions.Builder().setProjectId("demo-three-patti").setApiKey("demo-key").setApplicationId("1:570573461138:web:4414d0ce87282b91b2f84b").build(),"three-patti-native");FirebaseAuth.getInstance(app).useEmulator("10.0.2.2",9099);}
  Tasks.await(FirebaseAuth.getInstance(app).signInWithEmailAndPassword("tester@example.test","test-password-123"),30,TimeUnit.SECONDS);
 }
 private void event(long down,int action,float x,float y){MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);e.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);InstrumentationRegistry.getInstrumentation().sendPointerSync(e);e.recycle();}
 private float[] bounds(ActivityScenario<OnlineGameActivity> s){float[] b=new float[4];s.onActivity(a->{WebView w=find(a.getWindow().getDecorView());int[] xy=new int[2];w.getLocationOnScreen(xy);b[0]=xy[0];b[1]=xy[1];b[2]=w.getWidth();b[3]=w.getHeight();});return b;}
 private void swipe(ActivityScenario<OnlineGameActivity> s,boolean up)throws Exception{float[] b=bounds(s);float x=b[0]+b[2]*.46f,y=b[1]+b[3]*(up?.76f:.3f),end=b[1]+b[3]*(up?.3f:.76f);long down=SystemClock.uptimeMillis();event(down,MotionEvent.ACTION_DOWN,x,y);for(int i=1;i<=24;i++){Thread.sleep(16);event(down,MotionEvent.ACTION_MOVE,x,y+(end-y)*i/24);}event(down,MotionEvent.ACTION_UP,x,end);Thread.sleep(180);}
 private void tap(ActivityScenario<OnlineGameActivity> s,String selector)throws Exception{JSONArray r=new JSONArray(js(s,"(()=>{const r=document.querySelector("+JSONObject.quote(selector)+").getBoundingClientRect();return [r.x+r.width/2,r.y+r.height/2,innerWidth,innerHeight]})()"));float[] b=bounds(s);assertTrue("Tap target inside viewport",r.getDouble(1)>0&&r.getDouble(1)<r.getDouble(3));float x=b[0]+(float)(r.getDouble(0)/r.getDouble(2))*b[2],y=b[1]+(float)(r.getDouble(1)/r.getDouble(3))*b[3];long down=SystemClock.uptimeMillis();event(down,MotionEvent.ACTION_DOWN,x,y);Thread.sleep(60);event(down,MotionEvent.ACTION_UP,x,y);}
 private void create(ActivityScenario<OnlineGameActivity> s,int mode)throws Exception{
  await(s,"document.documentElement.dataset.nativeIdentity==='ready' && admin");js(s,"if(room)leaveRoomView()");await(s,"!!document.querySelector('#create')");
  js(s,"document.querySelector('#create input[name=sessionName]').value='Touch lobby "+mode+"';document.querySelector('#create select[name=playerId]').value='a';document.querySelector('#create select[name=mode]').value='"+mode+"';document.querySelector('#create button.gold').scrollIntoView({block:'center'});");Thread.sleep(250);tap(s,"#create button.gold");await(s,"room?.mode==="+mode+" && room.status==='lobby' && !!document.querySelector('.lobby-seats') && !busy");
  js(s,"window._scrollCalls=0;window._originalScrollTo=window.scrollTo;window.scrollTo=function(){window._scrollCalls++;return window._originalScrollTo.apply(this,arguments)};window._seatNode=document.querySelector('.lobby-seats');window._updates=setInterval(()=>{if(room&&room.status==='lobby'){room.revision++;room.sessionName='Touch lobby '+room.revision;renderNow();}},40)");
 }
 @Test public void sixSeatLobbyFingerScrollSurvivesLiveUpdates()throws Exception{
  try(ActivityScenario<OnlineGameActivity> s=ActivityScenario.launch(OnlineGameActivity.class)){
   create(s,6);js(s,"window._originalScrollTo(0,0)");Thread.sleep(250);
   for(int i=0;i<3;i++)swipe(s,true);
   android.util.Log.i("LobbyTouch",js(s,"JSON.stringify({scrollY,calls:_scrollCalls,height:innerHeight,content:document.documentElement.scrollHeight,body:document.body.className,same:document.querySelector('.lobby-seats')===_seatNode})"));
   assertEquals("Finger swipe must scroll the lobby","true",js(s,"scrollY>150"));
   assertEquals("Live updates must not imperatively reset finger scroll","0",js(s,"_scrollCalls"));
   assertEquals("All six seats remain reachable","true",js(s,"(()=>{const r=document.querySelector('.seat-card:last-child').getBoundingClientRect();return r.top<innerHeight&&r.bottom>0})()"));
   for(int i=0;i<3;i++)swipe(s,false);assertEquals("Can swipe back up","true",js(s,"scrollY<100"));
   js(s,"clearInterval(_updates);window.scrollTo=_originalScrollTo");
  }
 }
 @Test public void primaryReadyIsVisibleWithoutScrolling()throws Exception{
  try(ActivityScenario<OnlineGameActivity> s=ActivityScenario.launch(OnlineGameActivity.class)){
   create(s,6);assertEquals("Ready must be in the visible action area","true",js(s,"(()=>{const r=document.querySelector('[data-action=ready]').getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight})()"));js(s,"clearInterval(_updates)");
  }
 }
}
