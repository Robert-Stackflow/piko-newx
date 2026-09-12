package app.morphe.extension.newx.mediatools;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.AbsListView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import app.morphe.extension.newx.misc.InlineDownloadButton;
import app.morphe.extension.newx.settings.NewXCustomScreenFragment;
import app.morphe.extension.newx.settings.NewXSettingsUi;
import app.morphe.extension.newx.settings.NewXSettingsActivity;
import app.morphe.extension.newx.ui.Theme;
import static app.morphe.extension.newx.mediatools.MediaToolsUi.*;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

/** Only Piko task metadata. Clearing this list never removes downloaded media. */
@SuppressWarnings("deprecation")
public final class DownloadsFragment extends NewXCustomScreenFragment {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<DownloadTaskStore.Task> tasks = new ArrayList<>();
    private final Set<Long> retrying = new HashSet<>();
    private BaseAdapter adapter;
    private MediaPreviewLoader previews;
    private ListView list;
    private boolean scrolling;
    private TextView status;
    private boolean resumed, loading;
    private int generation;
    private final Runnable refresh = this::load;
    private static String text(String suffix) { return StringRef.str("piko_newx_tools_" + suffix); }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        Context context = getActivity();
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NewXSettingsUi.backgroundColor(context));
        previews = new MediaPreviewLoader();
        status = label(context, "", 14, true); status.setGravity(android.view.Gravity.CENTER);
        status.setPadding(dp(context, 32), dp(context, 24), dp(context, 32), dp(context, 24));
        root.addView(status, new LinearLayout.LayoutParams(-1, 0, 1));
        list = new ListView(context);
        list.setDivider(null); list.setSelector(android.R.color.transparent);
        list.setClipToPadding(false); list.setPadding(0, 0, 0, dp(context, 12));
        list.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView view, int state) { scrolling = state != SCROLL_STATE_IDLE; }
            public void onScroll(AbsListView view, int first, int visible, int total) {}
        });
        adapter = new BaseAdapter() {
            public int getCount() { return tasks.size(); }
            public Object getItem(int p) { return tasks.get(p); }
            public long getItemId(int p) { return tasks.get(p).id(); }
            public boolean hasStableIds() { return true; }
            public View getView(int p, View recycled, ViewGroup parentView) {
                TaskRow row = recycled != null && recycled.getTag() instanceof TaskRow previous ? previous : new TaskRow(context);
                row.bind(tasks.get(p)); return row.outer;
            }
        };
        list.setAdapter(adapter);
        list.setEmptyView(status);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener((p, view, index, id) -> {
            if (index < tasks.size()) showTask(tasks.get(index));
        });
        return root;
    }

    private void confirmClear() {
        new AlertDialog.Builder(getActivity())
                .setTitle(text("download_clear"))
                .setMessage(text("download_clear_confirm"))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        DownloadTaskStore.clearCompleted(success -> main.post(() -> {
                            if (!resumed) return;
                            if (!success) Utils.showToastShort(text("operation_failed"));
                            load();
                        }))).show();
    }

    private void load() {
        if (!resumed || loading || adapter == null) return;
        main.removeCallbacks(refresh);
        loading = true;
        int request = ++generation;
        DownloadTaskStore.query(result -> main.post(() -> {
            if (!resumed || request != generation) return;
            loading = false;
            // Never replace the adapter or interrupt an in-progress drag/fling.
            if (scrolling) { main.postDelayed(refresh, 750); return; }
            int first = list.getFirstVisiblePosition();
            long anchor = first < tasks.size() ? tasks.get(first).id() : Long.MIN_VALUE;
            View child = list.getChildAt(0); int top = child == null ? 0 : child.getTop();
            if (!tasks.equals(result.tasks())) {
                tasks.clear(); tasks.addAll(result.tasks()); adapter.notifyDataSetChanged();
                for (int i = 0; i < tasks.size(); i++) if (tasks.get(i).id() == anchor) { first = i; break; }
                list.setSelectionFromTop(first, top);
            }
            status.setText(result.failed() ? text("operation_failed") : text("download_empty"));
            main.postDelayed(refresh, 1500);
        }));
    }

    private final class TaskRow {
        final FrameLayout outer;
        final ImageView image;
        final Icon marker;
        final TextView title, metadata, state, failure;
        final ProgressBar progress;
        TaskRow(Context c) {
            LinearLayout card = card(c), header = row(c); outer = wrapCard(c, card); outer.setTag(this);
            FrameLayout preview = new FrameLayout(c);
            preview.setBackground(shape(c, Theme.surfaceContainerHigh(c), 12)); preview.setClipToOutline(true);
            image = new ImageView(c); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            preview.addView(image, new FrameLayout.LayoutParams(-1, -1));
            marker = new Icon(c, "image", text("media_preview")); marker.setClickable(false); marker.setFocusable(false);
            marker.setBackground(shape(c, 0x88000000, 18));
            FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(dp(c, 32), dp(c, 32), android.view.Gravity.BOTTOM | android.view.Gravity.END);
            badge.setMargins(0, 0, dp(c, 6), dp(c, 6)); preview.addView(marker, badge);
            header.addView(preview, space(c, 88, 88, 0));
            LinearLayout labels = column(c); labels.setPadding(dp(c, 14), 0, 0, 0);
            title = label(c, "", 15, false); title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            metadata = label(c, "", 12, true); oneLine(metadata);
            state = label(c, "", 13, true);
            labels.addView(title, space(c, -1, -2, 6)); labels.addView(metadata, space(c, -1, -2, 6)); labels.addView(state);
            header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1)); card.addView(header);
            progress = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
            progress.setProgressTintList(android.content.res.ColorStateList.valueOf(Theme.primaryAccent(c)));
            progress.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Theme.dividerColor(c)));
            LinearLayout.LayoutParams bar = space(c, -1, 4, 0); bar.topMargin = dp(c, 12); card.addView(progress, bar);
            failure = label(c, "", 13, true); failure.setPadding(0, dp(c, 8), 0, 0); card.addView(failure);
        }
        void bind(DownloadTaskStore.Task task) {
            Context c = outer.getContext(); boolean video = task.mime().startsWith("video/");
            title.setText(task.file());
            metadata.setText((task.author().isEmpty() ? "" : "@" + task.author() + " · ")
                    + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                    .format(new java.util.Date(task.updated())));
            boolean active = task.state().equals("queued") || task.state().equals("running") || task.state().equals("paused") || task.state().equals("finalizing");
            String size = task.total() > 0 && active ? " · " + android.text.format.Formatter.formatShortFileSize(c, task.bytes())
                    + " / " + android.text.format.Formatter.formatShortFileSize(c, task.total()) : "";
            state.setText(text("state_" + task.state()) + size);
            state.setTextColor(active || task.state().equals("complete") ? Theme.primaryAccent(c) : Theme.secondaryText(c));
            progress.setVisibility(active ? View.VISIBLE : View.GONE); progress.setIndeterminate(task.total() <= 0);
            if (task.total() > 0) progress.setProgress((int) Math.max(0, Math.min(100, 100.0 * task.bytes() / task.total())));
            failure.setText(reason(task.reason())); failure.setVisibility(task.reason().isEmpty() ? View.GONE : View.VISIBLE);
            marker.update(video ? "play" : "image", text(video ? "video_cover" : "media_preview"), android.graphics.Color.WHITE);
            String source = task.state().equals("complete") && task.uri().startsWith("content://") ? task.uri()
                    : video ? "" : MediaPreviewLoader.safeRemote(task.url());
            previews.load(image, source, video);
        }
    }

    private void showTask(DownloadTaskStore.Task task) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        if (task.state().equals("complete") && task.uri().startsWith("content://")) {
            labels.add(text("download_open")); actions.add(() -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(task.uri()), task.mime())
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
                catch (RuntimeException error) { Utils.showToastShort(text("open_failed")); }
            });
        }
        if (task.post().matches("[1-9][0-9]{0,19}")) {
            labels.add(text("download_source")); actions.add(() -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://x.com/i/status/" + task.post()))
                        .setPackage(getActivity().getPackageName())); }
                catch (RuntimeException error) { Utils.showToastShort(text("open_failed")); }
            });
        }
        if ((task.state().equals("failed") || task.state().equals("missing") || task.state().equals("finalizing"))
                && !retrying.contains(task.id())) {
            labels.add(text("download_retry")); actions.add(() -> {
                if (!retrying.add(task.id())) return;
                InlineDownloadButton.retryManagedDownload(getActivity(), task.id(), task.url(), task.file(),
                        task.mime(), task.post(), task.author(), success -> main.post(() -> {
                            retrying.remove(task.id());
                            if (!resumed) return;
                            if (!success) Utils.showToastShort(text("download_retry_failed"));
                            load();
                        }));
            });
        }
        AlertDialog.Builder dialog = new AlertDialog.Builder(getActivity()).setTitle(task.file())
                .setNegativeButton(android.R.string.cancel, null);
        if (labels.isEmpty()) dialog.setMessage(text("state_" + task.state()) + "\n" + reason(task.reason()));
        else dialog.setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run());
        dialog.show();
    }

    private static String reason(String value) {
        return switch (value) {
            case "" -> "";
            case "enqueue" -> text("reason_enqueue");
            case "finalize" -> text("reason_finalize");
            case "system_task_missing" -> text("reason_missing");
            default -> text("reason_system") + " " + value;
        };
    }

    @Override public void onResume() {
        super.onResume(); resumed = true;
        if (getActivity() instanceof NewXSettingsActivity host) {
            host.setPageTitle(text("downloads_title")); host.setPatchVersionFooterVisible(false);
            Icon clear = new Icon(host, "delete", text("download_clear"));
            clear.setOnClickListener(view -> confirmClear()); host.setPageAction(clear);
        }
        load();
    }
    @Override public void onPause() {
        if (getActivity() instanceof NewXSettingsActivity host) host.setPageAction(null);
        resumed = false; loading = false; generation++; main.removeCallbacks(refresh); super.onPause();
    }
    @Override public void onDestroyView() {
        previews.close(); previews = null; list = null;
        adapter = null; status = null; tasks.clear(); super.onDestroyView();
    }
}
