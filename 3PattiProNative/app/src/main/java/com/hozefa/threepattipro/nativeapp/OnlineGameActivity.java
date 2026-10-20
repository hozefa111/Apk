package com.hozefa.threepattipro.nativeapp;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.net.Uri;
import android.webkit.*;
import android.widget.*;
import androidx.webkit.WebViewAssetLoader;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.android.gms.tasks.Tasks;
import java.util.concurrent.*;
import okhttp3.*;
import org.json.JSONObject;

/** Bundled shared table inside the existing APK. No unrestricted Javascript bridge. */
public final class OnlineGameActivity extends Activity {
 private WebView web;private TextView status;private Button back;private final android.os.Handler ui=new android.os.Handler(android.os.Looper.getMainLooper());private FirebaseAuth auth;private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private static final String ORIGIN="https://appassets.androidplatform.net";
 private static final String PAGE=ORIGIN+"/assets/kali/index.html";
 private final OkHttpClient http=new OkHttpClient.Builder().callTimeout(25,TimeUnit.SECONDS).build();
 @Override public void onCreate(Bundle saved){super.onCreate(saved);
  LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setBackgroundColor(Color.rgb(8,20,15));
  back=new Button(this);back.setText("‹  3 Patti PRO");back.setOnClickListener(v->finish());layout.addView(back);
  status=new TextView(this);status.setTextColor(Color.WHITE);status.setPadding(20,8,20,8);status.setText("Connecting securely…");layout.addView(status);
  web=new WebView(this);layout.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(layout);
  FirebaseApp app;try{app=FirebaseApp.getInstance("three-patti-native");}catch(IllegalStateException absent){
   String project=BuildConfig.USE_EMULATORS?"demo-three-patti":"patti-pro-a61d8";
   app=FirebaseApp.initializeApp(this,new FirebaseOptions.Builder().setProjectId(project).setApiKey(BuildConfig.USE_EMULATORS?"demo-key":getString(com.hozefa.threepattipro.nativeapp.R.string.firebase_web_api_key)).setApplicationId("1:570573461138:web:4414d0ce87282b91b2f84b").build(),"three-patti-native");
   if(BuildConfig.USE_EMULATORS)FirebaseAuth.getInstance(app).useEmulator("10.0.2.2",9099);
  }auth=FirebaseAuth.getInstance(app);
  WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(BuildConfig.USE_EMULATORS?WebSettings.MIXED_CONTENT_ALWAYS_ALLOW:WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
  WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);
  WebViewAssetLoader loader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
  web.setWebChromeClient(new WebChromeClient());
  web.setWebViewClient(new WebViewClient(){
   @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){return loader.shouldInterceptRequest(request.getUrl());}
   @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){if(!request.isForMainFrame())return false;Uri u=request.getUrl();if(!ORIGIN.equals(u.getScheme()+"://"+u.getHost()))return true;
    if("/assets/kali/share".equals(u.getPath())){String code=u.getQueryParameter("code");if(code!=null&&code.matches("[A-Z2-9]{8}")){
      android.content.Intent invite=new android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT,"Join my 3 Patti PRO table · "+code+"\nhttps://hozefa111.github.io/hh/online/index.html#join="+code);
      try{startActivity(android.content.Intent.createChooser(invite,"Invite to your table"));}catch(android.content.ActivityNotFoundException unavailable){new android.app.AlertDialog.Builder(OnlineGameActivity.this).setTitle("Room invitation").setMessage("Share room code: "+code).setPositiveButton("Close",null).show();}
     }return true;}
if(u.getPath()==null||!u.getPath().startsWith("/assets/kali/")){if("1".equals(u.getQueryParameter("nativeLogin")))getSharedPreferences("preferences",MODE_PRIVATE).edit().putBoolean("online_sign_in_requested",true).apply();finish();return true;}return false;}
   @Override public void onPageFinished(WebView view,String url){if(!url.startsWith(PAGE))return;
    if(BuildConfig.USE_EMULATORS)web.evaluateJavascript("window.KALI_HOST='10.0.2.2';",null);
    exchange();
   }
   @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){if(request.isForMainFrame())status.setText("Unable to load online table. Go back and retry.");}
  });
  // Emulator host is injected before client.js through a sandbox-only asset override.
  String code=getIntent().getStringExtra("roomCode");
  web.loadUrl(PAGE+(BuildConfig.USE_EMULATORS?"?emulator=1":"")+(code!=null&&code.matches("[A-Z2-9]{8}")?"#join="+code:""));
 }
 private void exchange(){worker.execute(()->{try{
  if(auth.getCurrentUser()==null)Tasks.await(auth.signInAnonymously(),25,TimeUnit.SECONDS);
  String token=Tasks.await(auth.getCurrentUser().getIdToken(false),25,TimeUnit.SECONDS).getToken();
  String endpoint=BuildConfig.USE_EMULATORS?"http://10.0.2.2:5001/demo-three-patti/asia-south1/kaliApi":"https://asia-south1-patti-pro-a61d8.cloudfunctions.net/kaliApi";
  Request request=new Request.Builder().url(endpoint).header("Authorization","Bearer "+token).post(RequestBody.create("{\"operation\":\"exchange\"}",MediaType.get("application/json"))).build();
  try(Response response=http.newCall(request).execute()){
   JSONObject data=new JSONObject(response.body().string());if(!response.isSuccessful())throw new Exception(data.optString("error","Server unavailable"));
   String exchange=data.getString("exchange");runOnUiThread(()->{if(!isFinishing()){web.evaluateJavascript("window.kaliAcceptExchange("+JSONObject.quote(exchange)+").then(function(){document.documentElement.dataset.nativeIdentity='ready';}).catch(function(){document.documentElement.dataset.nativeIdentity='failed';})",null);status.setText("Signing in securely…");checkIdentity(0);}});
  }
 }catch(Exception failure){runOnUiThread(()->{if(!isFinishing())status.setText("Online connection unavailable. Check game-server deployment and retry.");});}});}
 private void checkIdentity(int attempt){
  if(isFinishing()||isDestroyed())return;
  web.evaluateJavascript("document.documentElement.dataset.nativeIdentity||''",value->{
   if("\"ready\"".equals(value)){status.setVisibility(android.view.View.GONE);back.setVisibility(android.view.View.GONE);}
   else if("\"failed\"".equals(value)||attempt>=80)status.setText("Secure sign-in failed. Return to Home and retry.");
   else ui.postDelayed(()->checkIdentity(attempt+1),500);
  });
 }
 @Override protected void onPause(){super.onPause();if(web!=null)web.onPause();}
 @Override protected void onResume(){super.onResume();if(web!=null)web.onResume();}
 @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);if(web!=null){web.stopLoading();web.destroy();}worker.shutdownNow();super.onDestroy();}
}
