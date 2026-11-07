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
- Direct-download checkpoints stay in private cache storage. A saved offset is
  reused only after revalidating both URLs, the strong ETag and total length;
  every resumed response must match its requested range before writing bytes.
  Checkpoints are atomically replaced after flushing data and are removed with
  the job on deletion/completion.
- The download action receiver (notification cancel/retry) is not exported.
- Out of scope by design: private accounts, login/session scraping, DRM
  bypass, paywall bypass, circumventing platform protections.

## Engine maintenance

The bundled runtime is Python 3.12 with yt-dlp 2026.10.26. Only an explicit user
check/install in Settings contacts the official PyPI package service. Update
wheels must be pure Python, runtime-compatible, hosted over HTTPS by
files.pythonhosted.org, and match the SHA-256 published in official package
metadata. Downloads, archive sizes and archive paths are bounded. Symlinks and
path traversal are rejected. Code is staged in private no-backup storage; the
active selection is replaced atomically and imported only in a fresh process.
A broken override falls back to the bundled engine, and a newer app bundle
supersedes an older override. No active engine is hot-swapped during a download.
SHA verification detects a mismatch against package metadata; it is not a
separate publisher signature independent of the package service.
