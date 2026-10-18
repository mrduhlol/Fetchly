# Storage

Downloads stream to a scratch dir in the app cache, then are published via
MediaStore (no broad storage permission needed on Android 10+):

- Video → `Movies/Fetchly/`
- Audio → `Music/Fetchly/`
- Images → `Pictures/Fetchly/`

The scratch dir is cleaned after every download; anything a crash leaves
behind is swept on the next launch (older than 48 h).

History (Room table `downloads`) stores only metadata — title, platform,
quality, status, MediaStore URI — never a second copy of the media.

- "Delete" in the details sheet removes the entry **and** deletes the
  published file from the media library.
- Deleting the file in a gallery/Files app does not touch history; the entry
  then says the file is no longer there.
