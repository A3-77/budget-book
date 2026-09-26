package com.budgetbook.app;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** 底部弹层：编辑分类 / 编辑项目 / 记一笔 / 设置本月可用 */
class Sheets {

    interface Done {
        void run();
    }

    /** 只留常用的，想要别的可以自己输入 */
    private static final String[] EMOJIS = {
            "🍚", "🛒", "🚌", "🏠", "💡", "📱", "👕", "💊",
            "📚", "🎮", "✈️", "🎁", "🐱", "💄", "🏋️", "🧾"
    };

    private final Context c;
    private final Store store;
    private final Palette p;
    private final String ym;
    private final Done done;
    private Dialog dialog;

    private Sheets(Context c, Store store, Palette p, String ym, Done done) {
        this.c = c;
        this.store = store;
        this.p = p;
        this.ym = ym;
        this.done = done;
    }

    static void group(Context c, Store s, Palette p, String ym, Store.Group existing, Done done) {
        new Sheets(c, s, p, ym, done).showGroup(existing);
    }

    static void item(Context c, Store s, Palette p, String ym, Store.Group group, Store.Item existing, Done done) {
        new Sheets(c, s, p, ym, done).showItem(group, existing);
    }

    static void record(Context c, Store s, Palette p, String ym, Store.Rec existing, String presetDate, Done done) {
        new Sheets(c, s, p, ym, done).showRecord(existing, presetDate);
    }

    static void allow(Context c, Store s, Palette p, String ym, Done done) {
        new Sheets(c, s, p, ym, done).showAllow();
    }

    /* ================= 弹层外壳 ================= */

    private void show(String title, View body) {
        dialog = new Dialog(c);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout wrap = Ui.col(c);
        wrap.setBackground(Ui.topRounded(p.surface, 26));
        Ui.pad(wrap, 18, 18, 18, 24);

        LinearLayout head = Ui.row(c);
        head.addView(Ui.tv(c, title, 18, p.text, true), Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView x = Ui.tv(c, "✕", 14, p.text2);
        x.setGravity(Gravity.CENTER);
        x.setBackground(Ui.bgStroke(p.surface2, p.line, 1, 12));
        x.setOnClickListener(v -> dialog.dismiss());
        head.addView(x, Ui.lp(Ui.dp(34), Ui.dp(34)));
        wrap.addView(head, Ui.matchW());

        LinearLayout inner = Ui.col(c);
        inner.addView(body, Ui.matchW());
        ScrollView sv = new ScrollView(c);
        sv.setClipToPadding(false);
        sv.addView(inner, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = Ui.matchW();
        slp.topMargin = Ui.dp(16);
        wrap.addView(sv, slp);

        dialog.setContentView(wrap);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.5f);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.setWindowAnimations(R.style.SheetAnim);
        }
    }

    private void finish() {
        if (dialog != null) dialog.dismiss();
        if (done != null) done.run();
    }

    private void toast(String s) {
        Toast.makeText(c, s, Toast.LENGTH_SHORT).show();
    }

    /* ================= 小控件 ================= */

    private int contentW() {
        return Ui.screenW(c) - Ui.dp(36);
    }

    private TextView note(String text) {
        TextView t = Ui.tv(c, text, 12, p.text3);
        t.setLineSpacing(Ui.dp(3), 1f);
        return t;
    }

    private View field(String label, View input) {
        LinearLayout box = Ui.col(c);
        box.addView(Ui.tv(c, label, 12, p.text2, true), Ui.matchW());
        LinearLayout.LayoutParams lp = Ui.matchW();
        lp.topMargin = Ui.dp(8);
        box.addView(input, lp);
        return box;
    }

    private EditText edit(String value, String hint) {
        EditText e = new EditText(c);
        e.setText(value);
        e.setHint(hint);
        e.setHintTextColor(p.text3);
        e.setTextColor(p.text);
        e.setTextSize(16f);
        e.setBackground(Ui.bgStroke(p.surface2, p.line, 1, 14));
        Ui.pad(e, 14, 13, 14, 13);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setSingleLine(true);
        return e;
    }

