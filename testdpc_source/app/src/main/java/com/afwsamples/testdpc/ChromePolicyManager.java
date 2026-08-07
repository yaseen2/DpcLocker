package com.afwsamples.testdpc;

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

            // 1. Disable Incognito Mode (IncognitoModeAvailability = 1)
            chromeBundle.putInt("IncognitoModeAvailability", 1);

            // 2. Force Strict Google SafeSearch (ForceGoogleSafeSearch = true, SafeSearchMode = 1)
            chromeBundle.putBoolean("ForceGoogleSafeSearch", true);
            chromeBundle.putInt("SafeSearchMode", 1);

            // 3. Enable Chrome SafeSites Adult Content Filter (SafeSitesFilterBehavior = 1)
            chromeBundle.putInt("SafeSitesFilterBehavior", 1);

            // 4. Default Notorious Domain Blocklist (URLBlocklist) including fboxtv.org, X, Reddit, Tumblr, Telegram, Proxies
            String[] urlBlocklist = new String[]{
                "*fboxtv.org*",
                "*x.com*",
                "*twitter.com*",
                "*twimg.com*",
                "*reddit.com*",
                "*redditmedia.com*",
                "*redd.it*",
                "*tumblr.com*",
                "*telegram.org*",
                "*t.me*",
                "*croxyproxy.com*",
                "*proxysite.com*",
                "*hide.me*",
                "*blockaway.net*"
            };
            chromeBundle.putStringArray("URLBlocklist", urlBlocklist);

            dpm.setApplicationRestrictions(DeviceAdminReceiver.getComponentName(context), CHROME_PACKAGE, chromeBundle);
            Log.i(TAG, "Successfully enforced default Chrome policies & domain blocklist (fboxtv.org, X, Reddit, Tumblr, Telegram, Proxies)");
        } catch (Exception e) {
            Log.e(TAG, "Error enforcing default Chrome policies", e);
        }
    }
}
