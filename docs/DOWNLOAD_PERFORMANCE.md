# Download performance — 2026-11-06

## What the GitHub research establishes

There is no verified universal winner in the sources reviewed. Project marketing,
star counts, and connection counts do not establish throughput. A fair ranking
needs the same source, file, network, device, output quality, and completion rule.
For Fetchly, aria2 is a useful reference for HTTP transfer techniques and yt-dlp
is the relevant reference for extracting and downloading supported media sites.

| Project | Relevant capability | Primary source |
| --- | --- | --- |
| aria2 | Segmented transfers, mirrors, persistent connections, resume and integrity checks | [README](https://github.com/aria2/aria2), [manual](https://aria2.github.io/manual/en/html/aria2c.html) |
| Gopeed | Multi-connection HTTP and concurrent tasks; also peer-to-peer protocols | [README](https://github.com/GopeedLab/gopeed) |
| Motrix | Desktop download manager advertising up to 64 threads per task | [README](https://github.com/agalwood/Motrix) |
| XDM | Resumable transfers and browser media capture | [README](https://github.com/subhra74/xdm) |
| yt-dlp | Concurrent native HLS/DASH fragments; default concurrency is one | [download options](https://github.com/yt-dlp/yt-dlp#download-options), [fragment implementation](https://github.com/yt-dlp/yt-dlp/blob/master/yt_dlp/downloader/fragment.py) |
| Seal / YTDLnis | Android media downloaders based on yt-dlp; Seal also supports embedded aria2 | [Seal](https://github.com/JunkFood02/Seal), [YTDLnis](https://github.com/deniscerri/ytdlnis) |

These are capabilities, not measured speed rankings. No competitor was installed
or benchmarked during this research. More concurrent requests can help when a
source limits each request, but do not raise the device's available bandwidth.
HTTP/2 may multiplex ranges over one transport connection, so request concurrency
does not necessarily mean separate TCP connections.

## Fetchly changes

- Direct files: four parallel byte ranges for files of at least 8 MiB when the
  initial response advertises byte ranges, has a known length, a strong ETag,
  and identity encoding. Smaller files use one stream and can resume with one
  range when the same validation requirements are met. Unvalidated sources stay
  sequential and restart from zero on retry.
- Each range sends If-Range, checks status 206, exact Content-Range, ETag,
  encoding and byte count. Independent file descriptors write into distinct
  offsets without concatenating temporary segment files afterward.
- Rejected or inconsistent range responses cancel all siblings and restart
  from scratch over a single request. Cancellation interrupts blocked OkHttp
  reads. Truncated responses cannot be accepted as completed transfers.
- Resume checkpoints record per-segment offsets with the source/resolved URLs,
  length and strong ETag. File data is flushed before each atomic sidecar update,
  at segment completion, interruption, and every 4 MiB or two seconds while data
  is flowing. Preallocated file length is never treated as downloaded progress.
- Each request gets up to two retries for connection failures or HTTP
  408/429/500/502/503/504, with cancellable exponential backoff starting at 500 ms.
  Retry-After seconds and HTTP dates are considered, with a 30-second delay cap.
  Failed segments resume at their last written byte without re-fetching completed
  segments. Segment retry budgets are not multiplied by a whole-file retry loop.
- Overlapping attempts for the same destination are serialized. Interrupted direct
  files survive WorkManager retries and manual Retry; completion and deletion
  remove their private cache data. Cache eviction or the 48-hour sweep can require
  restarting. Other yt-dlp media transfers retain their existing lifecycle.
- Native yt-dlp HLS/DASH fragment concurrency is four instead of one. This
  setting does not accelerate ordinary single-file HTTP media formats.
- Video and audio streams run concurrently, with at most two active streams
  per job, combined progress, stable merge ordering and sibling cancellation.
  FFmpeg still uses stream copy rather than re-encoding.

The four-range threshold and concurrency are conservative implementation choices,
not a claim that four is optimal for every mobile network. The initial GET used
to inspect headers is closed before parallel transfers start; some bytes may
already have arrived, and this adds overhead versus a single uninterrupted GET.
A job using two fragmented streams can have up to eight fragment requests active.
Separate queued jobs can multiply concurrency further.

## Verification and benchmarking

Run `python3 -m unittest discover -s app/src/test/python` and
`./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --console=plain`.

`DirectDownloaderTest` checks exact output bytes, ignored ranges, changed ETags,
wrong ranges, weak validators, small files, cancellation, and HTTP errors. It also
checks mid-body disconnect recovery, persisted resume, completed-segment skipping,
changed source versions/URLs, corrupt or evicted checkpoints, bounded retry
budgets, cancellation during backoff, overlapping attempts, and storage failures.
`StreamProgressTest` checks combined byte counts and speeds, including unknown sizes.
The Python suite verifies the fragment concurrency option reaches yt-dlp.

`DirectDownloaderTest.perResponseThrottleBenchmark` compares the sequential and
range paths on the same 8 MiB fixture with each response limited to 256 KiB per
100 ms. Both outputs must match the fixture. Timings are printed as
`CONTROLLED_BENCHMARK` in the test report; timing is deliberately not a CI pass/fail
assertion. This is a local JVM test of per-response throttling, not a mobile,
public-internet, or competitor benchmark.

Local verification passed: **206 Kotlin tests, 21 Python tests, Android lint,
and debug APK assembly**. The final full Gradle check used
`--offline -Pkotlin.incremental=false` to avoid stale incremental compiler state
following concurrent workspace builds.

An initial local benchmark measured 3,165 ms sequential versus 884 ms with ranges
(3.58× throughput). A subsequent passing download-test run measured 3,121 ms versus
875 ms (3.57×). Both outputs matched the fixture. These local observations show
the expected benefit under a per-response limit; they are not a general speedup
claim.

Before claiming Fetchly is fastest, measure at least five randomized runs per
configuration on the same Android device and network. Include a small file,
a large range-capable file, a source without range support, native HLS/DASH,
and separate video/audio. Record time to first byte, transfer time, and total
resolve-to-MediaStore time separately; verify identical output bytes or tracks.
Compare against aria2 for direct HTTP and Seal/YTDLnis for matched media formats.
Report medians and spread, plus device, network, versions and source conditions.

## Remaining work

Adaptive concurrency, reuse of extracted media metadata between streams, and
real-device comparisons remain future work. The new persistent resume/retry path
applies to direct files handled by Kotlin; it does not add a pause/resume API for
yt-dlp media downloads. Mirror discovery and BitTorrent are not implemented.
Source throttling, access restrictions and the unavailable JavaScript
runtime can limit available formats or speed independently of these changes.
