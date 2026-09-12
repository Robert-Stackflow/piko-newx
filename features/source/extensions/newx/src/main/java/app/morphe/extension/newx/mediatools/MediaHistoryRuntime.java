package app.morphe.extension.newx.mediatools;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Bundle;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import app.morphe.extension.newx.utils.NewXUtils;
import app.morphe.extension.shared.Utils;

/** UI-only observation. Native models are read through verified DEX bridges, never reflection. */
public final class MediaHistoryRuntime {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Visit> VISITS = new ArrayList<>();
    private static boolean scheduled;
    private static boolean lifecycleRegistered;
    private static final Runnable TICK = MediaHistoryRuntime::tick;

    private static final class Visit {
        final WeakReference<Object> owner;
        final boolean video;
        final WatchSession session = new WatchSession();
        String media;
        Visit(Object owner, boolean video) { this.owner = new WeakReference<>(owner); this.video = video; }
    }

    private MediaHistoryRuntime() {}

    // Called at the native getter/dispatcher boundary. Do not read Compose state synchronously.
    public static void bindPost(Object component) { bind(component, null, false); }
    public static void videoEvent(Object component, Object event) {
        if (event != null && isSeekEvent(event)) VideoToolsRuntime.userSeek(component);
        // Playback emits frequently. Do not enqueue UI work for progress or prefetch events.
        if (event != null && (isVideoPageEvent(event) || isMediaSelectionEvent(event))) bind(component, event, true);
    }

    private static void bind(Object component, Object event, boolean video) {
        if (component == null) return;
        WeakReference<Object> weak = new WeakReference<>(component);
        MAIN.post(() -> {
            Object owner = weak.get();
            if (owner == null) return;
            try {
                initializeLifecycle();
                Visit visit = null;
                for (Visit current : VISITS) if (current.owner.get() == owner) { visit = current; break; }
                if (visit == null) {
                    VISITS.removeIf(item -> item.owner.get() == null);
                    if (VISITS.size() >= 12) VISITS.remove(0);
                    visit = new Visit(owner, video);
                    VISITS.add(visit);
                }
                if (video && event != null && isMediaSelectionEvent(event)) visit.media = selectionMediaId(event);
                // Page changes schedule an immediate observation, regardless of playback state.
                observe(visit);
                wake();
            } catch (RuntimeException ignored) { /* Optional history never interrupts native navigation. */ }
        });
    }

    public static void wake() {
        if (Looper.myLooper() != Looper.getMainLooper()) { MAIN.post(MediaHistoryRuntime::wake); return; }
        if (NewXUtils.findUsableActivity(Utils.getContext()) == null) return;
        if (!scheduled && !VISITS.isEmpty() && (MediaHistoryStore.enabled() || VideoToolsRuntime.enabled())) {
            scheduled = true;
            MAIN.postDelayed(TICK, 300);
        }
    }

