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
    private static volatile boolean sPoliciesEnforcedInProcess = false;

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
            chromeBundle.putInt("IncognitoModeAvailability", 1);

            // --------------------------------------------------------------------------
            // 3. Force Google SafeSearch & Lock Default Search Provider to Google:
            // Ensures Google Search results strictly filter explicit adult content and prevents
            // switching the default search engine to unmoderated providers (Bing, Yandex, DuckDuckGo).
            // ForceGoogleSafeSearch (boolean) and SafeSearchMode (int, 1 = strict)
            // --------------------------------------------------------------------------
            chromeBundle.putBoolean("ForceGoogleSafeSearch", true);
            chromeBundle.putInt("SafeSearchMode", 1);
            chromeBundle.putBoolean("DefaultSearchProviderEnabled", true);
            chromeBundle.putString("DefaultSearchProviderName", "Google");
            chromeBundle.putString("DefaultSearchProviderSearchURL", "https://www.google.com/search?q={searchTerms}&safe=active&ssui=on");

            // --------------------------------------------------------------------------
            // 4. Chrome SafeSites Adult Content Filter & Family Secure DNS (1.1.1.3):
            // SafeSitesFilterBehavior:
            //   0 = Do not filter adult sites
            //   1 = Filter adult sites using Google SafeSearch API database
            // DnsOverHttpsTemplates:
            //   Routes Chrome DNS through Cloudflare Family (blocks malware + adult domains at 0ms cost)
            // --------------------------------------------------------------------------
            chromeBundle.putInt("SafeSitesFilterBehavior", 1);
            chromeBundle.putString("DnsOverHttpsMode", "secure");
            chromeBundle.putString("DnsOverHttpsTemplates", "https://family.cloudflare-dns.com/dns-query");

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
                // The Pirate Bay & Pirate Proxy Mirrors
                "thepiratebay.org", "[*.]thepiratebay.org",
                "thepiratesproxy.org", "[*.]thepiratesproxy.org",
                "tpb.party", "[*.]tpb.party",
                "pirateproxy.live", "[*.]pirateproxy.live",
                "piratesbay.net", "[*.]piratesbay.net",
                "thepiratebay-proxy.com", "[*.]thepiratebay-proxy.com",
                "piratebay.party", "[*.]piratebay.party",
                "piratebayproxy.info", "[*.]piratebayproxy.info",
                "piratebay.live", "[*.]piratebay.live",
                "1337x.to", "[*.]1337x.to",
                "1337x.st", "[*.]1337x.st",
                "1337x.ws", "[*.]1337x.ws",
                "yts.mx", "[*.]yts.mx",
                "torrentgalaxy.to", "[*.]torrentgalaxy.to",
                "limetorrents.lol", "[*.]limetorrents.lol",
                "nyaa.si", "[*.]nyaa.si",
                "eztv.re", "[*.]eztv.re",
                "magnetdl.com", "[*.]magnetdl.com",
                "fboxtv.org", "[*.]fboxtv.org",

                // Unfiltered adult social media platforms
                "x.com", "[*.]x.com",
                "twitter.com", "[*.]twitter.com",
                "twimg.com", "[*.]twimg.com",
                "reddit.com", "[*.]reddit.com",
                "redditmedia.com", "[*.]redditmedia.com",
                "redd.it", "[*.]redd.it",
                "tumblr.com", "[*.]tumblr.com",

                // Unfiltered messaging web clients
                "telegram.org", "[*.]telegram.org",
                "t.me", "[*.]t.me",

                // Online VPNs, web proxies & browser unblockers
                "onlinevpn.app", "[*.]onlinevpn.app",
                "onlineproxy.org", "[*.]onlineproxy.org",
                "uproxy.online", "[*.]uproxy.online",
                "croxyproxy.com", "[*.]croxyproxy.com",
                "croxyproxy.net", "[*.]croxyproxy.net",
                "croxyproxy.rocks", "[*.]croxyproxy.rocks",
                "croxy.network", "[*.]croxy.network",
                "croxy.org", "[*.]croxy.org",
                "proxysite.com", "[*.]proxysite.com",
                "extremevpn.com", "[*.]extremevpn.com",
                "azureserv.com", "[*.]azureserv.com",
                "proxypal.net", "[*.]proxypal.net",
                "hide.me", "[*.]hide.me",
                "blockaway.net", "[*.]blockaway.net",
                "proxyium.com", "[*.]proxyium.com",
                "kproxy.com", "[*.]kproxy.com",
                "vpnbook.com", "[*.]vpnbook.com",
                "plainproxies.com", "[*.]plainproxies.com",
                "hidemyass.com", "[*.]hidemyass.com",
                "whoer.net", "[*.]whoer.net",
                "zalmos.com", "[*.]zalmos.com",
                "filterbypass.me", "[*.]filterbypass.me",
                "4everproxy.com", "[*.]4everproxy.com",
                "toolur.com", "[*.]toolur.com",
                "webproxy.to", "[*.]webproxy.to",
                "turbohide.org", "[*.]turbohide.org",
                "freeproxy.win", "[*.]freeproxy.win",
                "nodeunblocker.net", "[*.]nodeunblocker.net",
                "rammerhead.org", "[*.]rammerhead.org",
                "ultraviolet.dev", "[*.]ultraviolet.dev",
                "anarchyproxy.com", "[*.]anarchyproxy.com",
                "hyperproxy.network", "[*.]hyperproxy.network",
                "shuttleproxy.com", "[*.]shuttleproxy.com",
                "alohabrowser.com", "[*.]alohabrowser.com",
                "womginx.org", "[*.]womginx.org",
                "zend2.com", "[*.]zend2.com",
                "megaproxy.com", "[*.]megaproxy.com",
                "dontfilter.us", "[*.]dontfilter.us",
                "unblock-web.com", "[*.]unblock-web.com",
                "surfshield.io", "[*.]surfshield.io",
                "scramjet.org", "[*.]scramjet.org",
                "browserling.com", "[*.]browserling.com",
                "neverinstall.com", "[*.]neverinstall.com",
                "hyperbeam.com", "[*.]hyperbeam.com",
                "kasmweb.com", "[*.]kasmweb.com",
                "webvm.io", "[*.]webvm.io",
                "distrosea.com", "[*.]distrosea.com",
                "onworks.net", "[*.]onworks.net",
                "squarex.com", "[*.]squarex.com",
                "sqrx.com", "[*.]sqrx.com",

                // Major adult tube & cam platforms
                "pornhub.com", "[*.]pornhub.com",
                "xvideos.com", "[*.]xvideos.com",
                "xnxx.com", "[*.]xnxx.com",
                "xhamster.com", "[*.]xhamster.com",
                "redtube.com", "[*.]redtube.com",
                "youporn.com", "[*.]youporn.com",
                "spankbang.com", "[*.]spankbang.com",
                "eporner.com", "[*.]eporner.com",
                "chaturbate.com", "[*.]chaturbate.com",
                "stripchat.com", "[*.]stripchat.com",
                "camsoda.com", "[*.]camsoda.com",
                "bongacams.com", "[*.]bongacams.com",
                "onlyfans.com", "[*.]onlyfans.com",
                "brazzers.com", "[*.]brazzers.com",
                "naughtyamerica.com", "[*.]naughtyamerica.com",
                "realitykings.com", "[*.]realitykings.com",
                "bangbros.com", "[*.]bangbros.com",
                "faphouse.com", "[*.]faphouse.com",
                "nhentai.net", "[*.]nhentai.net",
                "rule34.xxx", "[*.]rule34.xxx",
                "redgifs.com", "[*.]redgifs.com",
                "erome.com", "[*.]erome.com",
                "motherless.com", "[*.]motherless.com",
                "tnaflix.com", "[*.]tnaflix.com",
                "spankwire.com", "[*.]spankwire.com",
                "tube8.com", "[*.]tube8.com",
                "txxx.com", "[*.]txxx.com",
                "hclips.com", "[*.]hclips.com",
                "upornia.com", "[*.]upornia.com",
                "daftsex.com", "[*.]daftsex.com",
                "hqporner.com", "[*.]hqporner.com",
                "sxyprn.com", "[*.]sxyprn.com",
                "beeg.com", "[*.]beeg.com",
                "javhd.com", "[*.]javhd.com",
                "hanime.tv", "[*.]hanime.tv",
                "coomer.su", "[*.]coomer.su",
                "fapello.com", "[*.]fapello.com",
                "simpcity.su", "[*.]simpcity.su",
                "fansly.com", "[*.]fansly.com",
                "manyvids.com", "[*.]manyvids.com",
                "livejasmin.com", "[*.]livejasmin.com",
                "myfreecams.com", "[*.]myfreecams.com",

                // Alternative search engines that allow disabling SafeSearch or viewing inline adult video results
                "bing.com", "[*.]bing.com",
                "yandex.com", "[*.]yandex.com",
                "yandex.ru", "[*.]yandex.ru",
                "duckduckgo.com", "[*.]duckduckgo.com",
                "search.brave.com", "[*.]search.brave.com",
                "search.yahoo.com", "[*.]search.yahoo.com",
                "startpage.com", "[*.]startpage.com",
                "qwant.com", "[*.]qwant.com",
                "ecosia.org", "[*.]ecosia.org",
                "swisscows.com", "[*.]swisscows.com",
                "dogpile.com", "[*.]dogpile.com",
                "metacrawler.com", "[*.]metacrawler.com",
                "mojeek.com", "[*.]mojeek.com",

                // Translation & web archive proxies used to wrap and bypass blocked URLs
                "translate.goog", "[*.]translate.goog",
                "web.archive.org", "[*.]web.archive.org",
                "archive.org", "[*.]archive.org",
                "archive.is", "[*.]archive.is",
                "archive.ph", "[*.]archive.ph",
                "archive.today", "[*.]archive.today",
                "archive.md", "[*.]archive.md",
                "12ft.io", "[*.]12ft.io",
                "anonymouse.org", "[*.]anonymouse.org",
                "hidester.com", "[*.]hidester.com",

                // Block SafeSearch & Search Preferences pages so SafeSearch cannot be toggled off via web UI
                "google.com/safesearch", "[*.]google.com/safesearch",
                "www.google.com/safesearch", "https://www.google.com/safesearch*",
                "google.com/preferences", "[*.]google.com/preferences",
                "www.google.com/preferences", "https://www.google.com/preferences*",

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
            SecurityLogger.log(context, "[CHROME_POLICY]", "Successfully pushed Chrome policies & domain blocklist (" + urlBlocklist.length + " rules)");

            // --------------------------------------------------------------------------
            // 8. Force Chrome Process Restart:
            // Chrome caches policies in memory during its lifecycle. Killing its background
            // process forces it to re-read the newly injected restrictions on next launch.
            // --------------------------------------------------------------------------
            try {
                if (!sPoliciesEnforcedInProcess) {
                    sPoliciesEnforcedInProcess = true;
                    ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                    if (am != null) {
                        am.killBackgroundProcesses(CHROME_PACKAGE);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "killBackgroundProcesses ignored: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, "Error enforcing default Chrome policies", e);
        }
    }
}
