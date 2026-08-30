// ==UserScript==
// @name AutoExpand
// @namespace com.kaori.browser.builtin
// @version 1.0.0
// @description Expand collapsed mobile content and suppress common app/login overlays.
// @match <all_urls>
// @run-at document-end
// @grant none
// ==/UserScript==

(function () {
    'use strict';

    const expandText = /^(展开全文|展开全部|展开剩余|阅读全文|查看全文|显示全部|继续阅读|更多内容|read more|show more|show all|expand)$/i;
    const appText = /(打开\s*app|在\s*app\s*中打开|下载\s*app|客户端打开|open in app|download app|get the app)/i;
    const loginText = /(登录后继续|登录查看|注册后继续|sign in to continue|log in to continue)/i;
    const appSelectors = [
        '[class*="open-app"]', '[id*="open-app"]', '[class*="openApp"]',
        '[class*="app-download"]', '[class*="download-app"]', '[class*="downloadApp"]',
        '[data-testid*="open-app"]', '[data-testid*="download-app"]',
        '[class*="app-banner"]', '[class*="appBanner"]'
    ];

    function visible(element) {
        if (!(element instanceof Element)) return false;
        const style = getComputedStyle(element);
        const rect = element.getBoundingClientRect();
        return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0;
    }

    function clickExpanders(root) {
        const candidates = root.querySelectorAll('button, a, [role="button"], summary');
        for (const element of candidates) {
            if (!visible(element)) continue;
            const text = (element.textContent || '').replace(/\s+/g, ' ').trim();
            if (text.length <= 24 && expandText.test(text)) {
                try {
                    element.click();
                } catch (_) {
                }
            }
        }
        for (const details of root.querySelectorAll('details:not([open])')) {
            details.open = true;
        }
    }

    function hideOnce(element) {
        if (!(element instanceof Element) || element.dataset.kaoriAutoExpandHidden === '1') return false;
        element.dataset.kaoriAutoExpandHidden = '1';
        element.style.setProperty('display', 'none', 'important');
        return true;
    }

    function hideAppPrompts(root) {
        for (const selector of appSelectors) {
            for (const element of root.querySelectorAll(selector)) {
                hideOnce(element);
            }
        }
    }

    function removeBlockingOverlays(root) {
        let removed = false;
        for (const element of root.querySelectorAll('body *')) {
            if (!visible(element)) continue;
            const style = getComputedStyle(element);
            if (style.position !== 'fixed') continue;
            const rect = element.getBoundingClientRect();
            const text = (element.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 500);
            const largeOverlay = rect.width >= innerWidth * 0.7 && rect.height >= innerHeight * 0.35;
            const bottomBar = rect.bottom >= innerHeight - 4 && rect.height <= innerHeight * 0.3;
            if ((largeOverlay && loginText.test(text)) || ((largeOverlay || bottomBar) && appText.test(text))) {
                if (hideOnce(element)) removed = true;
            }
        }
        if (removed) {
            document.documentElement.style.removeProperty('overflow');
            if (document.body) document.body.style.removeProperty('overflow');
        }
    }

    function unclipArticles(root) {
        const candidates = root.querySelectorAll('article, main, [role="main"], [class*="article"], [class*="content"]');
        for (const element of candidates) {
            const style = getComputedStyle(element);
            if (style.overflow === 'hidden' && element.scrollHeight > element.clientHeight + 120) {
                element.style.setProperty('max-height', 'none', 'important');
                element.style.setProperty('height', 'auto', 'important');
                element.style.setProperty('overflow', 'visible', 'important');
            }
        }
    }

    let scheduled = false;
    function run() {
        scheduled = false;
        const root = document.body || document.documentElement;
        if (!root) return;
        clickExpanders(root);
        hideAppPrompts(root);
        removeBlockingOverlays(document);
        unclipArticles(root);
    }

    function schedule() {
        if (scheduled) return;
        scheduled = true;
        requestAnimationFrame(run);
    }

    run();
    new MutationObserver(schedule).observe(document.documentElement || document, {
        subtree: true,
        childList: true,
        attributes: true,
        attributeFilter: ['class', 'style', 'aria-expanded']
    });
})();