    private static void initializeLifecycle() {
        if (lifecycleRegistered || Utils.getContext() == null) return;
        if (!(Utils.getContext().getApplicationContext() instanceof Application application)) return;
        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity activity, Bundle state) {}
            public void onActivityStarted(Activity activity) {}
            public void onActivityResumed(Activity activity) { MAIN.postDelayed(MediaHistoryRuntime::wake, 100); }
            public void onActivityPaused(Activity activity) {
                MAIN.removeCallbacks(TICK); scheduled = false;
                for (Visit visit : VISITS) { visit.session.reset(); VideoToolsRuntime.inactive(visit.owner.get()); }
            }
            public void onActivityStopped(Activity activity) {}
            public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
            public void onActivityDestroyed(Activity activity) {}
        });
        lifecycleRegistered = true;
    }

    public static void resetVisits() {
        MAIN.post(() -> { for (Visit visit : VISITS) visit.session.reset(); wake(); });
    }

    private static void tick() {
        scheduled = false;
        for (Iterator<Visit> i = VISITS.iterator(); i.hasNext();) {
            Visit visit = i.next();
            Object owner = visit.owner.get();
            try {
                if (owner == null || "DESTROYED".equals(lifecycle(owner).name())) {
                    VideoToolsRuntime.inactive(owner); i.remove(); continue;
                }
                observe(visit);
            } catch (RuntimeException ignored) { visit.session.reset(); }
        }
        wake();
    }

    private static boolean foreground(Object owner) {
        if (!"RESUMED".equals(lifecycle(owner).name())) return false;
        Context context = Utils.getContext();
        Activity activity = NewXUtils.findUsableActivity(context);
        if (activity == null || !activity.hasWindowFocus()) return false;
        PowerManager power = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        return power != null && power.isInteractive();
    }

    private static void observe(Visit visit) {
        Object owner = visit.owner.get();
        if (owner == null || !foreground(owner)) {
            visit.session.reset(); VideoToolsRuntime.inactive(owner); return;
        }
        if (!MediaHistoryStore.enabled() && (!visit.video || !VideoToolsRuntime.enabled())) {
            visit.session.reset(); VideoToolsRuntime.inactive(owner); return;
        }
        long account = visit.video ? videoAccount(owner) : postAccount(owner);
        if (account <= 0) return;
        Object post;
        String media = "";
        if (visit.video) {
            List<?> items = videoItems(owner);
            int page = videoPage(owner);
            if (items == null || page < 0 || page >= items.size()) return;
            post = items.get(page);
            if (!isTimelinePost(post)) { visit.session.reset(); return; }
            // A sole video is unambiguous even before the player reports any progress.
            // Multi-media posts require the native current-media selection to match the post.
            List<Object> videos = new ArrayList<>();
            collectVideos(postMedia(post), videos);
            if (videos.isEmpty()) collectVideos(repostedMedia(post), videos);
            Object selected = null;
            for (Object candidate : videos) if (mediaId(candidate).equals(visit.media)) { selected = candidate; break; }
            if (selected == null && videos.size() == 1) selected = videos.get(0);
            if (selected == null) return;
            media = mediaId(selected);
        } else {
            String focal = focalPostId(owner);
            if (focal == null) return;
            post = findPost(detailItems(owner), focal, 0, new int[]{0});
            if (post == null) return;
        }
        String id = postId(post);
        if (id == null || id.isEmpty()) return;
        if (visit.video) {
            long position = -1, duration = -1;
            boolean seekable = false;
            // Preloaded progress may arrive after selection. Never use it for another video.
            if (id.equals(progressPostId(owner)) && media.equals(progressMediaId(owner))) {
                Object state = playbackState(owner);
                if (state != null) {
                    position = playbackPosition(state); duration = playbackDuration(state); seekable = playbackSeekable(state);
                }
            }
            VideoToolsRuntime.update(owner, account + "/" + id + "/" + media, position, duration, seekable);
        }
        if (visit.session.sample(account + "/" + id + "/" + media, true, true)) {
            MediaHistoryStore.record(account, id, media, visit.video ? "video" : "post",
                    getPostAuthorScreenName(post), getPostText(post), 0);
        }
    }

    private static void collectVideos(List<?> items, List<Object> output) {
        if (items == null) return;
        for (int i = 0; i < Math.min(16, items.size()); i++) {
            Object item = items.get(i);
            if (item != null && isVideo(item) && mediaId(item) != null) output.add(item);
        }
    }

    private static Object findPost(List<?> items, String focal, int depth, int[] visited) {
        if (items == null || depth > 4) return null;
        for (Object item : items) {
            if (++visited[0] > 500) return null;
            if (isTimelineModuleItem(item)) item = getModuleItem(item);
            if (isTimelinePost(item) && focal.equals(postId(item))) return item;
            if (isTimelineModule(item)) {
                Object found = findPost(getModuleInnerContent(item), focal, depth + 1, visited);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static IllegalStateException unpatched() { return new IllegalStateException("Media history bridge missing"); }
    private static Enum<?> lifecycle(Object owner) { throw unpatched(); }
    private static long videoAccount(Object owner) { throw unpatched(); }
    private static long postAccount(Object owner) { throw unpatched(); }
    private static List<?> videoItems(Object owner) { throw unpatched(); }
    private static int videoPage(Object owner) { throw unpatched(); }
    private static List<?> detailItems(Object owner) { throw unpatched(); }
    private static String focalPostId(Object owner) { throw unpatched(); }
    private static String postId(Object post) { throw unpatched(); }
    private static List<?> postMedia(Object post) { throw unpatched(); }
    private static List<?> repostedMedia(Object post) { throw unpatched(); }
    private static boolean isVideo(Object media) { throw unpatched(); }
    private static String mediaId(Object media) { throw unpatched(); }
    private static boolean isMediaSelectionEvent(Object event) { throw unpatched(); }
    private static boolean isVideoPageEvent(Object event) { throw unpatched(); }
    private static boolean isSeekEvent(Object event) { throw unpatched(); }
    private static String progressPostId(Object owner) { throw unpatched(); }
    private static String progressMediaId(Object owner) { throw unpatched(); }
    private static Object playbackState(Object owner) { throw unpatched(); }
    private static long playbackPosition(Object state) { throw unpatched(); }
    private static long playbackDuration(Object state) { throw unpatched(); }
    private static boolean playbackSeekable(Object state) { throw unpatched(); }
    public static void seekVideo(Object owner, float fraction) { throw unpatched(); }
    private static String selectionMediaId(Object event) { throw unpatched(); }
    private static boolean isTimelinePost(Object value) { throw unpatched(); }
    private static boolean isTimelineModule(Object value) { throw unpatched(); }
    private static boolean isTimelineModuleItem(Object value) { throw unpatched(); }
    private static Object getModuleItem(Object value) { throw unpatched(); }
    private static List<?> getModuleInnerContent(Object value) { throw unpatched(); }
    private static String getPostText(Object value) { throw unpatched(); }
    private static String getPostAuthorScreenName(Object value) { throw unpatched(); }
}
