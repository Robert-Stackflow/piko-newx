/** Isolated ART preflight for this exact frozen APK. Does not open history or play media. */
public final class AndroidMediaProbe {
    public static void main(String[] args) throws Exception {
        android.os.Looper.prepareMainLooper();
        String[] names = {
            "com.x.video.tab.v", "com.x.postdetail.n", "com.x.video.tab.g0",
            "app.morphe.extension.newx.misc.InlineDownloadButton",
            "app.morphe.extension.newx.settings.SettingsRenderer",
            "app.morphe.extension.newx.mediatools.WatchSession",
            "app.morphe.extension.newx.mediatools.MediaHistoryStore",
            "app.morphe.extension.newx.mediatools.MediaHistoryRuntime",
            "app.morphe.extension.newx.mediatools.HistoryFragment",
            "app.morphe.extension.newx.mediatools.DownloadTaskStore",
            "app.morphe.extension.newx.mediatools.DownloadsFragment",
            "app.morphe.extension.newx.mediatools.ResumePolicy",
            "app.morphe.extension.newx.mediatools.PlaybackStore",
            "app.morphe.extension.newx.mediatools.VideoToolsRuntime"
        };
        for (String name : names) {
            Class<?> type = Class.forName(name, true, AndroidMediaProbe.class.getClassLoader());
            type.getDeclaredMethods(); type.getDeclaredConstructors();
            System.out.println("ART verified: " + name);
        }
        System.out.println("ART_MEDIA_PASS " + names.length);
    }
}
