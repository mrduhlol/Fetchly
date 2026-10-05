'use strict';

/**
 * Fetchly API reference server (no dependencies, plain Node http).
 *
 *   POST /api/analyze  { "url": "https://..." } -> MediaInfo JSON
 *   GET  /healthz       -> { ok: true }
 *
 * Security: URL validation, http(s)-only, host allowlist via adapters,
 * 10s upstream timeout budget, 1 MB JSON body cap, simple per-IP rate limit.
 */

const http = require('http');
const { detectPlatform } = require('./adapters');

const PORT = process.env.PORT || 8080;
const RATE_WINDOW_MS = 60_000;
const RATE_MAX = 60;
const hits = new Map();

function rateLimited(ip) {
  const now = Date.now();
  const arr = (hits.get(ip) || []).filter((t) => now - t < RATE_WINDOW_MS);
  arr.push(now);
  hits.set(ip, arr);
  return arr.length > RATE_MAX;
}

function send(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(body),
  });
  res.end(body);
}

function validUrl(raw) {
  if (typeof raw !== 'string' || !raw.trim() || raw.length > 2048) return null;
  const withScheme = raw.trim().includes('://') ? raw.trim() : `https://${raw.trim()}`;
  let u;
  try {
    u = new URL(withScheme);
  } catch {
    return null;
  }
  if (u.protocol !== 'http:' && u.protocol !== 'https:') return null;
  return u;
}

const server = http.createServer((req, res) => {
  const ip = req.socket.remoteAddress || 'unknown';
  if (rateLimited(ip)) {
    send(res, 429, { success: false, error: 'Rate limited. Try again shortly.' });
    return;
  }

  if (req.method === 'GET' && req.url === '/healthz') {
    send(res, 200, { ok: true });
    return;
  }

  if (req.method !== 'POST' || req.url !== '/api/analyze') {
    send(res, 404, { success: false, error: 'Not found.' });
    return;
  }

  let size = 0;
  const chunks = [];
  req.on('data', (c) => {
    size += c.length;
    if (size > 1024 * 1024) {
      send(res, 413, { success: false, error: 'Request too large.' });
      req.destroy();
      return;
    }
    chunks.push(c);
  });
  req.on('end', async () => {
    let payload;
    try {
      payload = JSON.parse(Buffer.concat(chunks).toString('utf8'));
    } catch {
      send(res, 400, { success: false, error: 'Enter a valid media link.' });
      return;
    }
    const urlObj = validUrl(payload.url);
    if (!urlObj) {
      send(res, 400, { success: false, error: 'Enter a valid media link.' });
      return;
    }
    const adapter = detectPlatform(urlObj);
    if (!adapter) {
      send(res, 200, { success: false, error: "Fetchly doesn't support this source yet." });
      return;
    }
    try {
      const result = await adapter.analyze(urlObj);
      if (result.unsupported || !result.formats || result.formats.length === 0) {
        send(res, 200, {
          success: false,
          platform: adapter.id,
          error: result.unsupported || "This source isn't currently supported.",
        });
        return;
      }
      send(res, 200, {
        success: true,
        platform: adapter.id,
        title: result.title || 'Untitled media',
        author: result.author || null,
        thumbnail: result.thumbnail || null,
        mediaType: result.mediaType || 'video',
        duration: result.duration ?? null,
        formats: result.formats,
      });
    } catch (e) {
      console.error('analyze failed', e);
      send(res, 502, { success: false, error: "Fetchly couldn't analyze this link right now." });
    }
  });
});

server.listen(PORT, () => console.log(`fetchly-api listening on :${PORT}`));
