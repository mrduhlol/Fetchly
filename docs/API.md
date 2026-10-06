# Media resolution (local-first)

Fetchly has no backend server. Analysis runs entirely on-device:

1. The URL is validated (`UrlSecurity`) and the platform detected
   (`PlatformDetector`, with a direct-file hint).
2. `SourceCapabilities` decides honestly: `DIRECT` links and `YOUTUBE`
   (via the embedded on-device yt-dlp engine) are supported. Other social
   platforms return "This source isn't supported yet." — no server is
   consulted, no scraping happens.
3. `DirectMediaResolver` probes direct files with HEAD (GET-header
   fallback): real content type, size, and filename. YouTube links go
   through `YtDlpEngine` → `YtDlpMapper`, which keeps only genuinely
   downloadable streams (URL-less and storyboard formats are dropped).

## Direct format model

Every resolved file exposes exactly one honest format:

- quality: `Original`
- container: derived from the `Content-Type` header (URL path as fallback)
- size: `Content-Length` when the server sends it, otherwise unknown
- optional: `bitrateKbps`, `width`/`height`, `hasAudio` — only when known

## If a platform needs a server one day

A platform adapter would be genuinely required only when media for that
source cannot be resolved with a local or official on-device mechanism
(signed URLs, authenticated APIs, stream manifests). Until then the source
stays marked unsupported — Fetchly will not gain backend infrastructure
speculatively.
