"""Exercise real yt-dlp partial-file continuation against a local HTTP origin."""
import importlib.util
import pathlib
import tempfile
import threading
import unittest
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

spec = importlib.util.spec_from_file_location('resume_bridge', pathlib.Path(__file__).parents[2] / 'main/python/fetchly_yt.py')
bridge = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bridge)


class EngineResumeTest(unittest.TestCase):
    def test_pause_keeps_partial_and_resume_requests_remaining_bytes(self):
        data = bytes(range(251)) * 20000
        requested_ranges = []

        class Origin(BaseHTTPRequestHandler):
            def log_message(self, *args):
                pass

            def do_HEAD(self):
                self.send_response(200)
                self.send_header('Content-Type', 'video/mp4')
                self.send_header('Content-Length', str(len(data)))
                self.send_header('Accept-Ranges', 'bytes')
                self.end_headers()

            def do_GET(self):
                byte_range = self.headers.get('Range')
                start = int(byte_range.split('=')[1].split('-')[0]) if byte_range else 0
                requested_ranges.append(start)
                self.send_response(206 if byte_range else 200)
                self.send_header('Content-Type', 'video/mp4')
                self.send_header('Content-Length', str(len(data) - start))
                self.send_header('Accept-Ranges', 'bytes')
                if byte_range:
                    self.send_header('Content-Range', f'bytes {start}-{len(data) - 1}/{len(data)}')
                self.end_headers()
                try:
                    self.wfile.write(data[start:])
                except (BrokenPipeError, ConnectionResetError):
                    pass

        class Hook:
            def __init__(self, pause):
                self.pause = pause
                self.cancelled = False

            def shouldCancel(self):
                return self.cancelled

            def update(self, downloaded, total, speed):
                if self.pause and downloaded >= 65536:
                    self.cancelled = True

        server = ThreadingHTTPServer(('127.0.0.1', 0), Origin)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with tempfile.TemporaryDirectory() as directory:
                url = f'http://127.0.0.1:{server.server_port}/video.mp4'
                paused = json.loads(bridge.download(url, 'best', directory, 'media', Hook(True)))
                self.assertTrue(paused['cancelled'], paused)
                parts = list(pathlib.Path(directory).glob('media*.part'))
                self.assertEqual(1, len(parts))
                retained = parts[0].stat().st_size
                self.assertGreater(retained, 0)
                resumed = json.loads(bridge.download(url, 'best', directory, 'media', Hook(False)))
                self.assertTrue(resumed['ok'], resumed)
                self.assertEqual(data, pathlib.Path(resumed['path']).read_bytes())
                self.assertIn(retained, requested_ranges)
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=5)
