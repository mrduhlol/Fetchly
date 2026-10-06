# Security

- Every pasted URL is treated as untrusted (`UrlSecurity.validate`): http(s)
  only, length-capped, pattern-checked.
- Platform detection is a pure local host match — no fetching, no scraping.
- Extraction runs on-device (direct probing, embedded yt-dlp engine):
  URL validation, host allowlist through capabilities, 1 MB body cap
  equivalent (headers-only reads), per-source rate awareness.
- Network timeouts: 15s connect / 30s read on analysis; 20s connect / 60s
  read on download. Raw server exceptions are never shown to users; the
  repository maps them to friendly messages.
- Filenames are sanitized before saving; remote metadata is never trusted
  for filesystem access.
- Downloads are size-guarded (2 GB max) and streamed to disk, never held
  in memory.
- Out of scope by design: private accounts, login/session scraping, DRM
  bypass, paywall bypass, circumventing platform protections.
