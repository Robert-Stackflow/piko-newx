package app.morphe.extension.newx.mediatools;

/** One bounded attempt per displayed video; user navigation/seek always wins. */
public final class ResumePolicy {
    private boolean resolved;
    public void cancel() { resolved = true; }
    public boolean pending() { return !resolved; }
    public long choose(long elapsed, long current, long duration, boolean seekable,
                       long savedPosition, long savedDuration) {
        if (resolved) return -1;
        if (elapsed > 4000 || current > 1500 || current < 0) { resolved = true; return -1; }
        if (!seekable || duration <= 0) return -1;
        resolved = true;
        if (savedPosition < 3000 || savedDuration <= 0 || savedPosition >= duration - 3000
                || Math.abs(savedDuration - duration) > Math.max(1500, duration / 50)) return -1;
        return savedPosition;
    }
}
