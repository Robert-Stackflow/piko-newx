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
    private Anchor current, pending;
    private boolean first = true, suppressed;
    private long generation;
    public ListAnchorState(Anchor saved) { pending = saved; }
    public long generation() { return generation; }
    public Anchor current() { return current; }
    public boolean pending() { return pending != null; }
    public boolean hasItems() { return !indices.isEmpty(); }
    public String keyAt(int index) { return index < 0 || index >= keys.length ? null : keys[index]; }
    public boolean entries(String[] next) {
        if (Arrays.equals(keys, next)) return false;
        keys = next.clone(); indices.clear(); generation++;
        for (int i=0; i<keys.length; i++) if (keys[i] != null && indices.putIfAbsent(keys[i],i) != null) indices.put(keys[i],-1);
        if (indices.isEmpty()) return true;
        if (!first && !suppressed && pending == null) pending = current;
        first = false; suppressed = false;
        return true;
    }
    public void cancel() { pending = null; current = null; first = false; suppressed = true; }
    public int[] resolve(long expected) {
        if (expected != generation || pending == null) return null;
        Integer index = indices.get(pending.key);
        Anchor target = pending; pending = null;
        if (index == null || index < 0) { current = null; return null; }
        current = target;
        return new int[]{index,target.offset};
    }
    public boolean observe(String key, int index, int offset) {
        if (pending != null || key == null || offset < 0 || !key.equals(keyAt(index))) return false;
        Integer unique = indices.get(key);
        if (unique == null || unique != index) return false;
        current = new Anchor(key,offset); suppressed = false;
        return true;
    }
}
