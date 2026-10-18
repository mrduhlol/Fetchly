# Android permissions (and why)

| Permission | Why |
|---|---|
| `INTERNET` | Resolve links and download media. |
| `ACCESS_NETWORK_STATE` | The download queue waits for connectivity (WorkManager constraint) instead of failing mid-transfer. |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | Downloads keep running with a progress notification when the app is backgrounded. |
| `POST_NOTIFICATIONS` | Download progress / completion notifications. Runtime permission on Android 13+; downloads work without it, you just won't see notifications. |
| `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` | Added by WorkManager for reliable scheduled work. Fetchly itself only uses them to resume pending downloads. |

No broad storage permissions (`READ_EXTERNAL_STORAGE` / `MANAGE_EXTERNAL_STORAGE`)
are requested: files are saved through MediaStore, which is the modern
scoped-storage path on Android 10+.
