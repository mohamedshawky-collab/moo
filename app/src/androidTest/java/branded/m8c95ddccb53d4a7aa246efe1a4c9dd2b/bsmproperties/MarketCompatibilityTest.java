package branded.m8c95ddccb53d4a7aa246efe1a4c9dd2b.bsmproperties;

import android.net.Uri;
import android.app.Instrumentation;
import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.SystemClock;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebChromeClient;
import android.webkit.WebViewClient;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MarketCompatibilityTest {
    @Test public void documentChooserCanCancelAndOpenAgain() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            CountDownLatch pageLoaded = new CountDownLatch(1);
            scenario.onActivity(activity -> {
                WebView web = findWebView(activity.getWindow().getDecorView());
                web.stopLoading();
                WebViewClient original = web.getWebViewClient();
                web.setWebViewClient(new WebViewClient() {
                    @Override public void onPageFinished(WebView view, String url) {
                        original.onPageFinished(view, url);
                        if ("https://bsm-properties.com/".equals(view.getUrl())) pageLoaded.countDown();
                    }
                });
                web.loadDataWithBaseURL("https://bsm-properties.com/", "<input type='file'>", "text/html", "UTF-8", "https://bsm-properties.com/");
            });
            assertTrue("The trusted test page must finish loading before opening its picker", pageLoaded.await(15, TimeUnit.SECONDS));
            for (int i = 0; i < 2; i++) {
                CountDownLatch returned = new CountDownLatch(1);
                scenario.onActivity(activity -> {
                    WebView web = findWebView(activity.getWindow().getDecorView());
                    assertEquals("https://bsm-properties.com/", web.getUrl());
                    assertTrue("The trusted page must open its document picker", web.getWebChromeClient().onShowFileChooser(web, files -> {
                        assertNull(files);
                        returned.countDown();
                    }, new WebChromeClient.FileChooserParams() {
                        @Override public int getMode() { return MODE_OPEN_MULTIPLE; }
                        @Override public String[] getAcceptTypes() { return new String[]{"image/*", "application/pdf"}; }
                        @Override public boolean isCaptureEnabled() { return false; }
                        @Override public CharSequence getTitle() { return null; }
                        @Override public String getFilenameHint() { return null; }
                        @Override public Intent createIntent() { return new Intent(Intent.ACTION_GET_CONTENT).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); }
                    }));
                });
                long deadline = SystemClock.elapsedRealtime() + 15000;
                String pickerPackage = "";
                while (SystemClock.elapsedRealtime() < deadline) {
                    AccessibilityNodeInfo root = instrumentation.getUiAutomation().getRootInActiveWindow();
                    if (root != null && root.getPackageName() != null) {
                        pickerPackage = root.getPackageName().toString();
                        if (!pickerPackage.equals("branded.m8c95ddccb53d4a7aa246efe1a4c9dd2b.bsmproperties")
                                && !pickerPackage.equals("com.android.systemui")) break;
                    }
                    SystemClock.sleep(100);
                }
                assertTrue("The Android document picker must be visible; active=" + pickerPackage,
                        pickerPackage.contains("documentsui") || pickerPackage.contains("photopicker")
                                || pickerPackage.contains("providers.media") || pickerPackage.contains("intentresolver")
                                || pickerPackage.equals("android"));
                assertTrue(instrumentation.getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK));
                assertTrue("Cancellation must resolve the page callback", returned.await(10, TimeUnit.SECONDS));
                instrumentation.waitForIdleSync();
            }
        }
    }

    @Test public void startsWithSecureWebViewAndOriginalPackage() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals("branded.m8c95ddccb53d4a7aa246efe1a4c9dd2b.bsmproperties", activity.getPackageName());
                WebView web = findWebView(activity.getWindow().getDecorView());
                assertNotNull(web);
                assertTrue(web.getSettings().getJavaScriptEnabled());
                assertFalse(web.getSettings().getAllowFileAccess());
                assertFalse(web.getSettings().getAllowContentAccess());
                assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW, web.getSettings().getMixedContentMode());
                assertTrue(activity instanceof androidx.activity.OnBackPressedDispatcherOwner);
            });
        }
    }

    @Test public void onlyFirstPartySecureNavigationIsInternal() {
        assertTrue(MainActivity.isAllowedHttpUri(Uri.parse("https://bsm-properties.com/account?lang=ar")));
        assertTrue(MainActivity.isAllowedHttpUri(Uri.parse("https://www.bsm-properties.com:443/")));
        for (String url : new String[]{"http://bsm-properties.com/", "https://bsm-properties.com.evil.example/",
                "https://bsm-properties.com:8443/", "https://user@bsm-properties.com/", "file:///etc/passwd"}) {
            assertFalse(url, MainActivity.isAllowedHttpUri(Uri.parse(url)));
        }
        assertFalse(MainActivity.isAllowedHttpUri(null));
    }

    @Test public void acceptsMultiplePickerDocumentsWithoutStoragePermission() {
        assertTrue(MainActivity.areSafeSelectedFiles(new Uri[]{
                Uri.parse("content://com.android.providers.media.documents/document/image%3A123"),
                Uri.parse("content://com.android.providers.downloads.documents/document/42")}));
    }

    @Test public void rejectsCanceledAndUnsafePickerResults() {
        assertFalse(MainActivity.areSafeSelectedFiles(null));
        assertFalse(MainActivity.areSafeSelectedFiles(new Uri[0]));
        for (String value : new String[]{"file:///data/data/private", "https://example.com/a.pdf", "content:/missing-authority"}) {
            assertFalse(value, MainActivity.areSafeSelectedFiles(new Uri[]{Uri.parse(value)}));
        }
        assertFalse(MainActivity.areSafeSelectedFiles(new Uri[]{null}));
    }

    private static WebView findWebView(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                WebView found = findWebView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }
}
