package app.morphe.extension.newx.timeline;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.List;
import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.shared.Utils;

/** Only list timelines opt in. Obfuscated model access is generated at patch time. */
public final class ListReadingPosition {
    private static final String SETTING = "newx.timeline.list_reading_position";
    private static final String PREFS = "piko_newx_list_positions_v1";

    private ListReadingPosition() {}

    public static boolean enabled(Enum<?> type) {
        return type != null && "LIST_POSTS".equals(type.name())
                && SettingsRegistry.getBooleanOrDefault(SETTING, true);
    }

    public static boolean preserveMerge(Enum<?> type, Enum<?> request, Object cursor, List<?> items) {
        if (!enabled(type) || request == null || cursor != null || !hasContent(items)) return false;
        String name = request.name();
        return "AUTO_REFRESH".equals(name) || "PULL_TO_REFRESH".equals(name);
    }

    // A non-empty container of empty pages is not a populated timeline.
    private static boolean hasContent(List<?> items) {
        if (items == null) return false;
        for (Object item : items) {
            if (item instanceof List<?>) {
                if (hasContent((List<?>) item)) return true;
            } else if (item != null) return true;
        }
        return false;
    }

    private static boolean storeEnabled(Enum<?> type, String id) {
        return enabled(type) && id != null && !id.isEmpty()
                && SettingsRegistry.getBooleanOrDefault("newx.timeline.restore_position", true);
    }

    public static int[] restore(Enum<?> type, String id) {
        if (!storeEnabled(type, id)) return null;
        // Never fall through to the native LIST_POSTS-only cache, even on a cache miss.
        int[] result = new int[]{0, 0};
        try {
            SharedPreferences prefs = preferences();
            if (prefs != null) {
                result[0] = Math.max(0, prefs.getInt(id + ".index", 0));
                result[1] = Math.max(0, prefs.getInt(id + ".offset", 0));
            }
        } catch (RuntimeException ignored) {
            // A malformed saved value must not crash navigation.
        }
        return result;
    }

    /** true means this List event is handled and must not populate the type-only cache. */
    public static boolean save(Enum<?> type, String id, int index, int offset) {
        if (!storeEnabled(type, id)) return false;
        if (index < 0 || offset < 0) return true;
        try {
            SharedPreferences prefs = preferences();
            if (prefs != null) {
                // Index and offset are published together; apply updates memory synchronously.
                prefs.edit().putInt(id + ".index", index).putInt(id + ".offset", offset).apply();
            }
        } catch (RuntimeException ignored) {
            // Still bypass the shared native List cache when persistence is unavailable.
        }
        return true;
    }

    private static SharedPreferences preferences() {
        Context context = Utils.getContext();
        return context == null ? null : context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
