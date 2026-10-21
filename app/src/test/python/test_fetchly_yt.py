"""Bridge regressions. Run: python -m unittest discover -s app/src/test/python."""
import importlib.util
import json
import pathlib
import tempfile
import unittest
from unittest.mock import patch, Mock

MODULE = pathlib.Path(__file__).parents[2] / 'main/python/fetchly_yt.py'
spec = importlib.util.spec_from_file_location('fetchly_yt', MODULE)
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)


class BridgeTests(unittest.TestCase):
    def extract(self, info):
        with patch.object(bridge, 'yt_dlp') as engine:
            engine.YoutubeDL.return_value.__enter__.return_value.extract_info.return_value = info
            result = json.loads(bridge.extract('https://example.com/post'))
            params = engine.YoutubeDL.call_args.args[0]
        return result, params

    def test_single_url_extractor_produces_a_download_option(self):
        result, _ = self.extract({'title': 'A pin', 'url': 'https://cdn.example/a.jpg', 'ext': 'jpg'})
        self.assertTrue(result['ok'])
        self.assertEqual('best', result['formats'][0]['id'])
        self.assertEqual('jpg', result['formats'][0]['ext'])

    def test_nested_multi_video_resolves_same_first_item(self):
        result, params = self.extract({'_type': 'multi_video', 'entries': [None, {
            '_type': 'playlist', 'entries': [{'title': 'First', 'url': 'https://cdn.example/a.mp4', 'ext': 'mp4'}]
        }]})
        self.assertEqual('First', result['title'])
        self.assertEqual('1', params['playlist_items'])

    def test_unknown_video_codec_keeps_reported_quality(self):
        result, _ = self.extract({'formats': [{'format_id': 'hd', 'url': 'https://cdn.example/a.mp4',
                                              'ext': 'mp4', 'height': 1080}]})
        self.assertEqual('unknown', result['formats'][0]['vcodec'])
        self.assertEqual(1080, result['formats'][0]['height'])

    def test_image_dimensions_do_not_make_it_a_video(self):
        result, _ = self.extract({'url': 'https://cdn.example/a.jpg', 'ext': 'jpg', 'height': 1080})
        self.assertEqual('none', result['formats'][0]['vcodec'])

    def test_tiktok_country_redirect_is_recognized_without_matching_other_hosts(self):
        self.assertTrue(bridge._tiktok_region_redirect('https://www.tiktok.com/in/about'))
        self.assertFalse(bridge._tiktok_region_redirect('https://example.com/in/about'))
        self.assertFalse(bridge._tiktok_region_redirect('https://www.tiktok.com/@person/video/123'))

    def test_tiktok_override_reports_region_instead_of_challenge_error(self):
        from yt_dlp.extractor.tiktok import TikTokIE
        from yt_dlp.utils import GeoRestrictedError
        downloader = Mock()
        bridge._register_extractors(downloader)
        extractor = downloader.add_info_extractor.call_args.args[0]
        handle = Mock(url='https://www.tiktok.com/in/about')
        with patch.object(TikTokIE, '_download_webpage_handle', return_value=('<html/>', handle)):
            with self.assertRaises(GeoRestrictedError):
                extractor._download_webpage_handle('https://www.tiktok.com/@person/video/123', '123')

    def test_missing_codecs_keep_unknown_audio_on_social_video(self):
        result, _ = self.extract({'formats': [
            {'format_id': 'mp4', 'url': 'https://cdn.example/a.mp4', 'ext': 'mp4'},
            {'format_id': 'webm', 'url': 'https://cdn.example/a.webm', 'ext': 'webm'},
        ]})
        self.assertEqual(2, len(result['formats']))
        for stream in result['formats']:
            self.assertEqual('unknown', stream['vcodec'])
            self.assertEqual('unknown', stream['acodec'])

    def test_explicit_silent_codec_is_preserved_and_audio_ext_is_recognized(self):
        video = bridge._clean_format({'ext': 'mp4', 'height': 720, 'acodec': 'none'})
        self.assertEqual('none', video['acodec'])
        audio = bridge._clean_format({'ext': 'm4a'})
        self.assertEqual('none', audio['vcodec'])
        self.assertEqual('unknown', audio['acodec'])

    def test_storage_failure_returns_json_instead_of_throwing(self):
        with tempfile.NamedTemporaryFile() as existing_file:
            result = json.loads(bridge.download('https://example.com/video', 'best', existing_file.name, 'test', None))
        self.assertFalse(result['ok'])
        self.assertFalse(result['cancelled'])
        self.assertTrue(result['error'])

    def test_empty_collection_returns_explicit_failure(self):
        result, _ = self.extract({'_type': 'playlist', 'entries': []})
        self.assertFalse(result['ok'])
        self.assertIn('No media', result['error'])

    def test_thumbnail_uses_highest_resolution(self):
        result, _ = self.extract({'thumbnail': 'fallback', 'thumbnails': [
            {'url': 'small', 'width': 100}, {'url': 'large', 'width': 1000}, {'width': 2000}]})
        self.assertEqual('large', result['thumbnail'])


if __name__ == '__main__':
    unittest.main()
