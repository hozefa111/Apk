package com.hozefa.threepattipro.nativeapp;
import android.content.Intent;import android.webkit.WebView;import android.view.View;import android.view.ViewGroup;import androidx.test.core.app.ActivityScenario;import androidx.test.platform.app.InstrumentationRegistry;import org.junit.Test;import static org.junit.Assert.*;import java.util.concurrent.*;import java.util.concurrent.atomic.AtomicReference;
public class OnlineGameTest {
 private WebView web(View v){if(v instanceof WebView)return(WebView)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){WebView found=web(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
 private View described(View v,String text){if(text.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=described(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}return null;}
 private String js(ActivityScenario<OnlineGameActivity> s,String script)throws Exception{CountDownLatch latch=new CountDownLatch(1);AtomicReference<String> value=new AtomicReference<>();s.onActivity(a->web(a.getWindow().getDecorView()).evaluateJavascript(script,v->{value.set(v);latch.countDown();}));assertTrue("Javascript result",latch.await(15,TimeUnit.SECONDS));return value.get();}
 @Test public void existingNativeHomeContainsOnlineEntry()throws Exception{try(ActivityScenario<MainActivity> s=ActivityScenario.launch(MainActivity.class)){long until=System.currentTimeMillis()+20000;AtomicReference<View> entry=new AtomicReference<>();while(System.currentTimeMillis()<until){s.onActivity(a->entry.set(described(a.getWindow().getDecorView(),"Play Online Kali Ki Tidi")));if(entry.get()!=null)break;Thread.sleep(200);}assertNotNull(entry.get());assertTrue(entry.get().isClickable());}}
 @Test public void sharedTableLoadsInsideSamePackageWithSafeWebSettings()throws Exception{try(ActivityScenario<OnlineGameActivity> s=ActivityScenario.launch(OnlineGameActivity.class)){long until=System.currentTimeMillis()+45000;boolean loaded=false;while(System.currentTimeMillis()<until){if("true".equals(js(s,"typeof KaliRules!=='undefined' && !!document.getElementById('join')"))){loaded=true;break;}Thread.sleep(300);}assertTrue("Bundled table loaded",loaded);assertEquals("true",js(s,"!!document.querySelector('#join select[name=playerId]') && !document.querySelector('#join input[name=name]')"));assertEquals("[40,42,30]",js(s,"[KaliRules.deck(4).length,KaliRules.deck(6).length,KaliRules.points('3S')]"));js(s,"window.resumeRepairs=0;window.kaliReconnect=()=>window.resumeRepairs++");s.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);s.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);assertEquals("true",js(s,"window.resumeRepairs>0"));s.onActivity(a->{WebView w=web(a.getWindow().getDecorView());assertFalse(w.getSettings().getAllowFileAccess());assertFalse(w.getSettings().getAllowContentAccess());assertTrue(w.getUrl().startsWith("https://appassets.androidplatform.net/assets/kali/"));});}}
 @Test public void rotationKeepsTheOnlineWebViewAndJavascriptState()throws Exception{
  try(ActivityScenario<OnlineGameActivity> s=ActivityScenario.launch(OnlineGameActivity.class)){
   long end=System.currentTimeMillis()+45000;while(System.currentTimeMillis()<end&&!"true".equals(js(s,"typeof KaliRules!=='undefined'")))Thread.sleep(300);
   assertEquals("true",js(s,"typeof KaliRules!=='undefined'"));js(s,"window.rotationSentinel='same-table'");
   try{
    s.onActivity(a->a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE));
    java.util.concurrent.atomic.AtomicBoolean landscape=new java.util.concurrent.atomic.AtomicBoolean();end=System.currentTimeMillis()+15000;
    while(System.currentTimeMillis()<end){s.onActivity(a->landscape.set(a.getResources().getConfiguration().orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE));if(landscape.get())break;Thread.sleep(100);}assertTrue("Landscape configuration",landscape.get());assertEquals("\"same-table\"",js(s,"window.rotationSentinel"));
   }finally{s.onActivity(a->a.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT));}
  }
 }
 @Test public void amountBasedBiddingAndMaximumStateWorkInsideTheSameApk()throws Exception{
  org.junit.Assume.assumeTrue("Full online emulator required", "true".equals(InstrumentationRegistry.getArguments().getString("kaliOnlineBackend")));
  try(ActivityScenario<OnlineGameActivity> s=ActivityScenario.launch(OnlineGameActivity.class)){
   long end=System.currentTimeMillis()+60000;while(System.currentTimeMillis()<end&&!"true".equals(js(s,"document.documentElement.dataset.nativeIdentity==='ready' && typeof renderNow==='function'")))Thread.sleep(300);
   assertEquals("true",js(s,"document.documentElement.dataset.nativeIdentity==='ready'"));
   for(int mode:new int[]{4,6}){
    String script="(()=>{let serial=0,now=1000,r=KaliRules.create({id:'android_bid_fixture',code:'ABCDEFGH',uid:'owner',playerId:'profile',name:'Hozefa',mode:"+mode+"},now);for(let seat=2;seat<="+mode+";seat++)r=KaliRules.command(r,'owner',{type:'bot',seat,botId:'bot_'+seat},++now,n=>(serial++)%n);r=KaliRules.command(r,'owner',{type:'ready'},++now,n=>(serial++)%n);const local=r.hand.turn;user={uid:local,getIdToken:async()=>''};room=KaliRules.publicState(r);own=KaliRules.privateState(r,local);connectionReady=true;busy=false;reduced=true;error='';renderNow();const first=document.querySelector('#bid select').value,down=document.querySelector('[data-action=bid-down]').disabled;document.querySelector('[data-action=bid-max]').click();const max=document.querySelector('#bid button.gold').textContent==='Bid 250'&&document.querySelector('[data-action=bid-up]').disabled&&room.hand.bid===155;room.hand.bid=250;renderNow();return first==='160'&&down&&max&&!document.querySelector('#bid select')&&document.querySelector('.max-bid-state').textContent.includes('Maximum bid reached')&&!!document.querySelector('[data-action=pass]')&&document.documentElement.scrollWidth<=innerWidth+1&&document.documentElement.scrollHeight<=innerHeight+1;})()";
    assertEquals("Bundled bidding controls for "+mode+" seats","true",js(s,script));
   }
  }
 }
 @Test public void onlineAndManualHistoryExposeTheSameFourActions()throws Exception{
  try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
   for(String source:new String[]{"manual","online"}){
    long until=System.currentTimeMillis()+20000;java.util.concurrent.atomic.AtomicBoolean focused=new java.util.concurrent.atomic.AtomicBoolean();
    while(System.currentTimeMillis()<until){scenario.onActivity(a->focused.set(a.getWindow().getDecorView().hasWindowFocus()));if(focused.get())break;Thread.sleep(100);}
    assertTrue("History Activity has window focus before opening actions",focused.get());
    scenario.onActivity(a->{try{
     java.lang.reflect.Field gateway=MainActivity.class.getDeclaredField("api");gateway.setAccessible(true);((FirebaseGateway)gateway.get(a)).stop();
     java.lang.reflect.Field role=MainActivity.class.getDeclaredField("loggedIn");role.setAccessible(true);role.setBoolean(a,true);
     java.lang.reflect.Method menu=MainActivity.class.getDeclaredMethod("sessionActions",java.util.Map.class);menu.setAccessible(true);
     menu.invoke(a,FirebaseGateway.map("id","fixture-menu","source",source,"sessionName","Fixture session","endTime","2026-10-20T10:00:00Z"));
    }catch(Exception e){throw new AssertionError(e);}});
    for(String action:new String[]{"View Summary","Share Summary Image","Edit Session Name","Delete Session"})
     androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(action)).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));
    androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText("Close")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(androidx.test.espresso.action.ViewActions.click());
   }
  }
 }
 @Test public void invitationPrefillsRoomCodeInBundledTable()throws Exception{Intent intent=new Intent(InstrumentationRegistry.getInstrumentation().getTargetContext(),OnlineGameActivity.class).putExtra("roomCode","ABCDEFGH");try(ActivityScenario<OnlineGameActivity>s=ActivityScenario.launch(intent)){long until=System.currentTimeMillis()+45000;boolean loaded=false;while(System.currentTimeMillis()<until){if("\"ABCDEFGH\"".equals(js(s,"document.querySelector('#join input[name=code]')?.value"))){loaded=true;break;}Thread.sleep(300);}assertTrue("Invite retained",loaded);}}
 @Test public void nativeAndEmbeddedAuthRestoreExactlyTheSameIdentity()throws Exception{
  org.junit.Assume.assumeTrue("Full online emulator required", "true".equals(InstrumentationRegistry.getArguments().getString("kaliOnlineBackend")));
  try(ActivityScenario<OnlineGameActivity> scenario=ActivityScenario.launch(OnlineGameActivity.class)){
   long until=System.currentTimeMillis()+60000;boolean ready=false;
   while(System.currentTimeMillis()<until){if("true".equals(js(scenario,"document.documentElement.dataset.nativeIdentity==='ready' && !!user"))){ready=true;break;}Thread.sleep(300);}
   assertTrue("Native auth exchange completed",ready);
   String nativeUid=com.google.firebase.auth.FirebaseAuth.getInstance(com.google.firebase.FirebaseApp.getInstance("three-patti-native")).getCurrentUser().getUid();
   assertEquals(org.json.JSONObject.quote(nativeUid),js(scenario,"user.uid"));
   scenario.recreate();until=System.currentTimeMillis()+60000;ready=false;
   while(System.currentTimeMillis()<until){if("true".equals(js(scenario,"document.documentElement.dataset.nativeIdentity==='ready' && !!user"))){ready=true;break;}Thread.sleep(300);}
   assertTrue("Native auth exchange survives Activity recreation",ready);
   assertEquals(org.json.JSONObject.quote(nativeUid),js(scenario,"user.uid"));
  }
 }
}
