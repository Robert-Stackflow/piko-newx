package app.morphe.extension.newx.timeline;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** Pure UI state: never owns or mutates timeline content. */
public final class ListAnchorState {
    public static final class Anchor {
        public final String key;
        public final int offset;
        public Anchor(String key, int offset) { this.key = key; this.offset = offset; }
    }
    private String[] keys = new String[0];
    private final Map<String,Integer> indices = new HashMap<>();
    private final Map<String,Integer> identities = new HashMap<>();
    private Anchor current, pending;
    private boolean first = true, suppressed;
    private long generation;
    public ListAnchorState(Anchor saved) { current = saved; pending = saved; }
    public long generation() { return generation; }
    public Anchor current() { return current; }
    public boolean pending() { return pending != null; }
    public boolean hasItems() { return !indices.isEmpty(); }
    public String keyAt(int index) { return index < 0 || index >= keys.length ? null : keys[index]; }
    public boolean entries(String[] next) {
        if (Arrays.equals(keys, next)) return false;
        keys = next.clone(); indices.clear(); identities.clear(); generation++;
        for (int i=0; i<keys.length; i++) if (keys[i] != null) {
            if (indices.putIfAbsent(keys[i],i) != null) indices.put(keys[i],-1);
            String id = identity(keys[i]);
            if (id != null && identities.putIfAbsent(id,i) != null) identities.put(id,-1);
        }
        if (indices.isEmpty()) return true;
        if (!first && !suppressed && pending == null) pending = current;
        first = false;
        return true;
    }
    /** Cancel the automatic movement, not the last confirmed reading bookmark. */
    public void cancel() { pending = null; first = false; suppressed = true; }
    public void clear() { cancel(); current = null; }
    // Retain the complete native key for layout confirmation. Only its validated
    // length-prefixed entry identity is used for a unique fallback after re-ranking.
    private static String identity(String key) {
        if (key == null || !key.startsWith("regular:")) return null;
        int colon = key.indexOf(':', 8);
        if (colon < 9) return null;
        try {
            int length = Integer.parseInt(key.substring(8, colon));
            int end = colon + 1 + length;
            if (length <= 0 || length > 1024 || end >= key.length() || key.charAt(end) != ':') return null;
            Long.parseLong(key.substring(end + 1));
            return key.substring(0, end);
        } catch (NumberFormatException ignored) { return null; }
    }
    public int[] resolve(long expected) {
        if (expected != generation || pending == null) return null;
        Integer index = indices.get(pending.key);
        if (index == null) index = identities.get(identity(pending.key));
        if (index == null || index < 0) return null;
        return new int[]{index,pending.offset};
    }
    /** Only measured key AND offset in this generation commit a restoration. */
    public boolean confirm(long expected, String key, int index, int offset) {
        int[] target = resolve(expected);
        if (target == null || index != target[0] || offset != target[1] || !keyAt(index).equals(key)) return false;
        current = new Anchor(key, offset); pending = null; suppressed = false;
        return true;
    }
    public boolean observe(String key, int index, int offset) {
        if (pending != null || key == null || offset < 0 || !key.equals(keyAt(index))) return false;
        Integer unique = indices.get(key);
        if (unique == null || unique != index) return false;
        current = new Anchor(key,offset); suppressed = false;
        return true;
    }
}
