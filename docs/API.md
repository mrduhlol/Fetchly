# Fetchly API

Reference server in `backend/` (dependency-free Node).

## Endpoints

- `GET /healthz` → `{ "ok": true }`
- `POST /api/analyze`

Request:

```json
{ "url": "https://example.com/media" }
```

Success response:

```json
{
  "success": true,
  "platform": "instagram",
  "title": "Example",
  "author": "creator",
  "thumbnail": "https://...",
  "mediaType": "video",
  "duration": 42,
  "formats": [
    {
      "id": "format-1",
      "quality": "1080p",
      "container": "mp4",
      "size": 82345678,
      "downloadUrl": "https://...",
      "isAudioOnly": false
    }
  ]
}
```

Failure response (never fakes formats):

```json
{ "success": false, "platform": "youtube", "error": "This source isn't currently supported." }
```

## Configuring the app

Set the base URL one of:

1. Environment variable at build time: `FETCHLY_API_BASE_URL=https://api.example.com/`
2. `local.defaults.properties`: `FETCHLY_API_BASE_URL=https://...`
3. In-app Settings → API base URL override (debug/testing)

Run the server:

```bash
cd backend
PORT=8080 node server.js
```
