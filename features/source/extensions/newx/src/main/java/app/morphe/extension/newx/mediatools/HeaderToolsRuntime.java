package app.morphe.extension.newx.mediatools;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import app.morphe.extension.newx.settings.NewXSettingsActivity;
import app.morphe.extension.newx.settings.NewXSettingsFragment;
import app.morphe.extension.newx.utils.NewXUtils;
import app.morphe.extension.shared.Utils;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.functions.Function3;
import static app.morphe.extension.newx.mediatools.MediaToolsUi.*;

/** Real header children hosted through the APK's AndroidView bridge, never a home overlay. */
@SuppressWarnings("deprecation")
public final class HeaderToolsRuntime {
    public static final String DESTINATION = "piko.media_tools.destination";
    // Named classes are deliberately kept by the extension shrinker. Synthetic Java SAM methods
    // targeting Kotlin interfaces were removed before injection, even though the class survived.
    private static final Function1<Object, Object> HOME = new HomeFactory();
    private static final Function1<Object, Object> VIDEO = new VideoFactory();
    private static final Function1<Object, Object> UPDATE = new HeaderUpdate();
    public static final class HomeFactory implements Function1<Object, Object> {
        @Override public Object invoke(Object value) {
            Context context = (Context) value;
            Icon icon = new Icon(context, "more", text("more"));
            icon.setLayoutParams(new ViewGroup.LayoutParams(dp(context, 48), dp(context, 48)));
            icon.setOnClickListener(HeaderToolsRuntime::menu); return icon;
        }
    }
    public static final class VideoFactory implements Function1<Object, Object> {
        @Override public Object invoke(Object context) { return VideoToolsRuntime.createHeaderIcon((Context) context); }
    }
    public static final class HeaderUpdate implements Function1<Object, Object> {
        @Override public Object invoke(Object value) {
            if (value instanceof View view && "piko.video.lock".equals(view.getTag())) VideoToolsRuntime.refreshHeaderIcon(view);
            return null;
        }
    }
    public static final class VideoActions implements Function3<Object, Object, Object, Object> {
        private final Function2<Object, Object, Object> action;
        @SuppressWarnings("unchecked") public VideoActions(Object original) { action = (Function2<Object, Object, Object>) original; }
        @Override public Object invoke(Object scope, Object composer, Object flags) {
            render(composer, true); action.invoke(composer, 0); return null;
        }
    }
    private HeaderToolsRuntime() {}
    public static Function1<?, ?> factory(boolean video) { return video ? VIDEO : HOME; }
    public static Function1<?, ?> updater() { return UPDATE; }
    public static void home(Object composer) { render(composer, false); }
    private static void render(Object composer, boolean video) { throw new IllegalStateException("Header bridge missing"); }
    public static Function3<Object, Object, Object, Object> wrapVideoActions(Object original) {
        return new VideoActions(original);
    }
    private static void menu(View anchor) {
        Activity activity = NewXUtils.findUsableActivity(anchor.getContext());
        if (activity == null) return;
        PopupMenu popup = new PopupMenu(activity, anchor, Gravity.END);
        popup.getMenu().add(0, 0, 0, text("piko_settings"));
        popup.getMenu().add(0, 1, 1, text("downloads_title"));
        popup.getMenu().add(0, 2, 2, text("history_title"));
        popup.setOnMenuItemClickListener(item -> {
            Intent intent = new Intent(activity, NewXSettingsActivity.class);
            if (item.getItemId() == 1) intent.putExtra(DESTINATION, "downloads");
            if (item.getItemId() == 2) intent.putExtra(DESTINATION, "history");
            try { activity.startActivity(intent); }
            catch (RuntimeException error) { Utils.showToastShort(text("open_failed")); }
            return true;
        });
        popup.show();
    }
    public static Fragment initialScreen(Intent intent) {
        String destination = intent == null ? null : intent.getStringExtra(DESTINATION);
        if ("downloads".equals(destination)) return new DownloadsFragment();
        if ("history".equals(destination)) return new HistoryFragment();
        return new NewXSettingsFragment();
    }
}
