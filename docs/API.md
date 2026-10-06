# Media resolution (local-first)

Fetchly has no backend server. Analysis runs entirely on-device:

1. The URL is validated (`UrlSecurity`) and the platform detected
   (`PlatformDetector`, with a direct-file hint).
2. `SourceCapabilities` decides honestly: only `DIRECT` links are supported.
   Social platforms return "This source isn't supported yet." — no server
   is consulted, no scraping happens.
3. `DirectMediaResolver` probes the file with HEAD (GET-header fallback):
   real content type, real size, real filename. The UI is built only from
   what the probe returns — qualities and metadata are never invented.

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
