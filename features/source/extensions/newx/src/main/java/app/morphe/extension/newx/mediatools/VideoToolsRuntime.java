package app.morphe.extension.newx.mediatools;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import java.lang.ref.WeakReference;
import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.newx.utils.NewXUtils;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

/** Native speed and long-press behavior are preserved. Adds bounded resume and a touch shield. */
public final class VideoToolsRuntime {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<Object> owner = new WeakReference<>(null);
    private static String identity;
    private static long started, lastSave, position, duration;
    private static PlaybackStore.Bookmark bookmark;
    private static ResumePolicy policy = new ResumePolicy();
    private static boolean loaded, internalSeek;
    private static int generation;
    private static FrameLayout shield;
    private static Button button;
    private static boolean locked;

    private VideoToolsRuntime() {}
    private static boolean resumeEnabled() { return SettingsRegistry.getBooleanOrDefault("newx.media_tools.resume_video", false); }
    private static boolean lockEnabled() { return SettingsRegistry.getBooleanOrDefault("newx.media_tools.touch_lock", false); }
    public static boolean enabled() { return resumeEnabled() || lockEnabled(); }

    public static void userSeek(Object component) {
        if (!internalSeek && owner.get() == component) policy.cancel();
    }

    public static void inactive(Object component) {
        if (owner.get() != component) return;
        flush(); generation++; identity = null; owner.clear(); removeShield();
    }

    public static void update(Object component, String key, long current, long length, boolean seekable) {
        if (!enabled()) { inactive(component); return; }
        if (owner.get() != component || !key.equals(identity)) {
            flush(); generation++; owner = new WeakReference<>(component); identity = key;
            started = SystemClock.elapsedRealtime(); lastSave = started;
            position = -1; duration = -1; loaded = false; bookmark = null; policy = new ResumePolicy();
            int request = generation;
            if (resumeEnabled()) PlaybackStore.load(key, result -> MAIN.post(() -> {
                if (request != generation || !key.equals(identity)) return;
                bookmark = result; loaded = true;
            }));
        }
        showShield();
        if (current < 0 || length <= 0 || current > length) return;
        position = current; duration = length;
        long now = SystemClock.elapsedRealtime();
        if (resumeEnabled() && loaded && policy.pending()) {
            long target = policy.choose(now - started, current, length, seekable,
                    bookmark == null ? -1 : bookmark.position(), bookmark == null ? -1 : bookmark.duration());
            if (target >= 0) {
                internalSeek = true;
                try { MediaHistoryRuntime.seekVideo(component, (float) target / length); }
                finally { internalSeek = false; }
                // Do not save the old zero position while the native player processes the seek.
                position = target; lastSave = now;
                return;
            }
        }
        if (!resumeEnabled()) policy.cancel();
        if (now - lastSave >= 5000 && !policy.pending()) { flush(); lastSave = now; }
    }

    private static void flush() {
        // Pending or unavailable player data must not overwrite a previously saved bookmark.
        if (resumeEnabled() && identity != null && !policy.pending() && position >= 0 && duration > 0)
            PlaybackStore.save(identity, position, duration);
    }

    @SuppressWarnings("deprecation")
    private static void showShield() {
        if (!lockEnabled()) { removeShield(); return; }
        Activity activity = NewXUtils.findUsableActivity(Utils.getContext());
        if (activity == null || !activity.hasWindowFocus()) { removeShield(); return; }
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        if (shield != null && shield.getParent() != decor) removeShield();
        if (shield != null) return;
        shield = new FrameLayout(activity);
        shield.setOnTouchListener((view, event) -> locked);
        button = new Button(activity);
        button.setAllCaps(false);
        button.setOnClickListener(view -> { locked = !locked; updateButton(); });
        int density = Math.max(1, Math.round(activity.getResources().getDisplayMetrics().density));
        WindowInsets insets = decor.getRootWindowInsets();
        int top = insets == null ? 0 : insets.getSystemWindowInsetTop();
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, 48 * density, Gravity.TOP | Gravity.END);
        params.topMargin = top + 8 * density; params.rightMargin = 12 * density;
        shield.addView(button, params);
        decor.addView(shield, new ViewGroup.LayoutParams(-1, -1));
        locked = false; updateButton();
    }

    private static void updateButton() {
        if (button == null) return;
        String label = StringRef.str(locked ? "piko_newx_tools_unlock" : "piko_newx_tools_lock");
        button.setText(label); button.setContentDescription(label);
        shield.setClickable(locked);
    }

    private static void removeShield() {
        if (shield != null && shield.getParent() instanceof ViewGroup parent) parent.removeView(shield);
        shield = null; button = null; locked = false;
    }
}
