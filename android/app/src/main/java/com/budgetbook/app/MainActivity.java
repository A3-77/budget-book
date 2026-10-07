package com.budgetbook.app;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * 预算本 —— 纯原生界面（不套 WebView）。
 * 只有一页：顶部一行（月份 + 工具）→ 大卡片 → 汇总 → 分类列表 → 流水标签。
 * 日历页已删掉，按天看账在流水的日期分组里就够用了。
 */
public class MainActivity extends Activity {

    private static final int REQ_EXPORT = 1;
    private static final int REQ_IMPORT = 2;

    private Store store;
    private Palette p;
    private FrameLayout root;
    private ScrollView scroll;
    private LinearLayout content;
    private TextView fab;

    private String ym;
    /** 下面显示哪个：plan = 预算规划，ledger = 支出流水，schedule = 课程表 */
    private String view = "plan";
    /** 课程表正在看第几周 */
    private int week = Courses.currentWeek();

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(0xFF5B5BD6);

        Ui.init(this);
        store = new Store(this);
        p = Palette.of(this, store.theme);
        ym = Store.ymNow();

        buildShell();
        render();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("ym", ym);
        out.putString("view", view);
        out.putInt("week", week);
    }

    @Override
    protected void onRestoreInstanceState(Bundle saved) {
        super.onRestoreInstanceState(saved);
        ym = saved.getString("ym", Store.ymNow());
        view = saved.getString("view", "plan");
        week = saved.getInt("week", Courses.currentWeek());
        render();
    }

    /* ================= 骨架 ================= */

    private void buildShell() {
        root = new FrameLayout(this);
        root.setBackgroundColor(p.bg);

        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        content = Ui.col(this);
        scroll.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        fab = Ui.tv(this, "＋", 28, 0xFFFFFFFF);
        fab.setGravity(Gravity.CENTER);
        fab.setBackground(Ui.grad(p.brand, p.brand2, 22));
        fab.setElevation(Ui.dp(10));
        fab.setOnClickListener(v -> Sheets.record(this, store, p, ym, null, null, this::render));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(Ui.dp(60), Ui.dp(60));
        flp.gravity = Gravity.BOTTOM | Gravity.END;
        flp.rightMargin = Ui.dp(20);
        flp.bottomMargin = Ui.dp(26);
        root.addView(fab, flp);

        setContentView(root);
    }

    /* ================= 渲染 ================= */

    private void render() {
        int keepY = scroll.getScrollY();
        content.removeAllViews();
        content.setBackgroundColor(p.bg);
        Ui.pad(content, 16, 12, 16, 118);

        content.addView(topBar(), Ui.matchW());
        content.addView(hero(), topMargin(10));
        content.addView(body(), topMargin(12));

        scroll.post(() -> scroll.scrollTo(0, Math.min(keepY, Math.max(0, content.getHeight() - scroll.getHeight()))));
    }

    private LinearLayout.LayoutParams topMargin(int dp) {
        LinearLayout.LayoutParams lp = Ui.matchW();
        lp.topMargin = Ui.dp(dp);
        return lp;
    }

    /* ---------- 顶栏：月份 + 工具，压成一行 ---------- */
    private View topBar() {
        LinearLayout bar = Ui.row(this);

        bar.addView(navBtn("‹", v -> {
            ym = Store.shiftMonth(ym, -1);
            render();
        }));

        LinearLayout lab = Ui.col(this);
        TextView t1 = Ui.tv(this, Store.monthLabel(ym), 15, p.text, true);
        t1.setGravity(Gravity.CENTER);
        int days = Store.daysInMonth(ym);
        TextView t2 = Ui.tv(this, days + " 天 · 已过 " + (days - Store.daysLeft(ym)) + " 天", 11, p.text3);
        t2.setGravity(Gravity.CENTER);
        lab.addView(t1, Ui.matchW());
        lab.addView(t2, Ui.matchW());
        LinearLayout.LayoutParams llp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        llp.leftMargin = llp.rightMargin = Ui.dp(6);
        bar.addView(lab, llp);

        bar.addView(navBtn("›", v -> {
            ym = Store.shiftMonth(ym, 1);
            render();
        }));

        if (!ym.equals(Store.ymNow())) {
            TextView back = smallBtn("本月", v -> {
                ym = Store.ymNow();
                render();
            });
            bar.addView(back, rightMargin(6));
        }

        bar.addView(smallBtn(p.dark ? "☀" : "☾", v -> {
            store.theme = p.nextPref();
            store.save();
            p = Palette.of(this, store.theme);
            root.setBackgroundColor(p.bg);
            fab.setBackground(Ui.grad(p.brand, p.brand2, 22));
            render();
        }), rightMargin(6));
        bar.addView(smallBtn("导出", v -> doExport()), rightMargin(6));
        bar.addView(smallBtn("导入", v -> doImport()));

        return bar;
    }

    private LinearLayout.LayoutParams rightMargin(int dp) {
        LinearLayout.LayoutParams lp = Ui.wrap();
        lp.leftMargin = Ui.dp(dp);
        return lp;
    }

    private TextView navBtn(String s, View.OnClickListener l) {
        TextView t = Ui.tv(this, s, 16, p.text2);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.bgStroke(p.surface, p.line, 1, 11));
        t.setOnClickListener(l);
        Ui.tappable(this, t);
        t.setLayoutParams(Ui.lp(Ui.dp(34), Ui.dp(34)));
        return t;
    }

    private TextView smallBtn(String label, View.OnClickListener l) {
        TextView t = Ui.tv(this, label, 11.5f, p.text2, true);
        t.setGravity(Gravity.CENTER);
        t.setBackground(Ui.bgStroke(p.surface, p.line, 1, 11));
        Ui.pad(t, 9, 0, 9, 0);
        t.setLayoutParams(Ui.lp(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(34)));
        t.setOnClickListener(l);
        Ui.tappable(this, t);
        return t;
    }

    /* ---------- 首页大卡片：本月可用 + 剩余可支配 ---------- */
    private View hero() {
        Store.Month md = store.monthData(ym);
        double plan = store.monthPlan(ym);
        boolean hasAllow = md.allowance != null && md.allowance > 0;
        double base = hasAllow ? md.allowance : plan;      // 本月可用
        double spent = store.monthSpent(ym);
        // 剩余可支配 = 本月可用 − 已规划 − 已支配
        double free = hasAllow ? Store.round2(base - plan - spent) : 0;
        boolean over = hasAllow && free < -0.004;
        double barPct = base > 0
                ? Math.min(100, (hasAllow ? plan + spent : spent) / base * 100)
                : 0;

        LinearLayout hero = Ui.col(this);
        hero.setBackground(Ui.grad(0xFF5B5BD6, 0xFFA78BFA, 22));
        hero.setElevation(Ui.dp(8));
        Ui.pad(hero, 20, 18, 20, 18);

        LinearLayout top = Ui.row(this);
        top.addView(Ui.tv(this, "本月可用", 12.5f, Ui.alpha(0xFFFFFFFF, 0.9f), true),
                Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView pill = Ui.tv(this, hasAllow ? "✎ 修改" : "＋ 设置", 11.5f, 0xFFFFFFFF, true);
        pill.setBackground(Ui.bg(0x33FFFFFF, 99));
        Ui.pad(pill, 11, 7, 11, 7);
        pill.setOnClickListener(v -> Sheets.allow(this, store, p, ym, this::render));
        Ui.tappable(this, pill);
        top.addView(pill);
        hero.addView(top, Ui.matchW());

        LinearLayout amt = Ui.row(this);
        amt.setGravity(Gravity.BOTTOM);
        TextView sym = Ui.tv(this, "¥", 19, Ui.alpha(0xFFFFFFFF, 0.85f), true);
        LinearLayout.LayoutParams slp = Ui.wrap();
        slp.rightMargin = Ui.dp(3);
        amt.addView(sym, slp);
        amt.addView(Ui.tv(this, Ui.money(base), 34, 0xFFFFFFFF, true));
        LinearLayout.LayoutParams alp = Ui.matchW();
        alp.topMargin = Ui.dp(8);
        alp.bottomMargin = Ui.dp(6);
        hero.addView(amt, alp);

        // 下面这一行就是「剩余可支配」
        if (hasAllow) {
            LinearLayout line = Ui.row(this);
            line.setGravity(Gravity.BOTTOM);
            line.addView(Ui.tv(this, over ? "已超支 " : "剩余可支配 ",
                    12.5f, Ui.alpha(0xFFFFFFFF, 0.85f), true));
            line.addView(Ui.tv(this, "¥" + Ui.money(over ? -free : free), 15, 0xFFFFFFFF, true));
            LinearLayout.LayoutParams llp = Ui.matchW();
            llp.bottomMargin = Ui.dp(14);
            hero.addView(line, llp);
        } else {
            TextView hint = Ui.tv(this, "还没设可用金额，点右上角设置", 12.5f, Ui.alpha(0xFFFFFFFF, 0.85f));
            LinearLayout.LayoutParams hlp = Ui.matchW();
            hlp.bottomMargin = Ui.dp(14);
            hero.addView(hint, hlp);
        }

        hero.addView(Ui.progress(this, barPct, 0x40FFFFFF, Ui.bg(0xFFFFFFFF, 4.5f), 9), Ui.matchW());
        return hero;
    }

    /* ---------- 主体：汇总栏 + 下面的内容 ---------- */
    private View body() {
        LinearLayout box = Ui.col(this);

        double plan = store.monthPlan(ym);
        double spent = store.monthSpent(ym);

        LinearLayout sum = Ui.row(this);
        sum.setWeightSum(3);
        sum.addView(tabCell("已规划", "¥" + Ui.money(plan), "plan", 0), cellLp(0));
        sum.addView(tabCell("已支配", "¥" + Ui.money(spent), "ledger", 6), cellLp(6));
        sum.addView(tabCell("课程表", Courses.weekLabel(Courses.currentWeek()), "schedule", 6), cellLp(6));
        box.addView(sum, Ui.matchW());

        if ("ledger".equals(view)) {
            box.addView(ledgerBody(), topMargin(10));
        } else if ("schedule".equals(view)) {
            box.addView(scheduleBody(), topMargin(10));
        } else {
            box.addView(planBody(), topMargin(10));
        }
        return box;
    }

    /* ================= 课程表 ================= */

    private View scheduleBody() {
        LinearLayout box = Ui.col(this);

        // 周次条
        LinearLayout bar = Ui.row(this);
        bar.setBackground(Ui.bg(p.surface, 17));
        bar.setElevation(Ui.dp(2));
        Ui.pad(bar, 8, 8, 8, 8);

        bar.addView(navBtn("‹", v -> {
            week = Math.max(1, week - 1);
            render();
        }));

        LinearLayout mid = Ui.col(this);
        TextView t1 = Ui.tv(this, Courses.weekLabel(week), 15, p.text, true);
        t1.setGravity(Gravity.CENTER);
        int thisWeek = Courses.currentWeek();
        String sub = week == thisWeek ? "就是本周" : "周 " + Courses.dateLabel(week, 1) + " ~ " + Courses.dateLabel(week, 5);
        TextView t2 = Ui.tv(this, sub, 11, p.text3);
        t2.setGravity(Gravity.CENTER);
        mid.addView(t1, Ui.matchW());
        mid.addView(t2, Ui.matchW());
        LinearLayout.LayoutParams mlp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        mlp.leftMargin = mlp.rightMargin = Ui.dp(6);
        bar.addView(mid, mlp);

        bar.addView(navBtn("›", v -> {
            week = Math.min(Courses.WEEKS_TOTAL, week + 1);
            render();
        }));

        if (week != thisWeek) {
            bar.addView(smallBtn("本周", v -> {
                week = Courses.currentWeek();
                render();
            }), rightMargin(6));
        }
        box.addView(bar, Ui.matchW());

        box.addView(scheduleGrid(), topMargin(10));

        boolean anyConflict = false;
        for (int d = 1; d <= 5 && !anyConflict; d++) {
            for (int s = 1; s <= 5 && !anyConflict; s++) {
                if (Courses.at(d, s, week).size() > 1) anyConflict = true;
            }
        }
        TextView tip = Ui.tv(this, anyConflict
                ? "红框 = 这一格有两节课撞在一起，点开任意一节看详情"
                : "点任意一节课看详情（教室 / 教师 / 周次）", 11.5f, p.text3);
        tip.setGravity(Gravity.CENTER);
        box.addView(tip, topMargin(12));
        return box;
    }

    private View scheduleGrid() {
        LinearLayout card = Ui.card(this, p);
        Ui.pad(card, 6, 8, 6, 8);

        int cardW = Ui.screenW(this) - Ui.dp(32) - Ui.dp(12);
        int timeW = Ui.dp(34);
        int gap = Ui.dp(4);
        int dayW = (cardW - timeW - gap * 4) / 5;
        int rowH = Ui.dp(80);
        int today = Courses.todayDay();

        // 表头：日期
        LinearLayout head = Ui.row(this);
        View blank = new View(this);
        head.addView(blank, Ui.lp(timeW, Ui.dp(30)));
        for (int d = 1; d <= 5; d++) {
            boolean isToday = d == today;
            LinearLayout h = Ui.col(this);
            TextView nm = Ui.tv(this, "周" + "一二三四五".charAt(d - 1), 11.5f,
                    isToday ? p.brandInk : p.text2, isToday);
            nm.setGravity(Gravity.CENTER);
            TextView dt = Ui.tv(this, Courses.dateLabel(week, d), 9.5f, p.text3);
            dt.setGravity(Gravity.CENTER);
            h.addView(nm, Ui.matchW());
            h.addView(dt, Ui.matchW());
            head.addView(h, dayLp(dayW, gap, d));
        }
        card.addView(head, Ui.matchW());

        // 5 个大节
        for (int s = 1; s <= 5; s++) {
            LinearLayout row = Ui.row(this);

            LinearLayout t = Ui.col(this);
            t.setGravity(Gravity.CENTER);
            TextView num = Ui.tv(this, Courses.SLOTS[s - 1][0], 13, p.text2, true);
            num.setGravity(Gravity.CENTER);
            TextView time = Ui.tv(this, Courses.SLOTS[s - 1][1], 8.5f, p.text3);
            time.setGravity(Gravity.CENTER);
            t.addView(num, Ui.matchW());
            t.addView(time, Ui.matchW());
            row.addView(t, Ui.lp(timeW, rowH));

            for (int d = 1; d <= 5; d++) row.addView(dayCell(d, s, today), dayLp(dayW, gap, d));

            LinearLayout.LayoutParams rlp = Ui.matchW();
            rlp.topMargin = Ui.dp(4);
            card.addView(row, rlp);
        }
        return card;
    }

    private LinearLayout.LayoutParams dayLp(int width, int gap, int index) {
        LinearLayout.LayoutParams lp = Ui.lp(width, ViewGroup.LayoutParams.MATCH_PARENT);
        if (index > 1) lp.leftMargin = gap;
        return lp;
    }

    /** 一个格子：可能一节课都没有，也可能挤了两节（那就是这周真的撞了） */
    private View dayCell(int d, int s, int today) {
        List<Courses.Course> list = Courses.at(d, s, week);
        // 只有「这一周这一格真的有两节课」才算冲突。
        // 课程本身在别的周次有重叠不算 —— 否则第 5 周就会把 6-17 周才开始的实验课误报成冲突。
        boolean clash = list.size() > 1;

        LinearLayout cell = Ui.col(this);
        boolean isToday = d == today;
        cell.setBackground(Ui.bg(isToday ? Ui.mixAlpha(p.brand, 0.07f, p.surface) : p.surface2, 10));
        Ui.pad(cell, 2, 2, 2, 2);

        for (Courses.Course x : list) {
            LinearLayout b = Ui.col(this);
            b.setGravity(Gravity.CENTER);
            int bg, fg, line;
            if (x.kind == Courses.KIND_LAB) {
                bg = Ui.mixAlpha(p.amber, 0.16f, p.surface);
                fg = p.dark ? 0xFFFBBF24 : 0xFF8A5A06;
                line = Ui.mixAlpha(p.amber, 0.5f, p.surface);
            } else {
                bg = Ui.mixAlpha(p.brand, 0.11f, p.surface);
                fg = p.brandInk;
                line = Ui.mixAlpha(p.brand, 0.35f, p.surface);
            }
            b.setBackground(Ui.bgStroke(bg, clash ? p.rose : line, clash ? 1.5f : 1f, 8));

            TextView nm = Ui.tv(this, x.name, 9.5f, fg, true);
            nm.setGravity(Gravity.CENTER);
            nm.setMaxLines(3);
            nm.setEllipsize(TextUtils.TruncateAt.END);
            nm.setLineSpacing(Ui.dp(1), 1f);
            b.addView(nm, Ui.matchW());

            if (!x.room.isEmpty() && !"待定".equals(x.room)) {
                TextView rm = Ui.tv(this, x.room, 8f, fg);
                rm.setGravity(Gravity.CENTER);
                rm.setMaxLines(1);
                rm.setEllipsize(TextUtils.TruncateAt.END);
                LinearLayout.LayoutParams rlp = Ui.matchW();
                rlp.topMargin = Ui.dp(1);
                b.addView(rm, rlp);
            }

            LinearLayout.LayoutParams blp = Ui.matchW();
            blp.weight = 1;
            if (cell.getChildCount() > 0) blp.topMargin = Ui.dp(2);
            cell.addView(b, blp);

            b.setOnClickListener(v -> Sheets.course(this, p, x, week, null));
            Ui.tappable(this, b);
        }
        return cell;
    }

    /** 规划视图：分类卡片 */
    private View planBody() {
        LinearLayout box = Ui.col(this);
        List<Store.Group> groups = store.monthGroups(ym);
        double totalPlan = store.monthPlan(ym);

        if (groups.isEmpty()) {
            box.addView(emptyState("🍚", Store.monthLabel(ym) + "还没有预算",
                    "先建一个分类，比如「饮食」，\n再往里加「牛奶」「鸡蛋」这样的项目。",
                    "＋ 添加分类", v -> Sheets.group(this, store, p, ym, null, this::render)), Ui.matchW());
            return box;
        }

        for (Store.Group g : groups) box.addView(groupCard(g, totalPlan), topMargin(10));

        TextView add = Ui.tv(this, "＋ 添加分类", 14.5f, p.text2, true);
        add.setGravity(Gravity.CENTER);
        add.setBackground(Ui.bgStroke(p.surface, p.line, 1.5f, 18));
        Ui.pad(add, 12, 15, 12, 15);
        add.setOnClickListener(v -> Sheets.group(this, store, p, ym, null, this::render));
        Ui.tappable(this, add);
        box.addView(add, topMargin(10));
        return box;
    }

    private LinearLayout.LayoutParams cellLp(int left) {
        LinearLayout.LayoutParams lp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.leftMargin = Ui.dp(left);
        return lp;
    }

    /** 可点的那两格：选中时高亮，点一下切到对应视图 */
    private View tabCell(String label, String value, final String target, int leftMargin) {
        boolean on = view.equals(target);
        LinearLayout c = Ui.col(this);
        c.setBackground(on
                ? Ui.bgStroke(Ui.mixAlpha(p.brand, 0.12f, p.surface), p.brand, 1.5f, 17)
                : Ui.bgStroke(p.surface, p.line, 1f, 17));
        c.setElevation(Ui.dp(2));
        Ui.pad(c, 12, 12, 12, 12);

        LinearLayout lab = Ui.row(this);
        lab.addView(Ui.tv(this, label, 11.5f, on ? p.brandInk : p.text3, on),
                Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        lab.addView(Ui.tv(this, on ? "▴" : "▾", 10, on ? p.brandInk : p.text3, true));
        c.addView(lab, Ui.matchW());

        TextView v = Ui.tv(this, value, 15, on ? p.brandInk : p.text, true);
        v.setSingleLine(true);
        v.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(v, topMargin(3));

        c.setOnClickListener(x -> {
            view = target;
            render();
        });
        Ui.tappable(this, c);
        return c;
    }

    /** 一级分类卡片：头部有图标，里面是二级项目（不带图标） */
    private View groupCard(Store.Group g, double totalPlan) {
        double gp = Store.groupPlan(g, ym);
        int share = totalPlan > 0 ? (int) Math.round(gp / totalPlan * 100) : 0;

        LinearLayout card = Ui.card(this, p);

        LinearLayout head = Ui.row(this);
        Ui.pad(head, 14, 14, 14, 13);

        TextView ico = Ui.tv(this, g.emoji, 18f, p.text);
        ico.setGravity(Gravity.CENTER);
        ico.setBackground(Ui.bg(p.surface2, 13));
        head.addView(ico, Ui.lp(Ui.dp(40), Ui.dp(40)));

        LinearLayout mid = Ui.col(this);
        LinearLayout.LayoutParams mlp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        mlp.leftMargin = Ui.dp(11);
        head.addView(mid, mlp);

        LinearLayout r1 = Ui.row(this);
        TextView name = Ui.tv(this, g.name, 15, p.text, true);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        r1.addView(name, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        LinearLayout planBox = Ui.row(this);
        planBox.setGravity(Gravity.BOTTOM);
        planBox.addView(Ui.tv(this, "¥" + Ui.money(gp), 15, p.text, true));
        planBox.addView(Ui.tv(this, "/月", 11, p.text3));
        r1.addView(planBox);
        mid.addView(r1, Ui.matchW());

        mid.addView(Ui.tv(this, g.items.size() + " 个项目 · 占本月预算 " + share + "%", 11.5f, p.text3), topMargin(4));

        head.setOnClickListener(v -> Sheets.group(this, store, p, ym, g, this::render));
        Ui.tappable(this, head);
        card.addView(head, Ui.matchW());

        View line = new View(this);
        line.setBackgroundColor(p.line);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(1));
        llp.leftMargin = Ui.dp(14);
        llp.rightMargin = Ui.dp(14);
        card.addView(line, llp);

        if (g.items.isEmpty()) {
            TextView none = Ui.tv(this, "这个分类下还没有项目", 12.5f, p.text3);
            Ui.pad(none, 14, 13, 14, 13);
            card.addView(none, Ui.matchW());
        } else {
            for (Store.Item it : g.items) card.addView(itemRow(g, it, totalPlan), Ui.matchW());
        }

        TextView addRow = Ui.tv(this, "＋ 加一项", 13.5f, p.brandInk, true);
        Ui.pad(addRow, 14, 12, 14, 13);
        addRow.setOnClickListener(v -> Sheets.item(this, store, p, ym, g, null, this::render));
        Ui.tappable(this, addRow);
        card.addView(addRow, Ui.matchW());

        return card;
    }

    /** 二级项目一行（没有图标） */
    private View itemRow(Store.Group g, Store.Item it, double totalPlan) {
        LinearLayout rowV = Ui.row(this);
        Ui.pad(rowV, 14, 11, 14, 11);

        double own = Store.itemPlan(it, ym);
        String share = "";
        if (totalPlan > 0) {
            int s = (int) Math.round(own / totalPlan * 100);
            share = " · 占本月预算 " + (s == 0 ? "<1" : String.valueOf(s)) + "%";
        }

        LinearLayout mid = Ui.col(this);
        LinearLayout.LayoutParams mlp = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        mlp.rightMargin = Ui.dp(10);
        rowV.addView(mid, mlp);

        TextView nm = Ui.tv(this, it.name, 14, p.text);
        nm.setSingleLine(true);
        nm.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(nm, Ui.matchW());
        TextView sub = Ui.tv(this, Store.cycleDesc(it) + share, 11, p.text3);
        sub.setSingleLine(true);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        mid.addView(sub, topMargin(3));

        LinearLayout amountBox = Ui.row(this);
        amountBox.setGravity(Gravity.BOTTOM);
        amountBox.addView(Ui.tv(this, "¥" + Ui.money(own), 14, p.text, true));
        rowV.addView(amountBox);

        rowV.setOnClickListener(v -> Sheets.item(this, store, p, ym, g, it, this::render));
        Ui.tappable(this, rowV);
        return rowV;
    }

    /* ---------- 流水视图：点「已支配」切过来 ---------- */
    private View ledgerBody() {
        List<Store.Rec> recs = Store.sortedDesc(store.monthRecords(ym));
        LinearLayout box = Ui.col(this);

        if (recs.isEmpty()) {
            box.addView(emptyState("🧾", Store.monthLabel(ym) + "还没有支出",
                    "每记一笔就出现在这里，\n按日期分组，点一条可以改或删。",
                    "＋ 记一笔", v -> Sheets.record(this, store, p, ym, null, null, this::render)), Ui.matchW());
            return box;
        }

        LinearLayout card = Ui.card(this, p);
        LinearLayout list = Ui.col(this);
        Ui.pad(list, 0, 6, 0, 6);

        String curDate = null;
        LinearLayout group = null;
        for (Store.Rec r : recs) {
            if (!r.date.equals(curDate)) {
                curDate = r.date;
                group = Ui.col(this);
                LinearLayout dayHead = Ui.row(this);
                dayHead.addView(Ui.tv(this, Store.dayLabel(r.date), 12.5f, p.text2, true),
                        Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                double sum = 0;
                for (Store.Rec x : recs) if (x.date.equals(r.date)) sum += x.amount;
                dayHead.addView(Ui.tv(this, "¥" + Ui.money(sum), 12, p.text3));
                Ui.pad(dayHead, 14, 12, 14, 8);
                group.addView(dayHead, Ui.matchW());

                View line = new View(this);
                line.setBackgroundColor(p.line);
                LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(1));
                llp.leftMargin = Ui.dp(14);
                llp.rightMargin = Ui.dp(14);
                group.addView(line, llp);

                list.addView(group, Ui.matchW());
            }
            group.addView(recRow(r), Ui.matchW());
        }
        card.addView(list, Ui.matchW());
        box.addView(card, Ui.matchW());
        return box;
    }

    /** 流水一行：备注为主，没有备注就显示「支出」 */
    private View recRow(Store.Rec r) {
        LinearLayout rowV = Ui.row(this);
        Ui.pad(rowV, 14, 11, 14, 11);

        boolean empty = r.note.isEmpty();
        TextView title = Ui.tv(this, empty ? "支出" : r.note, empty ? 13.5f : 14.5f,
                empty ? p.text3 : p.text, !empty);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        rowV.addView(title, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView amount = Ui.tv(this, "-¥" + Ui.money(r.amount), 15, p.text, true);
        LinearLayout.LayoutParams alp = Ui.wrap();
        alp.leftMargin = Ui.dp(10);
        rowV.addView(amount, alp);

        rowV.setOnClickListener(v -> Sheets.record(this, store, p, ym, r, null, this::render));
        Ui.tappable(this, rowV);
        return rowV;
    }

    /* ---------- 空状态 ---------- */
    private View emptyState(String emoji, String title, String desc, String btn, View.OnClickListener l) {
        LinearLayout box = Ui.col(this);
        box.setBackground(Ui.bg(p.surface, 24));
        box.setElevation(Ui.dp(2));
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.pad(box, 22, 42, 22, 42);

        TextView e = Ui.tv(this, emoji, 44, p.text);
        e.setGravity(Gravity.CENTER);
        box.addView(e, Ui.matchW());

        TextView t = Ui.tv(this, title, 17, p.text, true);
        t.setGravity(Gravity.CENTER);
        box.addView(t, topMargin(14));

        TextView d = Ui.tv(this, desc, 13.5f, p.text2);
        d.setGravity(Gravity.CENTER);
        d.setLineSpacing(Ui.dp(3), 1f);
        box.addView(d, topMargin(8));

        TextView b = Ui.tv(this, btn, 15.5f, 0xFFFFFFFF, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.grad(p.brand, p.brand2, 16));
        Ui.pad(b, 20, 15, 20, 15);
        b.setOnClickListener(l);
        Ui.tappable(this, b);
        LinearLayout.LayoutParams blp = Ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(20);
        box.addView(b, blp);
        return box;
    }

    /* ---------- 导入导出 ---------- */
    private void doExport() {
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, "预算本-" + Store.today() + ".json");
        try {
            startActivityForResult(i, REQ_EXPORT);
        } catch (Exception e) {
            toast("这台设备没有可用的文件选择器");
        }
    }

    private void doImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_IMPORT);
        } catch (Exception e) {
            toast("这台设备没有可用的文件选择器");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (req == REQ_EXPORT) {
                OutputStream out = getContentResolver().openOutputStream(uri);
                if (out == null) throw new Exception("no stream");
                out.write(store.toJson().toString(2).getBytes("UTF-8"));
                out.close();
                toast("已导出备份文件");
            } else if (req == REQ_IMPORT) {
                InputStream in = getContentResolver().openInputStream(uri);
                if (in == null) throw new Exception("no stream");
                Scanner sc = new Scanner(in, "UTF-8").useDelimiter("\\A");
                String text = sc.hasNext() ? sc.next() : "";
                sc.close();
                in.close();
                if (store.replaceAll(text)) {
                    p = Palette.of(this, store.theme);
                    render();
                    toast("导入成功");
                } else {
                    toast("这个文件读不出来，请选择本应用导出的 JSON");
                }
            }
        } catch (Exception e) {
            toast("操作失败：" + e.getMessage());
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }
}
