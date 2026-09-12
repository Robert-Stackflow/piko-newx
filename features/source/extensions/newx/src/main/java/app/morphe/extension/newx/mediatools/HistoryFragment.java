package app.morphe.extension.newx.mediatools;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import app.morphe.extension.newx.settings.NewXCustomScreenFragment;
import app.morphe.extension.newx.settings.NewXSettingsUi;
import app.morphe.extension.newx.settings.SettingsNode;
import app.morphe.extension.newx.settings.SettingsRegistry;
import app.morphe.extension.shared.StringRef;
import app.morphe.extension.shared.Utils;

/** Local history only. Viewing this screen never starts a background network request. */
@SuppressWarnings("deprecation")
public final class HistoryFragment extends NewXCustomScreenFragment {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<MediaHistoryStore.Entry> entries = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private TextView status;
    private EditText search;
    private Spinner filter;
    private int generation;
    private boolean alive;
    private final Runnable refresh = this::load;

    private static String text(String key) { return StringRef.str("piko_newx_tools_" + key); }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        Context context = getActivity();
        alive = true;
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NewXSettingsUi.backgroundColor(context));
        NewXSettingsUi.SwitchRow enabled = NewXSettingsUi.switchRow(context,
                text("history_enabled_title"), text("history_enabled_summary"), MediaHistoryStore.enabled());
        enabled.setOnCheckedChangeListener(value -> {
            MediaHistoryStore.invalidatePending();
            if (!saveEnabled(SettingsRegistry.catalog(), value)) Utils.showToastShort(text("operation_failed"));
            MediaHistoryRuntime.resetVisits();
            load();
        });
        root.addView(enabled, new LinearLayout.LayoutParams(-1, -2));

        search = new EditText(context);
        search.setSingleLine(true);
        search.setHint(text("history_search"));
        root.addView(search, new LinearLayout.LayoutParams(-1, -2));
        filter = new Spinner(context);
        filter.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item,
                new String[]{text("history_all"), text("history_posts"), text("history_videos")}));
        root.addView(filter, new LinearLayout.LayoutParams(-1, -2));
        status = new TextView(context);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        ListView list = new ListView(context);
        adapter = new ArrayAdapter<>(context, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener((ignored, view, index, id) -> {
            if (index >= entries.size()) return;
            MediaHistoryStore.Entry entry = entries.get(index);
            // Canonical post link is stable; it does not pretend that the media URL is permanent.
            Uri uri = Uri.parse("https://x.com/i/status/" + entry.post());
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri).setPackage(context.getPackageName())); }
            catch (RuntimeException error) { Utils.showToastShort(text("open_failed")); }
        });

        Button clear = new Button(context);
        clear.setText(text("history_clear"));
        root.addView(clear, new LinearLayout.LayoutParams(-1, -2));
        clear.setOnClickListener(view -> new AlertDialog.Builder(context)
                .setMessage(text("history_clear_confirm"))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    generation++;
                    MediaHistoryStore.clear(success -> main.post(() -> {
                        if (!alive) return;
                        if (!success) Utils.showToastShort(text("operation_failed"));
                        load();
                    }));
                }).show());

        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                generation++;
                main.removeCallbacks(refresh);
                main.postDelayed(refresh, 250);
            }
            public void afterTextChanged(Editable e) {}
        });
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> p, View v, int position, long id) { load(); }
            public void onNothingSelected(AdapterView<?> p) {}
        });
        return root;
    }

    @Override public void onResume() { super.onResume(); load(); }

    private void load() {
        if (!alive || search == null || filter == null) return;
        int request = ++generation;
        String kind = filter.getSelectedItemPosition() == 1 ? "post"
                : filter.getSelectedItemPosition() == 2 ? "video" : "";
        MediaHistoryStore.query(kind, search.getText().toString(), result -> main.post(() -> {
            if (!alive || request != generation) return;
            entries.clear(); entries.addAll(result.entries()); adapter.clear();
            DateFormat date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);
            for (MediaHistoryStore.Entry entry : entries) {
                String label = entry.kind().equals("video") ? text("history_videos") : text("history_posts");
                adapter.add(label + " · @" + entry.author() + " · " + date.format(new Date(entry.visited()))
                        + "\n" + entry.text() + "\n" + entry.post());
            }
            status.setText(result.failed() ? text("operation_failed")
                    : entries.isEmpty() ? text("history_empty") : text("history_limit"));
        }));
    }

    private static boolean saveEnabled(List<? extends SettingsNode> nodes, boolean value) {
        for (SettingsNode node : nodes) {
            if (node instanceof SettingsNode.Toggle toggle && node.id.equals(MediaHistoryStore.ENABLED)) {
                toggle.setting.save(value);
                return true;
            }
            if (node instanceof SettingsNode.Group group && saveEnabled(group.children, value)) return true;
        }
        return false;
    }

    @Override public void onDestroyView() {
        alive = false; generation++; main.removeCallbacksAndMessages(null);
        entries.clear(); adapter = null; status = null; search = null; filter = null;
        super.onDestroyView();
    }
}
