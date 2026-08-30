// ==UserScript==
// @name SiteCleaner
// @namespace com.kaori.browser.builtin
// @version 1.0.0
// @description Lightweight site-specific cleanup rules for common Chinese content sites.
// @match *://*.zhihu.com/*
// @match *://*.bilibili.com/*
// @match *://*.weibo.com/*
// @match *://*.weibo.cn/*
// @match *://*.baidu.com/*
// @match *://*.csdn.net/*
// @match *://*.jianshu.com/*
// @run-at document-end
// @grant none
// ==/UserScript==

(function () {
    'use strict';

    const RULES = [
        {
            id: 'SiteCleaner-Zhihu',
            hosts: ['zhihu.com'],
            selectors: [
                '.AppHeader', '.CornerButtons', '.Pc-card', '.Question-sideColumn',
                '.Recommendations-Main', '.Modal-wrapper', '.OpenInAppButton',
                '[class*="DownloadApp"]', '[class*="OpenInApp"]'
            ],
            text: /(打开知乎App|App 内打开|下载知乎 App)/i
        },
        {
            id: 'SiteCleaner-Bilibili',
            hosts: ['bilibili.com'],
            selectors: [
                '.ad-report', '.bili-dyn-home--member', '.recommended-swipe',
                '.desktop-download-tip', '[class*="openapp"]', '[class*="open-app"]',
                '[class*="download-app"]'
            ],
            text: /(打开哔哩哔哩App|下载客户端|打开 App)/i
        },
        {
            id: 'SiteCleaner-Weibo',
            hosts: ['weibo.com', 'weibo.cn'],
            selectors: [
                '[class*="LoginLayer"]', '[class*="login_layer"]', '[class*="woo-box-flex"][class*="fixed"]',
                '[class*="OpenApp"]', '[class*="open-app"]', '[class*="download"]'
            ],
            text: /(打开微博App|登录后查看更多|下载微博)/i
        },
        {
            id: 'SiteCleaner-Baidu',
            hosts: ['baidu.com'],
            selectors: [
                '#content_right', '.ec_wise_ad', '[class*="wise-open-app"]',
                '[class*="openapp"]', '[class*="open-app"]', '[class*="app-download"]'
            ],
            text: /(打开百度App|百度 App 内打开|下载百度App)/i
        },
        {
            id: 'SiteCleaner-CSDN',
            hosts: ['csdn.net'],
            selectors: [
                '#asideProfile', '#asideNewNps', '.recommend-box', '.recommend-tit-mod',
                '.passport-login-container', '.csdn-side-toolbar', '.blog-footer-bottom',
                '[class*="open-app"]', '[class*="download-app"]'
            ],
            text: /(打开CSDN APP|登录后继续阅读|下载 CSDN)/i
        },
        {
            id: 'SiteCleaner-Jianshu',
            hosts: ['jianshu.com'],
            selectors: [
                '[class*="recommend"]', '[class*="download-app"]', '[class*="open-app"]',
                '[class*="modal"]', '[class*="note-bottom"]'
            ],
            text: /(打开简书App|下载简书|登录后继续)/i
        }
    ];

    function hostMatches(host, base) {
        return host === base || host.endsWith('.' + base);
    }

    const host = location.hostname.toLowerCase();
    const rule = RULES.find(candidate => candidate.hosts.some(base => hostMatches(host, base)));
    if (!rule) return;

    function hide(element) {
        if (!(element instanceof Element)) return;
        if (element.getAttribute('data-kaori-site-cleaner') === rule.id) return;
        element.setAttribute('data-kaori-site-cleaner', rule.id);
        element.style.setProperty('display', 'none', 'important');
    }

    function applySelectors(root) {
        for (const selector of rule.selectors) {
            let nodes;
            try {
                nodes = root.querySelectorAll(selector);
            } catch (_) {
                continue;
            }
            for (const node of nodes) hide(node);
        }
    }

    function applyTextOverlays(root) {
        const candidates = root.querySelectorAll('body > div, body > section, [role="dialog"]');
        for (const element of candidates) {
            if (element.dataset.kaoriSiteCleaner) continue;
            const text = (element.textContent || '').replace(/\s+/g, ' ').trim().slice(0, 500);
            if (!rule.text.test(text)) continue;
            const style = getComputedStyle(element);
            const rect = element.getBoundingClientRect();
            const floating = style.position === 'fixed' || style.position === 'sticky';
            const blocking = rect.width >= innerWidth * 0.6 && rect.height >= innerHeight * 0.15;
            if (floating || blocking) hide(element);
        }
    }

    let scheduled = false;
    function run() {
        scheduled = false;
        const root = document.body || document.documentElement;
        if (!root) return;
        applySelectors(root);
        applyTextOverlays(document);
        document.documentElement.style.removeProperty('overflow');
        if (document.body) document.body.style.removeProperty('overflow');
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
        attributeFilter: ['class', 'style']
    });
})();
