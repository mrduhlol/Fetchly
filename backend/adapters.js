'use strict';

/**
 * PlatformAdapter interface. Every platform implements:
 *   detect(url) -> platform id or null
 *   analyze(url) -> { title, author, thumbnail, mediaType, duration, formats[] }
 *                  or { unsupported: 'reason' }
 *
 * Adapters MUST only use permitted mechanisms (official APIs / public embeds
 * that allow downloading). No login scraping, no DRM bypass, no paywall
 * bypass. If no permitted mechanism exists, return { unsupported } and the
 * server responds honestly — never fake formats.
 */

function unsupported(platform, reason) {
  return { unsupported: `This source isn't currently supported.${reason ? ' ' + reason : ''}`, platform };
}

const adapters = {
  youtube: {
    id: 'youtube',
    detect: (u) => /(^|\.)(youtube\.com|youtu\.be)$/.test(u.host),
    analyze: async (u) => unsupported('youtube', 'No permitted download mechanism configured.'),
  },
  instagram: {
    id: 'instagram',
    detect: (u) => /(^|\.)instagram\.com$/.test(u.host),
    analyze: async (u) => unsupported('instagram', 'No permitted download mechanism configured.'),
  },
  tiktok: {
    id: 'tiktok',
    detect: (u) => /(^|\.)tiktok\.com$/.test(u.host),
    analyze: async (u) => unsupported('tiktok', 'No permitted download mechanism configured.'),
  },
  twitter: {
    id: 'twitter',
    detect: (u) => /(^|\.)(twitter\.com|x\.com)$/.test(u.host),
    analyze: async (u) => unsupported('twitter', 'No permitted download mechanism configured.'),
  },
  reddit: {
    id: 'reddit',
    detect: (u) => /(^|\.)reddit\.com$/.test(u.host),
    analyze: async (u) => unsupported('reddit', 'No permitted download mechanism configured.'),
  },
  facebook: {
    id: 'facebook',
    detect: (u) => /(^|\.)(facebook\.com|fb\.watch)$/.test(u.host),
    analyze: async (u) => unsupported('facebook', 'No permitted download mechanism configured.'),
  },
  pinterest: {
    id: 'pinterest',
    detect: (u) => /(^|\.)pinterest\.com$/.test(u.host),
    analyze: async (u) => unsupported('pinterest', 'No permitted download mechanism configured.'),
  },
};

function detectPlatform(urlObj) {
  const host = urlObj.host.toLowerCase().replace(/^www\./, '');
  for (const adapter of Object.values(adapters)) {
    if (adapter.detect({ host })) return adapter;
  }
  return null;
}

module.exports = { adapters, detectPlatform, unsupported };
