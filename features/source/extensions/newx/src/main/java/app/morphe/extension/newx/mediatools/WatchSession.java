package app.morphe.extension.newx.mediatools;

/** A visit starts when a video becomes the foreground current page, even when paused.
 * Callers prove selection independently of playback. Prefetch cannot start or end a visit.
 */
public final class WatchSession {
    private String key;

    public boolean sample(String identity, boolean selected, boolean foreground) {
        // Foreground describes the viewer, not a prefetched child's lifecycle.
        if (!foreground) {
            reset();
            return false;
        }
        if (!selected) return false;
        if (identity == null || identity.isEmpty()) {
            reset();
            return false;
        }
        if (identity.equals(key)) return false;
        key = identity;
        return true;
    }

    public void reset() {
        key = null;
    }
}
