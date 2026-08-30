// ==UserScript==
// @name CleanURL
// @namespace com.kaori.browser.builtin
// @version 1.0.0
// @description Remove common tracking parameters and unwrap known redirect links.
// @match <all_urls>
// @run-at document-start
// @grant none
// ==/UserScript==

(function () {
    'use strict';

    const exactTrackingParams = new Set([
        'spm', 'from', 'source', 'ref', 'fbclid', 'gclid', 'dclid', 'msclkid',
        'igshid', 'mc_cid', 'mc_eid', 'yclid', '_openstat', 'campaign',
        'campaignid', 'adgroupid', 'creative', 'keyword'
    ]);

    function isTrackingParam(name) {
        const key = name.toLowerCase();
        return key.startsWith('utm_') || exactTrackingParams.has(key);
    }

    function stripTracking(url) {
        for (const key of Array.from(url.searchParams.keys())) {
            if (isTrackingParam(key)) url.searchParams.delete(key);
        }
        return url;
    }

    function decodeHttpCandidate(value) {
        if (!value) return null;
        let candidate = value;
        for (let i = 0; i < 2; i++) {
            try {
                const decoded = decodeURIComponent(candidate);
                if (decoded === candidate) break;
                candidate = decoded;
            } catch (_) {
                break;
            }
        }
        return /^https?:\/\//i.test(candidate) ? candidate : null;
    }

    function unwrapKnownRedirect(url) {
        const host = url.hostname.toLowerCase();
        const path = url.pathname.toLowerCase();
        let target = null;

        if (host === 'link.zhihu.com') {
            target = decodeHttpCandidate(url.searchParams.get('target'));
        } else if (host.endsWith('google.com') && path === '/url') {
            target = decodeHttpCandidate(url.searchParams.get('q') || url.searchParams.get('url'));
        } else if (host === 'weibo.cn' && path.includes('sinaurl')) {
            target = decodeHttpCandidate(url.searchParams.get('u'));
        } else if (host.endsWith('baidu.com') && (path === '/link' || path.includes('redirect'))) {
            target = decodeHttpCandidate(url.searchParams.get('url') || url.searchParams.get('target'));
        }

        if (!target) return url;
        try {
            return new URL(target);
        } catch (_) {
            return url;
        }
    }

    function cleanHref(raw, base) {
        if (!raw || raw.startsWith('#') || /^(javascript|mailto|tel|data):/i.test(raw)) return raw;
        try {
            let url = new URL(raw, base || location.href);
            url = unwrapKnownRedirect(url);
            stripTracking(url);
            return url.href;
        } catch (_) {
            return raw;
        }
    }

    function cleanCurrentUrl() {
        try {
            const url = stripTracking(new URL(location.href));
            if (url.href !== location.href) {
                history.replaceState(history.state, document.title, url.href);
            }
        } catch (_) {
        }
    }

    function cleanAnchors(root) {
        if (!(root instanceof Element) && root !== document) return;
        const anchors = [];
        if (root instanceof HTMLAnchorElement) anchors.push(root);
        anchors.push(...root.querySelectorAll('a[href]'));
        for (const anchor of anchors) {
            const cleaned = cleanHref(anchor.getAttribute('href'), location.href);
            if (cleaned && cleaned !== anchor.href) anchor.href = cleaned;
        }
    }

    cleanCurrentUrl();
    cleanAnchors(document);

    document.addEventListener('click', event => {
        const anchor = event.target && event.target.closest ? event.target.closest('a[href]') : null;
        if (anchor) cleanAnchors(anchor);
    }, true);

    const root = document.documentElement || document;
    new MutationObserver(records => {
        for (const record of records) {
            for (const node of record.addedNodes) {
                if (node instanceof Element) cleanAnchors(node);
            }
        }
    }).observe(root, {subtree: true, childList: true});
})();
