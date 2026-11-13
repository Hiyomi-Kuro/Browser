# AGENTS.md — Browser (com.kaori.browser)

Rules for every agent or developer working in this project.

## Scope

1. Work strictly inside `/storage/emulated/0/AndroidIDEProjects/browser/`.
2. Never clear, re-create or re-download the whole project.
3. Never delete existing source, resources, localizations or assets without a concrete,
   verified reason tied to the task.
4. Existing browser behaviour (tabs, sessions, downloads, user scripts, reader mode,
   ad blocking, PDF/Markdown export) must keep working.
5. Reuse existing implementations before adding new ones. Extend, do not fork:
   - `reader/ArticleExtractor`, `export/MarkdownExporter`, `export/PdfExporter`
   - `session/SessionManager`, `browser/BrowserContainer`, `view/NinjaWebView`
   - `activity/BrowserActivity` for tab orchestration only
6. Keep package name `com.kaori.browser`, application name and `versionName 1.0`.
   Do not bump version codes without being asked.

## Architecture

- New AI code lives under `com.kaori.browser.ai.*` with clear responsibilities
  (`api`, `config`, `security`, `chat`, `reader`, `agent`, `research`, `storage`, `ui`, `model`).
- `BrowserActivity` orchestrates; it must not absorb agent logic.
- Network work is asynchronous. WebView calls happen on the Android main thread.
- Any behaviour that cannot be executed on the real device must be reported as
  unverified instead of being described as passed.

## Security

- Never commit, log, print or transmit API keys, access tokens, cookies or passwords.
- The API key is stored only through `ai/security/ApiKeyStore` (Android Keystore,
  AES/GCM). It is never written to plain `SharedPreferences`, source files or logs.
- The API key is never exposed to page JavaScript.
- Agent tools never execute arbitrary model-provided JavaScript, shell or Android
  system commands. Only validated, parameterised tools may run.
- Untrusted page content never escalates into instructions, permissions or system actions.
- High-risk actions (form submit, download, closing user tabs, sensitive input)
  require explicit user confirmation.

## Build and verification

1. Every stage must compile:
   `bash gradlew --no-daemon -Pandroid.aapt2FromMavenOverride=/data/data/com.tom.rv2ide/files/home/.androidide/aapt2 :app:assembleDebug :app:testDebugUnitTest`
2. Failures are read from real compiler/test output, fixed in source, then re-run.
3. Never fake a pass by disabling checks, hiding exceptions or skipping tests.
4. Existing environment warnings (AGP/compileSdk) are not churned away.

## Temporary files

- All scratch artefacts (logs, downloaded pages, intermediate JSON/Markdown, test
  dumps, scripts) go to `.tmp/` inside the project root.
- `.tmp/` is disposable and is cleaned at the end of a task.
- Never treat real sources, Gradle files, `docs/`, or the built APK as scratch.
