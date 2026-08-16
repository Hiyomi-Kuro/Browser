# Browser

Browser is a lightweight Android WebView browser focused on privacy, simplicity, and local control.

- Fully open source
- No trackers
- No unnecessary permissions
- Lightweight single-app architecture
- Uses Android System WebView
- Default start page: Bing
- Default search engine: Bing

## FEATURES

- Ad blocker using StevenBlack host lists
- Anti-browser-fingerprinting measures
- Cookie Banner Blocker with automatic opt-out support
- Per-domain and bookmark settings for JavaScript, cookies, and DOM storage
- Greasemonkey-style user scripts
- Bottom toolbar optimized for one-handed use
- Unlimited tabs
- Fast toggle for frequently used browser settings
- Search within the current website
- Web search from selected text
- Save webpages as PDF
- Save webpages as Markdown
- Open supported links in external apps
- Backup and restore browser data
- Custom start page and search engine
- Desktop mode
- Night mode
- Location, camera, and microphone controls

## SAVE WEBPAGE AS MARKDOWN

Browser can convert the currently loaded webpage from HTML to Markdown.

The feature uses the rendered page DOM and converts HTML content to Markdown using `flexmark-html2md-converter`.

To use it:

1. Open a webpage.
2. Open the Browser overflow menu.
3. Select the Save section.
4. Tap `Save as Markdown`.

Browser attempts to locate the main article content first. Common article containers such as `article`, `main`, full-text sections, and other main-content elements are preferred. If no suitable article container is found, Browser falls back to the page body.

Before conversion, common non-content elements such as navigation bars, scripts, styles, advertisements, sidebars, forms, buttons, and other interface elements are removed where possible.

Relative links and image URLs are converted to absolute URLs before Markdown conversion.

Markdown files are saved to:

`/storage/emulated/0/Download/Browser/`

The `Browser` folder is created automatically by Android when the first Markdown file is saved.

The filename is generated from the webpage title.

### Markor integration

If Markor is installed, Browser automatically opens the generated `.md` file in Markor using edit mode after saving.

The Markdown file remains stored in:

`Download/Browser/`

and can also be opened later with Markor or another Markdown editor.

Markor is optional. Markdown export works even when Markor is not installed.

### PubMed and scientific articles

The Markdown exporter can be used with PubMed, PubMed Central, and other article websites.

When a webpage contains accessible full-text article content, Browser attempts to extract the main article body and convert elements such as:

- Headings
- Paragraphs
- Links
- Lists
- Tables
- Block quotes
- Inline code
- Code blocks
- Images

into Markdown.

The exported file also contains the original webpage URL as source metadata.

Browser can only export content that is actually present and accessible in the currently loaded webpage. If a PubMed page only contains an abstract, or the full article is behind a login or paywall and is not loaded in the page, Browser cannot export unavailable full text.

Because websites use different HTML structures, Markdown conversion is best-effort and some pages may require manual cleanup after export.

## MAIN NAVIGATION

For each tab it is possible to enable or disable:

- AdBlock
- Anti-Browser-Fingerprinting measures
- Desktop Mode
- DOM Storage
- JavaScript

These settings, except Desktop Mode, are inherited from global settings when a new tab is created and are applied when a new website is opened.

Browser supports bookmark-specific settings for JavaScript, DOM Storage, and Desktop Mode. These settings are stored with the bookmark and applied when that bookmark is opened.

You can also define domains where cookies, DOM Storage, and JavaScript are always allowed from Browser Settings.

Third-party cookies are only supported when cookies are enabled and fingerprint protection is disabled.

Global website settings include:

- Allow location access
- Allow camera access
- Allow microphone access
- Download images depending on network conditions
- Night mode with algorithmic darkening

## COOKIE BANNER BLOCKER

Browser includes support for Mozilla Firefox Cookie Banner Rules.

The feature can automatically apply opt-out cookies and attempt to reject supported cookie banners.

Cookie Banner Blocker requires JavaScript.

Some banners, especially those running inside unsupported child contexts or using unusual website implementations, may not be handled automatically.

## GREASEMONKEY-STYLE USER SCRIPTS

Browser supports simple Greasemonkey-style user scripts.

Currently supported metadata:

- `@match`
- `@run-at`
- `@name`

`@match` is required.

Example:

`@match https://*/`

If the expression following `@match` starts and ends with `/`, it is treated as a regular expression.

If `@run-at` is set to `document-start`, the script runs from WebView `onPageStarted()`.

Otherwise the script runs after the page finishes loading.

The following metadata is not currently supported:

- `@include`
- `@exclude`
- `@grant`
- `@require`

## BROWSER SETTINGS

Browser Settings can be used to configure:

- Start page
- Search engine
- AdBlock host list
- Additional blocked domains
- Cookie exceptions
- JavaScript exceptions
- DOM Storage exceptions
- Website permissions
- User interface options

The default start page is:

`https://www.bing.com`

The default search engine is Bing.

## BACKUP AND RESTORE

Browser can back up and restore:

- Databases
- Bookmarks
- Preferences

Backup data is stored in:

`Documents/browser_backup`

## BUILD

The Android application is contained in a single `app` module.

Current application ID:

`com.kaori.browser`

Minimum Android version:

`minSdk 29`

The project can be built with Gradle.

## LICENSE

This app is licensed under the GPLv3.

The app uses code or libraries from:

- FOSS-Browser, https://codeberg.org/Gaukler_Faun/FOSS_Browser
- Ninja, https://github.com/mthli/Ninja
- Zip4j, https://github.com/srikanth-lingala/zip4j
- StevenBlack hosts, https://github.com/StevenBlack/hosts
- DuckDuckGo Android browser, https://github.com/duckduckgo/Android
- Flexmark Java / HTML to Markdown converter, https://github.com/vsch/flexmark-java

The app can optionally download and use Mozilla Firefox Cookie Banner Rules:

https://github.com/mozilla/cookie-banner-rules-list
