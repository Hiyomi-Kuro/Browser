#!/system/bin/sh
set -eu

PACKAGE="com.kaori.browser"
ACTIVITY="com.kaori.browser.activity.BrowserActivity"
COMPONENT="${PACKAGE}/${ACTIVITY}"

usage() {
    cat <<'EOF'
Usage:
  sh tools/browserctl.sh launch
  sh tools/browserctl.sh open <http(s)://url-or-host>
  sh tools/browserctl.sh search <query>
  sh tools/browserctl.sh installed

Commands:
  launch      Bring the browser activity to the foreground.
  open        Open a web page in this browser using an explicit ACTION_VIEW.
              A value without a scheme is treated as https://.
  search      Run a browser web search using ACTION_WEB_SEARCH.
  installed   Print the installed APK path for com.kaori.browser.
EOF
}

fail() {
    echo "browserctl: $*" >&2
    exit 1
}

require_runtime() {
    command -v am >/dev/null 2>&1 || fail "'am' is not available in this Android SSH environment"
    command -v pm >/dev/null 2>&1 || fail "'pm' is not available in this Android SSH environment"
}

require_installed() {
    pm path "$PACKAGE" >/dev/null 2>&1 || fail "$PACKAGE is not installed; build/install the app first"
}

normalize_url() {
    case "$1" in
        http://*|https://*)
            printf '%s\n' "$1"
            ;;
        *://*)
            fail "unsupported URL scheme; only http:// and https:// are allowed"
            ;;
        *)
            printf 'https://%s\n' "$1"
            ;;
    esac
}

require_runtime

command_name="${1:-}"
case "$command_name" in
    launch)
        require_installed
        exec am start -W \
            -a android.intent.action.MAIN \
            -c android.intent.category.LAUNCHER \
            -n "$COMPONENT"
        ;;
    open)
        [ "$#" -eq 2 ] || fail "open requires exactly one URL argument"
        require_installed
        url="$(normalize_url "$2")"
        exec am start -W \
            -a android.intent.action.VIEW \
            -d "$url" \
            -n "$COMPONENT"
        ;;
    search)
        shift
        [ "$#" -gt 0 ] || fail "search requires a query"
        require_installed
        query="$*"
        exec am start -W \
            -a android.intent.action.WEB_SEARCH \
            --es query "$query" \
            -n "$COMPONENT"
        ;;
    installed)
        require_installed
        exec pm path "$PACKAGE"
        ;;
    -h|--help|help|"")
        usage
        ;;
    *)
        usage >&2
        fail "unknown command: $command_name"
        ;;
esac
