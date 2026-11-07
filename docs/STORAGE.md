# Storage

Downloads stream to a scratch dir in the app cache, then are published via
MediaStore (no broad storage permission needed on Android 10+):

- Video → `Movies/Fetchly/`
- Audio → `Music/Fetchly/`
- Images → `Pictures/Fetchly/`

Completed downloads are removed from the scratch dir after publication. Interrupted
Kotlin direct-file downloads retain their data and private `.resume.json` sidecars
so automatic retries and the existing **Retry** action can continue from saved
positions. Cancel stops the transfer and keeps these private partial bytes for
Retry; Delete removes the job's data and sidecars. Non-direct media downloads
still discard their scratch files when their worker exits.

Resume requires byte-range support, a known size, identity encoding, and a strong
ETag. Fetchly checks the source and resolved URL, size, and ETag on every attempt;
changed or missing metadata, damaged checkpoints, or evicted cache files cause a
fresh download. Partial files never appear in the media library. Android may
reclaim the cache, and entries older than 48 hours are swept on startup, so resume
is best effort rather than permanent storage.

History (Room table `downloads`) stores only metadata — title, platform,
quality, status, MediaStore URI — never a second copy of the media.

- "Delete" in the details sheet removes the entry **and** deletes the
  published file from the media library.
- Deleting the file in a gallery/Files app does not touch history; the entry
  then says the file is no longer there.
