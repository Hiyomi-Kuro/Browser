// ==UserScript==
// @name CopyFreedom
// @namespace com.kaori.browser.builtin
// @version 1.0.0
// @description Restore copy, text selection and context menus blocked by pages.
// @match <all_urls>
// @run-at document-start
// @grant none
// ==/UserScript==

(function () {
    'use strict';

    const blockedEvents = ['copy', 'cut', 'contextmenu', 'selectstart'];
    const stopSiteBlocker = event => event.stopImmediatePropagation();

    for (const type of blockedEvents) {
        window.addEventListener(type, stopSiteBlocker, true);
        document.addEventListener(type, stopSiteBlocker, true);
    }

    function ensureStyle() {
        if (document.getElementById('kaori-copy-freedom-style')) return;
        const style = document.createElement('style');
        style.id = 'kaori-copy-freedom-style';
        style.textContent = `
            html, body, body * {
                -webkit-user-select: text !important;
                user-select: text !important;
                -webkit-touch-callout: default !important;
            }
            input, textarea, [contenteditable="true"] {
                -webkit-user-select: text !important;
                user-select: text !important;
            }
        `;
        const target = document.head || document.documentElement;
        if (target) {
            target.appendChild(style);
        } else {
            document.addEventListener('DOMContentLoaded', ensureStyle, {once: true});
        }
    }

    function removeInlineBlockers(root) {
        if (!(root instanceof Element) && root !== document) return;
        const selector = '[oncopy],[oncut],[oncontextmenu],[onselectstart]';
        const nodes = [];
        if (root instanceof Element && root.matches(selector)) nodes.push(root);
        nodes.push(...root.querySelectorAll(selector));
        for (const node of nodes) {
            node.removeAttribute('oncopy');
            node.removeAttribute('oncut');
            node.removeAttribute('oncontextmenu');
            node.removeAttribute('onselectstart');
        }
    }

    function apply() {
        ensureStyle();
        removeInlineBlockers(document);
    }

    apply();
    const root = document.documentElement || document;
    new MutationObserver(records => {
        for (const record of records) {
            for (const node of record.addedNodes) {
                if (node instanceof Element) removeInlineBlockers(node);
            }
        }
        ensureStyle();
    }).observe(root, {subtree: true, childList: true});
})();
