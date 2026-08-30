// ==UserScript==
// @name SearchEnhancer
// @namespace com.kaori.browser.builtin
// @version 1.0.0
// @description Improve Baidu, Bing and Google result pages with clean links and quick actions.
// @match *://*.baidu.com/s*
// @match *://*.bing.com/search*
// @match *://*.google.com/search*
// @run-at document-end
// @grant GM_getValue
// @grant GM_setValue
// @grant GM_addStyle
// @grant GM_openInTab
// @grant GM_setClipboard
// ==/UserScript==

(function () {
    'use strict';

    const defaults = {
        openInNewTab: true,
        highlightDomains: [],
        blockDomains: []
    };

    function setting(key) {
        const stored = GM_getValue(key, null);
        if (stored !== null && stored !== undefined) return stored;
        GM_setValue(key, defaults[key]);
        return defaults[key];
    }

    const openInNewTab = Boolean(setting('openInNewTab'));
    const highlightDomains = Array.isArray(setting('highlightDomains')) ? setting('highlightDomains') : [];
    const blockDomains = Array.isArray(setting('blockDomains')) ? setting('blockDomains') : [];

    const trackingParams = new Set([
        'spm', 'from', 'source', 'ref', 'fbclid', 'gclid', 'dclid', 'msclkid',
        'igshid', 'mc_cid', 'mc_eid', 'yclid'
    ]);

    GM_addStyle(`
        .kaori-search-tools {
            display: flex;
            align-items: center;
            gap: 8px;
            flex-wrap: wrap;
            margin: 4px 0 8px;
            font-size: 12px;
            opacity: .82;
        }
        .kaori-search-tools button {
            border: 1px solid currentColor;
            border-radius: 12px;
            padding: 2px 8px;
            background: transparent;
            color: inherit;
            font: inherit;
        }
        .kaori-search-highlight {
            outline: 2px solid currentColor !important;
            outline-offset: 3px !important;
        }
    `);

    function domainMatches(hostname, rule) {
        const host = String(hostname || '').toLowerCase().replace(/^www\./, '');
        const wanted = String(rule || '').toLowerCase().replace(/^www\./, '');
        return wanted && (host === wanted || host.endsWith('.' + wanted));
    }

    function cleanTarget(raw) {
        try {
            let url = new URL(raw, location.href);
            if (url.hostname.endsWith('google.com') && url.pathname === '/url') {
                const target = url.searchParams.get('q') || url.searchParams.get('url');
                if (target && /^https?:\/\//i.test(target)) url = new URL(target);
            }
            for (const key of Array.from(url.searchParams.keys())) {
                const lower = key.toLowerCase();
                if (lower.startsWith('utm_') || trackingParams.has(lower)) url.searchParams.delete(key);
            }
            return url.href;
        } catch (_) {
            return raw;
        }
    }

    function resultNodes() {
        const host = location.hostname;
        if (host.includes('baidu.com')) {
            return Array.from(document.querySelectorAll('#content_left .result, #content_left .c-container'));
        }
        if (host.includes('bing.com')) {
            return Array.from(document.querySelectorAll('#b_results > li.b_algo'));
        }
        return Array.from(document.querySelectorAll('div.MjjYud, div.tF2Cxc')).filter(node => node.querySelector('h3'));
    }

    function primaryLink(result) {
        const headings = result.querySelectorAll('h3');
        for (const heading of headings) {
            const anchor = heading.closest('a') || heading.parentElement && heading.parentElement.closest('a');
            if (anchor && anchor.href) return anchor;
        }
        return result.querySelector('a[href]');
    }

    function displayDomain(result, url) {
        try {
            const host = new URL(url).hostname.replace(/^www\./, '');
            if (!/baidu\.com$/i.test(host)) return host;
        } catch (_) {
        }
        const visibleUrl = result.querySelector('.c-showurl, .c-color-gray, cite');
        if (visibleUrl) {
            const match = (visibleUrl.textContent || '').match(/(?:https?:\/\/)?([a-z0-9.-]+\.[a-z]{2,})/i);
            if (match) return match[1].replace(/^www\./, '');
        }
        try {
            return new URL(url).hostname.replace(/^www\./, '');
        } catch (_) {
            return '';
        }
    }

    function addButton(parent, label, action) {
        const button = document.createElement('button');
        button.type = 'button';
        button.textContent = label;
        button.addEventListener('click', event => {
            event.preventDefault();
            event.stopPropagation();
            action();
        });
        parent.appendChild(button);
    }

    function enhanceResult(result, index) {
        if (result.dataset.kaoriSearchEnhanced === '1') return;
        const anchor = primaryLink(result);
        if (!anchor || !anchor.href) return;

        const cleanUrl = cleanTarget(anchor.href);
        anchor.href = cleanUrl;
        const title = (anchor.textContent || '').replace(/\s+/g, ' ').trim();
        const domain = displayDomain(result, cleanUrl);

        if (blockDomains.some(rule => domainMatches(domain, rule))) {
            result.style.setProperty('display', 'none', 'important');
            result.dataset.kaoriSearchEnhanced = '1';
            return;
        }
        if (highlightDomains.some(rule => domainMatches(domain, rule))) {
            result.classList.add('kaori-search-highlight');
        }

        const tools = document.createElement('div');
        tools.className = 'kaori-search-tools';

        const number = document.createElement('span');
        number.textContent = '#' + index;
        tools.appendChild(number);

        if (domain) {
            const domainLabel = document.createElement('span');
            domainLabel.textContent = domain;
            tools.appendChild(domainLabel);
        }

        addButton(tools, '复制标题', () => GM_setClipboard(title));
        addButton(tools, '复制链接', () => GM_setClipboard(cleanUrl));

        const heading = result.querySelector('h3');
        if (heading && heading.parentElement) heading.parentElement.insertAdjacentElement('afterend', tools);
        else result.insertBefore(tools, result.firstChild);

        if (openInNewTab) {
            anchor.addEventListener('click', event => {
                if (event.defaultPrevented) return;
                event.preventDefault();
                event.stopPropagation();
                GM_openInTab(cleanUrl, {active: true});
            }, true);
        }

        result.dataset.kaoriSearchEnhanced = '1';
    }

    let scheduled = false;
    function run() {
        scheduled = false;
        resultNodes().forEach((result, index) => enhanceResult(result, index + 1));
    }

    function schedule() {
        if (scheduled) return;
        scheduled = true;
        requestAnimationFrame(run);
    }

    run();
    new MutationObserver(schedule).observe(document.documentElement || document, {subtree: true, childList: true});
})();
