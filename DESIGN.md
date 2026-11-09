# Fetchly interface

The supplied design board and Fetchly Agent Manual define the Modern Hellenic visual direction. This is an Android utility interface: preserve real download state and platform capabilities. The owner requested closer fidelity to the board; Home, Downloads, and Library now use its three-destination navigation: an inset navigation dock with a shared low surface, fine outline, and sliding gold underline beneath the selected tab.

Use Material 3 roles from `ui/theme/Color.kt` and `Theme.kt` throughout. Gold uses #101010 backgrounds, #191817 low surfaces, #F4EEE5 foregrounds, and #D6BB92 accents. White uses #F7F2EA backgrounds, #F1EAE0 low surfaces, #24211D foregrounds, and muted bronze #675035 accents. Primary actions use flat #D9B873 gold with #282116 text and a #765A31 edge in both palettes. The owner approved the README gold-plaque style for the app: 10 dp clipped corners and a fine inset rule replace the previous ivory pills. Disabled actions fade the gold surface and content; Material Surface provides native pressed feedback. Warm inverse surface, foreground, and primary roles keep transient snackbars within the same neutral palette.

Use serif for editorial headlines and primary plaque labels; other functional labels and body copy use Android sans-serif. Shared shapes use 8/12/16/24/32 dp radii. Primary actions are chamfered rectangles with a 48 dp minimum logical height on Home and 56 dp on download confirmation; icon targets follow Material's 48 dp minimum. Content remains scrollable, inset-aware, and constrained to 640 dp.

Home order: brand header, heading, link-entry panel, supported platform shortcuts, then temple artwork. Do not invent platform support. Theme previews live in Settings → Appearance and use the existing persisted SYSTEM/LIGHT/DARK preferences. Library categories reuse the existing media-type filters over completed history entries. Downloads retains active transfers, failed and cancelled entries, search, retry, and file actions. Counts represent stored history rather than a fresh device storage scan.

## First-launch welcome

The welcome page follows the board’s dark Apollo presentation: the exact supplied logo, a spaced serif FETCHLY wordmark, “Paste. Choose. Fetch.” tagline, short purpose copy, and a gold plaque Get started action at least 56 dp high. Its centered, scrollable composition is constrained to 480 dp; Apollo fits without cropping. There is no artificial delay or simulated progress. While the saved welcome state is unknown, a dark brand loader shows Apollo and keeps Home hidden.

DataStore’s `welcome_completed` flag defaults to false, so new installs and existing installs without the key see welcome once. Get started must save completion successfully before entering Home; real saving disables the action and shows “Starting…”, while failure leaves welcome visible with a retry action. Later launches skip welcome. Notification permission is requested only after completion, and the main app resumes the selected or system palette. Incoming VIEW URLs remain pending until Home; existing compact-share routing is unchanged.

Native verification covered an actual VIEW URL with notification permission reset: welcome appeared unobstructed, Get started preceded the permission prompt, Home retained the URL, and force-stop/relaunch entered Home directly. Evidence: `/tmp/fetchly-welcome-first-open.png` and `/tmp/fetchly-welcome-returning-home.png`. Four welcome tests bring the passing unit suite to 232; lint and APK build also passed.

## Assets

- `fetchly_logo.png` is the exact owner-supplied 512×512 Apollo image from `1000026320.png`, copied byte-for-byte for version 2.4.2. Preserve its proportions and face. `fetchly_logo_light.png` is its grayscale launcher variant. The native splash also draws this complete logo.
- `fetchly_temple_dark.png` and `fetchly_temple_light.png` are high-resolution AI-generated architectural footer assets, produced with the built-in image tool. Their exact prompts and source paths are recorded in `docs/TEMPLE_ASSETS.md`. The original small board crop was removed after visual inspection showed it was blurry at phone size.
- Library symbols are native vector drawables. Do not replace brand artwork with generated faces.

## Verification limitation

Build, lint, unit tests, and Python tests are the automated checks. The refinement is verified against native screenshots in both palettes; network availability and individual platform restrictions still govern live source downloads.

## Reference fidelity refinement

