package com.hozefa.threepattipro.nativeapp;

import org.junit.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import android.content.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;

public class ShareIntentTest {
    @Test public void singleAndMultiplePngsReachAnotherUidWithReadPermission()throws Exception{
        Assume.assumeTrue(BuildConfig.USE_EMULATORS);
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();Context context=instrumentation.getTargetContext();
        assertTrue(context.getPackageName().endsWith(".sandbox"));
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)){
            for(int size:new int[]{1,75}){
                AtomicReference<Intent> chooser=new AtomicReference<>(),received=new AtomicReference<>();CountDownLatch delivered=new CountDownLatch(1);
                BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent result){received.set(result);delivered.countDown();}};
                IntentFilter filter=new IntentFilter("threepatti.verification.SHARE_RESULT");
                if(android.os.Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);else context.registerReceiver(receiver,filter);
                android.app.Instrumentation.ActivityMonitor monitor=new android.app.Instrumentation.ActivityMonitor(){
                    @Override public android.app.Instrumentation.ActivityResult onStartActivity(Intent intent){
                        if(Intent.ACTION_CHOOSER.equals(intent.getAction())){chooser.set(intent);return new android.app.Instrumentation.ActivityResult(android.app.Activity.RESULT_CANCELED,null);}return null;
                    }
                };
                instrumentation.addMonitor(monitor);
                try{
                    scenario.onActivity(activity->{java.util.List<NativeImageShare.RankRow> rows=new java.util.ArrayList<>();for(int i=0;i<size;i++)rows.add(new NativeImageShare.RankRow("Synthetic "+i,i,1,1,0,1,0));NativeImageShare.leaderboard(activity,"Share permission test",rows);});
                    long deadline=System.currentTimeMillis()+45000;boolean visible=false;
                    while(System.currentTimeMillis()<deadline){try{onView(withText("3 Patti PRO - Rankings")).inRoot(androidx.test.espresso.matcher.RootMatchers.isDialog()).check(matches(isDisplayed()));visible=true;break;}catch(RuntimeException|AssertionError e){Thread.sleep(200);}}
                    assertTrue("Preview must open",visible);onView(withText("Share")).perform(click());
                    assertNotNull("Native chooser must launch",chooser.get());Intent send=chooser.get().getParcelableExtra(Intent.EXTRA_INTENT);assertNotNull(send);
                    int expected=size==1?1:3;assertEquals(size==1?Intent.ACTION_SEND:Intent.ACTION_SEND_MULTIPLE,send.getAction());
                    assertEquals("image/png",send.getType());assertTrue((send.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0);assertEquals(expected,send.getClipData().getItemCount());
                    // Exercise the exact outgoing payload through a receiver in a different app UID.
                    send.setComponent(new ComponentName(instrumentation.getContext().getPackageName(),VerificationShareReceiver.class.getName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(send);assertTrue("Receiver must report within timeout",delivered.await(30,TimeUnit.SECONDS));
                    assertFalse(received.get().getStringExtra("error"),received.get().hasExtra("error"));assertEquals(expected,received.get().getIntExtra("count",0));
                    assertNotEquals("Read permission must work across app UIDs",android.os.Process.myUid(),received.get().getIntExtra("uid",-1));
                }finally{instrumentation.removeMonitor(monitor);context.unregisterReceiver(receiver);}
            }
        }
    }
}
