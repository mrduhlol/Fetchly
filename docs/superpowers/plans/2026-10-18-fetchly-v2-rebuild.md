# Fetchly v2.0 Rebuild — Implementation Plan

> **For the orchestrator:** REQUIRED SUB-SKILL: use superpowers:executing-plans (native execution — this session implements the plan; one fresh reviewer-pass at the end before the release tag). Steps use checkbox syntax.

**Goal:** Rebuild Fetchly from scratch: an on-device Android media fetcher — paste a link, yt-dlp resolves and downloads it, FFmpegKit merges, files land in MediaStore — with no backend of any kind.

**Architecture:** Single-module Kotlin + Jetpack Compose app. A Chaquopy-embedded Python module wraps yt-dlp for extraction *and* downloading (progress via a Kotlin callback object, cancel via a hook exception). A WorkManager foreground worker orchestrates download → merge (FFmpegKit `-c copy`) → verify → publish to MediaStore. Room for history, DataStore for settings, manual DI in `data/AppGraph.kt`.

**Tech stack:** Kotlin 2.0.20, Compose BOM 2024.09.00, Material 3, AGP 8.5.2 (kept — known-good with Chaquopy 15.0.1; `android.suppressUnsupportedCompileSdk=35`), Gradle 8.7 (kept), compileSdk 35, minSdk 29, targetSdk 34, Room 2.6.1, WorkManager 2.9.0, DataStore 1.1.1, OkHttp 4.12.0 (direct files + probe only), Coil 2.6.0, Chaquopy 15.0.1 (Python 3.10, `yt-dlp==2026.08.19`), `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.9` (verified on Maven Central).

**Spec:** `docs/superpowers/specs/2026-10-17-fetchly-rebuild-design.md`

**Why my error messages matter (context):** the bug the owner hit — "Fetchly couldn't retrieve media information from this link." — was the old app's *fallback* classification swallowing the real cause (stale yt-dlp got "Please sign in" from YouTube; the 2024 pin produced that for every video). The rebuild's error copy must always name the real cause.

---

## Global constraints (apply to every task)

