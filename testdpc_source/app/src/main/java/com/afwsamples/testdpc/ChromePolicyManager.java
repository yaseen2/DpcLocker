package com.afwsamples.testdpc;

import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

/**
 * ==============================================================================
 * CHROME POLICY MANAGER :: MANAGED APPLICATION RESTRICTIONS (ENTERPRISE MDM)
 * ==============================================================================
 * Purpose:
 * Leverages Android's DevicePolicyManager (DPM) enterprise API `setApplicationRestrictions`
 * to push managed policies directly into Google Chrome ("com.android.chrome").
 *
 * How It Works:
 * Chrome on Android supports the Android Enterprise Managed Configurations standard.
 * When a Device Owner passes a Bundle of key-value pairs via `setApplicationRestrictions()`,
 * Chrome reads them as administrative group policies that the user cannot alter or override
 * from within Chrome settings.
 *
 * Policies Configured:
 * 1. SafeSearch: Enforces Google SafeSearch globally.
 * 2. SafeSites Adult Filter: Enables Google Chrome's built-in adult content filter.
 * 3. Direct Proxy Mode: Prevents proxy or VPN extensions from rerouting traffic.
 * 4. URL Blocklist: Hard-blocks notorious social media, adult proxy, and short-form domains.
 * ==============================================================================
 */
public class ChromePolicyManager {

    // Logcat debugging tag
    private static final String TAG = "ChromePolicyManager";

    // Target application package for managed restrictions
    public static final String CHROME_PACKAGE = "com.android.chrome";

    /**
     * Builds and enforces the enterprise policy bundle on Google Chrome.
     * Called automatically on boot, during policy sync, and admin initialization.
     *
     * @param context Android context
     */
    public static void enforceDefaultChromePolicies(Context context) {
        try {
            // --------------------------------------------------------------------------
            // 1. Validate Device Owner Authority:
            // Only the official Android Device Owner app can enforce application restrictions.
            // --------------------------------------------------------------------------
            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
            if (dpm == null || !dpm.isDeviceOwnerApp(context.getPackageName())) {
                Log.w(TAG, "Cannot enforce Chrome policies: Test DPC is not Device Owner!");
                return;
            }

            // Bundle that holds all Chrome Managed Configurations
            Bundle chromeBundle = new Bundle();

            // Set Permission Policy to standard PROMPT mode so runtime permissions (Contacts, Camera, etc.) work normally
            dpm.setPermissionPolicy(DeviceAdminReceiver.getComponentName(context), DevicePolicyManager.PERMISSION_POLICY_PROMPT);

            // --------------------------------------------------------------------------
            // 2. Incognito Mode Configuration:
            // IncognitoModeAvailability:
            //   0 = Incognito mode available (standard browsing)
            //   1 = Incognito mode disabled (recommended for strict adult protection)
            //   2 = Incognito mode forced
            // --------------------------------------------------------------------------
            chromeBundle.putInt("IncognitoModeAvailability", 0);

            // --------------------------------------------------------------------------
            // 3. Force Google SafeSearch:
            // Ensures Google Search results strictly filter explicit adult content.
            // ForceGoogleSafeSearch (boolean) and SafeSearchMode (int, 1 = strict)
            // --------------------------------------------------------------------------
            chromeBundle.putBoolean("ForceGoogleSafeSearch", true);
            chromeBundle.putInt("SafeSearchMode", 1);

            // --------------------------------------------------------------------------
            // 4. Chrome SafeSites Adult Content Filter:
            // SafeSitesFilterBehavior:
            //   0 = Do not filter adult sites
            //   1 = Filter adult sites using Google SafeSearch API database
            // --------------------------------------------------------------------------
            chromeBundle.putInt("SafeSitesFilterBehavior", 1);

            // Enable YouTube Comments (ForceYouTubeRestrict: 0 = unrestricted comments, 1 = moderate, 2 = strict)
            chromeBundle.putInt("ForceYouTubeRestrict", 0);

            // --------------------------------------------------------------------------
            // 5. Force Direct Connection (Disables Proxy / VPN Extensions):
            // Setting ProxyMode to "direct" instructs Chrome to ignore any proxy extensions
            // or PAC scripts that might attempt to bypass network filters.
            // --------------------------------------------------------------------------
            chromeBundle.putString("ProxyMode", "direct");

            // --------------------------------------------------------------------------
            // 6. Enterprise URL Blocklist:
            // Chrome's URLBlocklist policy blocks matching URLs using URL filter patterns.
            // Both "URLBlocklist" (modern) and "URLBlacklist" (legacy) keys are provided
            // for compatibility across older and newer Chrome versions.
            // --------------------------------------------------------------------------
            String[] urlBlocklist = new String[]{
                // Video streaming & pirate hubs
                "fboxtv.org",

                // Unfiltered adult social media platforms
                "x.com",
                "twitter.com",
                "twimg.com",
                "reddit.com",
                "redditmedia.com",
                "redd.it",
                "tumblr.com",

                // Unfiltered messaging web clients
                "telegram.org",
                "t.me",

                // Online web proxies commonly used to bypass network filters
                "croxyproxy.com",
                "proxysite.com",
                "hide.me",
                "blockaway.net",

                // Addictive short-form infinite scroll video feeds
                "youtube.com/shorts",
                "www.youtube.com/shorts",
                "https://www.youtube.com/shorts/*",
                "https://youtube.com/shorts/*",
                "*://*.youtube.com/shorts/*"
            };
            chromeBundle.putStringArray("URLBlocklist", urlBlocklist);
            chromeBundle.putStringArray("URLBlacklist", urlBlocklist);

            // --------------------------------------------------------------------------
            // 7. Push Bundle to Chrome:
            // DevicePolicyManager sets these restrictions in Android's UserManager storage.
            // --------------------------------------------------------------------------
            dpm.setApplicationRestrictions(DeviceAdminReceiver.getComponentName(context), CHROME_PACKAGE, chromeBundle);
            Log.i(TAG, "Successfully enforced default Chrome policies & domain blocklist");

            // --------------------------------------------------------------------------
            // 8. Force Chrome Process Restart:
            // Chrome caches policies in memory during its lifecycle. Killing its background
            // process forces it to re-read the newly injected restrictions on next launch.
            // --------------------------------------------------------------------------
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.killBackgroundProcesses(CHROME_PACKAGE);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enforcing default Chrome policies", e);
        }
    }
}
