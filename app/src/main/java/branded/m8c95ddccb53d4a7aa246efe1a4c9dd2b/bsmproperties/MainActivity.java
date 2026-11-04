package branded.m8c95ddccb53d4a7aa246efe1a4c9dd2b.bsmproperties;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.CookieManager;
import android.webkit.SafeBrowsingResponse;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.ValueCallback;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends ComponentActivity {
    private static final String HOME_URL = "https://bsm-properties.com/";
    private WebView webView;
    private ProgressBar progressBar;
    private LinearLayout connectionMessage;
    private ValueCallback<Uri[]> pendingFiles;
    private final OnBackPressedCallback webBack = new OnBackPressedCallback(false) {
        @Override public void handleOnBackPressed() {
            webView.goBack();
        }
    };
    private final ActivityResultLauncher<Intent> filePicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Uri[] files = WebChromeClient.FileChooserParams.parseResult(
                        result.getResultCode(), result.getData());
                completeFileSelection(areSafeSelectedFiles(files) ? files : null);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(11, 23, 43));
        getWindow().setNavigationBarColor(Color.rgb(11, 23, 43));

        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);

        root.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                6
        );
        root.addView(progressBar, progressParams);
        connectionMessage = new LinearLayout(this);
        connectionMessage.setOrientation(LinearLayout.VERTICAL);
        connectionMessage.setGravity(Gravity.CENTER);
        connectionMessage.setPadding(40, 40, 40, 40);
        connectionMessage.setBackgroundColor(Color.rgb(247, 244, 238));
        TextView message = new TextView(this);
        message.setGravity(Gravity.CENTER);
        message.setTextColor(Color.rgb(11, 23, 43));
        message.setTextSize(18);
        message.setText("تعذر الاتصال بـ BSM MARKET. تحقق من الاتصال ثم أعد المحاولة.\n\nBSM MARKET could not connect. Check your connection and retry.");
        connectionMessage.addView(message);
        Button retry = new Button(this);
        retry.setText("إعادة المحاولة · Retry");
        retry.setOnClickListener(view -> {
            connectionMessage.setVisibility(View.GONE);
            webView.reload();
        });
        connectionMessage.addView(retry);
        connectionMessage.setVisibility(View.GONE);
        root.addView(connectionMessage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        // Android 15/16 enforce edge-to-edge; keep the website clear of system controls and cutouts.
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        setContentView(root);

        configureWebView();
        getOnBackPressedDispatcher().addCallback(this, webBack);

        Uri incoming = getIntent() != null ? getIntent().getData() : null;
        String initialUrl = isAllowedHttpUri(incoming) ? incoming.toString() : HOME_URL;
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(initialUrl);
        }
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUserAgentString(settings.getUserAgentString() + " BSMMarketAndroid/3.0");

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                if (pendingFiles != null || !isAllowedHttpUri(Uri.parse(
                        view.getUrl() == null ? "" : view.getUrl()))) return false;
                if (params.getMode() != FileChooserParams.MODE_OPEN
                        && params.getMode() != FileChooserParams.MODE_OPEN_MULTIPLE) return false;
                pendingFiles = callback;
                try {
                    Intent picker = params.createIntent();
                    picker.addCategory(Intent.CATEGORY_OPENABLE);
                    picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    filePicker.launch(picker);
                } catch (ActivityNotFoundException | SecurityException ignored) {
                    completeFileSelection(null);
                }
                return true;
            }

            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isAllowedHttpUri(uri)) return false;
                openExternal(uri);
                return true;
            }

            @Override
            public void onSafeBrowsingHit(WebView view, WebResourceRequest request, int threatType, SafeBrowsingResponse callback) {
                if (Build.VERSION.SDK_INT >= 27) callback.backToSafety(true);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) showConnectionMessage();
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse errorResponse) {
                if (request.isForMainFrame() && errorResponse.getStatusCode() >= 500) showConnectionMessage();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                webBack.setEnabled(view.canGoBack());
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                webBack.setEnabled(view.canGoBack());
            }
        });
    }

    private void showConnectionMessage() {
        progressBar.setVisibility(View.GONE);
        connectionMessage.setVisibility(View.VISIBLE);
    }

    static boolean isAllowedHttpUri(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) return false;
        boolean https = "https".equalsIgnoreCase(scheme);
        boolean allowedHost = "bsm-properties.com".equalsIgnoreCase(host)
                || "www.bsm-properties.com".equalsIgnoreCase(host);
        return https && allowedHost && uri.getUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    static boolean areSafeSelectedFiles(Uri[] files) {
        if (files == null || files.length == 0) return false;
        for (Uri file : files) {
            if (file == null || !"content".equalsIgnoreCase(file.getScheme())
                    || file.getAuthority() == null || file.getAuthority().isEmpty()) return false;
        }
        return true;
    }

    private void completeFileSelection(Uri[] files) {
        ValueCallback<Uri[]> callback = pendingFiles;
        pendingFiles = null;
        if (callback != null) callback.onReceiveValue(files);
    }

    private void openExternal(Uri uri) {
        if (uri == null) return;
        String scheme = uri.getScheme();
        if (!("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme)
                || "mailto".equalsIgnoreCase(scheme) || "tel".equalsIgnoreCase(scheme)
                || "whatsapp".equalsIgnoreCase(scheme))) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri incoming = intent.getData();
        if (isAllowedHttpUri(incoming)) {
            connectionMessage.setVisibility(View.GONE);
            webView.loadUrl(incoming.toString());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        webView.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        completeFileSelection(null);
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }
}
