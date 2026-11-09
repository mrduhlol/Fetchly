# Chaquopy calls these methods by their Java names from fetchly_yt.py.
-keep class com.fetchly.app.data.engine.DownloadHook {
    public void update(long, long, double);
    public boolean shouldCancel();
}
# WorkManager and Room instantiate these entry points reflectively.
-keep class com.fetchly.app.data.download.DownloadWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.fetchly.app.data.db.FetchlyDatabase_Impl { *; }
# Persisted enum names are part of the existing history/preferences format.
-keep enum com.fetchly.app.domain.model.** { *; }