1. **No backend, API, key, account, or endpoint — anywhere.** Hard rule (anti-goal). Settings has theme/quality/history/about only.
2. **yt-dlp downloads every extracted stream.** Never scrape extracted URLs into OkHttp (verified: they are throttled; yt-dlp's own downloader isn't). OkHttp is only for direct-file probing and direct-file downloads.
3. **Honesty:** no invented sizes, no fake progress, no silent videos passed off as successes. Unknown size → null → UI shows "—"; indeterminate progress when total unknown.
4. **Commit and push after every file change** (owner's workflow). Push with the explicit refspec: `git push origin refs/heads/main:refs/heads/main` (a stray tag named `main` makes the short form ambiguous).
5. Versions: versionCode **20**, versionName **"2.0"**; ABIs `arm64-v8a` + `x86_64` only; debug builds keep `applicationIdSuffix = ".debug"`.
6. Merge command is always copy-mux, never re-encode: `-i <video> -i <audio> -c copy -movflags +faststart <out>.mp4` (mkv: no faststart). Single stream → remux `-c copy` into the option's container.
7. Temp files live in `cacheDir/fetchly/`, are per-download named (`fetchly_<id8>_video/*_audio/*_merged`), and are deleted in a `finally` on failure/cancel/success. Stale (>48 h) temps are swept at app start.
8. Unit tests are JVM-only (`testDebugUnitTest`); Chaquopy/FFmpegKit are exercised on-device by the owner. Anything pure stays testable (that's why classification/formatting/sniffing are separate objects).
9. Python code must stay 3.9+ compatible (Chaquopy ships 3.10; the local smoke venv runs 3.14). Use `org.json` in main code (available on Android runtime; tests get the real impl via the existing `json-test` dependency).

---

## Review focus — five failure classes this plan must cover

| # | Failure class | Where covered |
|---|---|---|
| 1 | Source reports no sizes / unknown total (headless live-ish HLS, some CDNs) | Mapper returns `sizeBytes=null` (Task 4 tests); worker/UI indeterminate progress (Task 9/10) |
| 2 | Signed stream URLs expire (403/410) between Fetch and Download | Worker re-resolves once + `OptionMatcher` signature match (Tasks 6, 9) with tests |
| 3 | Garbage input: empty/whitespace clipboard, share text with prose/emoji, non-http schemes | `UrlSecurity` + `extractFirstUrl` table tests (Task 3) |
| 4 | opus-only audio (no m4a) → must not be mislabeled `.m4a` | Mapper container tests (Task 4) |
| 5 | Merge failure / disk full / kill mid-download → temp cleanup + honest error + retry possible | `DownloadErrors` tests + `TempFiles` policy + worker `finally` (Tasks 8/9) |

---

## Task 1 — Fresh skeleton that builds

**Goal:** old app code gone; new build config compiles an empty-but-real Compose app with Chaquopy + FFmpegKit wired; `assembleDebug` + `testDebugUnitTest` green.

**Files:**
- Modify `gradle/libs.versions.toml`: add `ffmpegKit = "8.1.9"`, `lifecycle-runtime-compose`, `okhttp-mockwebserver` (version.ref okhttp), `coroutines-test` (version.ref coroutines), `ffmpeg-kit-full` library entry.
- Modify `gradle.properties`: append `android.suppressUnsupportedCompileSdk=35` (verify `android.useAndroidX=true` present).
- Modify `app/build.gradle.kts`: chaquopy pip `install("yt-dlp==2026.08.19")`; `compileSdk = 35`; `abiFilters` → `arm64-v8a`, `x86_64`; `versionCode 20`, `versionName "2.0"`; add deps: `ffmpeg-kit-full`, `lifecycle-runtime-compose`; test deps: `mockwebserver`, `coroutines-test`.
- Delete: all of `app/src/main/java/com/fetchly/app/**` and `app/src/test/java/com/fetchly/app/**` (old app). Keep `app/src/main/python/fetchly_yt.py` until Task 4 rewrites it. Keep `app/src/main/res/**` (logo/icon/theme assets) except `values/strings.xml` + `values/themes.xml` get rewritten; delete `res/xml/file_paths.xml` (no FileProvider needed — MediaStore URIs are used).
- Write: `app/src/main/AndroidManifest.xml` (new), `app/src/main/java/com/fetchly/app/FetchlyApp.kt` (placeholder: `Application` + AppGraph stub), `MainActivity.kt` (placeholder Compose "Fetchly 2.0"), `data/AppGraph.kt` (skeleton), `ui/theme/{Color,Theme,Type}.kt`.
- Stash the 11 modified working-tree files first: `git stash push -m "superseded: partial v1.8→engine migration (rollback if ever needed)"` — they are the old partial migration, kept out of the way but recoverable.

**Steps:**
- [x] `git stash push` the working tree; `git status` clean.
- [x] Write the build files + placeholder source, delete old sources (`git rm -r`), commit + push after each file.
- [x] Verify: `ANDROID_HOME` set (toolchain task), `gradle :app:assembleDebug :app:testDebugUnitTest` → BUILD SUCCESSFUL; baseline debug APK size **124 MB** (both ABIs).
- [x] Contingency (only if build fails on it): Chaquopy-vs-AGP complaint → keep AGP as-is; compileSdk-35 rejection → already suppressed; if Chaquopy *hard-fails* on Gradle 8.7/AGP 8.5.2 (it should not — v1.8 builds on this exact combo), bump AGP to 8.6.1 + Gradle wrapper 8.9 as one atomic change and re-verify.

**Execution notes (2026-10-18, verified):**
- `buildToolsVersion = "35.0.0"` pinned — AGP 8.5.2's default (34.0.0) is not installed and its auto-install failed on this machine.
- Chaquopy 15.0.1's build venv runs its bundled 2023-era pip, which does `import cgi` — so the build machine needs a host **Python ≤ 3.12** on PATH (system Python 3.14 breaks it; Python 3.13 removed `cgi`). Local builds: prepend `mise` Python 3.12 to PATH. CI: add `actions/setup-python@v5` (3.12) to be safe, since ubuntu-latest's system Python drifts.
- APK contents verified: `lib/{arm64-v8a,x86_64}/libavcodec.so`, `libffmpegkit.so`, `assets/chaquopy/…` all present; yt-dlp 2026.08.19 packaged.

## Task 2 — Domain models & formatting utilities

**Files:** `domain/model/Models.kt`, `domain/util/{FormatSize,RelativeTime,MimeTypes}.kt`, tests mirroring each.

**Interfaces (exact):**
```kotlin
enum class Platform { YOUTUBE, INSTAGRAM, TIKTOK, TWITTER, REDDIT, PINTEREST, FACEBOOK, OTHER, DIRECT }
enum class MediaType { VIDEO, AUDIO, IMAGE }
enum class QualityKind { VIDEO, AUDIO, FILE }
data class StreamRef(val formatId: String, val url: String, val ext: String,
    val vcodec: String, val acodec: String, val height: Int?, val abr: Int?,
    val sizeBytes: Long?, val protocol: String?)
data class QualityOption(val id: String, val label: String, val kind: QualityKind,
    val container: String, val streams: List<StreamRef>, val needsMerge: Boolean, val sizeBytes: Long?)
data class MediaInfo(val sourceUrl: String, val platform: Platform, val title: String,
    val uploader: String?, val durationSeconds: Int?, val thumbnailUrl: String?,
    val mediaType: MediaType, val options: List<QualityOption>)
enum class DownloadStatus { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED }
data class HistoryEntry(val id: String, val title: String, val uploader: String?,
    val sourceUrl: String, val platform: Platform, val mediaType: MediaType,
    val thumbnailUrl: String?, val durationSeconds: Int?, val optionLabel: String,
    val container: String, val sizeBytes: Long?, val signature: List<StreamRef>,
    val status: DownloadStatus, val errorMessage: String?, val errorRaw: String?,
    val outputUri: String?, val outputName: String?, val outputPath: String?,
    val createdAt: Long, val updatedAt: Long)

object FormatSize { fun format(bytes: Long?): String?; fun formatApprox(bytes: Long?): String? } // "84 MB", "~84 MB"; null → null
object RelativeTime { fun format(thenMs: Long, nowMs: Long = System.currentTimeMillis()): String } // "just now","5 min ago","3 h ago","2 days ago"
object MimeTypes { fun forContainer(container: String): String; fun isMediaMime(mime: String?): Boolean; fun extensionFor(container: String): String }
```
**Tests (table-driven):** FormatSizeTest (null, 0, KB, MB, GB rounding, approx prefix), RelativeTimeTest (fixed `nowMs` for determinism), MimeTypesTest (mp4/mkv/webm/m4a/mp3/opus/jpg + unknown media check).

**Steps:** write test file → run (red) → implement → run (green) → commit+push per file.

## Task 3 — URL security & platform detection

**Files:** `domain/security/UrlSecurity.kt`, `domain/platform/PlatformDetector.kt`, tests.

**Interfaces:**
```kotlin
sealed interface UrlValidation { data class Valid(val url: String) : UrlValidation; data class Invalid(val reason: String) : UrlValidation }
object UrlSecurity { const val MAX_URL_LENGTH = 2048
    fun validate(raw: String?): UrlValidation
    fun extractFirstUrl(text: String?): String?   // share/clipboard prose → first http(s) token, trailing punctuation trimmed
    fun isPrivateOrLocalHost(host: String): Boolean }
enum class Capability { SUPPORTED, BEST_EFFORT }
object PlatformDetector { fun detect(url: String): Platform; fun capability(p: Platform): Capability }
```
Rules: trim; single line; length ≤ 2048; scheme http/https only; non-empty host; reject `localhost`, `*.local`, literal private/loopback IPs (10./192.168./172.16-31./127./::1). Detection: youtube.com/youtu.be/music.youtube.com → YOUTUBE; instagram/tiktok/x.com/twitter/reddit/facebook/pinterest hosts → respective; path ends in a known media extension (mp4/m4v/webm/mkv/mov/mp3/m4a/aac/ogg/opus/wav/jpg/jpeg/png/gif/webp) → DIRECT; else OTHER. `capability`: YOUTUBE+DIRECT → SUPPORTED; everything else → BEST_EFFORT (yt-dlp may still work; errors stay honest).

**Tests:** UrlSecurityTest (valid tables + invalid: blank, prose, ftp:, javascript:, whitespace-inside, private hosts, >2048, trailing `).` in share text, emoji-caked share text), PlatformDetectorTest (watch/shorts/youtu.be/music URL forms, social hosts, case-insensitive hosts, extension → DIRECT, unknown → OTHER).

## Task 4 — Python engine + Kotlin bridge + mapper

**Files:** `app/src/main/python/fetchly_yt.py` (rewrite), `data/engine/{YtDlpEngine,DownloadHook,YtDlpMapper}.kt`, `app/src/test/resources/extract_sample*.json`, `YtDlpMapperTest.kt`.

**Python API (exact JSON shapes — the mapper's contract):**
```python
def version() -> str                       # yt_dlp version or "missing"
def extract(url: str) -> str               # '{"ok":true,"title":...,"uploader":...,"duration":123,"thumbnail":"https://...","formats":[{"id","url","ext","vcodec","acodec","height","abr","filesize","filesize_approx","protocol"}...]}'
                                           # or '{"ok":false,"error":"<raw yt-dlp message>"}'
def download(url: str, format_id: str, dest_dir: str, prefix: str, hook) -> str   # url = the SOURCE PAGE url, not the stream url
                                           # '{"ok":true,"path":"/abs/file.ext","filename":"file.ext"}' | '{"ok":false,"cancelled":false,"error":...}'
```
- `extract`: `YoutubeDL({quiet, no_warnings, skip_download, noplaylist: True, socket_timeout: 20, js_runtimes disabled})` → `extract_info(url, download=False)`; catch everything → ok:false. **Verify the exact 2026.08.19 parameter that disables JS runtimes in the local venv** (the CLI `--no-js-runtimes` exists; find the dict equivalent) and pin it so the phone behaves exactly like the verified test.
- `download`: re-resolves the share URL and downloads `format` = format_id through yt-dlp's downloader into `dest_dir/prefix.%(ext)s`; `progress_hooks` bridge to `hook` (throttled ≥200 ms between JNI calls, plus always on status change); each hook call checks `hook.shouldCancel()` and raises `FetchlyCancelled` → returned as `{"ok":false,"cancelled":true}`; on success return the finished path (from the `finished` hook / `prepare_filename`); delete `.part` leftovers on cancel/error.
- `python fetchly_yt.py extract <url>` / `download <url> <format_id> <dest>` CLI mains for local smoke runs.

**Kotlin bridge:**
```kotlin
class DownloadHook(onProgress: (DownloadProgress) -> Unit) {
    fun update(downloaded: Long, total: Long, speed: Double)   // called FROM Python
    fun shouldCancel(): Boolean
    fun requestCancel()
}
data class DownloadProgress(val downloadedBytes: Long, val totalBytes: Long, val speedBytesPerSecond: Double)
sealed interface EngineDownload { data class Ok(val file: File) : EngineDownload; data object Cancelled : EngineDownload; data class Failed(val message: String) : EngineDownload }
class YtDlpEngine(private val context: Context, private val io: CoroutineDispatcher = Dispatchers.IO) : LinkExtractor {
    fun engineVersion(): String
    suspend fun extract(url: String): String                                   // raw JSON
    suspend fun download(pageUrl: String, formatId: String, destDir: File, prefix: String, hook: DownloadHook): EngineDownload }
interface LinkExtractor { suspend fun extract(url: String): String }
```
`Python.start(AndroidPlatform(context))` lazily inside a `@Synchronized ensureStarted()` on the IO dispatcher. Python module object cached.

**Mapper (pure, the pairing rules — spec of record):**
```kotlin
sealed interface MapResult { data class Ok(val info: MediaInfo) : MapResult; data class Fail(val rawError: String) : MapResult }
object YtDlpMapper { fun map(json: String, sourceUrl: String, platform: Platform): MapResult }
```
Algorithm: parse → `ok:false` → Fail(error). Collect formats with non-blank `url`+`ext`; drop `ext=mhtml`. Video streams: `vcodec!=none && height!=null` (muxed if also `acodec!=none`); audio streams: `acodec!=none && vcodec==none`; images: ext in (jpg,jpeg,png,webp,gif). Per height (desc): pick video by rank — muxed first, then codec `avc1>vp9/vp09>av01>other`, then size desc. Audio best: `mp4a/aac>opus>vorbis>other`, then abr desc. Option per height: label `"${h}p"` (muxed → streams=[video], needsMerge=false, no audio; else pair with best audio; if no audio exists anywhere → label `"${h}p (no audio)"`). Container: `mp4` when video is avc1/avc and (no audio or aac/mp4a), else `mkv`; muxed keeps that same rule. Audio option (when audio exists): label "Audio", `id="audio"`, container `m4a` if ext m4a/mp4 else ext (webm→webm), kind AUDIO. Degrade: zero video+exactly audio/one stream → single `FILE` option (kind FILE, container=ext, needsMerge=false). Images-only → FILE option kind FILE, mediaType IMAGE. `sizeBytes` per option = sum of streams **only if every stream's size is known**, else null. Option ids: `v${height}`, `audio`, `file`. mediaType VIDEO if any video option.

**Fixtures + tests:** run `fetchly_yt.py` in `/tmp/opencode/ytdlp-venv` against one YouTube video and dump JSON into `app/src/test/resources/extract_sample.json` (+ hand-make variants: progressive/muxed, single-format degrade, opus-only audio, unknown sizes, `ok:false` signin, malformed). YtDlpMapperTest asserts: pairing, codec preference, muxed passthrough, "(no audio)" label, size sums + unknown→null, audio container m4a vs webm, degrade, images, Fail cases. **Run the smoke against a second/third real URL** (short link, Shorts) per design review.

## Task 5 — Direct media resolver

**Files:** `data/direct/DirectMediaResolver.kt`, `DirectMediaResolverTest.kt` (MockWebServer).

```kotlin
sealed interface DirectResult { data class File(val url: String, val mime: String?, val sizeBytes: Long?, val filename: String?) : DirectResult; data object NotDirect : DirectResult }
class DirectMediaResolver(private val http: OkHttpClient) : DirectProbe {
    suspend fun resolve(url: String): DirectResult }   // interface DirectProbe for tests
```
Probe: HEAD → non-2xx/405/501/absent content-type → ranged GET (`Range: bytes=0-0`) reading `Content-Range` total. Media mime (via `MimeTypes.isMediaMime`) → File(url, mime, size, filename from Content-Disposition else URL path). `text/html`/JSON/anything else → NotDirect. Timeout 15 s. Tests: head-ok mp4; head-405→range; html → NotDirect; octet-stream → File; disposition filename; connection failure → NotDirect.

## Task 6 — Repository routing, honest errors, use case, retry matcher

**Files:** `data/repository/{MediaRepository,OnDeviceMediaRepository,ResolveErrors}.kt`, `domain/resolve/{ResolveUrlUseCase,OptionMatcher}.kt`, tests.

```kotlin
sealed interface ResolveOutcome { data class Success(val info: MediaInfo) : ResolveOutcome; data class Failed(val message: String, val raw: String?) : ResolveOutcome }
interface MediaRepository { suspend fun analyze(url: String): ResolveOutcome }
class OnDeviceMediaRepository(private val probe: DirectProbe, private val engine: LinkExtractor) : MediaRepository
class ResolveUrlUseCase(private val repo: MediaRepository) { suspend operator fun invoke(raw: String): ResolveOutcome }  // validate → Invalid(reason) as Failed
object OptionMatcher { fun match(signature: List<StreamRef>, options: List<QualityOption>): QualityOption? }  // primary: height+vcodec+acodec+ext multiset; fallback: height+ext; else null
```
Routing: YOUTUBE/known social → engine; DIRECT + OTHER → probe first, on NotDirect → engine. `extract` raw JSON → `YtDlpMapper.map` → Fail(raw) → `ResolveErrors.messageFor(raw)`. **Exact copy table (tests assert these strings):**

| Match (lowercased substring, checked in this order) | Message |
|---|---|
| `drm` | This media is DRM-protected, so it can't be downloaded. |
| `age` + (`confirm`/`restricted`) | This video is age-restricted. Fetchly can't sign in, so it can't download it. |
| `private video`/`login required`/`sign in`/`log in`/`cookies`/`members-only`/`requires authentication` | This media isn't publicly available. It needs a sign-in, or it's private. |
| `available in your country`/`geo`/`region` | The source says this media isn't available in your region. |
| `unsupported url`/`no suitable extractor` | This source isn't supported yet. |
| `video unavailable`/`removed`/`does not exist`/`404`/`410` | The source says this media doesn't exist or has been removed. |
| `unable to extract`/`player response`/`nsig`/`signature`/`precondition`/`failed to parse` | The source changed how it serves this media and Fetchly's engine couldn't read it. Updating Fetchly usually fixes this. |
| `timed out`/`timeout`/`getaddrinfo`/`connection`/`network`/`temporary failure`/`proxy` | Couldn't reach the source. Check your connection and try again. |
| anything else | Fetchly couldn't read this link. The engine said: "«raw trimmed to 160 chars»" |

Tests: ResolveErrorsTest (one case per row + ordering: DRM before sign-in, age before sign-in), OnDeviceMediaRepositoryTest (fakes: social→engine; DIRECT probe-hit→no engine call; DIRECT probe-miss→engine; engine `ok:false`→classified), OptionMatcherTest (match, fallback, null), ResolveUrlUseCaseTest (invalid URL never calls repo).

## Task 7 — Persistence: history DB + settings store

**Files:** `data/db/{FetchlyDatabase,HistoryDao,HistoryRepository}.kt`, `data/prefs/SettingsStore.kt`, tests for JSON codec + matcher-adjacent logic.

- Room `@Database(entities=[DownloadEntity::class], version=1, exportSchema=false)`; `DownloadEntity` mirrors `HistoryEntry` (incl. `kind`) with `signatureJson`/`streamUrlsJson` (org.json, `internal object Signatures` — `encodeSignature`/`encodeUrls`/`decode`), `workName: String`.
- `HistoryDao`: `observeAll(): Flow<List<DownloadEntity>>` (createdAt DESC), `getById`, `upsert`, `setStatus(id,status,message,raw,updatedAt)`, `setCompleted(id,uri,name,path,size,updatedAt)`, `delete(id)`, `clear()`.
- `HistoryRepository`: domain-facing mapping + `encode`/`decode` round-trip test (`SignaturesTest`).
- `SettingsStore` (DataStore, file "fetchly_settings"): `theme: Flow<ThemeChoice>` (SYSTEM/LIGHT/DARK), `defaultQuality: Flow<DefaultQuality>` (ASK/BEST), setters.

## Task 8 — Download pipeline: worker, queue, MediaStore, notifications, utils

**Files:** `data/download/{DownloadWorker,DownloadQueue,MediaStorePublisher,DownloadNotifications,DownloadActionReceiver,TempFiles,DownloadErrors}.kt`, `domain/util/{MediaSniffer,Filenames}.kt`, tests for the pure parts.

**Pure parts (with tests):**
```kotlin
object Filenames { fun sanitize(title: String): String; fun displayName(title: String, option: QualityOption): String  // "Title [1080p].mp4"
    fun tempPrefix(downloadId: String): String }
object MediaSniffer { fun matches(file: File, container: String): Boolean; fun containerOf(file: File): String? }  // magic bytes: mp4/m4a ftyp, webm/mkv EBML, mp3, ogg/opus, jpg, png, gif, webp, wav, flac
object DownloadErrors { fun textFor(raw: String): String }   // same table style: 403/410→"The download link expired…" (worker re-resolves once BEFORE surfacing), ENOSPC/"no space"→"Not enough free storage…", merge/"ffmpeg"→"…couldn't be assembled (merge failed).", network→…, default→raw-trimmed
object TempFiles { fun dir(context: Context): File; fun cleanupStale(dir: File, maxAgeMs: Long = 48h): Int }
```
**Worker** (`CoroutineWorker`, unique work name `"fetchly_" + id`, `NetworkType.CONNECTED`, exponential backoff 30 s, foreground `dataSync`): load entry → option (stored signature; on download failure 403/410-ish → re-resolve `sourceUrl` once → `OptionMatcher` → fresh option; no match → honest failure) → per stream: `engine.download(pageUrl, formatId, tempDir, tempPrefix_video|audio, hook)` feeding `setProgress` (keys: `stage`, `bytes`, `total`, `speed`) + `DownloadNotifications.progress`; single-stream options skip to merge; merge: FFmpegKit `execute` copy-mux (single stream → remux) with `-y` into `cacheDir/fetchly/…_merged.<container>`; verify (exists, size>0, `MediaSniffer.matches`, size ≥ 95% of reported when known); publish via `MediaStorePublisher` (`Movies/Fetchly`, `Music/Fetchly`, `Pictures/Fetchly`; `IS_PENDING` insert → copy → clear; pending entry deleted on failure); history `setCompleted`; completion notification (tap → open). `finally`: delete this download's temps. `onStopped` → `hook.requestCancel()` + OkHttp call cancel for direct downloads. Status: RUNNING → COMPLETED/FAILED/CANCELLED (never overwrite CANCELLED).
**Direct files** (option kind FILE from the probe): downloaded with OkHttp (GET, header `Accept-Encoding: identity` — important so the byte count matches Content-Length), streaming to temp with progress.
**Queue:** `enqueue(media, option): String` (id = UUID; insert QUEUED row; enqueue work) / `cancel(id)` / `retry(id)` (FAILED/CANCELLED → re-enqueue; worker re-resolves if needed) / `delete(id, deleteFile)` (MediaStore delete + row delete).
**Notifications:** channel `fetchly_downloads` ("Downloads"); progress notification (cancel action), completed (open file), failed (retry action); actions via `DownloadActionReceiver` (not exported). Manifest: add receiver; permissions already include FOREGROUND_SERVICE_DATA_SYNC + POST_NOTIFICATIONS.
**Tests:** FilenamesTest, MediaSnifferTest (temp files with real magic bytes), DownloadErrorsTest (table incl. disk-full + 403 + merge), TempFilesTest (age sweep).

## Task 9 — UI part 1: theme, navigation, Home, Result

**Files:** `ui/theme/*` (dark-first M3 palette, system/light/dark; no dynamic color), `navigation/FetchlyNav.kt` (routes home/result/downloads/settings/about; result guards on session), `MainActivity.kt` (edge-to-edge, share/link intents → `AppGraph.sharedIncoming` StateFlow; POST_NOTIFICATIONS launcher), `ui/home/*`, `ui/result/*`, `ui/components/{UrlInput,FetchButton,QualityRow,StatusCard,EmptyState}.kt`, `ui/result/ResultViewModel.kt`.

- Home: paste field, clipboard-peek suggestion chip on resume (`UrlSecurity.extractFirstUrl`), staged status during resolve ("Checking the link…" → "Asking yt-dlp…"), error card with the classified message, detected-platform line. On success → session holds `MediaInfo`, navigate to Result.
- `HomeViewModel(resolver, session, incoming)`: `HomeUiState(input, detected, phase: IDLE|ANALYZING, error, suggestion)` + `fetch()`; unit tests with a fake repository (success → session set + nav event; failure → error shown; invalid → no repo call; share text prefill).
- Result: thumbnail (Coil), title/uploader/duration, option list (best first, size or "—"), default-quality pref preselect (BEST→top, ASK→none), Download → `DownloadQueue.enqueue` → navigate Downloads.
- `AppGraph`: real singletons (db, history, settings, engine, http, probe, repository, queue, session, sharedIncoming) with `init()` for channel + temp sweep from `FetchlyApp.onCreate`.

## Task 10 — UI part 2: Downloads, Settings, About

**Files:** `ui/downloads/{DownloadsScreen,DownloadsViewModel,MediaDetailsSheet}.kt`, `ui/settings/{SettingsScreen,SettingsViewModel}.kt`, `ui/about/AboutScreen.kt`, VM tests.

- Downloads: active section (stage text, indeterminate or % + speed), history below (✔/✕ + retry); details sheet: open (ACTION_VIEW + read grant), share, delete file, retry. `DownloadsViewModel` combines `HistoryRepository.observeAll()` with `WorkManager.getWorkInfosForUniqueWorkFlow("fetchly_"+id)`; actions delegate to queue; tests with fakes for the mapping + retry/delete calls.
- Settings: Appearance(theme) / Downloads(default quality) / History(clear with confirm dialog) / About row → About screen. **No API/URL/endpoint field — test asserts this file contains none of "http://", "endpoint", "api key" (grep check, honest guard against regression).**
- About: app version (BuildConfig), yt-dlp version (engine, async), "no accounts, no servers" statement, limitations (no pause/resume; social best-effort; debug-signed builds need uninstall-first), licences (AGPL-3.0 app; yt-dlp Unlicense; Chaquopy; ffmpeg-kit-full LGPL-3.0).

## Task 11 — Release v2.0: CI, docs, tag, verify

**Files:** `.github/workflows/release-apk.yml` (platforms/build-tools → 35), `README.md`, `CHANGELOG.md`, `docs/RELEASE.md`, `docs/RESOLUTION.md` (rewrite: old API-era doc → new pipeline summary), design doc status line → building/built.

- [x] Full local gate: `gradle :app:testDebugUnitTest :app:assembleDebug` green (139 tests, 0 failures); APK 123.9 MB recorded; `aapt` badging checked (`.debug` suffix, versionCode 20 / "2.0", API 35, only the expected permissions).
- [x] Fresh reviewer pass (subagent) over the diff vs this plan + spec before tagging: 5 majors + 8 minors found; all majors fixed (notification ids, edge-to-edge insets, session-scoped ffmpeg cancel, atomic CANCELLED invariant, private-host parsing plus a DNS-level guard) and confirmed fixed in a second reviewer round. Minors fixed where cheap; the rest are recorded as accepted residuals (narrow, non-corrupting).
- [x] README: what v2.0 is, honest YouTube/social/direct scope, install + uninstall-old-first warning, size expectation, no-server statement.
- [ ] Tag `v2.0` → push tag → watch the CI run (GitHub API via curl; `gh` hangs on this machine) → release has `fetchly_v2.0.apk`.
- [ ] Owner device checklist (hand to user): YouTube 1080p with sound; Shorts; audio-only; direct mp4; private video → honest error; cancel + retry; Instagram post → honest best-effort outcome.

---

## Self-review (plan vs spec)

- Every "Left to the builder" item resolved: Compose/M3 screens (Tasks 9–10), Python API + hook bridge + cancel semantics (Task 4), merge-failure policy (temp always deleted; honest error; Task 8), Room schema (Task 7), channels/details-sheet/share filters (Tasks 8–10), CI/docs/version (Task 11), order/verification (whole plan).
- Anti-goals guarded by tests: no-API (grep guard Task 10 + Settings scope), no fake progress/sizes (mapper nulls + indeterminate paths), honest errors (ResolveErrors/DownloadErrors tables are exact strings).
- Contingencies named: AGP/Chaquopy build friction (Task 1), `js_runtimes` exact param (Task 4, verified in venv), ffmpeg-kit coordinate verified already (`ffmpeg-kit-full:8.1.9`).
- Known accepted risks (unchanged from spec): 16 KB-page Python alignment on future Chaquopy upgrade; YouTube PO-token/SABR walls; no JS runtime warning.