Home uses a compact 30 dp brand mark, 28 sp serif heading, 14 dp horizontal and 8 dp vertical link-panel insets, 6 dp internal gaps, 48 dp primary action, and two rows of four platform shortcuts. The shortcuts use 36 dp visual capsules inside targets at least 56 dp high. The 112 dp architectural vignette follows those shortcuts and blends into the palette. Other functional targets stay at least 48 dp even where the concept board depicts smaller controls. Appearance shows two Apollo palette previews plus the existing Follow system option; switching still uses the same DataStore preferences. Fetch Options uses a compact real-media preview instead of a large hero image. Settings and download rows use fine neutral outlines and readable labels instead of the board's generated placeholder copy.

## Finish verification

The real Android 14 phone emulator was used to resolve the owner's public Apollo PNG, select the available PNG format, transfer ~2.2 MB with real bytes/speed/progress, save it, view the completed entry and Library count, and open it in Android's image viewer. Both Home palettes, Appearance, Settings, Fetch Options, active and completed Downloads, and Library were captured. White persisted through reinstall/relaunch. Enlarged text remained readable with scrolling; the emulator's agent keyboard helper hides the visible IME, so visible-keyboard layout was not captured.

Warm inverse Material roles theme transient snackbars in both palettes. Downloads and Library show a loading state until the combined database/work observation arrives; empty-state copy is reserved for loaded empty data. A regression test covers delayed work observation, the initial queued entry, and a later genuinely empty history.

Architectural artwork fades to the exact theme background along all four edges, with opaque outer stops so inset image boundaries disappear in Gold and White.

## Default UI scale

The shared Fetchly theme applies an 80% visual scale across the main app and compact share UI. Density changes are app-local; Android system chrome is unaffected. Android font preferences and its nonlinear font conversion are retained, and physical minimum touch targets and gesture thresholds are preserved. Logical design token sizes remain unchanged; their displayed size follows this scale.

Primary plaque actions are implemented once in `ui/components/PlaqueButton.kt`, including native button semantics, ripple feedback, enabled/disabled colors, inset geometry, and serif labels. Outlined and tonal secondary actions share its clipped-corner silhouette without a competing gold fill. Existing action icons and factual labels remain unchanged.

## Platform selection

Home now places a three-column app selector below the brand header, with all eight existing apps and YouTube Music. Selecting an app stays inside Fetchly and reveals a large app logo, its name, and media-mode chips above the link form. Instagram offers reels/post audio, reels, and single/bulk posts; X offers video and its audio; YouTube offers video/audio; Pinterest offers video; YouTube Music offers audio. Other existing apps retain video/audio choices with the existing availability caveat. Selection uses native radio semantics and chips expose their selected state. The existing gold and white palettes apply throughout.

The chosen kind opens the corresponding Fetch Options tab; unavailable kinds produce an error rather than silently using another media type. Batch review retains the requested kind. Post bulk means multiple post URLs through the existing batch flow; carousel expansion from one URL is not added by this change.

## Installed-app feedback update

The owner's latest feedback supersedes the earlier platform selector and compact preview guidance. Home keeps the generic link form first, followed by larger compact pebble-shaped supported-app marks. Those marks only animate on tap. Home and About show a small © mrduhlol credit. New generated charcoal/gold and ivory/bronze pavilion backgrounds use Fit to preserve the entire architecture.

Fetch Options shows a large landscape 16:9 or portrait 9:16 preview using source dimensions, with title below. Container buttons prefer MP4 when available. Extracted audio offers MP3 at 96, 128, 256 or 320 kb/s, retaining source languages; estimates derive from duration and selected output bitrate. FFmpeg encodes MP3 rather than renaming source audio. Share picker labels show size rather than “Includes audio”.

Settings persists exactly 80%, 100%, and 120% app scale. Palette changes apply immediately within the app. Launcher palette changes wait until all app activities leave the foreground, use distinct original-logo variants, and never kill the process. Android may cache launcher icon updates. Download progress uses an ongoing foreground notification with a 🗿 large icon and the required monochrome small icon.

The extraction runtime caches its module handle, warms off the UI thread, and coalesces overlapping requests for the same URL. Live fetch latency remains dependent on the source and connection; no fixed speed claim is made.

## Full-page Home wallpaper correction

