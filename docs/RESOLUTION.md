# Media resolution (on-device)

Fetchly has no backend. Resolving a pasted link is a local pipeline, in order:

1. **Validation** (`UrlSecurity`) — http(s) only, length-capped,
   pattern-checked. Shared text is mined for the first link.
2. **Probe** (`DirectMediaResolver`) — a HEAD request (GET fallback) against
   the URL itself. A real media `Content-Type` means it's a direct file
   (mp4/mp3/jpg/…): one `Original` option, `Content-Length` as its size when
   the server sends it, the URL path as its filename fallback.
3. **Engine** (`YtDlpEngine` → `YtDlpMapper`) — anything else is handed to
   the bundled yt-dlp running on-device (Chaquopy), which extracts formats
   as JSON. The mapper pairs video + audio into quality options, drops
   URL-less and storyboard entries, and never invents a size the extractor
   didn't report.
4. **Classification** (`ResolveErrors`) — when resolution fails, the raw
   engine message is mapped to a sentence naming the real cause (sign-in or
   bot check, private/deleted, geo-block, rate limit, unsupported link…),
   with the raw line kept for the details view.

The UI shows exactly these stages — "Checking the link…", then "Asking
yt-dlp…" — as they actually happen: no fake progress, no artificial delays.

## Download-time re-resolve

Stream URLs from extractors expire. When a download fails with 403/410, the
worker re-resolves the source URL once and matches the fresh formats against
the recorded stream signature (`OptionMatcher`). A match continues the
download with fresh URLs; no match fails honestly with the engine's reason.

## Direct file downloads

Direct files are fetched with OkHttp using `Accept-Encoding: identity`, so
the received byte count always matches `Content-Length`; progress is real or
indeterminate, never guessed. The finished file must pass the same checks as
every download: magic bytes match the container, non-empty, and ≥95% of the
expected size when the size is known.

## If a platform ever needs a server

A remote adapter would be required only when media for that source cannot be
resolved with a legitimate on-device mechanism. Until then the source stays
honestly reported — Fetchly will not gain backend infrastructure
speculatively.
