package com.hozefa.threepattipro.nativeapp;

import android.os.SystemClock;
import android.view.*;
import android.webkit.WebView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.junit.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Bundled production UI, isolated in the sandbox. Commands never leave this test. */
public class HybridWebViewTest {
 private OnlineGameActivity activity;
 private void onUi(Runnable action)throws Exception{CountDownLatch done=new CountDownLatch(1);new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{try{action.run();}finally{done.countDown();}});assertTrue("UI callback must remain responsive",done.await(15,TimeUnit.SECONDS));}
 private WebView web(View v){if(v instanceof WebView)return (WebView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){WebView w=web(((ViewGroup)v).getChildAt(i));if(w!=null)return w;}return null;}
 private String js(ActivityScenario<OnlineGameActivity> s,String code)throws Exception{CountDownLatch l=new CountDownLatch(1);AtomicReference<String> r=new AtomicReference<>();onUi(()->web(activity.getWindow().getDecorView()).evaluateJavascript(code,x->{r.set(x);l.countDown();}));assertTrue(l.await(15,TimeUnit.SECONDS));return r.get();}
 private void waitFor(ActivityScenario<OnlineGameActivity>s,String code)throws Exception{long until=SystemClock.uptimeMillis()+30000;while(SystemClock.uptimeMillis()<until){if("true".equals(js(s,code)))return;Thread.sleep(100);}fail("WebView condition: "+code+"; "+js(s,"document.body.innerText"));}
 // DOM existence is not a painted frame on a cold Android WebView compositor.
 private void paintedFrame()throws Exception{CountDownLatch ready=new CountDownLatch(1);onUi(()->{WebView w=web(activity.getWindow().getDecorView());w.postVisualStateCallback(0,new WebView.VisualStateCallback(){@Override public void onComplete(long requestId){w.postOnAnimation(()->w.postOnAnimation(ready::countDown));}});});assertTrue("Latest table layout must reach the compositor before a real touch",ready.await(15,TimeUnit.SECONDS));}
 private void touch(ActivityScenario<OnlineGameActivity>s,float fx,float fy,int action,long down)throws Exception{float[]xy=new float[2];onUi(()->{WebView w=web(activity.getWindow().getDecorView());int[]loc=new int[2];w.getLocationOnScreen(loc);xy[0]=loc[0]+fx*w.getWidth();xy[1]=loc[1]+fy*w.getHeight();});MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,xy[0],xy[1],0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);InstrumentationRegistry.getInstrumentation().sendPointerSync(e);e.recycle();}
 private void tap(ActivityScenario<OnlineGameActivity>s)throws Exception{JSONArray p=new JSONArray(js(s,"(()=>{const r=document.querySelector('.hand-card:not([disabled])').getBoundingClientRect();return [(r.x+r.width/2)/innerWidth,(r.y+r.height/2)/innerHeight]})()"));long d=SystemClock.uptimeMillis();touch(s,(float)p.getDouble(0),(float)p.getDouble(1),MotionEvent.ACTION_DOWN,d);Thread.sleep(25);touch(s,(float)p.getDouble(0),(float)p.getDouble(1),MotionEvent.ACTION_UP,d);}
 private void doubleTap(ActivityScenario<OnlineGameActivity>s)throws Exception{JSONArray p=new JSONArray(js(s,"(()=>{const r=document.querySelector('.hand-card:not([disabled])').getBoundingClientRect();return [(r.x+r.width/2)/innerWidth,(r.y+r.height/2)/innerHeight]})()"));float[] xy=new float[2];onUi(()->{WebView w=web(activity.getWindow().getDecorView());int[] loc=new int[2];w.getLocationOnScreen(loc);xy[0]=loc[0]+(float)p.optDouble(0)*w.getWidth();xy[1]=loc[1]+(float)p.optDouble(1)*w.getHeight();});for(int i=0;i<2;i++){long d=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(d,SystemClock.uptimeMillis(),action,xy[0],xy[1],0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);InstrumentationRegistry.getInstrumentation().sendPointerSync(e);e.recycle();Thread.sleep(25);}if(i==0)Thread.sleep(70);}}
 @Test(timeout=90000) public void silentAndConnectingSocketsRecoverWithoutLosingSeatCardsOrPendingMove()throws Exception{
  assertTrue(BuildConfig.USE_EMULATORS);org.junit.Assume.assumeTrue("Isolated backend required", "true".equals(InstrumentationRegistry.getArguments().getString("kaliOnlineBackend")));
  try(ActivityScenario<OnlineGameActivity>s=ActivityScenario.launch(OnlineGameActivity.class)){
   s.onActivity(a->activity=a);waitFor(s,"typeof attachCloudRoom==='function' && document.documentElement.dataset.nativeIdentity==='ready' && !window.KALI_NATIVE_PENDING && user && auth.currentUser && user.uid===auth.currentUser.uid");
   java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();try(java.io.InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("cloud-socket-fixture.js")){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);}js(s,bytes.toString("UTF-8"));
   for(int mode:new int[]{4,6})for(String scenario:new String[]{"silent","handshake","idle"}){
    js(s,"window.socketResult=null;window.socketFailure=null;socketRecoveryFixture("+mode+",'"+scenario+"').then(function(r){window.socketResult=r;},function(e){window.socketFailure=String(e.stack||e);});true;");waitFor(s,"!!window.socketResult || !!window.socketFailure");assertEquals("No fixture exception","null",js(s,"window.socketFailure"));
    org.json.JSONObject r=new org.json.JSONObject(js(s,"window.socketResult"));assertTrue(r.getBoolean("connected"));assertEquals(2,r.getInt("seat"));
    if(scenario.equals("idle")){assertEquals(1,r.getInt("sockets"));assertEquals(1,r.getInt("requests"));}else{assertTrue(r.getBoolean("reconnecting"));assertEquals(12,r.getInt("revision"));assertEquals("human",r.getString("uid"));assertTrue(r.getBoolean("sameCards"));assertTrue(r.getBoolean("samePending"));assertTrue(r.getBoolean("ticketsOnly"));assertEquals(mode,r.getInt("countMembers"));}
   }
  }
 }
 @Test(timeout=150000) public void bundledLobbyScrollAndDoubleTapRemainUsable()throws Exception{
  assertTrue(BuildConfig.USE_EMULATORS);org.junit.Assume.assumeTrue("Native identity handoff requires the isolated Functions backend", "true".equals(InstrumentationRegistry.getArguments().getString("kaliOnlineBackend")));android.util.Log.i("HybridTouch","launch");
  try(ActivityScenario<OnlineGameActivity>s=ActivityScenario.launch(OnlineGameActivity.class)){
   s.onActivity(a->activity=a);
   android.util.Log.i("HybridTouch","activity resumed");waitFor(s,"typeof renderNow==='function' && document.documentElement.dataset.nativeIdentity==='ready' && !window.KALI_NATIVE_PENDING && !!user && auth.currentUser && user.uid===auth.currentUser.uid");
   for(int mode:new int[]{4,6}){android.util.Log.i("HybridTouch","lobby "+mode);
    js(s,"(()=>{window.KALI_NATIVE_PENDING=false;clearCollection();resetCardTaps();unsubs.forEach(f=>f());unsubs=[];let t=1000,r=R.create({id:'cf2_0123456789abcdef0123456789abcdef',code:'V2ABCDEF',uid:'human',playerId:'p1',name:'Human',mode:"+mode+"},t);window.fixtureRoom=r;user={uid:'human',getIdToken:async()=>''};room=R.publicState(r);own=R.privateState(r,'human');previous=null;subscribedRoomId=r.id;uncertainCommand=null;busy=false;connectionReady=true;reduced=true;error='';screen='room';scoreOpen=false;historyOpen=false;renderNow();return true;})()");
    waitFor(s,"!!document.querySelector('.lobby-ready') || !!document.querySelector('[data-action=ready]')");
    assertEquals("Lobby must not retain the live table scroll lock","false",js(s,"document.body.classList.contains('live-game')"));
    android.util.Log.i("HybridTouch","scroll "+mode);long d=SystemClock.uptimeMillis();touch(s,.45f,.76f,MotionEvent.ACTION_DOWN,d);for(int i=1;i<=20;i++){Thread.sleep(16);touch(s,.45f,.76f-.43f*i/20,MotionEvent.ACTION_MOVE,d);}touch(s,.45f,.33f,MotionEvent.ACTION_UP,d);Thread.sleep(250);
    assertEquals("Lobby receives real finger scroll","true",js(s,"scrollY>15"));
    assertEquals("Ready dock is visible","true",js(s,"(()=>{const b=document.querySelector('[data-action=ready]').getBoundingClientRect();return b.y>=0&&b.bottom<=innerHeight+1})()"));
    android.util.Log.i("HybridTouch","playing "+mode);js(s,"(()=>{let r=fixtureRoom,t=Date.now();clockAnchor={server:t,mono:performance.now()};for(let seat=2;seat<="+mode+";seat++)r=R.command(r,'human',{type:'bot',seat,botId:'bot_'+seat},++t,n=>n-1);r=R.command(r,'human',{type:'ready'},++t,n=>n-1);while(r.hand.phase!=='playing')r=R.command(r,r.hand.turn,KaliBot.decide(R.publicState(r),r.hand.turn,R.privateState(r,r.hand.turn).cards),++t,n=>n-1);user={uid:r.hand.turn,getIdToken:async()=>''};room=R.publicState(r);previous=room;own=R.privateState(r,user.uid);selected=null;submittedCardTurn=null;uncertainCommand=null;busy=false;connectionReady=true;window.playCalls=[];window.fixtureApiReads=[];api=async b=>{if(b.action)playCalls.push(b);else fixtureApiReads.push(b.operation);return b.operation==='liveRooms'?{rooms:[]}:{};};renderNow();window.scrollTo(0,0);return true;})()");
    waitFor(s,"!!document.querySelector('.hand-card:not([disabled])')");paintedFrame();
    android.util.Log.i("HybridTouch","geometry "+js(s,"(()=>{const c=document.querySelector('.hand-card:not([disabled])'),r=c.getBoundingClientRect();window.fixtureEvents=[];for(const type of ['pointerdown','pointerup','pointercancel','click'])document.addEventListener(type,e=>fixtureEvents.push([e.type,e.clientX,e.clientY,e.target.className]),{capture:true,once:true});return {width:innerWidth,height:innerHeight,scale:visualViewport.scale,scroll:scrollY,rect:[r.x,r.y,r.width,r.height],target:String((document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)||{}).outerHTML).slice(0,200),pending:window.KALI_NATIVE_PENDING,canPlay:canPlayCards()};})()"));
    android.util.Log.i("HybridTouch","tap "+mode);tap(s);Thread.sleep(150);android.util.Log.i("HybridTouch","events "+js(s,"fixtureEvents"));waitFor(s,"!!document.querySelector('.hand-card.selected')");android.util.Log.i("HybridTouch","mutation kinds "+js(s,"playCalls.map(function(b){return b.action.type;})")+"; background reads "+js(s,"fixtureApiReads"));assertEquals("Single tap only selects; background reads are not card commands","0",js(s,"playCalls.length"));js(s,"resetCardTaps();selected=null;renderNow()");paintedFrame();doubleTap(s);waitFor(s,"playCalls.length===1");
    assertEquals("Only one command, correct card, no zoom","true",js(s,"playCalls[0].action.type==='play' && own.cards.includes(playCalls[0].action.card) && visualViewport.scale===1"));
    js(s,"selected=own.cards[0];playSelectedCard();");assertEquals("Pending/stale turn cannot submit twice","1",js(s,"playCalls.length"));
   }
  }
 }
}
