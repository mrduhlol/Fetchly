# Recovery and download reliability

## Source changes and engine maintenance

Settings shows the installed engine and provides Check for engine updates,
Install engine update, and Use bundled engine. Compatible official PyPI wheels
are verified before staging. Updates never replace code inside an active engine.
After downloads finish, force stop Fetchly from the linked Android app settings
and reopen it to activate the selection. App releases are linked when an engine
release needs a newer Python runtime or another capability.

This build bundles Python 3.12 and yt-dlp 2026.10.26. It does not bundle a
JavaScript runtime. Source-side bot checks, private access, DRM, geography and
format gating remain source limitations. Opening a browser does not transfer
browser authentication back into Fetchly.

## Interrupted work

Direct files retain validated checkpoints and use bounded per-request retries.
The existing Retry action can resume eligible partial files. Other media may
restart. Queued retries have waiting text, duplicate actions are guarded, and
command failures are shown in a snackbar. WorkManager jobs that terminated
without a terminal history write appear as interrupted and can be retried.
Clearing history preserves active entries. Android's app settings are linked
for notification and battery troubleshooting without changing those settings
silently.

A cancelled link check releases the form immediately and discards stale results;
a blocking extractor may still take its socket timeout to unwind.

## Completion and files

Only transfer stages show transfer percentages and speed. Merging, checking,
saving and retry waits are indeterminate. A file becomes Completed only after
verification, publication and the guarded history commit. Approximate source
sizes cannot trigger an exact truncation check. Missing media fragments fail
instead of silently producing a nominally complete partial result.

Completed rows and notifications expose Open file. Details show the filename,
media-library folder, Share and Delete. Missing files offer Download again;
unavailable handlers and access failures produce visible feedback. Partial files
never appear in the media library.

## Quality and sound

Rows identify sound availability, container, reported size and source language
when present. Best available avoids confirmed silent videos. Explicit silent
video selection requires confirmation. Source-preferred audio is used for
pairing; available language/container audio choices are retained. Refreshing an
expired URL preserves an explicit language and does not silently substitute a
substantially lower audio bitrate. Legacy signatures without language metadata
remain compatible. No conversion or unsupported quality is invented.

## Actionable failures

Recovery distinguishes connectivity, request limits, bot checks, sign-in,
region restrictions, missing media, DRM, engine changes, storage and assembly.
The UI offers the applicable next steps: Retry, Open source, Engine updates,
or Choose another format. Authentication/bot failures are not mistaken for
expired URLs merely because they include HTTP 403.

## Batch downloads and library

Home accepts multiple pasted/shared public links and opens a review screen.
Add multiple links also supports an empty batch. At most 50 distinct links are
checked sequentially; duplicates and rejected links are counted. Oversized
batches are refused rather than silently truncated. A failed link does not
prevent other links from being reviewed or added. Cancelling checks discards
late results. Batch defaults support best video with sound, up to 1080p, up to
720p and audio; unavailable defaults require an explicit alternative. Batch
never offers confirmed silent video. Individual names and formats can be edited
before adding. Successfully added rows are excluded from subsequent retries.

The durable queue admits two download jobs at once, oldest first. Active
matches and readable completed copies are reused instead of creating accidental
duplicates. Source identity, format, codec and language are considered; matching
titles alone are insufficient. Removing a saved file allows a fresh download.
Files may still be downloaded again in a different format. Filename stems and
quality labels have UTF-8 byte limits and cannot contain path separators; the
actual name assigned by the media provider is retained in history. History has
text search and media/failure filters, while active work remains visible.
