/** Verify only the patched classes in an isolated ART process before installing a candidate. */
public final class AndroidTypeProbe {
    public static void main(String[] args) throws Exception {
        String[] names = {
            "com.x.urt.y", "com.x.urt.i", "com.x.urt.s1", "com.x.repositories.urt.v",
            "com.x.home.v0", "com.x.home.tabbed.d0", "com.x.home.tabbed.a0",
            "app.morphe.extension.newx.timeline.ListReadingPosition",
            "app.morphe.extension.newx.timeline.TimelineRepostFilter"
        };
        int verified = 0;
        for (String name : names) {
            // Class initialization is necessary: resolving methods with initialize=false did
            // NOT catch the known-bad candidate. Use dalvikvm -Xverify:all, no app Context.
            Class<?> type = Class.forName(name, true, AndroidTypeProbe.class.getClassLoader());
            type.getDeclaredMethods();
            type.getDeclaredConstructors();
            System.out.println("ART verified: " + name);
            verified++;
        }
        System.out.println("ART_PROBE_PASS " + verified);
    }
}
