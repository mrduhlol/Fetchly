"""Fetchly's on-device yt-dlp bridge (Chaquopy).

All network work is done by yt-dlp itself; this module only shapes its output
for Kotlin, bridges progress/cancellation, and guarantees every path returns
a JSON string. Kotlin never sees a Python exception.

Contract (see YtDlpMapper.kt for the consumer):
    version() -> "2026.08.19" | "missing"
    extract(url) -> {"ok":true,"title","uploader","duration","thumbnail","formats":[...]}
                  | {"ok":false,"error":"<raw yt-dlp message>"}
    download(url, format_id, dest_dir, prefix, hook)
                 -> {"ok":true,"path":"/abs/file.ext","filename":"file.ext"}
                  | {"ok":false,"cancelled":false,"error":"<raw message>"}
                  | {"ok":false,"cancelled":true,"error":"Cancelled."}
"""

import json
import copy
import threading
import os
import re
import time
from urllib.parse import urlsplit, parse_qs, unquote

try:
    import yt_dlp
except ImportError:  # pragma: no cover - only if the wheel is missing
    yt_dlp = None

# The phone has no JavaScript runtime (no deno/node), and these builds were
# verified working with runtimes disabled. This is the exact API equivalent
# of yt-dlp 2026.08.19's --no-js-runtimes flag (an empty dict).
JS_RUNTIMES_DISABLED = {}

_PROGRESS_MIN_INTERVAL = 0.2  # seconds between progress JNI calls


_MEDIA_CACHE = {}
_CACHE_LOCK = threading.Lock()
_PREVIEW_TTL = 30.0
_DOWNLOAD_TTL = 300.0


def _cached_media(url, max_age):
    with _CACHE_LOCK:
        item = _MEDIA_CACHE.get(url)
        if item is not None and time.monotonic() - item["at"] < max_age:
            return item
        return None


def _forget_media(url):
    with _CACHE_LOCK:
        _MEDIA_CACHE.pop(url, None)


def _remember_media(url, raw_info, preview):
    with _CACHE_LOCK:
        _MEDIA_CACHE[url] = {"at": time.monotonic(), "info": raw_info, "preview": preview}
        while len(_MEDIA_CACHE) > 8:
            _MEDIA_CACHE.pop(next(iter(_MEDIA_CACHE)))


def warmup():
    """Prepare the engine without guessing which site's code a share needs."""
    if yt_dlp is not None:
        with yt_dlp.YoutubeDL(_base_params(skip_download=True)) as ydl:
            # YoutubeDL registers lazy extractors. Importing YouTube, Twitter
            # and TikTok here competes with an incoming Instagram/other share.
            pass


class FetchlyCancelled(Exception):
    """Raised from the progress hook when the user cancels a download."""


def _base_params(**overrides):
    params = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        # X shared posts may contain several independently downloadable items.
        # Other extractors keep the established first-item behavior.
        "noprogress": True,
        "socket_timeout": 20,
        "retries": 3,
        "fragment_retries": 3,
        "js_runtimes": dict(JS_RUNTIMES_DISABLED),
    }
    params.update(overrides)
    return params


def version():
    if yt_dlp is None:
        return "missing"
    try:
        return str(yt_dlp.version.__version__)
    except Exception:
        return "missing"


def _preview_params(url, fast=True):
    # Fetching is interactive, unlike a background file transfer. Don't spend
    # several 20-second socket waits retrying a stalled metadata endpoint.
    params = _base_params(skip_download=True, socket_timeout=8, retries=1,
                          extractor_retries=1)
    if fast and _is_youtube_url(url):
        # The phone has no JS runtime. Avoid downloading player JS we cannot
        # execute and the extra "next" request for comments/chapters. Keep all
        # clients and HLS/DASH manifests so quality choices remain available.
        params["extractor_args"] = {"youtube": {"player_skip": ["js", "initial_data"]}}
    return params


