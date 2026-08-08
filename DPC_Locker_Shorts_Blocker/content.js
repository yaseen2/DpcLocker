// DPC Locker - YouTube Shorts & SPA Navigation Interceptor
(function () {
    'use strict';

    function blockShorts() {
        // 1. If currently on a Shorts URL (/shorts/xyz), immediately redirect to YouTube Home
        if (window.location.pathname.startsWith('/shorts/')) {
            window.location.href = 'https://www.youtube.com/';
            return;
        }

        // 2. Remove Shorts Tab from Left Sidebar
        const shortsSidebarTabs = document.querySelectorAll('a[title="Shorts"], tp-yt-paper-item:has(a[title="Shorts"]), ytd-guide-entry-renderer:has(a[href*="/shorts"])');
        shortsSidebarTabs.forEach(el => el.remove());

        // 3. Remove Shorts Shelves from Home Feed & Channel Feeds
        const shortsShelves = document.querySelectorAll('ytd-reel-shelf-renderer, ytd-rich-shelf-renderer[is-shorts], grid-subheader-renderer:has(#title:contains("Shorts"))');
        shortsShelves.forEach(el => el.remove());

        // 4. Remove Shorts Video Thumbnails from Home Feed
        const shortsThumbnails = document.querySelectorAll('a[href*="/shorts/"]');
        shortsThumbnails.forEach(el => {
            const card = el.closest('ytd-rich-item-renderer, ytd-video-renderer, ytd-grid-video-renderer');
            if (card) {
                card.remove();
            } else {
                el.remove();
            }
        });
    }

    // Run immediately on page load
    blockShorts();

    // Intercept dynamic Single Page Application (SPA) DOM updates & pushState navigations
    const observer = new MutationObserver(() => {
        blockShorts();
    });

    if (document.body) {
        observer.observe(document.body, { childList: true, subtree: true });
    } else {
        document.addEventListener('DOMContentLoaded', () => {
            if (document.body) {
                observer.observe(document.body, { childList: true, subtree: true });
            }
        });
    }
})();
