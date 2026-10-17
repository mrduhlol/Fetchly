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
import os
import time

try:
    import yt_dlp
except ImportError:  # pragma: no cover - only if the wheel is missing
    yt_dlp = None

# The phone has no JavaScript runtime (no deno/node), and these builds were
# verified working with runtimes disabled. This is the exact API equivalent
# of yt-dlp 2026.08.19's --no-js-runtimes flag (an empty dict).
JS_RUNTIMES_DISABLED = {}

_PROGRESS_MIN_INTERVAL = 0.2  # seconds between progress JNI calls


class FetchlyCancelled(Exception):
    """Raised from the progress hook when the user cancels a download."""


def _base_params(**overrides):
    params = {
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
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


def extract(url):
    """Resolve a link into media metadata + every raw format yt-dlp offers."""
    if yt_dlp is None:
        return json.dumps({"ok": False, "error": "yt-dlp is not installed in this build."})
    try:
        with yt_dlp.YoutubeDL(_base_params(skip_download=True)) as ydl:
            info = ydl.extract_info(url, download=False)
        info = _first_entry(info)
        if not info:
            return json.dumps({"ok": False, "error": "No media found at this link."})
        return json.dumps({
            "ok": True,
            "title": str(info.get("title") or ""),
            "uploader": str(info.get("uploader") or info.get("channel") or ""),
            "duration": int(info.get("duration") or 0),
            "thumbnail": _thumbnail(info),
            "formats": [_clean_format(f) for f in (info.get("formats") or [])],
        })
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
    dest_dir = os.path.abspath(dest_dir)
    os.makedirs(dest_dir, exist_ok=True)
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

    params = _base_params(
        format=format_id or "best",
        outtmpl=os.path.join(dest_dir, prefix + ".%(ext)s"),
        progress_hooks=[progress],
    )
    try:
        with yt_dlp.YoutubeDL(params) as ydl:
            ydl.download([url])
        path = finished["path"] or _find_output(dest_dir, prefix)
        if not path or not os.path.exists(path):
            _cleanup_partials(dest_dir, prefix)
            return json.dumps({"ok": False, "cancelled": False,
                               "error": "The download finished but produced no file."})
        return json.dumps({"ok": True, "path": os.path.abspath(path),
                           "filename": os.path.basename(path)})
    except FetchlyCancelled:
        _cleanup_partials(dest_dir, prefix)
        return json.dumps({"ok": False, "cancelled": True, "error": "Cancelled."})
    except Exception as exc:
        _cleanup_partials(dest_dir, prefix)
        return json.dumps({"ok": False, "cancelled": False, "error": _error_text(exc)})


# ---------------------------------------------------------------- helpers


def _first_entry(info):
    if isinstance(info, dict) and info.get("_type") == "playlist":
        for entry in info.get("entries") or []:
            if entry:
                return entry
        return None
    return info


def _clean_format(f):
    return {
        "id": str(f.get("format_id") or ""),
        "url": str(f.get("url") or ""),
        "ext": str(f.get("ext") or ""),
        "vcodec": str(f.get("vcodec") or "none"),
        "acodec": str(f.get("acodec") or "none"),
        "height": f.get("height"),
        "abr": f.get("abr"),
        "filesize": f.get("filesize"),
        "filesize_approx": f.get("filesize_approx"),
        "protocol": str(f.get("protocol") or ""),
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
