# Security

- Every pasted URL is treated as untrusted (`UrlSecurity.validate`): http(s)
  only, length-capped, pattern-checked. Shared text is mined for the first
  link only.
- Platform detection is a pure local host match — no fetching, no scraping.
- Resolution and downloading run on-device: a direct-media probe and the
  bundled yt-dlp engine (Chaquopy). The probe reads response headers only —
  no body is buffered for analysis.
- Fetchly's own HTTP client (probing, direct downloads, redirects) refuses
  DNS answers that point at loopback, LAN, link-local or unique-local
  addresses. The bundled engine carries its own HTTP stack that cannot be
  intercepted; it still only receives links that already passed the input
  check, but a host that resolves privately only after that check is not
  re-verified inside the engine.
- Network timeouts: 15 s connect / read / call cap for the direct probe;
  15 s connect / 30 s read for direct-file downloads, with no overall call
  cap — a download is allowed to take as long as it takes. The engine's own
  HTTP stack follows yt-dlp defaults.
- Raw server exceptions are never shown to users; the repository maps them
  to honest sentences and keeps the raw line alongside for diagnosis.
- Filenames are sanitized (`Filenames`) before saving; remote metadata is
  never trusted for filesystem access.
- Downloads stream straight to disk in 64 KB buffers — never held in
  memory — and must pass magic-byte and size verification before they are
  published to MediaStore.
- The download action receiver (notification cancel/retry) is not exported.
- Out of scope by design: private accounts, login/session scraping, DRM
  bypass, paywall bypass, circumventing platform protections.
