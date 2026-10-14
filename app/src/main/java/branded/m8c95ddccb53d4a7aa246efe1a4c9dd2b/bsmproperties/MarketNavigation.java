package branded.m8c95ddccb53d4a7aa246efe1a4c9dd2b.bsmproperties;

import android.net.Uri;

final class MarketNavigation {
    private MarketNavigation() {}
    static boolean internal(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
        return "bsm-properties.com".equalsIgnoreCase(uri.getHost())
                || "www.bsm-properties.com".equalsIgnoreCase(uri.getHost());
    }
    static boolean external(Uri uri) {
        if (uri == null || uri.getScheme() == null) return false;
        switch (uri.getScheme().toLowerCase(java.util.Locale.ROOT)) {
            case "https": case "http":
                return uri.getHost() != null && uri.getUserInfo() == null;
            case "mailto": case "tel": case "whatsapp": return true;
            default: return false;
        }
    }
}
