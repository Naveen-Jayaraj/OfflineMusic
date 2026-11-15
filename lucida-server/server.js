import express from 'express';
import cors from 'cors';
import Lucida from 'lucida';
import Soundcloud from 'lucida/streamers/soundcloud';
import { fetch } from 'undici';

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// Initialize Lucida modules
const modules = {};

// Soundcloud works out of the box with zero credentials
try {
  modules.soundcloud = new Soundcloud();
  console.log('[Lucida] Soundcloud module initialized successfully.');
} catch (e) {
  console.error('[Lucida] Failed to initialize Soundcloud module:', e);
}

// Optional: Tidal (if credentials provided in env)
if (process.env.TIDAL_TV_TOKEN && process.env.TIDAL_ACCESS_TOKEN) {
  try {
    const { default: Tidal } = await import('lucida/streamers/tidal');
    modules.tidal = new Tidal({
      tvToken: process.env.TIDAL_TV_TOKEN,
      tvSecret: process.env.TIDAL_TV_SECRET || '',
      accessToken: process.env.TIDAL_ACCESS_TOKEN,
      refreshToken: process.env.TIDAL_REFRESH_TOKEN || '',
      countryCode: process.env.TIDAL_COUNTRY_CODE || 'US'
    });
    console.log('[Lucida] Tidal module initialized.');
  } catch (e) {
    console.error('[Lucida] Failed to initialize Tidal module:', e);
  }
}

// Optional: Qobuz (if credentials provided in env)
if (process.env.QOBUZ_APP_ID && process.env.QOBUZ_TOKEN) {
  try {
    const { default: Qobuz } = await import('lucida/streamers/qobuz');
    modules.qobuz = new Qobuz({
      appId: process.env.QOBUZ_APP_ID,
      appSecret: process.env.QOBUZ_APP_SECRET || '',
      token: process.env.QOBUZ_TOKEN
    });
    console.log('[Lucida] Qobuz module initialized.');
  } catch (e) {
    console.error('[Lucida] Failed to initialize Qobuz module:', e);
  }
}

// Optional: Deezer (if ARL provided in env)
if (process.env.DEEZER_ARL) {
  try {
    const { default: Deezer } = await import('lucida/streamers/deezer');
    modules.deezer = new Deezer({ arl: process.env.DEEZER_ARL });
    console.log('[Lucida] Deezer module initialized.');
  } catch (e) {
    console.error('[Lucida] Failed to initialize Deezer module:', e);
  }
}

const lucida = new Lucida({ modules });

/**
 * Health check endpoint
 */
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    version: '1.0.0',
    package: 'lucida@2.0.0-54',
    streamers: Object.keys(modules)
  });
});

/**
 * Search tracks across Lucida modules
 * Query parameters:
 *  - q: search query string
 *  - limit: maximum results (default: 10)
 */
app.get('/api/search', async (req, res) => {
  const query = req.query.q || req.query.query;
  const limit = parseInt(req.query.limit) || 10;

  if (!query || !query.trim()) {
    return res.json([]);
  }

  const results = [];

  for (const [moduleName, moduleInstance] of Object.entries(modules)) {
    try {
      const searchRes = await moduleInstance.search(query.trim(), limit);
      if (searchRes && Array.Array?.isArray(searchRes.tracks) || Array.isArray(searchRes?.tracks)) {
        for (const track of searchRes.tracks) {
          const artists = track.artists?.map(a => a.name || a).join(', ') || 'Unknown Artist';
          const cover = track.coverArtwork?.[0]?.url || track.coverArtwork || track.artwork_url || null;
          results.push({
            id: `lucida_${moduleName}_${track.id}`,
            title: track.title || 'Unknown Title',
            artist: artists,
            album: track.album?.title || '',
            durationMs: track.durationMs || 0,
            duration: formatDuration(track.durationMs || 0),
            source: 'LUCIDA',
            sourceUrl: track.url || '',
            quality: `${capitalize(moduleName)} (Lucida)`,
            thumbnailUrl: cover,
            module: moduleName
          });
        }
      }
    } catch (e) {
      console.warn(`[Lucida] Search failed on module ${moduleName}:`, e.message);
    }
  }

  res.json(results.slice(0, limit));
});

/**
 * Direct track audio stream proxy
 * Query parameters:
 *  - url: Track URL (Soundcloud, Tidal, etc.)
 *  - redirect: Boolean (true to return 302 redirect to CDN)
 */