def _is_youtube_url(url):
    host = (urlsplit(url).hostname or "").lower()
    return host == "youtu.be" or host == "youtube.com" or host.endswith(".youtube.com")


def _extract_preview(ydl, url, params):
    try:
        return ydl.extract_info(url, download=False)
    except Exception as exc:
        # Some videos need player metadata even without a JS runtime. Retry
        # those with the complete extractor, not an incomplete format list.
        error = _error_text(exc).lower()
        if not (_is_youtube_url(url) and params.get("extractor_args") and
                any(message in error for message in (
                    "no video formats", "no playable formats", "requested format is not available"))):
            raise
        full_params = dict(params)
        full_params.pop("extractor_args", None)
        with yt_dlp.YoutubeDL(full_params) as full:
            _register_extractors(full, url)
            return full.extract_info(url, download=False)


def extract(url):
    """Resolve a link into media metadata + every raw format yt-dlp offers."""
    if yt_dlp is None:
        return json.dumps({"ok": False, "error": "yt-dlp is not installed in this build."})
    cached = _cached_media(url, _PREVIEW_TTL)
    if cached is not None:
        return cached["preview"]
    try:
        params = _preview_params(url)
        if _is_x_url(url) or _is_multi_post(url):
            params["noplaylist"] = False
            params["playlistend"] = 50
        else:
            params["playlist_items"] = "1"
        with yt_dlp.YoutubeDL(params) as ydl:
            _register_extractors(ydl, url)
            photo_only = False
            try:
                info = _extract_preview(ydl, url, params)
            except Exception as exc:
                photos = _x_photos(getattr(ydl, "_fetchly_x_status", None)) if _is_x_url(url) else []
                if not photos or "No video could be found" not in _error_text(exc):
                    raise
                info = photos[0]
                photo_only = True
            photos = _x_photos(getattr(ydl, "_fetchly_x_status", None)) if _is_x_url(url) else []
        raw_info = info
        multi_entries = (([] if photo_only else (_entries(info) or [info])) + photos if _is_x_url(url)
                         else _entries(info) if _is_multi_post(url) else [])
        is_multi = len(multi_entries) > 1
        info = _first_entry(info)
        if not info:
            return json.dumps({"ok": False, "error": "No media found at this link."})
        preview = json.dumps({
            "ok": True,
            "title": str(info.get("title") or ""),
            "uploader": str(info.get("uploader") or info.get("channel") or ""),
            "duration": int(info.get("duration") or 0),
            "thumbnail": _thumbnail(info),
            "width": info.get("width"),
            "height": info.get("height"),
            "formats": [_clean_format(f, info.get("duration")) for f in _formats(info)],
            **({"entries": [_clean_entry(entry) for entry in multi_entries]} if is_multi else {}),
        })
        _remember_media(url, raw_info, preview)
        return preview
    except Exception as exc:
        return json.dumps({"ok": False, "error": _error_text(exc)})


