package app.morphe.extension.newx.mediatools;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.View;
import android.graphics.Rect;
import android.graphics.Color;
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
    private static MediaToolsUi.Icon button;
    private static WeakReference<MediaToolsUi.Icon> headerIcon = new WeakReference<>(null);
    private static final Rect lastHeaderBounds = new Rect();
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
        refreshHeaderIcon(headerIcon.get());
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

    public static View createHeaderIcon(android.content.Context context) {
        MediaToolsUi.Icon icon = new MediaToolsUi.Icon(context, "lock", MediaToolsUi.text("lock"));
        icon.setTag("piko.video.lock");
        icon.setLayoutParams(new ViewGroup.LayoutParams(MediaToolsUi.dp(context, 48), MediaToolsUi.dp(context, 48)));
        icon.setOnClickListener(view -> {
            if (!lockEnabled()) return;
            headerIcon = new WeakReference<>(icon);
            locked = true; showShield();
        });
        icon.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            public void onViewAttachedToWindow(View view) { headerIcon = new WeakReference<>(icon); refreshHeaderIcon(icon); }
            public void onViewDetachedFromWindow(View view) { if (headerIcon.get() == icon) headerIcon.clear(); }
        });
        refreshHeaderIcon(icon); return icon;
    }

    public static void refreshHeaderIcon(View view) {
        if (!(view instanceof MediaToolsUi.Icon icon)) return;
        boolean enabled = lockEnabled();
        int size = enabled ? MediaToolsUi.dp(icon.getContext(), 48) : 0;
        ViewGroup.LayoutParams params = icon.getLayoutParams();
        if (params.width != size || params.height != size) { params.width = size; params.height = size; icon.setLayoutParams(params); }
        icon.setVisibility(enabled ? View.VISIBLE : View.GONE);
        icon.update("lock", MediaToolsUi.text("lock"), Color.WHITE);
        icon.setAlpha(locked ? 0f : 1f);
        if (enabled && icon.isAttachedToWindow()) headerIcon = new WeakReference<>(icon);
    }

    @SuppressWarnings("deprecation")
    private static void showShield() {
        refreshHeaderIcon(headerIcon.get());
        if (!lockEnabled()) { removeShield(); return; }
        // Unlocked controls are actual header children. No floating button or full-screen view.
        if (!locked) return;
        Activity activity = NewXUtils.findUsableActivity(Utils.getContext());
        if (activity == null || !activity.hasWindowFocus()) { removeShield(); return; }
        ViewGroup decor = (ViewGroup) activity.getWindow().getDecorView();
        if (shield != null && shield.getParent() != decor) { removeShield(); return; }
        if (shield != null) return;
        MediaToolsUi.Icon nativeIcon = headerIcon.get();
        if (nativeIcon != null) nativeIcon.getGlobalVisibleRect(lastHeaderBounds);
        shield = new FrameLayout(activity);
        shield.setOnTouchListener((view, event) -> true);
        shield.setClickable(true);
        button = new MediaToolsUi.Icon(activity, "unlock", MediaToolsUi.text("unlock"));
        button.update("unlock", MediaToolsUi.text("unlock"), Color.WHITE);
        button.setOnClickListener(view -> removeShield());
        int size = MediaToolsUi.dp(activity, 48);
        int[] origin = new int[2]; decor.getLocationOnScreen(origin);
        WindowInsets insets = decor.getRootWindowInsets();
        int safeTop = insets == null ? 0 : insets.getSystemWindowInsetTop();
        int left = lastHeaderBounds.isEmpty() ? (decor.getWidth() - size) / 2 : lastHeaderBounds.left - origin[0];
        int top = lastHeaderBounds.isEmpty() ? safeTop : lastHeaderBounds.top - origin[1];
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.LEFT);
        params.leftMargin = Math.max(0, Math.min(left, decor.getWidth() - size));
        params.topMargin = Math.max(safeTop, Math.min(top, decor.getHeight() - size));
        // While locked, retain the unlock hit target at the exact header slot even if native controls auto-hide.
        button.setBackground(MediaToolsUi.shape(activity, 0x33000000, 24));
        shield.addView(button, params); decor.addView(shield, new ViewGroup.LayoutParams(-1, -1));
        refreshHeaderIcon(nativeIcon);
    }

    private static void removeShield() {
        if (shield != null && shield.getParent() instanceof ViewGroup parent) parent.removeView(shield);
        shield = null; button = null; locked = false;
        MediaToolsUi.Icon icon = headerIcon.get(); if (icon != null) icon.setAlpha(1f);
    }
}
