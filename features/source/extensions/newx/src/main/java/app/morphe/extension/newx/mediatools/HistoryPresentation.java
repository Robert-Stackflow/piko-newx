package app.morphe.extension.newx.mediatools;

/** Pure presentation policy, shared by the screen and layout regression tests. */
public final class HistoryPresentation {
    private HistoryPresentation() {}
    public static int mode(int value) { return value >= 0 && value < 3 ? value : 0; }
    public static int nextMode(int value) { return (mode(value) + 1) % 3; }
    public static String kind(int value) { return mode(value) == 1 ? "post" : mode(value) == 2 ? "video" : ""; }
    public static String label(int value) { return mode(value) == 1 ? "history_posts" : mode(value) == 2 ? "history_videos" : "history_all"; }
    public static String glyph(int value) { return mode(value) == 1 ? "post" : mode(value) == 2 ? "video" : "all"; }
    /** [left, top, right, bottom], with 3 images using a full-height left tile. */
    public static int[] tile(int count, int index, int width, int height, int gap) {
        if (count < 1 || count > 4 || index < 0 || index >= count || width < 1 || height < 1)
            throw new IllegalArgumentException("Invalid media grid");
        if (count == 1) return new int[]{0, 0, width, height};
        int gutter = Math.min(Math.max(0, gap), Math.max(0, Math.min(width, height) - 2));
        int leftWidth = (width - gutter) / 2, topHeight = (height - gutter) / 2;
        boolean right = count == 3 ? index > 0 : index % 2 == 1;
        int left = right ? leftWidth + gutter : 0, end = right ? width : leftWidth;
        if (count == 2 || count == 3 && index == 0) return new int[]{left, 0, end, height};
        boolean bottom = count == 3 ? index == 2 : index >= 2;
        return new int[]{left, bottom ? topHeight + gutter : 0, end, bottom ? height : topHeight};
    }
}
