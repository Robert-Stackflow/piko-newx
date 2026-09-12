package app.morphe.extension.newx.mediatools;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import app.morphe.extension.newx.settings.NewXSettingsUi;
import app.morphe.extension.newx.ui.Theme;
import app.morphe.extension.shared.StringRef;

/** Shared, host-themed controls. No dependence on Android's unthemed list/spinner layouts. */
public final class MediaToolsUi {
    private MediaToolsUi() {}
    public static String text(String key) { return StringRef.str("piko_newx_tools_" + key); }
    public static int dp(Context c, float value) { return Theme.dpToPx(c, value); }
    public static GradientDrawable shape(Context c, int color, int radius) {
        GradientDrawable result = new GradientDrawable();
        result.setColor(color); result.setCornerRadius(dp(c, radius)); return result;
    }
    public static void ripple(View view, int color, int radius) {
        Context c = view.getContext();
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(Theme.rippleColor(c)),
                shape(c, color, radius), shape(c, Color.WHITE, radius)));
    }
    public static TextView label(Context c, String value, int size, boolean secondary) {
        TextView label = secondary ? NewXSettingsUi.summaryText(c) : NewXSettingsUi.titleText(c);
        label.setText(value); label.setTextSize(size); return label;
    }
    public static TextView chip(Context c, String title, boolean selected) {
        TextView view = label(c, title, 14, false);
        view.setGravity(Gravity.CENTER); view.setSingleLine();
        view.setPadding(dp(c, 12), 0, dp(c, 12), 0);
        view.setMinHeight(dp(c, 44)); view.setClickable(true); view.setFocusable(true);
        select(view, selected); return view;
    }
    public static void select(TextView view, boolean selected) {
        Context c = view.getContext(); view.setSelected(selected);
        view.setTextColor(selected ? Theme.onPrimaryContainer(c) : Theme.secondaryText(c));
        ripple(view, selected ? Theme.primaryContainer(c) : Color.TRANSPARENT, 22);
    }
    public static LinearLayout column(Context c) {
        LinearLayout view = new LinearLayout(c); view.setOrientation(LinearLayout.VERTICAL); return view;
    }
    public static LinearLayout row(Context c) {
        LinearLayout view = new LinearLayout(c); view.setGravity(Gravity.CENTER_VERTICAL); return view;
    }
    public static LinearLayout.LayoutParams space(Context c, int width, int height, int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width < 0 ? width : dp(c, width),
                height < 0 ? height : dp(c, height)); p.bottomMargin = dp(c, bottom); return p;
    }
    public static TextView status(Context c) {
        TextView view = label(c, "", 12, true);
        view.setPadding(dp(c, 20), dp(c, 8), dp(c, 20), dp(c, 8)); return view;
    }
    public static LinearLayout card(Context c) {
        LinearLayout card = column(c);
        card.setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14));
        ripple(card, Theme.surfaceContainer(c), 0);
        return card;
    }
    public static FrameLayout wrapCard(Context c, View card) {
        FrameLayout outer = new FrameLayout(c);
        outer.addView(card, new FrameLayout.LayoutParams(-1, -2));
        View divider = new View(c); divider.setBackgroundColor(Theme.dividerColor(c));
        outer.addView(divider, new FrameLayout.LayoutParams(-1, Math.max(1, dp(c, .5f)), Gravity.BOTTOM));
        return outer;
    }
    public static void oneLine(TextView view) {
        view.setSingleLine(); view.setEllipsize(TextUtils.TruncateAt.END);
    }

    /** Small vector icons with a 48dp hit target and spoken/long-press labels. */
    public static final class Icon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String glyph;
        private int color;
        public Icon(Context c, String glyph, String description) {
            super(c); this.glyph = glyph; color = Theme.primaryText(c);
            setContentDescription(description); setTooltipText(description);
            setFocusable(true); setClickable(true); ripple(this, Color.TRANSPARENT, 24);
        }
        public void update(String glyph, String description, int color) {
            this.glyph = glyph; this.color = color;
            setContentDescription(description); setTooltipText(description); invalidate();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.save(); canvas.translate((getWidth() - dp(getContext(), 24)) / 2f,
                    (getHeight() - dp(getContext(), 24)) / 2f);
            float scale = getResources().getDisplayMetrics().density; canvas.scale(scale, scale);
            paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.8f);
            paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            switch (glyph) {
                case "more" -> {
                    paint.setStyle(Paint.Style.FILL);
                    canvas.drawCircle(5, 12, 1.7f, paint); canvas.drawCircle(12, 12, 1.7f, paint); canvas.drawCircle(19, 12, 1.7f, paint);
                }
                case "search" -> { canvas.drawCircle(10, 10, 6, paint); canvas.drawLine(15, 15, 21, 21, paint); }
                case "clear" -> { canvas.drawLine(6, 6, 18, 18, paint); canvas.drawLine(18, 6, 6, 18, paint); }
                case "delete" -> {
                    canvas.drawLine(4, 6, 20, 6, paint); canvas.drawLine(9, 3, 15, 3, paint);
                    canvas.drawRoundRect(6, 6, 18, 21, 2, 2, paint);
                    canvas.drawLine(10, 10, 10, 17, paint); canvas.drawLine(14, 10, 14, 17, paint);
                }
                case "lock", "unlock" -> {
                    canvas.drawRoundRect(5, 10, 19, 21, 2, 2, paint);
                    canvas.drawArc(8, 2, 16, 14, 180, glyph.equals("lock") ? 180 : 135, false, paint);
                    canvas.drawLine(12, 14, 12, 17, paint);
                }
                case "play" -> {
                    Path p = new Path(); p.moveTo(9, 5); p.lineTo(19, 12); p.lineTo(9, 19); p.close();
                    paint.setStyle(Paint.Style.FILL); canvas.drawPath(p, paint);
                }
                default -> {
                    canvas.drawRoundRect(3, 3, 21, 21, 3, 3, paint); canvas.drawCircle(8, 8, 1, paint);
                    Path p = new Path(); p.moveTo(4, 18); p.lineTo(10, 12); p.lineTo(14, 16); p.lineTo(18, 11);
                    p.lineTo(21, 14); canvas.drawPath(p, paint);
                }
            }
            canvas.restore();
        }
    }
}