app.get('/api/stream', async (req, res) => {
  const trackUrl = req.query.url;
  if (!trackUrl) {
    return res.status(400).json({ error: 'url parameter is required' });
  }

  try {
    // 1. Check if Soundcloud URL
    if (trackUrl.includes('soundcloud.com')) {
      const sc = modules.soundcloud || new Soundcloud();
      const client = sc.client || await getSoundcloudClient();
      
      const html = await (await fetch(trackUrl)).text();
      let trackId = html.split('"soundcloud://sounds:')[1]?.split('">')[0];
      if (!trackId) {
        trackId = html.split('content="https://w.soundcloud.com/player/?url=https%3A%2F%2Fapi.soundcloud.com%2Ftracks%2F')[1]?.split('%3F')[0];
      }
      
      if (!trackId) {
        return res.status(404).json({ error: 'Soundcloud track ID could not be found' });
      }

      const trackApi = await (await fetch(`https://api-v2.soundcloud.com/tracks/${trackId}?client_id=${client.id}`)).json();
      const transcodings = trackApi.media?.transcodings || [];
      
      // Select best format: progressive MP3 preferred for simple downloads, or high quality HLS/AAC
      let selected = transcodings.find(t => t.format?.protocol === 'progressive')
                  || transcodings.find(t => t.preset?.startsWith('aac_'))
                  || transcodings.find(t => t.preset?.startsWith('mp3_'))
                  || transcodings[0];

      if (!selected) {
        return res.status(404).json({ error: 'No transcodings found for track' });
      }

      const streamUrlObj = new URL(selected.url);
      streamUrlObj.searchParams.append('client_id', client.id);
      if (trackApi.track_authorization) {
        streamUrlObj.searchParams.append('track_authorization', trackApi.track_authorization);
      }

      const resolved = await (await fetch(streamUrlObj.toString())).json();
      if (!resolved?.url) {
        return res.status(502).json({ error: 'Failed to resolve audio stream URL' });
      }

      if (req.query.redirect === 'true') {
        return res.redirect(302, resolved.url);
      }

      // Proxy audio stream directly with range support
      const audioResp = await fetch(resolved.url, {
        headers: req.headers.range ? { range: req.headers.range } : {}
      });

      res.status(audioResp.status);
      for (const [key, value] of audioResp.headers.entries()) {
        if (['content-type', 'content-length', 'content-range', 'accept-ranges'].includes(key.toLowerCase())) {
          res.setHeader(key, value);
        }
      }

      const nodeStream = ReadableStreamToNodeStream(audioResp.body);
      nodeStream.pipe(res);
      return;
    }

    // 2. Generic Lucida getByUrl
    const trackItem = await lucida.getByUrl(trackUrl);
    if (!trackItem || !trackItem.getStream) {
      return res.status(404).json({ error: 'Stream not supported for this URL' });
    }

    const streamResult = await trackItem.getStream();
    if (streamResult.mimeType) {
      res.setHeader('Content-Type', streamResult.mimeType);
    }
    if (streamResult.sizeBytes) {
      res.setHeader('Content-Length', streamResult.sizeBytes);
    }

    if (streamResult.stream?.pipe) {
      streamResult.stream.pipe(res);
    } else {
      res.status(500).json({ error: 'Stream is not readable' });
    }
  } catch (error) {
    console.error('[Lucida] Stream error:', error);
    res.status(500).json({ error: error.message || 'Stream processing failed' });
  }
});

function formatDuration(ms) {
  const totalSeconds = Math.floor(ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${minutes}:${seconds.toString().padStart(2, '0')}`;
}

function capitalize(s) {
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : '';
}

async function getSoundcloudClient() {
  const home = await (await fetch('https://soundcloud.com/')).text();
  const scriptMatches = [...home.matchAll(/<script crossorigin src="([^"]+)"/g)];
  for (const match of scriptMatches) {
    const src = match[1];
    if (src.startsWith('http')) {
      const sText = await (await fetch(src)).text();
      const m = sText.match(/client_id:"([a-zA-Z0-9]+)"/);
      if (m) {
        return { id: m[1] };
      }
    }
  }
  return { id: 'qLcgHhXgeEFFaSolTB9I71LBsqncsD93' }; // Fallback
}

import { Readable } from 'stream';
function ReadableStreamToNodeStream(webStream) {
  return Readable.fromWeb(webStream);
}

app.listen(PORT, () => {
  console.log(`[Lucida Server] Listening on http://localhost:${PORT}`);
});
