package com.budgetbook.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 数据存取与计算。
 * 预算分两层：一级分类（有图标，如「饮食」）> 二级项目（没有图标，如「牛奶」「鸡蛋」）。
 * 分类的月预算是它下面所有项目加起来，分类自己不单独设金额。
 *
 * 记账只有日期、金额、备注，不参与分类 —— 预算那边是「计划」，流水那边是「实际」。
 */
class Store {

    static final String[] CYCLES = {"day", "week", "month"};

    /** 二级项目：没有图标 */
    static class Item {
        String id = "", name = "", cycle = "day";
        int times = 1;
        double amount = 0;
    }

    /** 一级分类：有图标 */
    static class Group {
        String id = "", name = "", emoji = "🧾";
        final List<Item> items = new ArrayList<>();
    }

    static class Month {
        Double allowance;                       // null = 没设总额
        final List<Group> groups = new ArrayList<>();
    }

    static class Rec {
        String id = "", date = "", note = "";
        double amount = 0;
        long createdAt = 0;
    }

    final Map<String, Month> months = new TreeMap<>();
    final List<Rec> records = new ArrayList<>();
    String theme = "auto";

    private final SharedPreferences sp;

    Store(Context c) {
        sp = c.getSharedPreferences("budgetbook", Context.MODE_PRIVATE);
        load();
    }

    /* ================= 读写 ================= */

    private void load() {
        String raw = sp.getString("data", null);
        if (raw == null || raw.isEmpty()) return;
        try {
            JSONObject o = new JSONObject(raw);
            theme = o.optString("theme", "auto");

            JSONObject ms = o.optJSONObject("months");
            if (ms != null) {
                for (Iterator<String> it = ms.keys(); it.hasNext(); ) {
                    String k = it.next();
                    JSONObject mo = ms.optJSONObject(k);
                    if (mo == null) continue;
                    Month m = new Month();
                    if (mo.has("allowance") && !mo.isNull("allowance")) m.allowance = mo.optDouble("allowance");
                    JSONArray gs = mo.optJSONArray("groups");
                    if (gs != null) {
                        for (int i = 0; i < gs.length(); i++) {
                            JSONObject go = gs.optJSONObject(i);
                            if (go != null) m.groups.add(groupFrom(go));
                        }
                    } else {
                        // v2 升级：原来是一层平铺的 items，全部收进一个分类里
                        JSONArray arr = mo.optJSONArray("items");
                        if (arr != null && arr.length() > 0) {
                            Group g = new Group();
                            g.id = uid();
                            g.name = "日常开销";
                            g.emoji = "🧾";
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject io = arr.optJSONObject(i);
                                if (io != null) g.items.add(itemFrom(io));
                            }
                            m.groups.add(g);
                        }
                    }
                    months.put(k, m);
                }
            } else if (o.has("items")) {
                // v1 升级：只有一层项目，放进本月的一个分类里
                Month m = new Month();
                JSONArray arr = o.optJSONArray("items");
                if (arr != null && arr.length() > 0) {
                    Group g = new Group();
                    g.id = uid();
                    g.name = "日常开销";
                    g.emoji = "🧾";
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject io = arr.optJSONObject(i);
                        if (io != null) g.items.add(itemFrom(io));
                    }
                    m.groups.add(g);
                }
                if (!m.groups.isEmpty()) months.put(ymNow(), m);
            }

