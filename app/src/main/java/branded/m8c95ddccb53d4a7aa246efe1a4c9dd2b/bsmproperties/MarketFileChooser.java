package branded.m8c95ddccb53d4a7aa246efe1a4c9dd2b.bsmproperties;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import java.util.ArrayList;
import java.util.LinkedHashSet;

final class MarketFileChooser {
    static final int REQUEST_CODE = 7412;
    private ValueCallback<Uri[]> pending;
    private boolean multiple;
    private int nextCode = REQUEST_CODE;
    private int activeCode = -1;

    boolean open(Activity activity, WebChromeClient.FileChooserParams params, ValueCallback<Uri[]> callback) {
        cancel();
        if (nextCode > 65535 || callback == null || params == null || (params.getMode() != WebChromeClient.FileChooserParams.MODE_OPEN
                && params.getMode() != WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE)) return false;
        pending = callback;
        activeCode = nextCode++;
        multiple = params.getMode() == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE;
        try {
            Intent picker = params.createIntent();
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, multiple);
            activity.startActivityForResult(picker, activeCode);
        } catch (ActivityNotFoundException | SecurityException unavailable) { cancel(); }
        return true;
    }

    boolean result(int requestCode, int resultCode, Intent data) {
        if (requestCode != activeCode) return false;
        ValueCallback<Uri[]> callback = pending;
        pending = null;
        activeCode = -1;
        if (callback == null) return true;
        ArrayList<Uri> parsed = new ArrayList<>();
        if (resultCode == Activity.RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                for (int index = 0; index < data.getClipData().getItemCount(); index++)
                    parsed.add(data.getClipData().getItemAt(index).getUri());
            } else if (data.getData() != null) parsed.add(data.getData());
        }
        LinkedHashSet<Uri> safe = new LinkedHashSet<>();
        for (Uri uri : parsed) {
            // Never pass a returned file:// or an app-private provider to WebView.
            String authority = uri == null ? null : uri.getAuthority();
            if (uri == null || !"content".equalsIgnoreCase(uri.getScheme()) || authority == null
                    || authority.startsWith("branded.m8c95ddccb53d4a7aa246efe1a4c9dd2b.bsmproperties")) continue;
            safe.add(uri);
            if (!multiple) break;
        }
        callback.onReceiveValue(safe.isEmpty() ? null : new ArrayList<>(safe).toArray(new Uri[0]));
        return true;
    }

    void cancel() {
        ValueCallback<Uri[]> callback = pending;
        pending = null;
        activeCode = -1;
        if (callback != null) callback.onReceiveValue(null);
    }
}

