package app.morphe.extension.newx.mediatools;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.newx.settings.SettingsRegistry;

/** Bounded history, deliberately excluded from Android/settings backup and all diagnostics. */
public final class MediaHistoryStore {
    public static final String ENABLED = "newx.media_tools.history_enabled";
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final AtomicLong EPOCH = new AtomicLong();
    // Protect enqueue ordering only. Never hold this monitor during disk I/O.
    private static final Object QUEUE_LOCK = new Object();
    private static final int LIMIT = 5000;
    private static SQLiteDatabase database;

    public record Entry(long account, String post, String media, String kind,
                        String author, String text, long visited, long position, String previews) {}
    public record Result(List<Entry> entries, boolean failed) {}

    private MediaHistoryStore() {}

    public static boolean enabled() {
        return SettingsRegistry.getBooleanOrDefault(ENABLED, false);
    }

    private static synchronized SQLiteDatabase database() {
        if (database != null) return database;
        Context context = Utils.getContext();
        if (context == null) throw new IllegalStateException("Context unavailable");
        File file = new File(context.getNoBackupFilesDir(), "piko_media_history_v1.db");
        SQLiteDatabase opened = SQLiteDatabase.openOrCreateDatabase(file, null);
        try {
            if (opened.getVersion() > 2) throw new IllegalStateException("Unsupported history schema");
            opened.beginTransaction();
            try {
            opened.execSQL("CREATE TABLE IF NOT EXISTS history (account INTEGER NOT NULL, "
                    + "post TEXT NOT NULL, media TEXT NOT NULL, kind TEXT NOT NULL, "
                    + "author TEXT NOT NULL, body TEXT NOT NULL, visited INTEGER NOT NULL, "
                    + "position INTEGER NOT NULL, PRIMARY KEY(account,post,media,kind))");
            opened.execSQL("CREATE INDEX IF NOT EXISTS history_recent ON history(visited DESC)");
            if (opened.getVersion() < 2)
                opened.execSQL("ALTER TABLE history ADD COLUMN previews TEXT NOT NULL DEFAULT '[]'");
            opened.setVersion(2);
            opened.setTransactionSuccessful();
            } finally { opened.endTransaction(); }
            database = opened;
            return opened;
        } catch (RuntimeException error) {
            opened.close();
            throw error;
        }
    }

    public static void record(long account, String post, String media, String kind,
                              String author, String text, long position, String previews) {
        if (!enabled() || account <= 0 || !numericId(post)
                || !("post".equals(kind) || "video".equals(kind))) return;
        String safeMedia = media == null ? "" : media;
        if (safeMedia.length() > 256 || ("video".equals(kind) && safeMedia.isEmpty())) return;
        Entry entry = new Entry(account, post, safeMedia, kind, bounded(author, 128),
                bounded(text, 1500), System.currentTimeMillis(), Math.max(0, position),
                previews != null && previews.length() <= 18000 ? previews : "[]");
        synchronized (QUEUE_LOCK) {
            long epoch = EPOCH.get();
            IO.execute(() -> {
                if (epoch != EPOCH.get() || !enabled()) return;
                try {
                    SQLiteDatabase db = database();
                    ContentValues values = new ContentValues();
                    values.put("account", entry.account()); values.put("post", entry.post());
                    values.put("media", entry.media()); values.put("kind", entry.kind());
                    values.put("author", entry.author()); values.put("body", entry.text());
                    values.put("visited", entry.visited()); values.put("position", entry.position());
                    values.put("previews", entry.previews());
                    db.beginTransaction();
                    try {
                        db.insertWithOnConflict("history", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                        prune(db);
                        db.setTransactionSuccessful();
                    } finally { db.endTransaction(); }
                } catch (RuntimeException ignored) {
                    // History is optional; errors must not interrupt native navigation/playback.
                }
            });
        }
    }

    /** Call when toggling recording, so a disable/re-enable cannot revive queued old events. */
    public static void invalidatePending() {
        synchronized (QUEUE_LOCK) { EPOCH.incrementAndGet(); }
    }

    public static void clear(java.util.function.Consumer<Boolean> done) {
        synchronized (QUEUE_LOCK) {
            EPOCH.incrementAndGet();
            IO.execute(() -> {
                boolean success = true;
                try { database().delete("history", null, null); }
                catch (RuntimeException ignored) { success = false; }
                if (done != null) done.accept(success);
            });
        }
    }

    /** Runs callback off the UI thread; callers must marshal view updates and guard lifecycle. */
    public static void query(String kind, String search, java.util.function.Consumer<Result> done) {
        IO.execute(() -> {
            List<Entry> result = new ArrayList<>();
            boolean failed = false;
            try {
                SQLiteDatabase db = database();
                prune(db);
                String term = bounded(search, 200).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                String where = "(?='' OR kind=?) AND (body LIKE ? ESCAPE '\\' OR author LIKE ? ESCAPE '\\' OR post LIKE ? ESCAPE '\\')";
                String filter = "video".equals(kind) || "post".equals(kind) ? kind : "";
                String like = "%" + term + "%";
                try (Cursor c = db.query("history", null, where,
                        new String[]{filter, filter, like, like, like}, null, null, "visited DESC", "300")) {
                    while (c.moveToNext()) result.add(new Entry(
                            c.getLong(c.getColumnIndexOrThrow("account")), c.getString(c.getColumnIndexOrThrow("post")),
                            c.getString(c.getColumnIndexOrThrow("media")), c.getString(c.getColumnIndexOrThrow("kind")),
                            c.getString(c.getColumnIndexOrThrow("author")), c.getString(c.getColumnIndexOrThrow("body")),
                            c.getLong(c.getColumnIndexOrThrow("visited")), c.getLong(c.getColumnIndexOrThrow("position")),
                            c.getString(c.getColumnIndexOrThrow("previews"))));
                }
            } catch (RuntimeException ignored) { failed = true; }
            done.accept(new Result(result, failed));
        });
    }

    private static void prune(SQLiteDatabase db) {
        String value = SettingsRegistry.getStringOrDefault("newx.media_tools.history_days", "30");
        int days = "7".equals(value) ? 7 : "90".equals(value) ? 90 : 30;
        long cutoff = System.currentTimeMillis() - days * 86400000L;
        db.delete("history", "visited < ?", new String[]{Long.toString(cutoff)});
        db.execSQL("DELETE FROM history WHERE rowid IN (SELECT rowid FROM history ORDER BY visited DESC LIMIT -1 OFFSET " + LIMIT + ")");
    }

    private static boolean numericId(String value) {
        return value != null && value.matches("[1-9][0-9]{0,19}");
    }

    private static String bounded(String value, int limit) {
        return value == null ? "" : value.substring(0, Math.min(limit, value.length()));
    }
}
