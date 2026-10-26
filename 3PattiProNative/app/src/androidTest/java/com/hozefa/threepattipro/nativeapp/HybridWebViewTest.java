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
 private void touch(ActivityScenario<OnlineGameActivity>s,float fx,float fy,int action,long down)throws Exception{float[]xy=new float[2];onUi(()->{WebView w=web(activity.getWindow().getDecorView());int[]loc=new int[2];w.getLocationOnScreen(loc);xy[0]=loc[0]+fx*w.getWidth();xy[1]=loc[1]+fy*w.getHeight();});MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,xy[0],xy[1],0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);InstrumentationRegistry.getInstrumentation().sendPointerSync(e);e.recycle();}
 private void tap(ActivityScenario<OnlineGameActivity>s)throws Exception{JSONArray p=new JSONArray(js(s,"(()=>{const r=document.querySelector('.hand-card:not([disabled])').getBoundingClientRect();return [(r.x+r.width/2)/innerWidth,(r.y+r.height/2)/innerHeight]})()"));long d=SystemClock.uptimeMillis();touch(s,(float)p.getDouble(0),(float)p.getDouble(1),MotionEvent.ACTION_DOWN,d);Thread.sleep(25);touch(s,(float)p.getDouble(0),(float)p.getDouble(1),MotionEvent.ACTION_UP,d);}
 private void doubleTap(ActivityScenario<OnlineGameActivity>s)throws Exception{JSONArray p=new JSONArray(js(s,"(()=>{const r=document.querySelector('.hand-card:not([disabled])').getBoundingClientRect();return [(r.x+r.width/2)/innerWidth,(r.y+r.height/2)/innerHeight]})()"));float[] xy=new float[2];onUi(()->{WebView w=web(activity.getWindow().getDecorView());int[] loc=new int[2];w.getLocationOnScreen(loc);xy[0]=loc[0]+(float)p.optDouble(0)*w.getWidth();xy[1]=loc[1]+(float)p.optDouble(1)*w.getHeight();});for(int i=0;i<2;i++){long d=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(d,SystemClock.uptimeMillis(),action,xy[0],xy[1],0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);InstrumentationRegistry.getInstrumentation().sendPointerSync(e);e.recycle();Thread.sleep(25);}if(i==0)Thread.sleep(70);}}
 @Test(timeout=150000) public void bundledLobbyScrollAndDoubleTapRemainUsable()throws Exception{
  assertTrue(BuildConfig.USE_EMULATORS);android.util.Log.i("HybridTouch","launch");
  try(ActivityScenario<OnlineGameActivity>s=ActivityScenario.launch(OnlineGameActivity.class)){
   s.onActivity(a->activity=a);
   android.util.Log.i("HybridTouch","activity resumed");waitFor(s,"typeof renderNow==='function' && document.documentElement.dataset.nativeIdentity==='ready' && !window.KALI_NATIVE_PENDING && !!user && user.uid===auth.currentUser?.uid");
   for(int mode:new int[]{4,6}){android.util.Log.i("HybridTouch","lobby "+mode);
    js(s,"(()=>{window.KALI_NATIVE_PENDING=false;clearCollection();resetCardTaps();unsubs.forEach(f=>f());unsubs=[];let t=1000,r=R.create({id:'cf2_0123456789abcdef0123456789abcdef',code:'V2ABCDEF',uid:'human',playerId:'p1',name:'Human',mode:"+mode+"},t);window.fixtureRoom=r;user={uid:'human',getIdToken:async()=>''};room=R.publicState(r);own=R.privateState(r,'human');previous=null;subscribedRoomId=r.id;uncertainCommand=null;busy=false;connectionReady=true;reduced=true;error='';screen='room';scoreOpen=false;historyOpen=false;renderNow();return true;})()");
    waitFor(s,"!!document.querySelector('.lobby-ready') || !!document.querySelector('[data-action=ready]')");
    assertEquals("Lobby must not retain the live table scroll lock","false",js(s,"document.body.classList.contains('live-game')"));
    android.util.Log.i("HybridTouch","scroll "+mode);long d=SystemClock.uptimeMillis();touch(s,.45f,.76f,MotionEvent.ACTION_DOWN,d);for(int i=1;i<=20;i++){Thread.sleep(16);touch(s,.45f,.76f-.43f*i/20,MotionEvent.ACTION_MOVE,d);}touch(s,.45f,.33f,MotionEvent.ACTION_UP,d);Thread.sleep(250);
    assertEquals("Lobby receives real finger scroll","true",js(s,"scrollY>15"));
    assertEquals("Ready dock is visible","true",js(s,"(()=>{const b=document.querySelector('[data-action=ready]').getBoundingClientRect();return b.y>=0&&b.bottom<=innerHeight+1})()"));
    android.util.Log.i("HybridTouch","playing "+mode);js(s,"(()=>{let r=fixtureRoom,t=Date.now();clockAnchor={server:t,mono:performance.now()};for(let seat=2;seat<="+mode+";seat++)r=R.command(r,'human',{type:'bot',seat,botId:'bot_'+seat},++t,n=>n-1);r=R.command(r,'human',{type:'ready'},++t,n=>n-1);while(r.hand.phase!=='playing')r=R.command(r,r.hand.turn,KaliBot.decide(R.publicState(r),r.hand.turn,R.privateState(r,r.hand.turn).cards),++t,n=>n-1);user={uid:r.hand.turn,getIdToken:async()=>''};room=R.publicState(r);previous=room;own=R.privateState(r,user.uid);selected=null;submittedCardTurn=null;uncertainCommand=null;busy=false;connectionReady=true;window.playCalls=[];api=async b=>{playCalls.push(b);return {};};renderNow();window.scrollTo(0,0);return true;})()");
    waitFor(s,"!!document.querySelector('.hand-card:not([disabled])')");Thread.sleep(300);
    android.util.Log.i("HybridTouch","tap "+mode);tap(s);waitFor(s,"!!document.querySelector('.hand-card.selected')");assertEquals("Single tap only selects","0",js(s,"playCalls.length"));js(s,"resetCardTaps();selected=null;renderNow()");Thread.sleep(100);doubleTap(s);waitFor(s,"playCalls.length===1");
    assertEquals("Only one command, correct card, no zoom","true",js(s,"playCalls[0].action.type==='play' && own.cards.includes(playCalls[0].action.card) && visualViewport.scale===1"));
    js(s,"selected=own.cards[0];playSelectedCard();");assertEquals("Pending/stale turn cannot submit twice","1",js(s,"playCalls.length"));
   }
  }
 }
}
