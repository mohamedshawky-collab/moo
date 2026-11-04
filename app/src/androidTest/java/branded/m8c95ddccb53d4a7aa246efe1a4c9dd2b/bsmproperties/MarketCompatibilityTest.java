package branded.m8c95ddccb53d4a7aa246efe1a4c9dd2b.bsmproperties;

import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebSettings;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class MarketCompatibilityTest {
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
