package branded.m8c95ddccb53d4a7aa246efe1a4c9dd2b.bsmproperties;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 36})
public class MarketWebViewTest {
    private WebChromeClient.FileChooserParams params(boolean multiple) {
        return new WebChromeClient.FileChooserParams() {
            public int getMode() { return multiple ? MODE_OPEN_MULTIPLE : MODE_OPEN; }
            public String[] getAcceptTypes() { return new String[]{"application/pdf", "image/*"}; }
            public boolean isCaptureEnabled() { return false; }
            public CharSequence getTitle() { return null; }
            public String getFilenameHint() { return null; }
            public Intent createIntent() { return new Intent(Intent.ACTION_GET_CONTENT).setType("*/*"); }
        };
    }
    @Test public void onlyCanonicalHttpsHostsStayInside() {
        assertTrue(MarketNavigation.internal(Uri.parse("https://bsm-properties.com/requests?lang=ar")));
        assertTrue(MarketNavigation.internal(Uri.parse("https://www.bsm-properties.com/project/BSM-2026-0047")));
        for (String url : new String[]{"http://bsm-properties.com/", "https://bsm-properties.com.evil.test/", "https://evil.test/", "https://user@bsm-properties.com/", "https://bsm-properties.com:8443/", "file:///private.pdf", "javascript:alert(1)"}) assertFalse(url, MarketNavigation.internal(Uri.parse(url)));
    }
    @Test public void contactLinksOpenExternallyButExecutableSchemesDoNot() {
        for (String url : new String[]{"https://wa.me/971566564503", "mailto:test@example.test", "tel:+0000000000", "whatsapp://send?text=test"}) assertTrue(url,MarketNavigation.external(Uri.parse(url)));
        for (String url : new String[]{"file:///data/private", "content://private/1", "javascript:alert(1)", "intent://attack", "data:text/html,test"}) assertFalse(url,MarketNavigation.external(Uri.parse(url)));
    }
    @Test public void singleSelectionDeliversOneContentUriExactlyOnce() {
        Activity activity=Robolectric.buildActivity(Activity.class).create().get();
        MarketFileChooser chooser=new MarketFileChooser();List<Uri[]> results=new ArrayList<>();
        assertTrue(chooser.open(activity,params(false),results::add));
        Intent intent=shadowOf(activity).getNextStartedActivityForResult().intent;
        assertTrue(intent.hasCategory(Intent.CATEGORY_OPENABLE));assertFalse(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE,true));
        Uri document=Uri.parse("content://com.android.providers.media.documents/document/test");
        assertTrue(chooser.result(MarketFileChooser.REQUEST_CODE,Activity.RESULT_OK,new Intent().setData(document)));
        assertArrayEquals(new Uri[]{document},results.get(0));
        chooser.result(MarketFileChooser.REQUEST_CODE,Activity.RESULT_OK,new Intent().setData(document));assertEquals(1,results.size());
    }
    @Test public void multipleSelectionPreservesOrderAndRemovesDuplicates() {
        Activity activity=Robolectric.buildActivity(Activity.class).create().get();MarketFileChooser chooser=new MarketFileChooser();List<Uri[]> results=new ArrayList<>();
        chooser.open(activity,params(true),results::add);Uri a=Uri.parse("content://documents.test/a"),b=Uri.parse("content://documents.test/b");
        ClipData clip=ClipData.newRawUri("test",a);clip.addItem(new ClipData.Item(b));clip.addItem(new ClipData.Item(a));
        chooser.result(MarketFileChooser.REQUEST_CODE,Activity.RESULT_OK,new Intent().setClipData(clip));
        assertArrayEquals(new Uri[]{a,b},results.get(0));
    }
    @Test public void cancellationAndReplacementReleasePreviousCallback() {
        Activity activity=Robolectric.buildActivity(Activity.class).create().get();MarketFileChooser chooser=new MarketFileChooser();List<Uri[]> first=new ArrayList<>(),second=new ArrayList<>();
        chooser.open(activity,params(false),first::add);chooser.open(activity,params(false),second::add);
        assertEquals(1,first.size());assertNull(first.get(0));
        assertFalse(chooser.result(MarketFileChooser.REQUEST_CODE,Activity.RESULT_OK,new Intent().setData(Uri.parse("content://documents.test/stale"))));assertEquals(0,second.size());
        chooser.result(MarketFileChooser.REQUEST_CODE+1,Activity.RESULT_CANCELED,null);assertEquals(1,second.size());assertNull(second.get(0));
        chooser.cancel();assertEquals(1,second.size());assertFalse(chooser.result(1,Activity.RESULT_OK,null));
    }
    @Test public void unsafeReturnedUrisAreNeverPassedToWebView() {
        for(String uri:new String[]{"file:///data/private.pdf","https://example.test/file.pdf","content://branded.m8c95ddccb53d4a7aa246efe1a4c9dd2b.bsmproperties/private"}){
            Activity activity=Robolectric.buildActivity(Activity.class).create().get();MarketFileChooser chooser=new MarketFileChooser();List<Uri[]> results=new ArrayList<>();
            chooser.open(activity,params(false),results::add);chooser.result(MarketFileChooser.REQUEST_CODE,Activity.RESULT_OK,new Intent().setData(Uri.parse(uri)));assertNull(uri,results.get(0));
        }
    }
    @Test public void actualActivityWiresChooserToCallbackAndCancelsOnDestroy() {
        var controller=Robolectric.buildActivity(MainActivity.class).create();MainActivity activity=controller.get();
        WebView webView=ReflectionHelpers.getField(activity,"webView");assertEquals("https://bsm-properties.com/",webView.getUrl());
        List<Uri[]> result=new ArrayList<>();assertTrue(webView.getWebChromeClient().onShowFileChooser(webView,result::add,params(false)));
        controller.destroy();assertEquals(1,result.size());assertNull(result.get(0));
    }
}
