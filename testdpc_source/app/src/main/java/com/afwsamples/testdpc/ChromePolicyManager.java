package com.afwsamples.testdpc;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

public class ChromePolicyManager {
    private static final String TAG = "ChromePolicyManager";
    public static final String CHROME_PACKAGE = "com.android.chrome";

    public static void enforceDefaultChromePolicies(Context context) {
        try {
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null || !dpm.isDeviceOwnerApp(context.getPackageName())) {
                return;
            }

            Bundle chromeBundle = new Bundle();

            // Set Permission Policy to standard PROMPT mode so runtime permissions (Contacts, Camera, etc.) work normally
            dpm.setPermissionPolicy(DeviceAdminReceiver.getComponentName(context), DevicePolicyManager.PERMISSION_POLICY_PROMPT);

            // 1. Enable Incognito Mode (IncognitoModeAvailability = 0)
            chromeBundle.putInt("IncognitoModeAvailability", 0);

            // 2. Force Strict Google SafeSearch (ForceGoogleSafeSearch = true, SafeSearchMode = 1)
            chromeBundle.putBoolean("ForceGoogleSafeSearch", true);
            chromeBundle.putInt("SafeSearchMode", 1);

            // 3. Enable Chrome SafeSites Adult Content Filter (SafeSitesFilterBehavior = 1)
            chromeBundle.putInt("SafeSitesFilterBehavior", 1);

            // 4. Force Direct Connections (Disables Proxy/VPN Extensions)
            chromeBundle.putString("ProxyMode", "direct");

            // 5. Default Notorious Domain Blocklist (URLBlocklist & legacy URLBlacklist) using standard Chrome URL patterns
            String[] urlBlocklist = new String[]{
                "fboxtv.org",
                "x.com",
                "twitter.com",
                "twimg.com",
                "reddit.com",
                "redditmedia.com",
                "redd.it",
                "tumblr.com",
                "telegram.org",
                "t.me",
                "croxyproxy.com",
                "proxysite.com",
                "hide.me",
                "blockaway.net",
                "chromewebstore.google.com"
            };
            chromeBundle.putStringArray("URLBlocklist", urlBlocklist);
            chromeBundle.putStringArray("URLBlacklist", urlBlocklist);

            dpm.setApplicationRestrictions(DeviceAdminReceiver.getComponentName(context), CHROME_PACKAGE, chromeBundle);
            Log.i(TAG, "Successfully enforced default Chrome policies & domain blocklist");

            // Kill background process of Chrome to force re-initialization of policies
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.killBackgroundProcesses(CHROME_PACKAGE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enforcing default Chrome policies", e);
        }
    }
}
