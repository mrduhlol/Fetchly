# Asset provenance

Apollo source: `app/src/main/res/drawable-nodpi/fetchly_logo.png` in this repository. Source remains untouched. Derived cutout created with the built-in imagegen tool, transparent-background mode. No API/CLI fallback used.

Exact prompt:

> Use case: background-extraction. Edit target: attached Fetchly Apollo artwork. Create a high resolution transparent PNG cutout of ONLY the existing Apollo sculpture INCLUDING his gold sun disk and pointed rays, arrows/quiver at left, ivory draped clothing and sun brooch. Remove ONLY the dark rounded square plaque, thin enclosing rounded-square gold border, and all background. Preserve the exact original facial features, pose looking upward right, hair curls, geometry, sculptural faceting, clothing, proportions, gold colors and ivory marble. Do not redesign or photorealistically reinterpret. No text, no new pedestal, no frame, no watermark. Clean precise alpha edges. Deliver large native-resolution artwork (ideally 2048 square or larger) with comfortable transparent margins.

Result: 1254 × 1254 RGBA. No claim of a native 4K Apollo source. Master displays it at 1220 × 1220.

Wordmark: FETCHLY, seven Cinzel letter outlines; font from Google Fonts' official `google/fonts` repository, OFL included. Laurel, background and effects: original procedural vector artwork in `build_assets.py`. Shimmer motion adapted from HyperFrames registry `shimmer-sweep`, with local GSAP 3.15.0.
