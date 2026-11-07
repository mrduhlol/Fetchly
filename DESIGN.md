# Fetchly interface

The supplied design board and Fetchly Agent Manual define the Modern Hellenic visual direction. This is an Android utility interface: preserve real download state, existing navigation, and platform capabilities.

Use Material 3 roles from `ui/theme/Color.kt` and `Theme.kt` throughout. Gold uses #101010 backgrounds, #191817 low surfaces, #F4EEE5 foregrounds, and #D6BB92 accents. White uses #F7F2EA backgrounds, #F1EAE0 low surfaces, #24211D foregrounds, and muted bronze #675035 accents. Primary actions use flat #F3E5D0 ivory with #211C16 text in both palettes. Disabled and pressed states use Material behavior.

Use serif only for editorial headlines; functional labels and body copy use Android sans-serif. Shared shapes use 8/12/16/24/32 dp radii. Primary actions are pills with 56 dp minimum height; icon targets follow Material's 48 dp minimum. Content remains scrollable, inset-aware, and constrained to 640 dp.

Home order: brand header, heading, link-entry panel, supported platform shortcuts, then temple artwork. Do not invent platform support. Theme previews live in the existing Appearance section and use the existing persisted SYSTEM/LIGHT/DARK preferences. Saved media categories reuse Downloads history filters; counts represent stored history entries, including failed entries, rather than device storage scans.

## Assets

- `fetchly_logo.png` is the exact owner-supplied Apollo image, copied byte-for-byte from `file_0000000011ac8211a5ad581011244116.png`. Preserve its proportions and face.
- `fetchly_temple.png` is a 236 × 112 crop at (1245, 340) from the owner's 1536 × 1024 design board. It is decorative and follows the platform shortcuts. The source limits its resolution.
- Library symbols are native vector drawables. Do not replace brand artwork with generated faces.

## Verification limitation

Build, lint, unit tests, and Python tests are the automated checks. Device visual verification was blocked by another session holding the Android emulator; both palette renders and live download smoke testing still require a free device.
