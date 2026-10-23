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
 private void tap(ActivityScenario<OnlineGameActivity> s,String selector)throws Exception{
  long focusEnd=System.currentTimeMillis()+15000;java.util.concurrent.atomic.AtomicBoolean focused=new java.util.concurrent.atomic.AtomicBoolean();
  do{s.onActivity(a->focused.set(a.getWindow().getDecorView().hasWindowFocus()));if(focused.get())break;Thread.sleep(100);}while(System.currentTimeMillis()<focusEnd);
  assertTrue("Activity must own input focus",focused.get());InstrumentationRegistry.getInstrumentation().waitForIdleSync();Thread.sleep(150);
  JSONArray r=new JSONArray(js(s,"(()=>{const r=document.querySelector("+JSONObject.quote(selector)+").getBoundingClientRect();return [r.x+r.width/2,r.y+r.height/2,innerWidth,innerHeight]})()"));float[] b=bounds(s);assertTrue("Tap target inside viewport",r.getDouble(1)>0&&r.getDouble(1)<r.getDouble(3));float x=b[0]+(float)(r.getDouble(0)/r.getDouble(2))*b[2],y=b[1]+(float)(r.getDouble(1)/r.getDouble(3))*b[3];android.util.Log.i("LobbyTouch","tap "+selector+" at "+x+","+y+" web="+java.util.Arrays.toString(b)+" css="+r.toString());long down=SystemClock.uptimeMillis();event(down,MotionEvent.ACTION_DOWN,x,y);Thread.sleep(60);event(down,MotionEvent.ACTION_UP,x,y);}
 private void create(ActivityScenario<OnlineGameActivity> s,int mode)throws Exception{
  await(s,"document.documentElement.dataset.nativeIdentity==='ready' && admin");js(s,"if(room)leaveRoomView()");await(s,"!!document.querySelector('#create')");
  js(s,"document.querySelector('#create input[name=sessionName]').value='Touch lobby "+mode+"';document.querySelector('#create select[name=playerId]').value='a';document.querySelector('#create select[name=mode]').value='"+mode+"';document.querySelector('#create button.gold').scrollIntoView({block:'center'});");Thread.sleep(250);tap(s,"#create button.gold");await(s,"room?.mode==="+mode+" && room.status==='lobby' && !!document.querySelector('.lobby-seats') && !busy");
  js(s,"window._scrollCalls=0;window._originalScrollTo=window.scrollTo;window.scrollTo=function(){window._scrollCalls++;return window._originalScrollTo.apply(this,arguments)};window._seatNode=document.querySelector('.lobby-seats');window._updates=setInterval(()=>{if(room&&room.status==='lobby'){room.revision++;room.sessionName='Touch lobby '+room.revision;renderNow();}},40)");
 }
 private void checkScroll(ActivityScenario<OnlineGameActivity> s,int mode)throws Exception{
  js(s,"window._scrollCalls=0;window._originalScrollTo=window.scrollTo;window.scrollTo=function(){window._scrollCalls++;return window._originalScrollTo.apply(this,arguments)};window._seatNode=document.querySelector('.lobby-seats');window._updates=setInterval(()=>{if(room&&room.status==='lobby'){room.revision++;room.sessionName='Touch lobby '+room.revision;renderNow();}},40);window._originalScrollTo(0,0)");Thread.sleep(250);
  for(int i=0;i<3;i++)swipe(s,true);
  android.util.Log.i("LobbyTouch",js(s,"JSON.stringify({mode:room.mode,scrollY,calls:_scrollCalls,height:innerHeight,content:document.documentElement.scrollHeight,body:document.body.className,same:document.querySelector('.lobby-seats')===_seatNode})"));
  assertEquals("Finger swipe must scroll the lobby","true",js(s,"scrollY>100"));
  assertEquals("Refresh must not cancel finger scroll","0",js(s,"_scrollCalls"));
  assertEquals("Last seat remains reachable","true",js(s,"(()=>{const r=document.querySelector('.seat-card:last-child').getBoundingClientRect(),dock=document.querySelector('.lobby-ready').getBoundingClientRect();return r.top<dock.top&&r.bottom>0})()"));
  assertEquals("Lobby never gets the live-table lock","true",js(s,"!document.body.classList.contains('live-game') && !document.body.classList.contains('bidding') && document.documentElement.scrollWidth<=innerWidth+1"));
  for(int i=0;i<3;i++)swipe(s,false);
  assertEquals("Can swipe back up","true",js(s,"scrollY<100"));
  js(s,"clearInterval(_updates);window.scrollTo=_originalScrollTo;window._restoredRoom=false;db.doc('onlineRooms/'+room.id).get({source:'server'}).then(s=>{room=s.data();previous=room;renderNow();_restoredRoom=true})");await(s,"_restoredRoom");
 }
 private void visible(ActivityScenario<OnlineGameActivity> s,String selector)throws Exception{
  for(int i=0;i<10;i++){
   String where=js(s,"(()=>{const el=document.querySelector("+JSONObject.quote(selector)+");if(!el)return 'missing';const r=el.getBoundingClientRect(),top=document.querySelector('header').getBoundingClientRect().bottom,bottom=document.querySelector('.lobby-ready')?.getBoundingClientRect().top||innerHeight;return r.top<top?'up':r.bottom>bottom?'down':'visible'})()");
   if("\"visible\"".equals(where))return;assertNotEquals("Missing control "+selector,"\"missing\"",where);swipe(s,"\"down\"".equals(where));
  }fail("Could not reach "+selector);
 }
 private void flow(int mode)throws Exception{
  String roomId;
  try(ActivityScenario<OnlineGameActivity> s=ActivityScenario.launch(OnlineGameActivity.class)){
   create(s,mode);js(s,"clearInterval(_updates);window.scrollTo=_originalScrollTo");
   assertEquals("New lobby public state omits hand","false",js(s,"Object.prototype.hasOwnProperty.call(room,'hand')"));
   assertEquals("Ready visible without scrolling","true",js(s,"(()=>{const b=document.querySelector('[data-action=ready]'),r=b.getBoundingClientRect();return document.querySelectorAll('[data-action=ready]').length===1&&r.top>=0&&r.bottom<=innerHeight&&b.disabled})()"));
   checkScroll(s,mode);roomId=js(s,"room.id");
  }
  // Close the Activity/WebView, then open a new instance with persisted Auth and room.
  try(ActivityScenario<OnlineGameActivity> s=ActivityScenario.launch(OnlineGameActivity.class)){
   await(s,"document.documentElement.dataset.nativeIdentity==='ready' && room?.id==="+roomId+" && !!document.querySelector('.lobby-seats')");checkScroll(s,mode);
   for(int seat=2;seat<=mode;seat++){
    String selector="[data-action=\"bot:"+seat+"\"]";visible(s,selector);tap(s,selector);
    await(s,"!busy && Object.values(room.members).some(p=>p.kind==='bot'&&p.seat==="+seat+")");
   }
   await(s,"!document.querySelector('[data-action=ready]').disabled");
   assertEquals("Exactly one unobstructed Ready button","true",js(s,"(()=>{const b=document.querySelector('[data-action=ready]'),r=b.getBoundingClientRect();return document.querySelectorAll('[data-action=ready]').length===1&&r.top>=0&&r.bottom<=innerHeight&&document.elementFromPoint(r.x+r.width/2,r.y+r.height/2).closest('[data-action=ready]')===b})()"));
   tap(s,"[data-action=ready]");await(s,"!!KaliRules.active(room) && !!document.querySelector('.live-main') && own.cards.length==="+(mode==6?7:10));
   assertEquals("Correct UI revision in actual WebView","\"scroll-dock-v1\"",js(s,"document.documentElement.dataset.lobbyUi"));
   assertEquals("No stale service worker controls bundled assets","true",js(s,"!navigator.serviceWorker || !navigator.serviceWorker.controller"));
   tap(s,"[data-action=stop]");await(s,"!!document.querySelector('[data-choice=void]')");tap(s,"[data-choice=void]");await(s,"room.status==='stopped'");
   tap(s,"[data-action=home]");await(s,"!room && !!document.querySelector('#create') && !document.body.classList.contains('live-game') && !document.querySelector('.lobby-ready')");
  }
 }
 @Test public void sixSeatLobbyFingerScrollSurvivesLiveUpdates()throws Exception{flow(6);}
 @Test public void fourSeatLobbyFingerScrollAndReadyAfterReopen()throws Exception{flow(4);}
}