            JSONArray rs = o.optJSONArray("records");
            if (rs != null) {
                for (int i = 0; i < rs.length(); i++) {
                    JSONObject ro = rs.optJSONObject(i);
                    if (ro == null) continue;
                    Rec r = new Rec();
                    r.id = ro.optString("id", uid());
                    r.date = ro.optString("date", "");
                    r.note = ro.optString("note", "");
                    r.amount = ro.optDouble("amount", 0);
                    r.createdAt = ro.optLong("createdAt", 0);
                    if (!r.date.isEmpty()) records.add(r);
                }
            }
        } catch (Exception e) {
            // 数据坏了就当空的处理，绝不让应用起不来
            months.clear();
            records.clear();
        }
    }

    void save() {
        try {
            sp.edit().putString("data", toJson().toString()).apply();
        } catch (Exception ignored) {
        }
    }

    JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("app", "budgetbook");
            o.put("version", 3);
            o.put("theme", theme);

            JSONObject ms = new JSONObject();
            for (Map.Entry<String, Month> e : months.entrySet()) {
                JSONObject mo = new JSONObject();
                if (e.getValue().allowance != null) mo.put("allowance", e.getValue().allowance);
                JSONArray gs = new JSONArray();
                for (Group g : e.getValue().groups) {
                    JSONObject go = new JSONObject();
                    go.put("id", g.id);
                    go.put("name", g.name);
                    go.put("emoji", g.emoji);
                    JSONArray arr = new JSONArray();
                    for (Item it : g.items) arr.put(itemTo(it));
                    go.put("items", arr);
                    gs.put(go);
                }
                mo.put("groups", gs);
                ms.put(e.getKey(), mo);
            }
            o.put("months", ms);

            JSONArray rs = new JSONArray();
            for (Rec r : records) {
                JSONObject ro = new JSONObject();
                ro.put("id", r.id);
                ro.put("date", r.date);
                ro.put("note", r.note);
                ro.put("amount", r.amount);
                ro.put("createdAt", r.createdAt);
                rs.put(ro);
            }
            o.put("records", rs);
        } catch (Exception ignored) {
        }
        return o;
    }

    /** 用外部 JSON 覆盖现有数据（兼容 v1 / v2 / v3 的备份） */
    boolean replaceAll(String raw) {
        try {
            JSONObject o = new JSONObject(raw);
            JSONObject ms = o.optJSONObject("months");
            JSONArray rs = o.optJSONArray("records");
            if (ms == null && o.has("items")) {
                ms = new JSONObject();
                JSONObject only = new JSONObject();
                only.put("items", o.optJSONArray("items"));
                ms.put(ymNow(), only);
            }
            if (ms == null || rs == null) return false;

            months.clear();
            records.clear();
            for (Iterator<String> it = ms.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONObject mo = ms.optJSONObject(k);
                if (mo == null) continue;
                Month m = new Month();
                if (mo.has("allowance") && !mo.isNull("allowance")) m.allowance = mo.optDouble("allowance");
                JSONArray gs = mo.optJSONArray("groups");
                if (gs != null) {
                    for (int i = 0; i < gs.length(); i++) {
                        JSONObject go = gs.optJSONObject(i);
                        if (go != null) m.groups.add(groupFrom(go));
                    }
                } else {
                    JSONArray arr = mo.optJSONArray("items");
                    if (arr != null && arr.length() > 0) {
                        Group g = new Group();
                        g.id = uid();
                        g.name = "日常开销";
                        g.emoji = "🧾";
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject io = arr.optJSONObject(i);
                            if (io != null) g.items.add(itemFrom(io));
                        }
                        m.groups.add(g);
                    }
                }
                months.put(k, m);
            }
            for (int i = 0; i < rs.length(); i++) {
                JSONObject ro = rs.optJSONObject(i);
                if (ro == null) continue;
                Rec r = new Rec();
                r.id = ro.optString("id", uid());
                r.date = ro.optString("date", "");
                r.note = ro.optString("note", "");
                r.amount = ro.optDouble("amount", 0);
                r.createdAt = ro.optLong("createdAt", 0);
                if (!r.date.isEmpty()) records.add(r);
            }
            save();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static Group groupFrom(JSONObject go) {
        Group g = new Group();
        g.id = go.optString("id", uid());
        g.name = go.optString("name", "");
        g.emoji = go.optString("emoji", "🧾");
        JSONArray arr = go.optJSONArray("items");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject io = arr.optJSONObject(i);
                if (io != null) g.items.add(itemFrom(io));
            }
        }
        return g;
    }

    private static Item itemFrom(JSONObject io) {
        Item it = new Item();
        it.id = io.optString("id", uid());
        it.name = io.optString("name", "");
        String cy = io.optString("cycle", "day");
        it.cycle = ("week".equals(cy) || "month".equals(cy)) ? cy : "day";
        it.times = Math.max(1, io.optInt("times", 1));
        it.amount = io.optDouble("amount", 0);
        return it;
    }

    private static JSONObject itemTo(Item it) {
        JSONObject o = new JSONObject();
        try {
            o.put("id", it.id);
            o.put("name", it.name);
            o.put("cycle", it.cycle);
            o.put("times", it.times);
            o.put("amount", it.amount);
        } catch (Exception ignored) {
        }
        return o;
    }

    /* ================= 每个月一份预算 ================= */

    private static Group copyOf(Group s) {
        Group g = new Group();
        g.id = s.id;
        g.name = s.name;
        g.emoji = s.emoji;
        for (Item it : s.items) {
            Item n = new Item();
            n.id = it.id;
            n.name = it.name;
            n.cycle = it.cycle;
            n.times = it.times;
            n.amount = it.amount;
            g.items.add(n);
        }
        return g;
    }

    /**
     * 读某个月的预算。规划是「每个月都一样的」，所以任何一个月没单独建过，
     * 都拿离它最近的已建月份来用 —— 优先取更早的，没有更早的就取之后最早的。
     * （只在内存里算，不落库；真要改这个月了才由 ensureMonth 固化一份。）
     *
     * 注意每个月只是「项目定义」沿用，月总额会按当月天数重算：
     * 「每天 2 元」在 30 天的月是 60，在 31 天的月是 62。
     * 记账那边（已支配）是按日期分月的，天然每月刷新。
     */
    Month monthData(String ym) {
        Month own = months.get(ym);
        if (own != null) return own;
        if (months.isEmpty()) return new Month();

        String src = null;
        for (String k : months.keySet()) {
            if (k.compareTo(ym) < 0) src = k;        // TreeMap，最后留下的就是最近的更早月份
        }
        if (src == null) src = months.keySet().iterator().next();   // 没有更早的，用最早的那个

        Month s = months.get(src);
        Month out = new Month();
        out.allowance = s.allowance;
        for (Group g : s.groups) out.groups.add(copyOf(g));
        return out;
    }

    /** 要改这个月了，先固化下来 */
    Month ensureMonth(String ym) {
        Month m = months.get(ym);
        if (m == null) {
            m = monthData(ym);
            months.put(ym, m);
        }
        return m;
    }

    List<Group> monthGroups(String ym) {
        return monthData(ym).groups;
    }

    Group findGroup(String id, String viewYm) {
        if (id == null || id.isEmpty()) return null;
        for (Group g : monthGroups(viewYm)) if (id.equals(g.id)) return g;
        for (Month m : months.values()) for (Group g : m.groups) if (id.equals(g.id)) return g;
        return null;
    }

    /** 展开成一层，方便算总账 */
    List<Item> monthItems(String ym) {
        List<Item> out = new ArrayList<>();
        for (Group g : monthGroups(ym)) out.addAll(g.items);
        return out;
    }

    int itemCount(String ym) {
        int n = 0;
        for (Group g : monthGroups(ym)) n += g.items.size();
        return n;
    }

    int monthCount() {
        return months.size();
    }

    /* ================= 计算 ================= */

    static double periodsIn(String ym, String cycle) {
        int d = daysInMonth(ym);
        if ("day".equals(cycle)) return d;
        if ("week".equals(cycle)) return d / 7.0;
        return 1;
    }

    static double itemPlan(Item it, String ym) {
        return round2(it.amount * Math.max(1, it.times) * periodsIn(ym, it.cycle));
    }

    static double groupPlan(Group g, String ym) {
        double s = 0;
        for (Item it : g.items) s += itemPlan(it, ym);
        return round2(s);
    }

    List<Rec> monthRecords(String ym) {
        List<Rec> out = new ArrayList<>();
        for (Rec r : records) {
            if (r.date.length() >= 7 && r.date.startsWith(ym)) out.add(r);
        }
        return out;
    }

    double monthPlan(String ym) {
        double s = 0;
        for (Group g : monthGroups(ym)) s += groupPlan(g, ym);
        return round2(s);
    }

    double monthSpent(String ym) {
        double s = 0;
        for (Rec r : monthRecords(ym)) s += r.amount;
        return round2(s);
    }

    double daySpent(String ds) {
        double s = 0;
        for (Rec r : records) if (ds.equals(r.date)) s += r.amount;
        return round2(s);
    }

    static int daysLeft(String ym) {
        String t = ymNow();
        if (ym.compareTo(t) < 0) return 0;
        if (ym.compareTo(t) > 0) return daysInMonth(ym);
        Calendar c = Calendar.getInstance();
        return daysInMonth(ym) - c.get(Calendar.DAY_OF_MONTH) + 1;
    }

    static String cycleDesc(Item it) {
        String unit;
        if ("week".equals(it.cycle)) unit = "每周 ";
        else if ("month".equals(it.cycle)) unit = "每月 ";
        else unit = "每天 ";
        String a = "¥" + money(it.amount);
        return unit + (it.times > 1 ? it.times + " 次 × " + a : a);
    }

    static String previewText(String cycle, int times, double amount, String ym) {
        int days = daysInMonth(ym);
        int t = Math.max(1, times);
        double total = amount * t * periodsIn(ym, cycle);
        String per = t > 1 ? t + " 次 × ¥" + money(amount) : "¥" + money(amount);
        String head = ("week".equals(cycle) ? "每周 " : "month".equals(cycle) ? "每月 " : "每天 ") + per;
        String formula;
        if ("day".equals(cycle)) formula = "本月 " + days + " 天";
        else if ("week".equals(cycle)) formula = "本月约 " + money(days / 7.0) + " 周";
        else formula = "按月固定";
        return head + " → " + formula + "，预计 ¥" + money(round2(total));
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    static String money(double v) {
        return Ui.money(v);
    }

    /* ================= 日期 ================= */

    static String ymNow() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
    }

    static String today() {
        Calendar c = Calendar.getInstance();
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    /**
     * 在某个月里记账时，默认日期该填哪天。
     * 必须是「那个月」的日子 —— 否则翻到 10 月记一笔，日期还是今天(9月26)，
     * 这笔账就会落回 9 月去。
     * 当月就用今天；别的月份用「今天几号」推到那个月，超出当月天数就取月末。
     */
    static String defaultDateFor(String ym) {
        if (ym.equals(ymNow())) return today();
        Calendar c = Calendar.getInstance();
        int day = Math.min(c.get(Calendar.DAY_OF_MONTH), daysInMonth(ym));
        return ym + "-" + (day < 10 ? "0" + day : String.valueOf(day));
    }

    static int daysInMonth(String ym) {
        int y = Integer.parseInt(ym.substring(0, 4));
        int m = Integer.parseInt(ym.substring(5, 7));
        return new GregorianCalendar(y, m, 0).getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    static String shiftMonth(String ym, int k) {
        int y = Integer.parseInt(ym.substring(0, 4));
        int m = Integer.parseInt(ym.substring(5, 7));
        Calendar c = new GregorianCalendar(y, m - 1, 1);
        c.add(Calendar.MONTH, k);
        return String.format(Locale.US, "%04d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
    }

    static String monthLabel(String ym) {
        return Integer.parseInt(ym.substring(0, 4)) + " 年 " + Integer.parseInt(ym.substring(5, 7)) + " 月";
    }

    /** 1 号是星期几 → 周一开头的偏移量 */
    static int mondayOffset(String ym) {
        int y = Integer.parseInt(ym.substring(0, 4));
        int m = Integer.parseInt(ym.substring(5, 7));
        int dow = new GregorianCalendar(y, m - 1, 1).get(Calendar.DAY_OF_WEEK);
        return (dow + 5) % 7;
    }

    static String dayLabel(String ds) {
        if (ds.equals(today())) return "今天";
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, -1);
        String y = String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        if (ds.equals(y)) return "昨天";
        int mo = Integer.parseInt(ds.substring(5, 7));
        int d = Integer.parseInt(ds.substring(8, 10));
        return mo + " 月 " + d + " 日 " + weekdayCN(ds);
    }

    static String weekdayCN(String ds) {
        int y = Integer.parseInt(ds.substring(0, 4));
        int m = Integer.parseInt(ds.substring(5, 7));
        int d = Integer.parseInt(ds.substring(8, 10));
        String[] w = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        return w[new GregorianCalendar(y, m - 1, d).get(Calendar.DAY_OF_WEEK) - 1];
    }

    static String uid() {
        return Long.toString(System.currentTimeMillis(), 36) + Integer.toString((int) (Math.random() * 1296), 36);
    }

    static List<Rec> sortedDesc(List<Rec> in) {
        List<Rec> out = new ArrayList<>(in);
        Collections.sort(out, (a, b) -> {
            int c = b.date.compareTo(a.date);
            if (c != 0) return c;
            return Long.compare(b.createdAt, a.createdAt);
        });
        return out;
    }
}
