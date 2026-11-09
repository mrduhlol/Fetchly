"""Bounded social-post extraction and item-specific download regressions."""
import json
import pathlib
import tempfile
import unittest
from unittest.mock import patch
from test_fetchly_yt import bridge


class PostAttachmentTests(unittest.TestCase):
    def setUp(self):
        bridge._MEDIA_CACHE.clear()

    def test_instagram_post_exposes_each_attachment_with_metadata(self):
        items = [{"title": f"Photo {i}", "url": f"https://cdn.example/{i}.jpg",
                  "ext": "jpg", "vcodec": "none", "acodec": "none",
                  "thumbnail": f"https://cdn.example/{i}.jpg"} for i in range(5)]
        with patch.object(bridge, "yt_dlp") as engine:
            engine.YoutubeDL.return_value.__enter__.return_value.extract_info.return_value = {
                "_type": "playlist", "entries": items}
            result = json.loads(bridge.extract("https://www.instagram.com/p/post/"))
            params = engine.YoutubeDL.call_args.args[0]
        self.assertTrue(result["ok"])
        self.assertEqual(5, len(result["entries"]))
        self.assertEqual("Photo 3", result["entries"][3]["title"])
        self.assertEqual("https://cdn.example/3.jpg", result["entries"][3]["thumbnail"])
        self.assertFalse(params["noplaylist"])
        self.assertEqual(50, params["playlistend"])

    def test_profile_and_youtube_playlist_are_not_enabled_as_bulk_posts(self):
        for url in ["https://www.instagram.com/someone/", "https://www.youtube.com/playlist?list=123"]:
            self.assertFalse(bridge._is_multi_post(url))
        self.assertTrue(bridge._is_multi_post("https://www.tiktok.com/@person/photo/123"))
        self.assertFalse(bridge._is_multi_post("https://instagram.com.evil.example/p/id"))

    def test_instagram_selected_video_uses_correct_cached_entry_and_selector(self):
        url = "https://www.instagram.com/p/post/"
        entries = [{"title": str(i), "url": f"https://cdn.example/{i}.mp4", "ext": "mp4"} for i in range(3)]
        with tempfile.TemporaryDirectory() as directory, patch.object(bridge, "yt_dlp") as engine:
            downloader = engine.YoutubeDL.return_value.__enter__.return_value
            downloader.extract_info.return_value = {"_type": "playlist", "entries": entries}
            bridge.extract(url)
            def transfer(info, download):
                self.assertEqual("2", info["title"])
                pathlib.Path(directory, "media.mp4").write_bytes(b"selected attachment")
            downloader.process_ie_result.side_effect = transfer
            result = json.loads(bridge.download(url, "x-entry-3::best", directory, "media", None))
            params = engine.YoutubeDL.call_args.args[0]
            self.assertTrue(result["ok"], result)
            self.assertEqual("3", params["playlist_items"])
            self.assertEqual("best", params["format"])
            self.assertFalse(params["noplaylist"])
            downloader.download.assert_not_called()
