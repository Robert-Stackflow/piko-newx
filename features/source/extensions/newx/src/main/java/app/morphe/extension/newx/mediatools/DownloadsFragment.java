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
import android.widget.ArrayAdapter;
import android.widget.Button;
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
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

/** Only Piko task metadata. Clearing this list never removes downloaded media. */
@SuppressWarnings("deprecation")
public final class DownloadsFragment extends NewXCustomScreenFragment {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<DownloadTaskStore.Task> tasks = new ArrayList<>();
    private final Set<Long> retrying = new HashSet<>();
    private ArrayAdapter<String> adapter;
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
        status = new TextView(context);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        ListView list = new ListView(context);
        adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener((p, view, index, id) -> {
            if (index < tasks.size()) showTask(tasks.get(index));
        });
        Button clear = new Button(context);
        clear.setText(text("download_clear"));
        root.addView(clear, new LinearLayout.LayoutParams(-1, -2));
        clear.setOnClickListener(view -> new AlertDialog.Builder(context)
                .setMessage(text("download_clear_confirm"))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        DownloadTaskStore.clearCompleted(success -> main.post(() -> {
                            if (!resumed) return;
                            if (!success) Utils.showToastShort(text("operation_failed"));
                            load();
                        }))).show());
        return root;
    }

    private void load() {
        if (!resumed || loading || adapter == null) return;
        main.removeCallbacks(refresh);
        loading = true;
        int request = ++generation;
        DownloadTaskStore.query(result -> main.post(() -> {
            if (!resumed || request != generation) return;
            loading = false;
            tasks.clear(); tasks.addAll(result.tasks()); adapter.clear();
            for (DownloadTaskStore.Task task : tasks) {
                String progress = task.total() > 0 ? " · " + Math.min(100, (int) (100.0 * task.bytes() / task.total())) + "%" : "";
                if (task.state().equals("complete") || task.state().equals("retried")) progress = "";
                adapter.add(task.file() + "\n" + text("state_" + task.state()) + progress
                        + (task.reason().isEmpty() ? "" : "\n" + reason(task.reason())));
            }
            status.setText(result.failed() ? text("operation_failed") : tasks.isEmpty()
                    ? text("download_empty") : text("download_limit"));
            main.postDelayed(refresh, 1500);
        }));
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

    @Override public void onResume() { super.onResume(); resumed = true; load(); }
    @Override public void onPause() {
        resumed = false; loading = false; generation++; main.removeCallbacks(refresh); super.onPause();
    }
    @Override public void onDestroyView() {
        adapter = null; status = null; tasks.clear(); super.onDestroyView();
    }
}
