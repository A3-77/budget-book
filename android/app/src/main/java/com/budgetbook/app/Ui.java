package com.budgetbook.app;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

/** 建视图的小工具，省得每处都写一长串 LayoutParams */
class Ui {

    private static float density = 2f;

    static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
    }

    static int dp(float v) {
        return Math.round(v * density);
    }

    static int screenW(Context c) {
        return c.getResources().getDisplayMetrics().widthPixels;
    }

    /* ---------- 数值格式 ---------- */
    static String money(double v) {
        double r = Math.round(v * 100.0) / 100.0;
        if (r == Math.rint(r) && Math.abs(r) < 1e15) {
            return String.format(Locale.CHINA, "%,d", (long) r);
        }
        String s = String.format(Locale.CHINA, "%,.2f", r);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        return s;
    }

    /** 日历格子很窄，用缩写 */
    static String moneyCell(double v) {
        long r = Math.round(v);
        if (r >= 10000) {
            String s = String.format(Locale.CHINA, "%.1f", r / 10000.0);
            if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
            return s + "万";
        }
        return String.format(Locale.CHINA, "%,d", r);
    }

    /* ---------- 背景 ---------- */
    static GradientDrawable bg(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    static GradientDrawable bgStroke(int color, int strokeColor, float strokeDp, float radiusDp) {
        GradientDrawable d = bg(color, radiusDp);
        d.setStroke(dp(strokeDp), strokeColor);
        return d;
    }

    static GradientDrawable grad(int from, int to, float radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{from, to});
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    static GradientDrawable topRounded(int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        float r = dp(radiusDp);
        d.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        return d;
    }

    /* ---------- 基础控件 ---------- */
    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static TextView tv(Context c, CharSequence s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    static TextView tv(Context c, CharSequence s, float sp, int color, boolean bold) {
        TextView t = tv(c, s, sp, color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    static LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams matchW() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static void margins(View v, float l, float t, float r, float b) {
        ViewGroup.LayoutParams raw = v.getLayoutParams();
        if (!(raw instanceof LinearLayout.LayoutParams)) raw = matchW();
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) raw;
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        v.setLayoutParams(lp);
    }

    static void pad(View v, float l, float t, float r, float b) {
        v.setPadding(dp(l), dp(t), dp(r), dp(b));
    }

    /** 让圆角卡片有按压水波纹 */
    static void tappable(Context c, View v) {
        TypedValue out = new TypedValue();
        if (c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, out, true)) {
            Drawable d = c.getDrawable(out.resourceId);
            if (d != null) {
                v.setForeground(d);
                v.setClickable(true);
            }
        }
        if (v.getBackground() instanceof GradientDrawable) {
            v.setClipToOutline(true);
        }
    }

    static LinearLayout card(Context c, Palette p) {
        LinearLayout l = col(c);
        l.setBackground(bg(p.surface, 20));
        l.setElevation(dp(2));
        return l;
    }

    /** 进度条：用一个横向 LinearLayout 按权重分两段 */
    static LinearLayout progress(Context c, double pct, int trackColor, Drawable fill, float heightDp) {
        LinearLayout bar = row(c);
        bar.setBackground(bg(trackColor, heightDp / 2f));
        bar.setWeightSum(100f);
        float v = (float) Math.max(0.6, Math.min(100, pct));
        View f = new View(c);
        f.setBackground(fill);
        bar.addView(f, lp(0, dp(heightDp), v));
        View rest = new View(c);
        bar.addView(rest, lp(0, dp(heightDp), 100f - v));
        return bar;
    }

    /** 把一堆 chip 按可用宽度折行摆放（不引 androidx 的 FlowLayout） */
    static LinearLayout wrapChips(Context c, List<View> chips, int maxWidthPx, int gapPx, int rowGapPx) {
        LinearLayout col = col(c);
        LinearLayout cur = null;
        int used = 0;
        for (View v : chips) {
            v.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int w = v.getMeasuredWidth();
            int need = (used == 0 ? w : used + gapPx + w);
            if (cur == null || need > maxWidthPx) {
                cur = row(c);
                cur.setGravity(Gravity.START);
                LinearLayout.LayoutParams clp = matchW();
                if (col.getChildCount() > 0) clp.topMargin = rowGapPx;
                col.addView(cur, clp);
                used = 0;
                need = w;
            }
            LinearLayout.LayoutParams vlp = wrap();
            if (used > 0) vlp.leftMargin = gapPx;
            cur.addView(v, vlp);
            used = need;
        }
        return col;
    }

    /** 一个胶囊标签（chip） */
    static TextView chip(Context c, Palette p, String text, boolean on) {
        TextView t = tv(c, text, 12.5f, on ? p.brandInk : p.text2, on);
        t.setBackground(on
                ? bgStroke(mixAlpha(p.brand, 0.13f, p.surface), p.brand, 1f, 12)
                : bgStroke(p.surface2, p.line, 1f, 12));
        pad(t, 12, 9, 12, 9);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    static TextView emojiChip(Context c, Palette p, String emoji, boolean on) {
        TextView t = tv(c, emoji, 18f, p.text);
        t.setBackground(on
                ? bgStroke(mixAlpha(p.brand, 0.13f, p.surface), p.brand, 1f, 12)
                : bgStroke(p.surface2, p.line, 1f, 12));
        pad(t, 9, 6, 9, 6);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    /** 把 overlay 按透明度叠在 base 上 */
    static int mixAlpha(int overlay, float a, int base) {
        int or = (overlay >> 16) & 0xFF, og = (overlay >> 8) & 0xFF, ob = overlay & 0xFF;
        int br = (base >> 16) & 0xFF, bg = (base >> 8) & 0xFF, bb = base & 0xFF;
        int r = Math.round(br + (or - br) * a);
        int g = Math.round(bg + (og - bg) * a);
        int b = Math.round(bb + (ob - bb) * a);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static int alpha(int color, float a) {
        int x = Math.round(255 * Math.max(0, Math.min(1, a)));
        return (color & 0x00FFFFFF) | (x << 24);
    }
}
