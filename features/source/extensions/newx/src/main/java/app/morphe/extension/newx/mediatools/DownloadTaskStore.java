package app.morphe.extension.newx.mediatools;

import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import app.morphe.extension.shared.Utils;

/** Only explicitly tracked Piko downloads; never enumerate unrelated system downloads. */
public final class DownloadTaskStore {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static SQLiteDatabase database;
    public record Task(long id, String url, String file, String mime, String post,
                       String author, String state, String reason, String uri,
                       long bytes, long total, long updated) {}

    private DownloadTaskStore() {}

    private static synchronized SQLiteDatabase database(Context context) {
        if (database != null) return database;
        SQLiteDatabase opened = SQLiteDatabase.openOrCreateDatabase(
                new File(context.getNoBackupFilesDir(), "piko_download_tasks_v1.db"), null);
        try {
            if (opened.getVersion() > 1) throw new IllegalStateException("Unsupported task schema");
            opened.execSQL("CREATE TABLE IF NOT EXISTS tasks (id INTEGER PRIMARY KEY, url TEXT NOT NULL, "
                    + "file TEXT NOT NULL, mime TEXT NOT NULL, post TEXT NOT NULL, author TEXT NOT NULL, "
                    + "state TEXT NOT NULL, reason TEXT NOT NULL, uri TEXT NOT NULL, updated INTEGER NOT NULL)");
            opened.setVersion(1);
            database = opened;
            return opened;
        } catch (RuntimeException error) { opened.close(); throw error; }
    }

    public static void queued(Context context, long id, String url, String file, String mime,
                              String post, String author) {
        insert(context, id, url, file, mime, post, author, "queued", "");
    }

    public static void enqueueFailed(Context context, String url, String file, String mime,
                                     String post, String author) {
        insert(context, -android.os.SystemClock.elapsedRealtimeNanos(), url, file, mime,
                post, author, "failed", "enqueue");
    }

    private static void insert(Context context, long id, String url, String file, String mime,
                               String post, String author, String state, String reason) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                ContentValues values = new ContentValues();
                values.put("id", id); values.put("url", safe(url, 8192));
                values.put("file", safe(file, 255)); values.put("mime", safe(mime, 128));
                values.put("post", safe(post, 32)); values.put("author", safe(author, 128));
                values.put("state", state); values.put("reason", reason); values.put("uri", "");
                values.put("updated", System.currentTimeMillis());
                SQLiteDatabase db = database(app);
                db.insertWithOnConflict("tasks", null, values, SQLiteDatabase.CONFLICT_IGNORE);
                // Never prune an active task. Completed metadata is bounded independently.
                db.execSQL("DELETE FROM tasks WHERE id IN (SELECT id FROM tasks WHERE state IN "
                        + "('complete','failed','missing') ORDER BY updated DESC LIMIT -1 OFFSET 1000)");
            } catch (RuntimeException ignored) {}
        });
    }

    public static void published(Context context, long id, String uri) {
        update(context, id, "complete", "", uri);
    }

    public static void finished(Context context, long id) {
        // On API 28 the native media scan may not have produced an openable content URI yet.
        update(context, id, "complete", "", null);
    }

    public static void failed(Context context, long id, String reason) {
        update(context, id, "failed", reason, null);
    }

    private static void update(Context context, long id, String state, String reason, String uri) {
        Context app = context.getApplicationContext();
        IO.execute(() -> {
            try {
                ContentValues values = new ContentValues();
                values.put("state", state); values.put("reason", safe(reason, 128));
                if (uri != null) values.put("uri", safe(uri, 2048));
                values.put("updated", System.currentTimeMillis());
                database(app).update("tasks", values, "id=?", new String[]{Long.toString(id)});
            } catch (RuntimeException ignored) {}
        });
    }

    public static void query(Consumer<List<Task>> done) {
        IO.execute(() -> {
            List<Task> tasks = new ArrayList<>();
            Context context = Utils.getContext();
            try {
                SQLiteDatabase db = database(context);
                DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                try (Cursor c = db.query("tasks", null, null, null, null, null, "updated DESC", "300")) {
                    while (c.moveToNext()) {
                        long id = c.getLong(c.getColumnIndexOrThrow("id"));
                        String state = value(c, "state");
                        String reason = value(c, "reason");
                        long bytes = 0, total = -1;
                        if (id > 0 && !state.equals("complete") && !state.equals("failed") && manager != null) {
                            try (Cursor download = manager.query(new DownloadManager.Query().setFilterById(id))) {
                                if (download != null && download.moveToFirst()) {
                                    int status = download.getInt(download.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                                    bytes = download.getLong(download.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                                    total = download.getLong(download.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                                    state = switch (status) {
                                        case DownloadManager.STATUS_RUNNING -> "running";
                                        case DownloadManager.STATUS_PAUSED -> "paused";
                                        case DownloadManager.STATUS_SUCCESSFUL -> "finalizing";
                                        case DownloadManager.STATUS_FAILED -> "failed";
                                        default -> "queued";
                                    };
                                    if (state.equals("failed") || state.equals("paused"))
                                        reason = Integer.toString(download.getInt(download.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)));
                                } else {
                                    state = "missing";
                                    reason = "system_task_missing";
                                }
                            }
                        }
                        tasks.add(new Task(id, value(c, "url"), value(c, "file"), value(c, "mime"),
                                value(c, "post"), value(c, "author"), state, reason, value(c, "uri"),
                                bytes, total, c.getLong(c.getColumnIndexOrThrow("updated"))));
                    }
                }
            } catch (RuntimeException ignored) {}
            done.accept(tasks);
        });
    }

    public static void clearCompleted(Runnable done) {
        IO.execute(() -> {
            try { database(Utils.getContext()).delete("tasks", "state='complete'", null); }
            catch (RuntimeException ignored) {}
            if (done != null) done.run();
        });
    }

    private static String value(Cursor c, String column) { return c.getString(c.getColumnIndexOrThrow(column)); }
    private static String safe(String value, int max) {
        return value == null ? "" : value.substring(0, Math.min(value.length(), max));
    }
}
