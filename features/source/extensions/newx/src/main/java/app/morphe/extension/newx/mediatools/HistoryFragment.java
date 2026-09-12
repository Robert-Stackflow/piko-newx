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
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import app.morphe.extension.newx.settings.NewXCustomScreenFragment;
import app.morphe.extension.newx.settings.NewXSettingsActivity;
import app.morphe.extension.newx.settings.NewXSettingsUi;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.shared.Utils;
import static app.morphe.extension.newx.mediatools.MediaToolsUi.*;

/** Private history with on-demand covers. The recording switch lives in settings only. */
@SuppressWarnings("deprecation")
public final class HistoryFragment extends NewXCustomScreenFragment {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<MediaHistoryStore.Entry> entries = new ArrayList<>();
    private BaseAdapter adapter;
    private MediaPreviewLoader previews;
    private TextView status;
    private EditText search;
    private ListView list;
    private final TextView[] chips = new TextView[3];
    private int generation, selected;
    private boolean alive;
    private final Runnable refresh = () -> load(true);

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        Context c = getActivity(); alive = true; previews = new MediaPreviewLoader();
        LinearLayout root = column(c); root.setBackgroundColor(Theme.surfaceContainer(c));
        LinearLayout controls = column(c);
        controls.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), 0);
        LinearLayout searchBar = row(c);
        searchBar.setBackground(shape(c, Theme.blend(Theme.surfaceContainer(c), Theme.primaryText(c), .065f), 26));
        Icon searchIcon = new Icon(c, "search", text("history_search"));
        searchIcon.setClickable(false); searchIcon.setFocusable(false);
        searchIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        searchBar.addView(searchIcon, space(c, 48, 48, 0));
        search = NewXSettingsUi.textInput(c, text("history_search"), android.text.InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(); search.setTextSize(15); search.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        search.setPadding(0, dp(c, 8), 0, dp(c, 8)); search.setMinHeight(dp(c, 52));
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBar.addView(search, new LinearLayout.LayoutParams(0, -2, 1));
        Icon reset = new Icon(c, "clear", text("search_clear")); reset.setVisibility(View.INVISIBLE);
        searchBar.addView(reset, space(c, 48, 48, 0)); reset.setOnClickListener(v -> search.setText(""));
        controls.addView(searchBar, space(c, -1, -2, 12));
        LinearLayout options = row(c), filters = row(c);
        filters.setBackground(shape(c, Theme.blend(Theme.surfaceContainer(c), Theme.primaryText(c), .035f), 24));
        String[] labels = {"history_all", "history_posts", "history_videos"};
        for (int i = 0; i < labels.length; i++) {
            final int index = i; chips[i] = chip(c, text(labels[i]), selected == i);
            filters.addView(chips[i], new LinearLayout.LayoutParams(0, dp(c, 44), 1));
            chips[i].setOnClickListener(v -> {
                if (selected == index) return; selected = index;
                for (int j = 0; j < chips.length; j++) select(chips[j], selected == j);
                load(true);
            });
        }
        options.addView(filters, new LinearLayout.LayoutParams(0, -2, 1));
        Icon clear = new Icon(c, "delete", text("history_clear"));
        LinearLayout.LayoutParams clearParams = space(c, 48, 48, 0); clearParams.setMarginStart(dp(c, 8));
        options.addView(clear, clearParams); clear.setOnClickListener(v -> confirmClear());
        controls.addView(options); root.addView(controls);
        status = status(c); root.addView(status);
        list = new ListView(c); list.setDivider(null); list.setSelector(android.R.color.transparent);
        list.setClipToPadding(false); list.setPadding(0, 0, 0, dp(c, 12));
        adapter = new BaseAdapter() {
            public int getCount() { return entries.size(); }
            public Object getItem(int p) { return entries.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p, View recycled, ViewGroup parentView) {
                HistoryRow row = recycled != null && recycled.getTag() instanceof HistoryRow previous
                        ? previous : new HistoryRow(c);
                row.bind(entries.get(p)); return row.outer;
            }
        };
        list.setAdapter(adapter); root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setOnItemClickListener((p, view, index, id) -> {
            if (index >= entries.size()) return;
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://x.com/i/status/" + entries.get(index).post()))
                    .setPackage(c.getPackageName())); }
            catch (RuntimeException error) { Utils.showToastShort(text("open_failed")); }
        });
        if (state != null) search.setText(state.getString("query", ""));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                reset.setVisibility(s.length() == 0 ? View.INVISIBLE : View.VISIBLE);
                generation++; main.removeCallbacks(refresh); main.postDelayed(refresh, 250);
            }
            public void afterTextChanged(Editable e) {}
        });
        reset.setVisibility(search.length() == 0 ? View.INVISIBLE : View.VISIBLE);
        return root;
    }

    private final class HistoryRow {
        final FrameLayout outer;
        final TextView author, date, body;
        final LinearLayout media;
        final ImageView[] images = new ImageView[4];
        final FrameLayout[] frames = new FrameLayout[4];
        final Icon[] markers = new Icon[4];
        String boundPreviews;
        HistoryRow(Context c) {
            LinearLayout card = card(c); outer = wrapCard(c, card); outer.setTag(this);
            LinearLayout heading = row(c);
            author = label(c, "", 15, false); oneLine(author);
            heading.addView(author, new LinearLayout.LayoutParams(0, -2, 1));
            date = label(c, "", 12, true);
            date.setPadding(dp(c, 8), 0, 0, 0); heading.addView(date);
            card.addView(heading, space(c, -1, -2, 8));
            body = label(c, "", 15, false); body.setMaxLines(4); body.setEllipsize(TextUtils.TruncateAt.END);
            card.addView(body, space(c, -1, -2, 8));
            media = row(c); card.addView(media);
            for (int i = 0; i < 4; i++) {
                FrameLayout frame = new FrameLayout(c); frames[i] = frame;
                frame.setBackground(shape(c, Theme.surfaceContainerHigh(c), 12)); frame.setClipToOutline(true);
                images[i] = new ImageView(c); images[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
                frame.addView(images[i], new FrameLayout.LayoutParams(-1, -1));
                markers[i] = new Icon(c, "image", text("media_preview"));
                markers[i].setClickable(false); markers[i].setFocusable(false);
                markers[i].setBackground(shape(c, 0x88000000, 20));
                FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(dp(c, 36), dp(c, 36), Gravity.BOTTOM | Gravity.END);
                badge.setMargins(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 8)); frame.addView(markers[i], badge);
                LinearLayout.LayoutParams item = new LinearLayout.LayoutParams(0, dp(c, 126), 1);
                if (i > 0) item.setMarginStart(dp(c, 6)); media.addView(frame, item);
            }
        }
        void bind(MediaHistoryStore.Entry entry) {
            author.setText(entry.author().isEmpty() ? text("history_posts") : "@" + entry.author());
            date.setText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(entry.visited())));
            body.setText(entry.text().isEmpty() ? text(entry.kind().equals("video") ? "history_videos" : "history_posts") : entry.text());
            if (entry.previews().equals(boundPreviews)) return;
            boundPreviews = entry.previews(); JSONArray array;
            try { array = new JSONArray(entry.previews()); } catch (Exception ignored) { array = new JSONArray(); }
            int count = Math.min(4, array.length()); media.setVisibility(count == 0 ? View.GONE : View.VISIBLE);
            for (int i = 0; i < 4; i++) {
                frames[i].setVisibility(i < count ? View.VISIBLE : View.GONE);
                if (i >= count) { images[i].setTag(null); images[i].setImageDrawable(null); continue; }
                JSONObject item = array.optJSONObject(i); String url = item == null ? "" : item.optString("url", "");
                boolean video = item != null && item.optBoolean("video", false);
                frames[i].getLayoutParams().height = dp(frames[i].getContext(), count == 1 ? 190 : 126);
                markers[i].update(video ? "play" : "image", text(video ? "history_videos" : "media_preview"), android.graphics.Color.WHITE);
                images[i].setContentDescription(text(video ? "video_cover" : "media_preview"));
                previews.load(images[i], url, false);
            }
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(getActivity()).setTitle(text("history_clear")).setMessage(text("history_clear_confirm"))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    generation++;
                    MediaHistoryStore.clear(success -> main.post(() -> {
                        if (!alive) return;
                        if (!success) Utils.showToastShort(text("operation_failed"));
                        previews.clear(); load(true);
                    }));
                }).show();
    }
    private void load(boolean resetPosition) {
        if (!alive || search == null) return;
        int request = ++generation;
        String kind = selected == 1 ? "post" : selected == 2 ? "video" : "";
        MediaHistoryStore.query(kind, search.getText().toString(), result -> main.post(() -> {
            if (!alive || request != generation) return;
            int first = list.getFirstVisiblePosition(); View child = list.getChildAt(0); int top = child == null ? 0 : child.getTop();
            entries.clear(); entries.addAll(result.entries()); adapter.notifyDataSetChanged();
            if (resetPosition) list.setSelection(0); else list.setSelectionFromTop(first, top);
            status.setText(result.failed() ? text("operation_failed") : entries.isEmpty() ? text("history_empty")
                    : String.format(text("history_count"), entries.size()));
        }));
    }
    @Override public void onResume() {
        super.onResume();
        if (getActivity() instanceof NewXSettingsActivity host) {
            host.setPageTitle(text("history_title")); host.setPatchVersionFooterVisible(false);
        }
        load(false);
    }
    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putInt("filter", selected);
        if (search != null) state.putString("query", search.getText().toString());
    }
    @Override public void onCreate(Bundle state) { super.onCreate(state); if (state != null) selected = state.getInt("filter", 0); }
    @Override public void onDestroyView() {
        alive = false; generation++; main.removeCallbacksAndMessages(null); previews.close(); previews = null;
        entries.clear(); adapter = null; status = null; search = null; list = null;
        java.util.Arrays.fill(chips, null); super.onDestroyView();
    }
}
