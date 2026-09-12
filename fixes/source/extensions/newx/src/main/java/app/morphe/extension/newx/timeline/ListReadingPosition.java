package app.morphe.extension.newx.timeline;

import app.morphe.extension.newx.settings.SettingsRegistry;

/** Disables the obsolete ordinal cache only for opted-in Lists. */
public final class ListReadingPosition {
    private ListReadingPosition() {}
    public static boolean enabled(Enum<?> type) {
        return type != null && "LIST_POSTS".equals(type.name())
            && SettingsRegistry.getBooleanOrDefault("newx.timeline.list_reading_position",true);
    }
    private static boolean active(Enum<?> type, String id) {
        return enabled(type) && id != null && !id.isEmpty()
            && SettingsRegistry.getBooleanOrDefault("newx.timeline.restore_position",true);
    }
    public static int[] restore(Enum<?> type, String id) {
        // Returning neutral position also disables native initial ordinal restoration.
        return active(type,id) ? new int[]{0,0} : null;
    }
    public static boolean save(Enum<?> type, String id, int index, int offset) {
        // UI lifecycle observer persists identities; never write the shared ordinal cache.
        return active(type,id);
    }
}