def download(url, format_id, dest_dir, prefix, hook):
    """Download one exact format id with progress + cancellation, into dest_dir.

    The caller (Kotlin) passes the *page* URL; yt-dlp re-resolves and
    downloads. `hook` is a DownloadHook (or None for CLI smoke runs).
    """
    if yt_dlp is None:
        return json.dumps({"ok": False, "cancelled": False,
                           "error": "yt-dlp is not installed in this build."})
    try:
        dest_dir = os.path.abspath(dest_dir)
        os.makedirs(dest_dir, exist_ok=True)
    except (OSError, TypeError) as exc:
        return json.dumps({"ok": False, "cancelled": False, "error": _error_text(exc)})
    finished = {"path": None}
    last = {"at": 0.0, "status": None}

    def progress(d):
        status = d.get("status")
        if hook is not None and hook.shouldCancel():
            raise FetchlyCancelled()
        if status == "finished":
            finished["path"] = d.get("filename") or finished["path"]
            if hook is not None:
                total = d.get("total_bytes") or d.get("downloaded_bytes") or 0
                _safe_update(hook, int(d.get("downloaded_bytes") or 0), int(total), 0.0)
            return
        if status == "downloading":
            now = time.monotonic()
            if hook is not None and (last["status"] != status
                                     or (now - last["at"]) >= _PROGRESS_MIN_INTERVAL):
                last["at"] = now
                last["status"] = status
                _safe_update(
                    hook,
                    int(d.get("downloaded_bytes") or 0),
                    int(d.get("total_bytes") or d.get("total_bytes_estimate") or 0),
                    float(d.get("speed") or 0.0),
                )

    playlist_item = None
    selected_format = format_id or "best"
    if selected_format.startswith("x-entry-") and "::" in selected_format:
        selector, selected_format = selected_format.split("::", 1)
        try:
            playlist_item = int(selector[len("x-entry-"):])
        except ValueError:
            playlist_item = None
    params = _base_params(
        format=selected_format,
        outtmpl=os.path.join(dest_dir, prefix + ".%(ext)s"),
        progress_hooks=[progress],
        # Native HLS/DASH fragments only; plain HTTP is unaffected. Bound
        # concurrency for mobile memory, sockets, and CDN rate limits.
        concurrent_fragment_downloads=4,
        skip_unavailable_fragments=False,
        continuedl=True,
        nopart=False,
    )
    if playlist_item is not None and (_is_x_url(url) or _is_multi_post(url)):
        params["noplaylist"] = False
        params["playlist_items"] = str(playlist_item)
    else:
        params["playlist_items"] = "1"
    try:
        with yt_dlp.YoutubeDL(params) as ydl:
            _register_extractors(ydl, url)
            cached = _cached_media(url, _DOWNLOAD_TTL)
            if cached is not None:
                raw = cached["info"]
                entries = _entries(raw)
                item_index = (playlist_item or 1) - 1
                selected = entries[item_index] if entries and 0 <= item_index < len(entries) else _first_entry(raw)
                # Let yt-dlp choose the exact format using already-resolved URLs,
                # headers and fragment metadata, rather than resolving the page again.
                try:
                    ydl.process_ie_result(copy.deepcopy(selected), download=True)
                except FetchlyCancelled:
                    raise
                except Exception:
                    if hook is not None and hook.shouldCancel():
                        raise FetchlyCancelled()
                    # Expired signed URLs or guest cookies require a fresh source
                    # resolution. Successful cached transfers never take this path.
                    _forget_media(url)
                    ydl.download([url])
            else:
                ydl.download([url])
        path = finished["path"] or _find_output(dest_dir, prefix)
        if not path or not os.path.exists(path):
            _cleanup_partials(dest_dir, prefix)
            return json.dumps({"ok": False, "cancelled": False,
                               "error": "The download finished but produced no file."})
        return json.dumps({"ok": True, "path": os.path.abspath(path),
                           "filename": os.path.basename(path)})
    except FetchlyCancelled:
        # Pause keeps .part and .ytdl checkpoints; explicit Cancel cleans them in Kotlin.
        return json.dumps({"ok": False, "cancelled": True, "error": "Cancelled."})
    except Exception as exc:
        if hook is not None and hook.shouldCancel():
            return json.dumps({"ok": False, "cancelled": True, "error": "Paused or cancelled."})
        _forget_media(url)
        _cleanup_partials(dest_dir, prefix)
        return json.dumps({"ok": False, "cancelled": False, "error": _error_text(exc)})


# ---------------------------------------------------------------- helpers


def _register_extractors(ydl, url=None):
    host = (urlsplit(url).hostname or "").lower() if url else None
    if host is None or _is_x_url(url) or host == "t.co":
        _register_twitter(ydl)
    if host is None or host == "tiktok.com" or host.endswith(".tiktok.com"):
        _register_tiktok(ydl)


