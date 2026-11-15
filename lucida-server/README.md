# Lucida NPM Server Bridge

A lightweight, dedicated REST bridge powered by the official [`lucida`](https://www.npmjs.com/package/lucida) npm package, designed for the OfflineMusic Android app.

## Features
- **Uses official `lucida` NPM package**: Connects directly to streaming modules.
- **Zero-Config Streaming**: Includes Soundcloud by default with automatic dynamic client authentication.
- **Extensible**: Supports Tidal, Qobuz, Deezer, and Spotify when credentials/tokens are provided.
- **Direct Audio Streaming**: Proxies audio streams and progressive MP3/FLAC directly to the Android app.
- **No Web scraping / No Cloudflare challenges**: Eliminates the fragile HTML scraping and captcha dialogs.

## Quick Start (Local)

1. Navigate to the `lucida-server` directory:
   ```bash
   cd lucida-server
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Start the server:
   ```bash
   npm start
   ```
   The server starts on `http://localhost:3000` (or `http://10.0.2.2:3000` from Android Emulator).

## Endpoints

- `GET /health` - Health check and list of enabled streamers.
- `GET /api/search?q={query}&limit={limit}` - Search tracks across enabled Lucida streamers.
- `GET /api/stream?url={trackUrl}` - Stream the audio track directly.

## Environment Variables (Optional)

You can provide account credentials in a `.env` file:
- `PORT` - Port to listen on (default: 3000)
- `TIDAL_TV_TOKEN`, `TIDAL_TV_SECRET`, `TIDAL_ACCESS_TOKEN`, `TIDAL_REFRESH_TOKEN`
- `QOBUZ_APP_ID`, `QOBUZ_APP_SECRET`, `QOBUZ_TOKEN`
- `DEEZER_ARL`
- `SPOTIFY_USERNAME`, `SPOTIFY_PASSWORD`
