# Storage

Downloads stream to a temp file in the app cache, then are published via
MediaStore (no broad storage permission needed on Android 10+):

- Video → `Movies/Fetchly/`
- Audio → `Music/Fetchly/`
- Images → `Pictures/Fetchly/`

History (Room `history` table) stores only metadata — filename, title,
thumbnail URL, platform, quality, status, local URI — never a second copy of
the media. "Delete" in history removes the entry; the file stays on the
device unless the user deletes it from the gallery/Files app.
