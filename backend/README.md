# Fetchly API (reference server)

Dependency-free Node server implementing `POST /api/analyze` behind a
`PlatformAdapter` interface (`adapters.js`).

Adding a platform = adding one adapter entry with a permitted extraction
mechanism. Until then it honestly returns
"This source isn't currently supported." — never fake formats.

```bash
node server.js          # PORT=8080 by default
```
