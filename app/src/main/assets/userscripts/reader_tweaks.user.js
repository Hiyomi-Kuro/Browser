// ==UserScript==
// @name ReaderTweaks
// @namespace com.kaori.browser.builtin
// @version 1.0.0
// @description General reading improvements with persistent per-script settings.
// @match <all_urls>
// @run-at document-end
// @grant GM_getValue
// @grant GM_setValue
// @grant GM_addStyle
// ==/UserScript==

(function () {
    'use strict';

    const defaults = {
        maxWidth: 860,
        fontSize: 18,
        lineHeight: 1.75,
        paragraphSpacing: 0.9,
        hideSidebars: true,
        hideRecommendations: true,
        hideFloatingAds: true,
        showProgress: true,
        showBackToTop: true
    };

    function setting(key) {
        const stored = GM_getValue(key, null);
        if (stored !== null && stored !== undefined) return stored;
        GM_setValue(key, defaults[key]);
        return defaults[key];
    }

    const config = {};
    for (const key of Object.keys(defaults)) config[key] = setting(key);

    const maxWidth = Math.min(1400, Math.max(480, Number(config.maxWidth) || defaults.maxWidth));
    const fontSize = Math.min(28, Math.max(14, Number(config.fontSize) || defaults.fontSize));
    const lineHeight = Math.min(2.4, Math.max(1.2, Number(config.lineHeight) || defaults.lineHeight));
    const paragraphSpacing = Math.min(2.4, Math.max(0.2, Number(config.paragraphSpacing) || defaults.paragraphSpacing));

    const hideSelectors = [];
    if (config.hideSidebars) {
        hideSelectors.push('aside', '[role="complementary"]', '.sidebar', '.side-bar', '[class*="sidebar"]');
    }
    if (config.hideRecommendations) {
        hideSelectors.push(
            '[class*="recommend"]', '[id*="recommend"]', '[class*="related"]',
            '[id*="related"]', '[class*="suggest"]'
        );
    }
    if (config.hideFloatingAds) {
        hideSelectors.push(
            '[class*="floating-ad"]', '[class*="float-ad"]', '[class*="fixed-ad"]',
            '[id*="floating-ad"]', '[data-ad-slot]'
        );
    }

    GM_addStyle(`
        article, main, [role="main"] {
            max-width: ${maxWidth}px !important;
            margin-left: auto !important;
            margin-right: auto !important;
        }
        article p, main p, [role="main"] p,
        article li, main li, [role="main"] li {
            font-size: ${fontSize}px !important;
            line-height: ${lineHeight} !important;
        }
        article p, main p, [role="main"] p {
            margin-top: ${paragraphSpacing}em !important;
            margin-bottom: ${paragraphSpacing}em !important;
        }
        article img, main img, [role="main"] img,
        article video, main video, [role="main"] video {
            max-width: 100% !important;
            height: auto !important;
        }
        ${hideSelectors.length ? hideSelectors.join(',') + ' { display: none !important; }' : ''}
        #kaori-reader-progress {
            position: fixed;
            z-index: 2147483646;
            top: 0;
            left: 0;
            width: 100%;
            height: 3px;
            transform: scaleX(0);
            transform-origin: left center;
            background: currentColor;
            pointer-events: none;
            opacity: .65;
        }
        #kaori-reader-top {
            position: fixed;
            z-index: 2147483646;
            right: 18px;
            bottom: 88px;
            width: 44px;
            height: 44px;
            border: 0;
            border-radius: 22px;
            font-size: 24px;
            line-height: 44px;
            text-align: center;
            box-shadow: 0 2px 10px rgba(0,0,0,.24);
            opacity: .72;
        }
    `);

    let progress = null;
    if (config.showProgress) {
        progress = document.createElement('div');
        progress.id = 'kaori-reader-progress';
        (document.body || document.documentElement).appendChild(progress);
    }

    let topButton = null;
    if (config.showBackToTop) {
        topButton = document.createElement('button');
        topButton.id = 'kaori-reader-top';
        topButton.type = 'button';
        topButton.textContent = '↑';
        topButton.setAttribute('aria-label', 'Back to top');
        topButton.addEventListener('click', () => window.scrollTo({top: 0, behavior: 'smooth'}));
        (document.body || document.documentElement).appendChild(topButton);
    }

    function updateReadingUi() {
        const root = document.documentElement;
        const total = Math.max(1, root.scrollHeight - innerHeight);
        const ratio = Math.max(0, Math.min(1, scrollY / total));
        if (progress) progress.style.transform = `scaleX(${ratio})`;
        if (topButton) topButton.style.display = scrollY > innerHeight * 0.8 ? 'block' : 'none';
    }

    let pending = false;
    function scheduleUpdate() {
        if (pending) return;
        pending = true;
        requestAnimationFrame(() => {
            pending = false;
            updateReadingUi();
        });
    }

    addEventListener('scroll', scheduleUpdate, {passive: true});
    addEventListener('resize', scheduleUpdate, {passive: true});
    updateReadingUi();
})();
