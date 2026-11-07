# Fetchly interface

The supplied design board and Fetchly Agent Manual define the Modern Hellenic visual direction. This is an Android utility interface: preserve real download state and platform capabilities. The owner requested closer fidelity to the board; Home, Downloads, and Library now use its three-destination navigation: a flat 68 dp bottom bar with a fine divider and a tonal selected indicator.

Use Material 3 roles from `ui/theme/Color.kt` and `Theme.kt` throughout. Gold uses #101010 backgrounds, #191817 low surfaces, #F4EEE5 foregrounds, and #D6BB92 accents. White uses #F7F2EA backgrounds, #F1EAE0 low surfaces, #24211D foregrounds, and muted bronze #675035 accents. Primary actions use flat #F3E5D0 ivory with #211C16 text in both palettes. Home’s disabled action retains ivory at 85% opacity with text at 60%; pressed feedback uses Material behavior. Warm inverse surface, foreground, and primary roles keep transient snackbars within the same neutral palette.

Use serif only for editorial headlines; functional labels and body copy use Android sans-serif. Shared shapes use 8/12/16/24/32 dp radii. Primary actions are pills with a 48 dp minimum height on Home and 56 dp on download confirmation; icon targets follow Material's 48 dp minimum. Content remains scrollable, inset-aware, and constrained to 640 dp.

Home order: brand header, heading, link-entry panel, supported platform shortcuts, then temple artwork. Do not invent platform support. Theme previews live in Settings → Appearance and use the existing persisted SYSTEM/LIGHT/DARK preferences. Library categories reuse the existing media-type filters over completed history entries. Downloads retains active transfers, failed and cancelled entries, search, retry, and file actions. Counts represent stored history rather than a fresh device storage scan.

## First-launch welcome

The welcome page follows the board’s dark Apollo presentation: the exact supplied logo, a spaced serif FETCHLY wordmark, “Paste. Choose. Fetch.” tagline, short purpose copy, and a flat ivory Get started action at least 56 dp high. Its centered, scrollable composition is constrained to 480 dp; Apollo fits without cropping. There is no artificial delay or simulated progress. While the saved welcome state is unknown, a dark brand loader shows Apollo and keeps Home hidden.

DataStore’s `welcome_completed` flag defaults to false, so new installs and existing installs without the key see welcome once. Get started must save completion successfully before entering Home; real saving disables the action and shows “Starting…”, while failure leaves welcome visible with a retry action. Later launches skip welcome. Notification permission is requested only after completion, and the main app resumes the selected or system palette. Incoming VIEW URLs remain pending until Home; existing compact-share routing is unchanged.

Native verification covered an actual VIEW URL with notification permission reset: welcome appeared unobstructed, Get started preceded the permission prompt, Home retained the URL, and force-stop/relaunch entered Home directly. Evidence: `/tmp/fetchly-welcome-first-open.png` and `/tmp/fetchly-welcome-returning-home.png`. Four welcome tests bring the passing unit suite to 232; lint and APK build also passed.

## Assets

- `fetchly_logo.png` is the exact owner-supplied Apollo image, copied byte-for-byte from `file_0000000011ac8211a5ad581011244116.png`. Preserve its proportions and face.
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

The shared Fetchly theme applies an 80% visual scale across the main app and compact share UI. Density changes are app-local; Android system chrome is unaffected. Android font preferences and its nonlinear font conversion are retained, and physical minimum touch targets and gesture thresholds are preserved. Logical design token sizes below remain unchanged; their displayed size follows this scale.
