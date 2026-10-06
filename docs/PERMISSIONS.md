# Android permissions (and why)

| Permission | Why |
|---|---|
| `INTERNET` | Probe direct media links and download permitted media. |
| `ACCESS_NETWORK_STATE` | Fail fast with a friendly message when offline. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | Keep legitimate downloads running with a progress notification when the app is backgrounded (WorkManager). |
| `POST_NOTIFICATIONS` | Download progress / completion notifications. Runtime permission on Android 13+; downloads work without it, you just won't see notifications. |

No broad storage permissions (`READ_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE`)
are requested: files are saved through MediaStore, which is the modern
scoped-storage path on Android 10+.
