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
import java.util.ArrayList;
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
    private Icon filter;
    private int generation, selected;
    private boolean alive;
    private final Runnable refresh = () -> load(true);

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        Context c = getActivity(); alive = true; previews = new MediaPreviewLoader();
        LinearLayout root = column(c); root.setBackgroundColor(Theme.surfaceContainer(c));
        LinearLayout controls = column(c);
        controls.setPadding(dp(c, 16), dp(c, 12), dp(c, 12), dp(c, 12));
        LinearLayout searchRow = row(c);
        LinearLayout searchBar = row(c);
        searchBar.setBackground(shape(c, Theme.blend(Theme.surfaceContainer(c), Theme.primaryText(c), .065f), 26));
        Icon searchIcon = new Icon(c, "search", text("history_search"));
        searchIcon.setClickable(false); searchIcon.setFocusable(false);
        searchIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        searchBar.addView(searchIcon, space(c, 40, 48, 0));
        search = NewXSettingsUi.textInput(c, text("history_search"), android.text.InputType.TYPE_CLASS_TEXT);
        search.setSingleLine(); search.setTextSize(15); search.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        search.setPadding(0, dp(c, 8), 0, dp(c, 8)); search.setMinHeight(dp(c, 52));
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchBar.addView(search, new LinearLayout.LayoutParams(0, -2, 1));
        Icon reset = new Icon(c, "clear", text("search_clear")); reset.setVisibility(View.GONE);
        searchBar.addView(reset, space(c, 40, 48, 0)); reset.setOnClickListener(v -> search.setText(""));
        searchRow.addView(searchBar, new LinearLayout.LayoutParams(0, -2, 1));
        filter = new Icon(c, "all", "");
        LinearLayout.LayoutParams filterParams = space(c, 48, 48, 0); filterParams.setMarginStart(dp(c, 4));
        searchRow.addView(filter, filterParams); updateFilter();
        filter.setOnClickListener(v -> {
            selected = HistoryPresentation.nextMode(selected); updateFilter();
            main.removeCallbacks(refresh); load(true);
            Utils.showToastShort(text(HistoryPresentation.label(selected)));
        });
        controls.addView(searchRow); root.addView(controls);
        status = label(c, "", 14, true); status.setGravity(Gravity.CENTER);
        status.setPadding(dp(c, 32), dp(c, 24), dp(c, 32), dp(c, 24));
        root.addView(status, new LinearLayout.LayoutParams(-1, 0, 1));
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
        list.setAdapter(adapter); list.setEmptyView(status); root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
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
                reset.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                generation++; main.removeCallbacks(refresh); main.postDelayed(refresh, 250);
            }
            public void afterTextChanged(Editable e) {}
        });
        reset.setVisibility(search.length() == 0 ? View.GONE : View.VISIBLE);
        return root;
    }

    private void updateFilter() {
        String current = text(HistoryPresentation.label(selected));
        String next = text(HistoryPresentation.label(HistoryPresentation.nextMode(selected)));
        filter.update(HistoryPresentation.glyph(selected), String.format(text("history_mode"), current, next),
                selected == 0 ? Theme.primaryText(filter.getContext()) : Theme.primaryAccent(filter.getContext()));
        filter.setSelected(selected != 0);
    }

    private final class HistoryRow {
        final FrameLayout outer;
        final TextView author, handle, date, body;
        final ImageView avatar;
        final MediaGrid media;
        String boundPreviews;
        HistoryRow(Context c) {
            LinearLayout card = card(c); card.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
            outer = wrapCard(c, card); outer.setTag(this);
            LinearLayout content = row(c); content.setGravity(Gravity.TOP);
            card.addView(content);
            FrameLayout portrait = new FrameLayout(c);
            portrait.setBackground(shape(c, Theme.surfaceContainerHigh(c), 24)); portrait.setClipToOutline(true);
            Icon placeholder = new Icon(c, "person", text("author_avatar"));
            placeholder.setClickable(false); placeholder.setFocusable(false);
            placeholder.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            portrait.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
            avatar = new ImageView(c); avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
            avatar.setContentDescription(text("author_avatar"));
            portrait.addView(avatar, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout.LayoutParams portraitParams = space(c, 48, 48, 0); portraitParams.setMarginEnd(dp(c, 12));
            content.addView(portrait, portraitParams);
            LinearLayout textColumn = column(c);
            content.addView(textColumn, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout heading = row(c);
            author = label(c, "", 15, false); oneLine(author);
            author.setMaxWidth(dp(c, 160));
            author.setTypeface(android.graphics.Typeface.create(author.getTypeface(), android.graphics.Typeface.BOLD));
            heading.addView(author, space(c, -2, -2, 0));
            handle = label(c, "", 13, true); oneLine(handle); handle.setPadding(dp(c, 6), 0, 0, 0);
            heading.addView(handle, new LinearLayout.LayoutParams(0, -2, 1));
            textColumn.addView(heading, space(c, -1, -2, 3));
            body = label(c, "", 15, false); body.setMaxLines(6); body.setEllipsize(TextUtils.TruncateAt.END);
            body.setTypeface(android.graphics.Typeface.create(body.getTypeface(), android.graphics.Typeface.NORMAL));
            body.setLineSpacing(dp(c, 2), 1); textColumn.addView(body, space(c, -1, -2, 8));
            media = new MediaGrid(c); textColumn.addView(media, space(c, -1, -2, 0));
            date = label(c, "", 12, true); date.setPadding(0, dp(c, 8), 0, 0); textColumn.addView(date);
        }
        void bind(MediaHistoryStore.Entry entry) {
            String screenName = entry.author().isEmpty() ? "" : "@" + entry.author();
            author.setText(!entry.displayName().isEmpty() ? entry.displayName() : !screenName.isEmpty() ? screenName : text("history_posts"));
            handle.setText(screenName); handle.setVisibility(entry.displayName().isEmpty() || screenName.isEmpty() ? View.GONE : View.VISIBLE);
            previews.load(avatar, MediaPreviewLoader.safeRemote(entry.avatar()), false);
            date.setText(String.format(text("history_seen"), android.text.format.DateUtils.getRelativeTimeSpanString(
                    entry.visited(), System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)));
            body.setText(entry.text()); body.setVisibility(entry.text().isEmpty() ? View.GONE : View.VISIBLE);
            if (entry.previews().equals(boundPreviews)) return;
            boundPreviews = entry.previews(); JSONArray array;
            try { array = new JSONArray(entry.previews()); } catch (Exception ignored) { array = new JSONArray(); }
            media.bind(array);
        }
    }

    /** X-style 1/2/3/4-image mosaic, clipped once at the outside edge. */
    private final class MediaGrid extends ViewGroup {
        final ImageView[] images = new ImageView[4];
        final Icon[] markers = new Icon[4];
        int count;
        boolean singleVideo;
        MediaGrid(Context c) {
            super(c); setBackground(shape(c, Theme.surfaceContainerHigh(c), 16)); setClipToOutline(true);
            for (int i = 0; i < 4; i++) {
                FrameLayout frame = new FrameLayout(c);
                images[i] = new ImageView(c); images[i].setScaleType(ImageView.ScaleType.CENTER_CROP);
                frame.addView(images[i], new FrameLayout.LayoutParams(-1, -1));
                markers[i] = new Icon(c, "play", text("video_cover"));
                markers[i].update("play", text("video_cover"), android.graphics.Color.WHITE);
                markers[i].setClickable(false); markers[i].setFocusable(false);
                markers[i].setBackground(shape(c, 0x99000000, 16));
                FrameLayout.LayoutParams badge = new FrameLayout.LayoutParams(dp(c, 32), dp(c, 32), Gravity.BOTTOM | Gravity.START);
                badge.setMargins(dp(c, 8), dp(c, 8), dp(c, 8), dp(c, 8)); frame.addView(markers[i], badge);
                addView(frame, new ViewGroup.LayoutParams(-1, -1));
            }
        }
        void bind(JSONArray array) {
            count = Math.min(4, array.length()); setVisibility(count == 0 ? View.GONE : View.VISIBLE);
            singleVideo = false;
            for (int i = 0; i < 4; i++) {
                getChildAt(i).setVisibility(i < count ? View.VISIBLE : View.GONE);
                if (i >= count) { images[i].setTag(null); images[i].setImageDrawable(null); continue; }
                JSONObject item = array.optJSONObject(i);
                boolean video = item != null && item.optBoolean("video", false);
                if (i == 0) singleVideo = video;
                markers[i].setVisibility(video ? View.VISIBLE : View.GONE);
                images[i].setContentDescription(text(video ? "video_cover" : "media_preview"));
                previews.load(images[i], item == null ? "" : MediaPreviewLoader.safeRemote(item.optString("url", "")), false);
            }
            requestLayout();
        }
        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = MeasureSpec.getSize(widthSpec);
            float ratio = singleVideo ? 16f / 9f : 4f / 3f;
            android.graphics.drawable.Drawable drawable = images[0].getDrawable();
            if (count == 1 && drawable != null && drawable.getIntrinsicHeight() > 0)
                ratio = Math.max(.8f, Math.min(1.91f, drawable.getIntrinsicWidth() / (float) drawable.getIntrinsicHeight()));
            int height = count == 0 ? 0 : Math.round(width / (count == 1 ? ratio : 1.5f));
            setMeasuredDimension(width, height);
            if (width == 0 || height == 0) return;
            for (int i = 0; i < count; i++) {
                int[] r = HistoryPresentation.tile(count, i, width, height, dp(getContext(), 2));
                getChildAt(i).measure(MeasureSpec.makeMeasureSpec(r[2] - r[0], MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(r[3] - r[1], MeasureSpec.EXACTLY));
            }
        }
        @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            if (right <= left || bottom <= top) return;
            for (int i = 0; i < count; i++) {
                int[] r = HistoryPresentation.tile(count, i, right - left, bottom - top, dp(getContext(), 2));
                getChildAt(i).layout(r[0], r[1], r[2], r[3]);
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
        String kind = HistoryPresentation.kind(selected);
        MediaHistoryStore.query(kind, search.getText().toString(), result -> main.post(() -> {
            if (!alive || request != generation) return;
            int first = list.getFirstVisiblePosition(); View child = list.getChildAt(0); int top = child == null ? 0 : child.getTop();
            entries.clear(); entries.addAll(result.entries()); adapter.notifyDataSetChanged();
            if (resetPosition) list.setSelection(0); else list.setSelectionFromTop(first, top);
            status.setText(result.failed() ? text("operation_failed") : text("history_empty"));
        }));
    }
    @Override public void onResume() {
        super.onResume();
        if (getActivity() instanceof NewXSettingsActivity host) {
            host.setPageTitle(text("history_title")); host.setPatchVersionFooterVisible(false);
            Icon clear = new Icon(host, "delete", text("history_clear"));
            clear.setOnClickListener(view -> confirmClear()); host.setPageAction(clear);
        }
        load(false);
    }
    @Override public void onPause() {
        if (getActivity() instanceof NewXSettingsActivity host) host.setPageAction(null);
        super.onPause();
    }
    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putInt("filter", selected);
        if (search != null) state.putString("query", search.getText().toString());
    }
    @Override public void onCreate(Bundle state) { super.onCreate(state); if (state != null) selected = HistoryPresentation.mode(state.getInt("filter", 0)); }
    @Override public void onDestroyView() {
        alive = false; generation++; main.removeCallbacksAndMessages(null); previews.close(); previews = null;
        entries.clear(); adapter = null; status = null; search = null; list = null;
        filter = null; super.onDestroyView();
    }
}
