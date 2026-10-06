"""On-device media extraction using yt-dlp.

Called from Kotlin through Chaquopy. Returns a JSON string with only the
fields the app needs — never raw player internals.
"""

import json

try:
    import yt_dlp
except ImportError:
    yt_dlp = None


def _clean_format(f):
    return {
        "id": str(f.get("format_id") or ""),
        "quality": str(f.get("format_note") or ""),
        "height": f.get("height") or 0,
        "ext": str(f.get("ext") or ""),
        "filesize": f.get("filesize") or f.get("filesize_approx") or 0,
        "url": str(f.get("url") or ""),
        "vcodec": str(f.get("vcodec") or "none"),
        "acodec": str(f.get("acodec") or "none"),
        "abr": f.get("abr") or 0,
    }


def _pick_entry(info):
    # Playlists / channels: analyze the first playable entry only.
    if isinstance(info, dict) and info.get("_type") == "playlist":
        for e in info.get("entries") or []:
            if e:
                return e
        return None
    return info


def extract(url):
    """Returns JSON: { ok, title, uploader, duration, thumbnail, formats[] }
    or { ok: False, error } on any failure."""
    if yt_dlp is None:
        return json.dumps({"ok": False, "error": "ENGINE_MISSING"})
    try:
        params = {
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
            "socket_timeout": 15,
            "noplaylist": False,
        }
        with yt_dlp.YoutubeDL(params) as ydl:
            info = ydl.extract_info(url, download=False)
        info = _pick_entry(info)
        if not info:
            return json.dumps({"ok": False, "error": "NO_MEDIA"})
        # Largest thumbnail available.
        thumbs = info.get("thumbnails") or []
        thumbnail = ""
        if thumbs:
            best = max(thumbs, key=lambda t: (t.get("width") or 0))
            thumbnail = str(best.get("url") or "")
        if not thumbnail:
            thumbnail = str(info.get("thumbnail") or "")
        out = {
            "ok": True,
            "title": str(info.get("title") or "Untitled media"),
            "uploader": info.get("uploader") or info.get("channel"),
            "duration": info.get("duration") or 0,
            "thumbnail": thumbnail,
            "formats": [_clean_format(f) for f in (info.get("formats") or [])],
        }
        return json.dumps(out)
    except Exception as e:  # yt-dlp raises many error types; classify in Kotlin.
        return json.dumps({"ok": False, "error": "EXTRACT_FAILED:" + str(e)[:300]})