def _register_tiktok(ydl):
    # TikTok redirects Indian users to /in/about with HTTP 200. Upstream
    # mistakes this for a malformed challenge, hiding the actual region block.
    from yt_dlp.extractor.tiktok import TikTokIE as BaseTikTokIE
    from yt_dlp.utils import GeoRestrictedError

    class TikTokIE(BaseTikTokIE):
        def _download_webpage_handle(self, *args, **kwargs):
            response = super()._download_webpage_handle(*args, **kwargs)
            if response and _tiktok_region_redirect(response[1].url):
                raise GeoRestrictedError("TikTok is not available in your country.")
            return response

    ydl.add_info_extractor(TikTokIE())


def _register_twitter(ydl):
    from yt_dlp.extractor.twitter import TwitterIE as BaseTwitterIE

    class TwitterIE(BaseTwitterIE):
        def _extract_status(self, *args, **kwargs):
            status = super()._extract_status(*args, **kwargs)
            # Reuse the response already fetched by the extractor; no extra request.
            self._downloader._fetchly_x_status = status
            return status

    ydl.add_info_extractor(TwitterIE())


def _tiktok_region_redirect(url):
    parsed = urlsplit(url)
    host = (parsed.hostname or "").lower()
    return (host == "tiktok.com" or host.endswith(".tiktok.com")) and parsed.path.rstrip("/") == "/in/about"


def _first_entry(info):
    if isinstance(info, dict) and info.get("_type") in ("playlist", "multi_video"):
        for entry in info.get("entries") or []:
            if entry:
                return _first_entry(entry)
        return None
    return info


def _is_x_url(url):
    host = (urlsplit(url).hostname or "").lower()
    return host == "x.com" or host.endswith(".x.com") or host == "twitter.com" or host.endswith(".twitter.com")


def _is_multi_post(url):
    parsed = urlsplit(url)
    host = (parsed.hostname or "").lower()
    return ((host == "instagram.com" or host.endswith(".instagram.com"))
            and parsed.path.startswith(("/p/", "/reel/"))) or (
            (host == "tiktok.com" or host.endswith(".tiktok.com"))
            and ("/photo/" in parsed.path or "/video/" in parsed.path))


def _entries(info):
    if isinstance(info, dict) and info.get("_type") in ("playlist", "multi_video"):
        return [entry for entry in info.get("entries") or [] if entry]
    return []


def _x_photos(status):
    if not isinstance(status, dict):
        return []
    photos = []
    for media in (status.get("extended_entities") or {}).get("media") or []:
        if media.get("type") != "photo":
            continue
        url = media.get("media_url_https") or media.get("media_url")
        if not isinstance(url, str) or not url.startswith("https://pbs.twimg.com/"):
            continue
        url += "&name=orig" if "?" in url else "?name=orig"
        photos.append({
            "title": status.get("full_text") or status.get("text") or "X photos",
            "uploader": (status.get("user") or {}).get("name"),
            "thumbnail": url,
            "formats": [{"format_id": "direct", "url": url,
                         "ext": urlsplit(url).path.rsplit(".", 1)[-1].lower(),
                         "vcodec": "none", "acodec": "none"}],
        })
    return photos


def _clean_entry(info):
    return {"ok": True, "title": str(info.get("title") or ""),
            "uploader": str(info.get("uploader") or ""),
            "duration": int(info.get("duration") or 0), "thumbnail": _thumbnail(info),
            "width": info.get("width"), "height": info.get("height"),
            "formats": [_clean_format(f, info.get("duration")) for f in _formats(info)]}


def _formats(info):
    """Single-media extractors can return a URL directly, without formats."""
    formats = info.get("formats") or []
    if not formats and info.get("url"):
        return [dict(info, format_id=info.get("format_id") or "best")]
    return formats


