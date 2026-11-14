package app.morphe.extension.newx.mediatools;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import app.morphe.extension.shared.Utils;

/** Playback bookmarks are separate from browsing history and excluded from backup. */
public final class PlaybackStore {
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static SQLiteDatabase database;
    public record Bookmark(long position, long duration) {}
    private PlaybackStore() {}
    private static SQLiteDatabase database() {
        if (database == null) {
            SQLiteDatabase opened = SQLiteDatabase.openOrCreateDatabase(new File(Utils.getContext().getNoBackupFilesDir(),
                    "piko_playback_v1.db"), null);
            try {
                if (opened.getVersion() > 1) throw new IllegalStateException("Unsupported playback schema");
                opened.execSQL("CREATE TABLE IF NOT EXISTS playback (identity TEXT PRIMARY KEY, position INTEGER NOT NULL, "
                        + "duration INTEGER NOT NULL, updated INTEGER NOT NULL)");
                opened.setVersion(1); database = opened;
            } catch (RuntimeException e) { opened.close(); throw e; }
        }
        return database;
    }
    public static void load(String identity, Consumer<Bookmark> done) {
        IO.execute(() -> {
            Bookmark result = null;
            try (Cursor c = database().query("playback", new String[]{"position", "duration"},
                    "identity=? AND updated>?", new String[]{identity, Long.toString(System.currentTimeMillis() - 90L * 86400000)},
                    null, null, null)) {
                if (c.moveToFirst()) result = new Bookmark(c.getLong(0), c.getLong(1));
            } catch (RuntimeException ignored) {}
            done.accept(result);
        });
    }
    public static void save(String identity, long position, long duration) {
        if (identity == null || identity.length() > 350 || duration <= 0 || position < 0 || position > duration) return;
        IO.execute(() -> {
            try {
                SQLiteDatabase db = database();
                ContentValues values = new ContentValues();
                values.put("identity", identity); values.put("position", position); values.put("duration", duration);
                values.put("updated", System.currentTimeMillis());
                db.insertWithOnConflict("playback", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                db.execSQL("DELETE FROM playback WHERE identity IN (SELECT identity FROM playback ORDER BY updated DESC LIMIT -1 OFFSET 1000)");
            } catch (RuntimeException ignored) {}
        });
    }
}