The owner's 2.3 feedback supersedes the vignette guidance: Home uses one full-height portrait wallpaper behind its content, with no separate footer image. Both themes have purpose-generated continuous portrait compositions. Supported platform marks have transparent backgrounds and no filled pebble tiles; monochrome brands adapt to the palette. Both launcher variants and the inherited share-sheet icon have zero artificial inset, leaving Android's launcher mask to define the outside shape.


## Download controls in 2.3

Both full and compact pickers place Video/Audio first and MP4/MKV beneath, defaulting to MP4. Output choices retain actual source streams; MP4 converts non-AAC audio while copying video. Sizes use source byte counts or clearly marked bitrate/duration estimates. X multi-media posts expose a Bulk ZIP file choice, with original photo attachments and available video entries, stored in Downloads/Fetchly. Paused rows persist and remain outside automatic queue admission until Resume. Ongoing notifications expose Pause and a monochrome Moai small icon; successful notifications switch to the sunglasses version.

## 2.4 installed-app corrections

Paused downloads remain in the active list with persisted bytes and total. Their card dims and freezes its progress bar; only the green outlined Resume control stays enabled. Engine partials survive Pause and immediate requeue, while completed and explicitly cancelled non-direct attempts clean their scratch files. A real local HTTP test covers Range continuation.

Home keeps one wallpaper but subdues it with a theme scrim, restores translucent pebble backings behind platform marks, enlarges the fetch panel, and ends its scrollable content with a full-width black gradient and light © mrduhlol credit. Library replaces temple artwork with a generated lyre and scroll still life. Notifications show the media thumbnail rather than a large Moai; the small status icon uses a monochrome alpha adaptation of the original Noto Moai emoji. A new default-importance channel has sound and vibration disabled, allowing status-bar display without treating the transfer as a silent notification.

### 2.4.4 feedback

Home’s footer fades to the selected palette: warm ivory in light mode and black in dark mode, with matching readable copyright text. Notifications retain the small Moai status icons and download actions but show no media thumbnail or other large artwork. The installer and share sheet use the complete original PNG directly; adaptive launcher foregrounds fit a 72dp viewport with 18dp layer insets. These choices supersede the earlier black-only footer, notification thumbnail and zero-inset notes above.


## Approved scrolling Home artwork (November 2026)

Home now uses the approved matching night/day Apollo pavilion compositions, with quiet sky behind the existing controls and a complete monument revealed by scrolling. This supersedes the fixed wallpaper scrim and black footer fades. Each composition is split at 72% height into `fetchly_home_dark.webp` / `fetchly_home_light.webp` (sky) and matching `_monument.webp` assets. The sky can expand for content or larger text; the monument keeps its original proportions and remains below the controls. Both portions share the same scroll container. No gradient obscures the artwork.

Keep the original Apollo logo, functional sans-serif labels, existing serif headline, and clipped-corner gold plaque primary buttons with their fine inset rule. Home's brand name also uses serif, and the link panel has a restrained bronze border. Supported platforms and download behavior are unchanged.

The assets were generated with the built-in image tool and approved as previews before implementation. Dark prompt: extend the charcoal-blue sky upward in a 1:3 portrait while preserving the full Apollo pavilion, lyre statue, steps, olive foliage and distant mountains; reserve the upper sky for controls. Light prompt: preserve that composition and convert only the lighting/palette to soft morning daylight, ivory/limestone, muted bronze and misty mountains. Source files: `exec-574ec9c7-4015-45fc-abe8-b502dd9ee744.png` and `exec-f7ba5b1d-865c-4b48-b0e3-e9b673106c4e.png` in the generation session `01a1bd2d-c732-7670-96b2-6099df3d8b29`.


## Approved navigation dock (November 2026)

The owner selected preview B: an inset dock shared by Home, Downloads, and Library. In the main navigation, keep 16 dp horizontal margins, 8 dp vertical margins, 16 dp rounded corners, a 0.5 dp outline, and a low surface from the selected theme. Tabs have at least 70 dp height and retain their labels and outline icons. Selection uses the secondary gold/bronze role for both icon and semibold label; a 28 × 3 dp underline slides between equal-width tabs over 260 ms. Preserve native ripple, tab selection semantics, system motion scaling, RTL positioning, and the existing retained navigation state. The system gesture area remains outside the dock. This supersedes the earlier flat bar and per-icon tonal pill treatment for the main navigation.