def _clean_format(f, duration=None):
    ext = str(f.get("ext") or "").lower()
    is_image = ext in ("jpg", "jpeg", "png", "webp", "gif")
    is_audio = ext in ("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac")
    is_video = ext in ("mp4", "m4v", "webm", "mkv", "mov", "flv", "avi", "ts")
    # "none" means the extractor confirmed absence; missing metadata means
    # unknown. Do not turn a normal muxed social video into a silent stream.
    video_codec = f.get("vcodec") or (
        "none" if is_image or is_audio else "unknown" if is_video or f.get("height") else "none")
    audio_codec = f.get("acodec") or ("none" if is_image else "unknown")
    filesize = f.get("filesize")
    if filesize is None:
        try:
            length = parse_qs(urlsplit(f.get("url") or "").query).get("clen", [None])[0]
            if length is None:
                embedded = re.search(r"(?:^|[;/])clen=(\d+)(?:[;/]|$)", unquote(urlsplit(f.get("url") or "").path))
                length = embedded.group(1) if embedded else None
            if length is not None and int(length) > 0:
                filesize = int(length)
        except (ValueError, TypeError):
            pass
    filesize_approx = f.get("filesize_approx")
    if filesize is None and filesize_approx is None and duration:
        bitrate = f.get("tbr")
        if bitrate is None:
            parts = [f.get("vbr"), f.get("abr")]
            bitrate = sum(x for x in parts if x is not None) if any(x is not None for x in parts) else None
        try:
            if bitrate is not None and float(bitrate) > 0:
                filesize_approx = int(float(duration) * float(bitrate) * 1000 / 8)
        except (TypeError, ValueError, OverflowError):
            pass
    return {
        "id": str(f.get("format_id") or ""),
        "url": str(f.get("url") or ""),
        "ext": str(f.get("ext") or ""),
        "vcodec": str(video_codec),
        "acodec": str(audio_codec),
        "height": f.get("height"),
        "abr": f.get("abr"),
        "filesize": filesize,
        "filesize_approx": filesize_approx,
        "protocol": str(f.get("protocol") or ""),
        "language": f.get("language"),
        "language_preference": f.get("language_preference"),
    }


def _thumbnail(info):
    best = None
    for t in info.get("thumbnails") or []:
        if not t.get("url"):
            continue
        if best is None or (t.get("width") or 0) > (best.get("width") or 0):
            best = t
    return str((best or {}).get("url") or info.get("thumbnail") or "")


def _safe_update(hook, downloaded, total, speed):
    try:
        hook.update(downloaded, total, speed)
    except Exception:
        pass  # progress must never break a download


def _find_output(dest_dir, prefix):
    start = prefix + "."
    try:
        names = [n for n in os.listdir(dest_dir) if n.startswith(start)]
    except OSError:
        return None
    completed = [n for n in names if not (".part" in n or n.endswith(".ytdl"))]
    if not completed:
        return None
    completed.sort(
        key=lambda n: os.path.getmtime(os.path.join(dest_dir, n)), reverse=True)
    return os.path.join(dest_dir, completed[0])


def _cleanup_partials(dest_dir, prefix):
    start = prefix + "."
    try:
        for n in os.listdir(dest_dir):
            if n.startswith(start) and (".part" in n or n.endswith(".ytdl")):
                try:
                    os.remove(os.path.join(dest_dir, n))
                except OSError:
                    pass
    except OSError:
        pass


def _error_text(exc):
    text = str(exc).strip() or exc.__class__.__name__
    return text[:2000]


if __name__ == "__main__":
    import sys

    argv = sys.argv[1:]
    if argv and argv[0] == "version":
        print(version())
    elif len(argv) >= 2 and argv[0] == "extract":
        print(extract(argv[1]))
    elif len(argv) >= 4 and argv[0] == "download":
        _prefix = argv[4] if len(argv) >= 5 else "fetchly_smoke"
        print(download(argv[1], argv[2], argv[3], _prefix, None))
    else:
        print("usage: fetchly_yt.py version | extract <url> | download <url> <format_id> <dest_dir> [prefix]")
        raise SystemExit(2)