    private EditText numEdit(String value, String hint, float sizeSp, boolean big) {
        EditText e = new EditText(c);
        e.setText(value);
        e.setHint(hint);
        e.setHintTextColor(p.text3);
        e.setTextColor(p.text);
        e.setTextSize(sizeSp);
        e.setTypeface(Typeface.DEFAULT_BOLD);
        if (big) {
            e.setBackground(null);
            Ui.pad(e, 0, 0, 0, 0);
        } else {
            e.setBackground(Ui.bgStroke(p.surface2, p.line, 1, 14));
            Ui.pad(e, 14, 13, 14, 13);
            e.setTypeface(Typeface.DEFAULT);
            e.setTextSize(16f);
        }
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setSingleLine(true);
        return e;
    }

    private double valueOf(EditText e) {
        try {
            return Double.parseDouble(e.getText().toString().trim());
        } catch (Exception ex) {
            return 0;
        }
    }

    private int intOf(EditText e) {
        try {
            return Math.max(1, Integer.parseInt(e.getText().toString().trim()));
        } catch (Exception ex) {
            return 1;
        }
    }

    private void watch(EditText e, Runnable r) {
        e.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int d) {
            }

            public void afterTextChanged(Editable s) {
                r.run();
            }
        });
    }

    private LinearLayout seg(String[] labels, String[] values, final String[] cur, final Runnable onChange) {
        final LinearLayout segBox = Ui.row(c);
        segBox.setBackground(Ui.bg(p.surface2, 15));
        Ui.pad(segBox, 4, 4, 4, 4);
        for (int i = 0; i < labels.length; i++) {
            final TextView b = Ui.tv(c, labels[i], 14, p.text2, false);
            b.setTag(values[i]);
            b.setGravity(Gravity.CENTER);
            Ui.pad(b, 6, 11, 6, 11);
            b.setOnClickListener(v -> {
                cur[0] = (String) v.getTag();
                paintSeg(segBox, cur[0]);
                onChange.run();
            });
            segBox.addView(b, Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        paintSeg(segBox, cur[0]);
        return segBox;
    }

    private void paintSeg(LinearLayout segBox, String cur) {
        for (int k = 0; k < segBox.getChildCount(); k++) {
            TextView t = (TextView) segBox.getChildAt(k);
            boolean on = cur.equals(t.getTag());
            t.setTextColor(on ? p.brandInk : p.text2);
            t.setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
            t.setBackground(on ? Ui.bg(p.segOn, 12) : null);
        }
    }

    private TextView primary(String label, View.OnClickListener l) {
        TextView b = Ui.tv(c, label, 15.5f, 0xFFFFFFFF, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.grad(p.brand, p.brand2, 16));
        Ui.pad(b, 16, 15, 16, 15);
        b.setOnClickListener(l);
        Ui.tappable(c, b);
        return b;
    }

    private TextView danger(String label, View.OnClickListener l) {
        TextView b = Ui.tv(c, label, 15, p.rose, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.bg(Ui.mixAlpha(p.rose, 0.11f, p.surface), 16));
        Ui.pad(b, 16, 15, 16, 15);
        b.setOnClickListener(l);
        Ui.tappable(c, b);
        return b;
    }

    private void addBlock(LinearLayout box, View v, int topDp) {
        LinearLayout.LayoutParams lp = Ui.matchW();
        lp.topMargin = Ui.dp(topDp);
        box.addView(v, lp);
    }

    private LinearLayout amountBox(EditText e) {
        LinearLayout box = Ui.row(c);
        box.setBackground(Ui.bgStroke(p.surface2, p.line, 1, 16));
        Ui.pad(box, 15, 11, 15, 11);
        box.addView(Ui.tv(c, "¥", 19, p.text3, true), Ui.wrap());
        e.setLayoutParams(Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        box.addView(e);
        return box;
    }

    /** 图标选择：一排常用图标 + 一个自定义输入框（输入任意表情） */
    private View emojiPicker(final String[] emoji, final Runnable onChange) {
        LinearLayout box = Ui.col(c);
        final List<View> chips = new ArrayList<>();
        for (String e : EMOJIS) {
            final TextView ch = Ui.emojiChip(c, p, e, e.equals(emoji[0]));
            ch.setTag(e);
            chips.add(ch);
        }

        final EditText custom = edit("", "也可以直接输入任意表情");
        custom.setTextSize(15f);

        final Runnable paint = () -> {
            for (View v : chips) {
                boolean on = emoji[0].equals(v.getTag());
                TextView t = (TextView) v;
                t.setTextColor(on ? p.brandInk : p.text);
                t.setBackground(on
                        ? Ui.bgStroke(Ui.mixAlpha(p.brand, 0.13f, p.surface), p.brand, 1, 12)
                        : Ui.bgStroke(p.surface2, p.line, 1, 12));
            }
            onChange.run();
        };

        for (View v : chips) {
            v.setOnClickListener(x -> {
                emoji[0] = (String) x.getTag();
                custom.setText("");
                paint.run();
            });
        }
        watch(custom, () -> {
            String s = custom.getText().toString().trim();
            if (!s.isEmpty()) {
                // 只取前两个字符，免得把整句话当成图标
                emoji[0] = s.length() > 2 ? s.substring(0, 2) : s;
                paint.run();
            }
        });

        addBlock(box, Ui.wrapChips(c, chips, contentW(), Ui.dp(8), Ui.dp(8)), 0);
        LinearLayout.LayoutParams clp = Ui.matchW();
        clp.topMargin = Ui.dp(10);
        box.addView(custom, clp);
        return box;
    }

    /* ================= 一级分类 ================= */

    private void showGroup(Store.Group existing) {
        final boolean editing = existing != null;
        final String[] emoji = {editing ? existing.emoji : "🍚"};

        LinearLayout body = Ui.col(c);
        addBlock(body, note("一级分类用来给预算分组，比如「饮食」「交通」「居住」。\n"
                + "具体买什么，放进分类里当二级项目。"), 0);

        final TextView preview = Ui.tv(c, "", 15, p.text, true);
        preview.setBackground(Ui.bg(Ui.mixAlpha(p.brand, 0.09f, p.surface), 14));
        Ui.pad(preview, 15, 13, 15, 13);

        final EditText name = edit(editing ? existing.name : "", "比如：饮食、交通、居住、娱乐");
        final Runnable refresh = () -> {
            String n = name.getText().toString().trim();
            preview.setText(emoji[0] + "  " + (n.isEmpty() ? "分类名称" : n));
        };
        watch(name, refresh);

        addBlock(body, field("分类名称", name), 14);
        addBlock(body, field("图标", emojiPicker(emoji, refresh)), 14);
        addBlock(body, preview, 16);
        refresh.run();

        addBlock(body, primary(editing ? "保存修改" : "添加分类", v -> {
            String nm = name.getText().toString().trim();
            if (nm.isEmpty()) {
                toast("给这个分类起个名字");
                return;
            }
            Store.Month m = store.ensureMonth(ym);
            if (editing) {
                for (Store.Group g : m.groups) {
                    if (g.id.equals(existing.id)) {
                        g.name = nm;
                        g.emoji = emoji[0];
                    }
                }
            } else {
                Store.Group g = new Store.Group();
                g.id = Store.uid();
                g.name = nm;
                g.emoji = emoji[0];
                m.groups.add(g);
            }
            store.save();
            finish();
            toast(editing ? "已保存" : "已添加分类「" + nm + "」");
        }), 16);

        if (editing) {
            addBlock(body, danger("删除这个分类", v -> {
                Store.Month m = store.ensureMonth(ym);
                List<Store.Group> keep = new ArrayList<>();
                for (Store.Group g : m.groups) if (!g.id.equals(existing.id)) keep.add(g);
                m.groups.clear();
                m.groups.addAll(keep);
                store.save();
                finish();
                toast("已删除分类");
            }), 10);
            if (!existing.items.isEmpty()) {
                addBlock(body, note("它下面的 " + existing.items.size() + " 个项目会一起删掉。"), 8);
            }
        }

        show(editing ? "编辑分类" : "添加分类", body);
    }

    /* ================= 二级项目 ================= */

    private void showItem(Store.Group startGroup, Store.Item existing) {
        final boolean editing = existing != null;
        final List<Store.Group> groups = store.monthGroups(ym);
        final String[] groupId = {startGroup != null ? startGroup.id
                : (groups.isEmpty() ? "" : groups.get(0).id)};
        final String[] cycle = {editing ? existing.cycle : "day"};

        LinearLayout body = Ui.col(c);
        addBlock(body, note("只改 " + Store.monthLabel(ym) + " 的预算，其他月份不受影响。"), 0);

        final EditText name = edit(editing ? existing.name : "", "比如：牛奶、鸡蛋、午餐");
        addBlock(body, field("名称", name), 14);

        final List<View> gchips = new ArrayList<>();
        for (Store.Group g : groups) {
            TextView ch = Ui.chip(c, p, g.emoji + " " + g.name, g.id.equals(groupId[0]));
            ch.setTag(g.id);
            gchips.add(ch);
        }
        for (View v : gchips) {
            v.setOnClickListener(x -> {
                groupId[0] = (String) x.getTag();
                for (View y : gchips) {
                    boolean on = groupId[0].equals(y.getTag());
                    TextView t = (TextView) y;
                    t.setTextColor(on ? p.brandInk : p.text2);
                    t.setBackground(on
                            ? Ui.bgStroke(Ui.mixAlpha(p.brand, 0.13f, p.surface), p.brand, 1, 12)
                            : Ui.bgStroke(p.surface2, p.line, 1, 12));
                }
            });
        }
        addBlock(body, field("放在哪个分类", Ui.wrapChips(c, gchips, contentW(), Ui.dp(8), Ui.dp(8))), 14);

        final EditText times = numEdit(String.valueOf(editing ? existing.times : 1), "1", 16, false);
        final EditText amount = numEdit(editing ? Ui.money(existing.amount).replace(",", "") : "", "0.00", 16, false);

        final TextView preview = Ui.tv(c, "", 13, p.dark ? 0xFF5EE0B0 : 0xFF0A7A58, true);
        preview.setLineSpacing(Ui.dp(3), 1f);
        preview.setBackground(Ui.bg(Ui.mixAlpha(p.mint, p.dark ? 0.14f : 0.11f, p.surface), 14));
        Ui.pad(preview, 15, 13, 15, 13);

        final Runnable refresh = () -> preview.setText(
                Store.previewText(cycle[0], intOf(times), valueOf(amount), ym));
        watch(times, refresh);
        watch(amount, refresh);

        addBlock(body, field("多久花一次",
                seg(new String[]{"每天", "每周", "每月"}, Store.CYCLES, cycle, refresh)), 14);

        LinearLayout inline = Ui.row(c);
        LinearLayout c1 = Ui.col(c);
        c1.addView(Ui.tv(c, "次数", 10.5f, p.text3), Ui.matchW());
        c1.addView(times, Ui.matchW());
        c1.setBackground(Ui.bgStroke(p.surface2, p.line, 1, 14));
        Ui.pad(c1, 13, 9, 13, 9);
        LinearLayout c2 = Ui.col(c);
        c2.addView(Ui.tv(c, "每次金额（元）", 10.5f, p.text3), Ui.matchW());
        c2.addView(amount, Ui.matchW());
        c2.setBackground(Ui.bgStroke(p.surface2, p.line, 1, 14));
        Ui.pad(c2, 13, 9, 13, 9);

        LinearLayout.LayoutParams l1 = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        LinearLayout.LayoutParams l2 = Ui.lp(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.9f);
        l2.leftMargin = Ui.dp(10);
        inline.addView(c1, l1);
        inline.addView(c2, l2);
        addBlock(body, field("频次与金额", inline), 14);

        addBlock(body, preview, 4);
        refresh.run();

        addBlock(body, primary(editing ? "保存修改" : "添加", v -> {
            String nm = name.getText().toString().trim();
            if (nm.isEmpty()) {
                toast("给它起个名字");
                return;
            }
            double amt = valueOf(amount);
            if (amt <= 0) {
                toast("填一下每次的金额");
                return;
            }
            Store.Month m = store.ensureMonth(ym);
            if (m.groups.isEmpty()) {
                toast("先建一个分类");
                return;
            }
            Store.Group target = null;
            for (Store.Group g : m.groups) if (g.id.equals(groupId[0])) target = g;
            if (target == null) target = m.groups.get(0);

            if (editing) {
                Store.Group from = null;
                Store.Item it = null;
                for (Store.Group g : m.groups) {
                    for (Store.Item x : g.items) {
                        if (x.id.equals(existing.id)) {
                            from = g;
                            it = x;
                        }
                    }
                }
                if (it == null) return;
                it.name = nm;
                it.cycle = cycle[0];
                it.times = intOf(times);
                it.amount = amt;
                if (from != null && from != target) {
                    from.items.remove(it);
                    target.items.add(it);
                }
            } else {
                Store.Item it = new Store.Item();
                it.id = Store.uid();
                it.name = nm;
                it.cycle = cycle[0];
                it.times = intOf(times);
                it.amount = amt;
                target.items.add(it);
            }
            store.save();
            finish();
            toast(editing ? "已保存（只改了 " + Store.monthLabel(ym) + "）"
                    : "已添加，本月预计 ¥" + Ui.money(amt * intOf(times) * Store.periodsIn(ym, cycle[0])));
        }), 16);

        if (editing) {
            addBlock(body, danger("删除这个项目", v -> {
                Store.Month m = store.ensureMonth(ym);
                for (Store.Group g : m.groups) {
                    List<Store.Item> keep = new ArrayList<>();
                    for (Store.Item x : g.items) if (!x.id.equals(existing.id)) keep.add(x);
                    g.items.clear();
                    g.items.addAll(keep);
                }
                store.save();
                finish();
                toast("已删除");
            }), 10);
        }

        show(editing ? "编辑项目" : "添加项目", body);
    }

    /* ================= 记一笔 ================= */

    private void showRecord(Store.Rec existing, String presetDate) {
        final boolean editing = existing != null;
        final String[] date = {editing ? existing.date
                : (presetDate != null ? presetDate : Store.defaultDateFor(ym))};

        LinearLayout body = Ui.col(c);

        int sameCount = 0;
        double sameSum = 0;
        for (Store.Rec r : store.records) {
            if (r.date.equals(date[0]) && (!editing || !r.id.equals(existing.id))) {
                sameCount++;
                sameSum += r.amount;
            }
        }
        if (sameCount > 0) {
            addBlock(body, note("这一天已经记了 " + sameCount + " 笔，共 ¥" + Ui.money(sameSum)), 0);
        }

        final TextView dateTv = Ui.tv(c, date[0], 16, p.text);
        dateTv.setBackground(Ui.bgStroke(p.surface2, p.line, 1, 14));
        Ui.pad(dateTv, 14, 13, 14, 13);
        dateTv.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            try {
                cal.set(Integer.parseInt(date[0].substring(0, 4)),
                        Integer.parseInt(date[0].substring(5, 7)) - 1,
                        Integer.parseInt(date[0].substring(8, 10)));
            } catch (Exception ignored) {
            }
            new DatePickerDialog(c, (dp, y, m, d) -> {
                date[0] = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
                dateTv.setText(date[0]);
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        Ui.tappable(c, dateTv);
        addBlock(body, field("日期", dateTv), sameCount > 0 ? 14 : 0);

        final EditText amount = numEdit(editing ? Ui.money(existing.amount).replace(",", "") : "", "0.00", 27, true);
        addBlock(body, field("金额（元）", amountBox(amount)), 14);

        final EditText noteE = edit(editing ? existing.note : "", "选填，比如：楼下超市买菜");
        addBlock(body, field("备注", noteE), 14);

        addBlock(body, primary(editing ? "保存修改" : "记下来", v -> {
            double amt = valueOf(amount);
            if (!(amt > 0)) {
                toast("请输入金额");
                return;
            }
            if (editing) {
                for (Store.Rec r : store.records) {
                    if (r.id.equals(existing.id)) {
                        r.date = date[0];
                        r.amount = Store.round2(amt);
                        r.note = noteE.getText().toString().trim();
                    }
                }
            } else {
                Store.Rec r = new Store.Rec();
                r.id = Store.uid();
                r.date = date[0];
                r.amount = Store.round2(amt);
                r.note = noteE.getText().toString().trim();
                r.createdAt = System.currentTimeMillis();
                store.records.add(r);
            }
            store.save();
            finish();
            toast(editing ? "已保存" : "记好了 ✓");
        }), 18);

        if (editing) {
            addBlock(body, danger("删除这条记录", v -> {
                List<Store.Rec> keep = new ArrayList<>();
                for (Store.Rec r : store.records) if (!r.id.equals(existing.id)) keep.add(r);
                store.records.clear();
                store.records.addAll(keep);
                store.save();
                finish();
                toast("已删除");
            }), 10);
        }

        show(editing ? "修改这一笔" : "记一笔支出", body);
    }

    /* ================= 本月可用 ================= */

    private void showAllow() {
        Store.Month md = store.monthData(ym);
        final Double cur = md.allowance;

        LinearLayout body = Ui.col(c);
        addBlock(body, note(Store.monthLabel(ym) + " 一共可以花多少（比如这个月的生活费）。\n"
                + "填了之后首页会显示「还能花多少」，每记一笔就相应减少。"), 0);

        final EditText amount = numEdit(cur == null ? "" : Ui.money(cur).replace(",", ""), "比如 8000", 27, true);

        final TextView preview = Ui.tv(c, "", 13, p.dark ? 0xFF5EE0B0 : 0xFF0A7A58, true);
        preview.setLineSpacing(Ui.dp(3), 1f);
        preview.setBackground(Ui.bg(Ui.mixAlpha(p.mint, p.dark ? 0.14f : 0.11f, p.surface), 14));
        Ui.pad(preview, 15, 13, 15, 13);

        final Runnable refresh = () -> {
            double plan = store.monthPlan(ym);
            double spent = store.monthSpent(ym);
            double v = valueOf(amount);
            if (v <= 0) {
                preview.setText("本月各项预算合计 ¥" + Ui.money(plan) + " · 已花 ¥" + Ui.money(spent));
            } else {
                double left = Store.round2(v - spent);
                preview.setText("已花 ¥" + Ui.money(spent) + " → 还能花 ¥" + Ui.money(Math.max(0, left))
                        + "（其中已规划 ¥" + Ui.money(plan) + "）");
            }
        };
        watch(amount, refresh);

        addBlock(body, field("金额（元）", amountBox(amount)), 16);
        addBlock(body, preview, 16);
        refresh.run();

        addBlock(body, primary("保存", v -> {
            double val = Store.round2(valueOf(amount));
            if (!(val > 0)) {
                toast("填一个大于 0 的金额");
                return;
            }
            store.ensureMonth(ym).allowance = val;
            store.save();
            finish();
            toast("已设置：本月可用 ¥" + Ui.money(val));
        }), 16);

        if (cur != null) {
            addBlock(body, danger("不用总额度，只按项目合计", v -> {
                store.ensureMonth(ym).allowance = null;
                store.save();
                finish();
                toast("已改为按项目合计");
            }), 10);
        }

        show("本月可用金额", body);
    }
}
