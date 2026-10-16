package com.hozefa.threepattipro;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.content.ClipData;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.webkit.WebViewAssetLoader;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;

public final class MainActivity extends Activity {
    private static final String APP_HOST = "appassets.androidplatform.net";
    private static final String HOME = "https://appassets.androidplatform.net/assets/index.html";
    private static final int BG = Color.rgb(10, 10, 12);
    private static final int GOLD = Color.rgb(246, 211, 101);
    private final Handler handler = new Handler(Looper.getMainLooper());

    private WebView browser;
    private View splash;
    private View errorOverlay;
    private TextView errorMessage;
    private ProgressBar loadingLine;
    private long backPressTime;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(APP_HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        browser = new WebView(this);
        browser.setBackgroundColor(BG);
        browser.setOverScrollMode(View.OVER_SCROLL_NEVER);
        browser.setVerticalScrollBarEnabled(false);

        WebSettings ws = browser.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(false);
        ws.setAllowContentAccess(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        ws.setJavaScriptCanOpenWindowsAutomatically(false);
        ws.setSupportZoom(false);
        ws.setDefaultTextEncodingName("UTF-8");
        if (Build.VERSION.SDK_INT >= 26) ws.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true);

        // The original site checks for FlutterShare.postMessage; provide the same API.
        browser.addJavascriptInterface(new ImageShareBridge(), "FlutterShare");

        browser.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                return loader.shouldInterceptRequest(r.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri uri = r.getUrl();
                if (APP_HOST.equalsIgnoreCase(uri.getHost()) && "https".equalsIgnoreCase(uri.getScheme())) {
                    return false;
                }
                if (!r.isForMainFrame()) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
                catch (Exception ex) {
                    Toast.makeText(MainActivity.this, "Link could not be opened", Toast.LENGTH_SHORT).show();
                }
                return true;
            }
            @Override
            public void onPageFinished(WebView v, String url) {
                splash.setVisibility(View.GONE);
                loadingLine.setVisibility(View.GONE);
                // Give external Firebase SDK time to initialize. The UI itself is packaged locally.
                handler.postDelayed(() -> {
                    if (browser == null || isFinishing() || errorOverlay.getVisibility() == View.VISIBLE) return;
                    browser.evaluateJavascript(
                        "(typeof firebase !== 'undefined' && typeof db !== 'undefined' && typeof auth !== 'undefined')",
                        result -> {
                            if ("false".equals(result) && errorOverlay.getVisibility() != View.VISIBLE) {
                                showError("The game is ready, but Firebase needs internet.\nCheck your connection, then tap to retry.");
                            }
                        });
                }, 12000);
            }
            @Override
            public void onReceivedError(WebView v, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showError("Could not open 3 Patti PRO.\nTap to retry.");
                }
            }
        });
        browser.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (loadingLine != null) {
                    loadingLine.setProgress(progress);
                    if (progress >= 100) loadingLine.setVisibility(View.GONE);
                }
            }
        });

        root.addView(browser, new FrameLayout.LayoutParams(-1, -1));

        splash = createOverlay("Connecting to your table...", true);
        root.addView(splash, new FrameLayout.LayoutParams(-1, -1));

        errorOverlay = createOverlay("Could not connect. Tap to retry.", false);
        errorMessage = (TextView) ((LinearLayout) errorOverlay).getChildAt(2);
        errorOverlay.setOnClickListener(v -> retry());
        errorOverlay.setVisibility(View.GONE);
        root.addView(errorOverlay, new FrameLayout.LayoutParams(-1, -1));

        loadingLine = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        loadingLine.setMax(100);
        loadingLine.setProgressTintList(ColorStateList.valueOf(GOLD));
        loadingLine.setProgressBackgroundTintList(ColorStateList.valueOf(BG));
        root.addView(loadingLine, new FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP));
        setContentView(root);

        if (savedInstanceState != null) {
            browser.restoreState(savedInstanceState);
        } else {
            browser.loadUrl(HOME);
        }
    }

    private LinearLayout createOverlay(String message, boolean spinner) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setBackgroundColor(BG);
        column.setPadding(dp(24), dp(30), dp(24), dp(30));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher_foreground);
        LinearLayout.LayoutParams logoSize = new LinearLayout.LayoutParams(dp(148), dp(148));
        logoSize.bottomMargin = dp(16);
        column.addView(logo, logoSize);

        TextView name = new TextView(this);
        name.setText("3 PATTI PRO");
        name.setTextColor(GOLD);
        name.setTextSize(28);
        name.setGravity(Gravity.CENTER);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        column.addView(name, new LinearLayout.LayoutParams(-1, -2));

        TextView detail = new TextView(this);
        detail.setText(message);
        detail.setGravity(Gravity.CENTER);
        detail.setTextSize(15);
        detail.setTextColor(0xFFC5C6CC);
        detail.setPadding(0, dp(12), 0, dp(18));
        column.addView(detail, new LinearLayout.LayoutParams(-1, -2));

        if (spinner) {
            ProgressBar progress = new ProgressBar(this);
            progress.setIndeterminateTintList(ColorStateList.valueOf(GOLD));
            column.addView(progress, new LinearLayout.LayoutParams(dp(34), dp(34)));
        } else {
            TextView retry = new TextView(this);
            retry.setText("TAP TO RETRY");
            retry.setTextColor(GOLD);
            retry.setTextSize(14);
            retry.setGravity(Gravity.CENTER);
            column.addView(retry);
        }
        return column;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            if (errorOverlay == null) return;
            splash.setVisibility(View.GONE);
            loadingLine.setVisibility(View.GONE);
            errorMessage.setText(message);
            errorOverlay.setVisibility(View.VISIBLE);
        });
    }

    private void retry() {
        errorOverlay.setVisibility(View.GONE);
        splash.setVisibility(View.VISIBLE);
        loadingLine.setVisibility(View.VISIBLE);
        browser.loadUrl(HOME);
    }

    @Override
    public void onBackPressed() {
        if (errorOverlay.getVisibility() == View.VISIBLE) {
            retry();
            return;
        }
        browser.evaluateJavascript(
            "(typeof window.handleAndroidBack === 'function' ? window.handleAndroidBack() : false)",
            result -> {
                if ("true".equals(result)) return;
                if (System.currentTimeMillis() - backPressTime < 1700) {
                    finish();
                } else {
                    backPressTime = System.currentTimeMillis();
                    Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show();
                }
            }
        );
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        if (browser != null) browser.saveState(out);
        super.onSaveInstanceState(out);
    }

    @Override
    protected void onDestroy() {
        if (browser != null) {
            browser.removeJavascriptInterface("FlutterShare");
            browser.destroy();
            browser = null;
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private final class ImageShareBridge {
        @JavascriptInterface
        public void postMessage(String dataUrl) {
            if (dataUrl == null || !dataUrl.startsWith("data:image/png;base64,")
                    || dataUrl.length() > 18000000) return;
            try {
                byte[] bytes = android.util.Base64.decode(
                        dataUrl.substring("data:image/png;base64,".length()),
                        android.util.Base64.DEFAULT);
                if (bytes.length < 10 || bytes.length > 12000000) return;
                File folder = new File(getCacheDir(), "shared");
                if (!folder.exists() && !folder.mkdirs()) return;
                File image = new File(folder, "3-patti-pro.png");
                try (FileOutputStream stream = new FileOutputStream(image)) {
                    stream.write(bytes);
                }
                Uri uri = FileProvider.getUriForFile(MainActivity.this,
                        getPackageName() + ".fileprovider", image);
                runOnUiThread(() -> {
                    try {
                        Intent send = new Intent(Intent.ACTION_SEND);
                        send.setType("image/png");
                        send.putExtra(Intent.EXTRA_STREAM, uri);
                        send.setClipData(ClipData.newRawUri("3 Patti PRO", uri));
                        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(Intent.createChooser(send, "Share 3 Patti PRO image"));
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "Share unavailable",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception ex) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this,
                        "Could not share the image", Toast.LENGTH_SHORT).show());
            }
        }
    }
}
