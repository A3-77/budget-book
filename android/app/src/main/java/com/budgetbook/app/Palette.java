package com.budgetbook.app;

import android.content.Context;
import android.content.res.Configuration;

/**
 * 配色。没有用 res/values-night，而是在代码里算，
 * 这样「跟随系统 / 强制浅色 / 强制深色」三种偏好都能立刻生效。
 * 色值照搬网页版那套。
 */
class Palette {

    boolean dark;
    int bg, surface, surface2, text, text2, text3, line;
    int brand, brand2, brandInk, mint, amber, rose, segOn;
    int onBrand = 0xFFFFFFFF;

    static Palette light() {
        Palette p = new Palette();
        p.dark = false;
        p.bg = 0xFFF4F5FA;
        p.surface = 0xFFFFFFFF;
        p.surface2 = 0xFFEEF0F7;
        p.text = 0xFF111420;
        p.text2 = 0xFF697086;
        p.text3 = 0xFF9AA1B3;
        p.line = 0xFFE7E9F2;
        p.brand = 0xFF5B5BD6;
        p.brand2 = 0xFF8B7CF6;
        p.brandInk = 0xFF5B5BD6;
        p.mint = 0xFF10B981;
        p.amber = 0xFFF59E0B;
        p.rose = 0xFFF43F5E;
        p.segOn = 0xFFFFFFFF;
        return p;
    }

    static Palette dark() {
        Palette p = new Palette();
        p.dark = true;
        p.bg = 0xFF0A0C12;
        p.surface = 0xFF141824;
        p.surface2 = 0xFF1C2130;
        p.text = 0xFFEEF1F8;
        p.text2 = 0xFF98A0B5;
        p.text3 = 0xFF6D768C;
        p.line = 0xFF252C3D;
        p.brand = 0xFF5B5BD6;
        p.brand2 = 0xFF8B7CF6;
        p.brandInk = 0xFFB3A8FF;
        p.mint = 0xFF10B981;
        p.amber = 0xFFFBBF24;
        p.rose = 0xFFF43F5E;
        p.segOn = 0xFF2C3448;
        return p;
    }

    static Palette of(Context c, String pref) {
        boolean d;
        if ("dark".equals(pref)) {
            d = true;
        } else if ("light".equals(pref)) {
            d = false;
        } else {
            int mode = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            d = mode == Configuration.UI_MODE_NIGHT_YES;
        }
        return d ? dark() : light();
    }

    String nextPref() {
        if (dark) return "light";
        return "dark";
    }

    /** 在半透明底上混一点色，用来做 hint 底色 */
    static int mix(int base, int overlay, float a) {
        int br = (base >> 16) & 0xFF, bgc = (base >> 8) & 0xFF, bb = base & 0xFF;
        int or = (overlay >> 16) & 0xFF, og = (overlay >> 8) & 0xFF, ob = overlay & 0xFF;
        int r = Math.round(br + (or - br) * a);
        int g = Math.round(bgc + (og - bgc) * a);
        int b = Math.round(bb + (ob - bb) * a);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
