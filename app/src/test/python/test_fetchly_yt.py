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
    def setUp(self):
        bridge._MEDIA_CACHE.clear()

    def extract(self, info):
        with patch.object(bridge, 'yt_dlp') as engine:
            engine.YoutubeDL.return_value.__enter__.return_value.extract_info.return_value = info
            result = json.loads(bridge.extract('https://example.com/post'))
            params = engine.YoutubeDL.call_args.args[0]
        return result, params

    def test_repeated_preview_reuses_recent_success_instead_of_extracting_again(self):
        with patch.object(bridge, 'yt_dlp') as engine:
            downloader = engine.YoutubeDL.return_value.__enter__.return_value
            downloader.extract_info.return_value = {'title': 'Clip', 'url': 'https://cdn.example/v.mp4', 'ext': 'mp4'}
            first = bridge.extract('https://example.com/post')
            second = bridge.extract('https://example.com/post')
            self.assertEqual(first, second)
            downloader.extract_info.assert_called_once()

    def test_download_uses_the_fetched_stream_metadata_without_resolving_page_again(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(bridge, 'yt_dlp') as engine:
            downloader = engine.YoutubeDL.return_value.__enter__.return_value
            downloader.extract_info.return_value = {'title': 'Clip', 'url': 'https://cdn.example/v.mp4', 'ext': 'mp4'}
            bridge.extract('https://example.com/post')
            def transfer(info, download):
                pathlib.Path(directory, 'media.mp4').write_bytes(b'actual downloaded bytes')
            downloader.process_ie_result.side_effect = transfer
            result = json.loads(bridge.download('https://example.com/post', 'best', directory, 'media', None))
            self.assertTrue(result['ok'], result)
            downloader.extract_info.assert_called_once()
            downloader.download.assert_not_called()
            downloader.process_ie_result.assert_called_once()

    def test_failed_cached_transfer_invalidates_metadata_for_the_next_fetch(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(bridge, 'yt_dlp') as engine:
            downloader = engine.YoutubeDL.return_value.__enter__.return_value
            downloader.extract_info.return_value = {'title': 'Clip', 'url': 'https://cdn.example/v.mp4', 'ext': 'mp4'}
            bridge.extract('https://example.com/post')
            downloader.process_ie_result.side_effect = Exception('HTTP Error 403: Forbidden')
            bridge.download('https://example.com/post', 'best', directory, 'media', None)
            bridge.extract('https://example.com/post')
            self.assertEqual(2, downloader.extract_info.call_count)
            downloader.download.assert_called_once_with(['https://example.com/post'])

    def test_warmup_loads_extractors_without_fetching_a_page(self):
        with patch.object(bridge, 'yt_dlp') as engine:
            downloader = engine.YoutubeDL.return_value.__enter__.return_value
            bridge.warmup()
            downloader.get_info_extractor.assert_not_called()
            downloader.add_info_extractor.assert_not_called()
            downloader.extract_info.assert_not_called()
            downloader.download.assert_not_called()

    def test_sharing_does_not_import_unrelated_site_overrides(self):
        with patch.object(bridge, '_register_tiktok') as tiktok, \
                patch.object(bridge, '_register_twitter') as twitter:
            bridge._register_extractors(Mock(), 'https://www.instagram.com/reel/clip/')
            tiktok.assert_not_called()
            twitter.assert_not_called()
            bridge._register_extractors(Mock(), 'https://x.com/user/status/123')
            twitter.assert_called_once()
            tiktok.assert_not_called()
            bridge._register_extractors(Mock(), 'https://vm.tiktok.com/clip/')
            tiktok.assert_called_once()

    def test_preview_has_short_retries_without_changing_download_policy(self):
        preview = bridge._preview_params('https://www.instagram.com/reel/clip/')
        self.assertEqual(8, preview['socket_timeout'])
        self.assertEqual(1, preview['extractor_retries'])
        self.assertEqual(1, preview['retries'])
        self.assertEqual(20, bridge._base_params()['socket_timeout'])
        self.assertEqual(3, bridge._base_params()['retries'])
        self.assertNotIn('extractor_args', preview)

    def test_youtube_preview_skips_optional_requests_but_keeps_manifests(self):
        for url in ('https://youtu.be/clip', 'https://m.youtube.com/shorts/clip'):
            params = bridge._preview_params(url)
            youtube = params['extractor_args']['youtube']
            self.assertEqual(['js', 'initial_data'], youtube['player_skip'])
            self.assertNotIn('skip', youtube)
            self.assertNotIn('player_client', youtube)
        self.assertNotIn('extractor_args', bridge._preview_params('https://youtube.com.example/clip'))

    def test_youtube_missing_fast_formats_uses_full_extraction(self):
        url = 'https://youtu.be/clip'
        with patch.object(bridge, 'yt_dlp') as engine:
            downloader = engine.YoutubeDL.return_value.__enter__.return_value
            downloader.extract_info.side_effect = [
                Exception('Requested format is not available'),
                {'title': 'Clip', 'url': 'https://cdn.example/v.mp4', 'ext': 'mp4'},
            ]
            result = json.loads(bridge.extract(url))
            self.assertTrue(result['ok'], result)
            self.assertEqual(2, downloader.extract_info.call_count)
            first, fallback = engine.YoutubeDL.call_args_list
            self.assertIn('extractor_args', first.args[0])
            self.assertNotIn('extractor_args', fallback.args[0])
            self.assertEqual('1', fallback.args[0]['playlist_items'])

    def test_youtube_source_restriction_is_not_retried_as_missing_formats(self):
        with patch.object(bridge, 'yt_dlp') as engine:
            downloader = engine.YoutubeDL.return_value.__enter__.return_value
            downloader.extract_info.side_effect = Exception('Video unavailable')
            result = json.loads(bridge.extract('https://youtu.be/private'))
            self.assertFalse(result['ok'])
            downloader.extract_info.assert_called_once()
            engine.YoutubeDL.assert_called_once()

    def test_content_length_is_read_from_encoded_youtube_playlist_path(self):
        result = bridge._clean_format({'ext': 'mp4', 'url': 'https://manifest.googlevideo.com/api/sgoap/clen%3D3449447%3Bdur%3D213.089/playlist/index.m3u8'})
        self.assertEqual(3449447, result['filesize'])

    def test_content_length_is_read_from_regular_source_query(self):
        result = bridge._clean_format({'ext': 'm4a', 'url': 'https://example.com/audio?clen=123456'})
        self.assertEqual(123456, result['filesize'])

    def test_size_estimate_uses_reported_bitrate_and_duration(self):
        result = bridge._clean_format({'ext': 'mp4', 'tbr': 1200}, 60)
        self.assertEqual(9_000_000, result['filesize_approx'])
        result = bridge._clean_format({'ext': 'mp4', 'tbr': 1200, 'filesize': 1234}, 60)
        self.assertEqual(1234, result['filesize'])
        self.assertIsNone(result['filesize_approx'])

    def test_x_photo_attachments_use_original_urls_and_ignore_other_hosts(self):
        photos = bridge._x_photos({'full_text': 'Photos', 'extended_entities': {'media': [
            {'type': 'photo', 'media_url_https': 'https://pbs.twimg.com/media/a.jpg'},
            {'type': 'photo', 'media_url_https': 'https://example.com/a.jpg'},
            {'type': 'video', 'media_url_https': 'https://pbs.twimg.com/media/b.jpg'},
        ]}})
        self.assertEqual(1, len(photos))
        self.assertEqual('https://pbs.twimg.com/media/a.jpg?name=orig', photos[0]['formats'][0]['url'])
        self.assertEqual('direct', photos[0]['formats'][0]['format_id'])

    def test_x_photo_only_post_still_provides_bulk_entries(self):
        with patch.object(bridge, 'yt_dlp') as engine:
            downloader = engine.YoutubeDL.return_value.__enter__.return_value
            downloader.extract_info.side_effect = Exception('No video could be found in this tweet')
            downloader._fetchly_x_status = {'extended_entities': {'media': [
                {'type': 'photo', 'media_url_https': 'https://pbs.twimg.com/media/a.jpg'},
                {'type': 'photo', 'media_url_https': 'https://pbs.twimg.com/media/b.png'},
            ]}}
            result = json.loads(bridge.extract('https://x.com/person/status/123'))
        self.assertTrue(result['ok'])
        self.assertEqual(2, len(result['entries']))
        self.assertEqual('jpg', result['formats'][0]['ext'])

    def test_x_bulk_video_download_selects_its_own_playlist_item(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(bridge, 'yt_dlp') as engine:
            bridge.download('https://x.com/person/status/123', 'x-entry-2::hd', directory, 'item', None)
            params = engine.YoutubeDL.call_args.args[0]
        self.assertEqual('2', params['playlist_items'])
        self.assertEqual('hd', params['format'])
        self.assertFalse(params['noplaylist'])

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

    def test_download_enables_bounded_fragment_parallelism(self):
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(bridge, 'yt_dlp') as engine:
                bridge.download('https://example.com/video', 'best', directory, 'test', None)
                params = engine.YoutubeDL.call_args.args[0]
                self.assertEqual(4, params['concurrent_fragment_downloads'])
                self.assertFalse(params['skip_unavailable_fragments'])

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
