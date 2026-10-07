package com.budgetbook.app;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

/**
 * 课程表数据。
 * 来源：B24级通信工程（4）班 课程表（b24-tx4-schedule.pages.dev），按仙林校区作息。
 *
 * 学期基准：2026-09-07 是第 1 周周一。
 * 一条记录 = 一个「星期几 + 第几大节」，所以同一门课上两次就是两条
 * （和原来网页版的数据结构一致）。
 */
class Courses {

    /** 第 1 周周一 */
    private static final Calendar START = new GregorianCalendar(2026, Calendar.SEPTEMBER, 7);

    static final int WEEKS_TOTAL = 20;

    /** 5 个大节，仙林校区作息 */
    static final String[][] SLOTS = {
            {"1", "08:20", "10:00"},
            {"2", "10:15", "11:55"},
            {"3", "14:10", "15:50"},
            {"4", "16:05", "17:45"},
            {"5", "19:00", "20:40"},
    };

    static final String[] DAY_NAMES = {"", "周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    /** 理论课 / 实验课，决定卡片配色 */
    static final int KIND_THEORY = 0;
    static final int KIND_LAB = 1;

    static class Course {
        String name = "", code = "", room = "", teacher = "";
        int day;              // 1=周一 … 7=周日
        int slot;             // 1..5
        int[] weeks = new int[0];
        String weeksStr = "";
        int kind = KIND_THEORY;
        boolean conflict = false;       // 和别的课时间重叠
        final List<String> conflictWith = new ArrayList<>();
    }

    private static Course c(String name, String code, String room, String teacher,
                            int day, int slot, int[] weeks, String weeksStr, int kind) {
        Course x = new Course();
        x.name = name;
        x.code = code;
        x.room = room;
        x.teacher = teacher;
        x.day = day;
        x.slot = slot;
        x.weeks = weeks;
        x.weeksStr = weeksStr;
        x.kind = kind;
        return x;
    }

    private static int[] w(int from, int to) {
        int[] a = new int[to - from + 1];
        for (int i = 0; i < a.length; i++) a[i] = from + i;
        return a;
    }

    static final Course[] ALL = {
            // ---- 原有课程（来自 b24-tx4-schedule）----
            c("数字逻辑电路", "02003011-03", "教B-107", "谭雪琴", 2, 1,
                    new int[]{1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15}, "1-4,6-15周", KIND_THEORY),
            c("工程高等数学1", "03238021-09", "教B-303", "吴清太", 3, 1,
                    new int[]{1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14}, "1-4,6-14周", KIND_THEORY),
            c("C程序设计与应用", "02345011-04", "教B-406", "荣百川", 5, 1,
                    new int[]{1, 2, 5, 6, 7, 8, 9, 10, 11, 12, 13}, "1-2,5-13周", KIND_THEORY),
            c("转本英语(I)", "05013011-09", "教C-112", "毛启红", 3, 2,
                    new int[]{1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17}, "1-4,6-17周", KIND_THEORY),
            c("数字逻辑电路", "02003011-03", "教B-107", "谭雪琴", 4, 2,
                    new int[]{1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15}, "1-3,5-15周", KIND_THEORY),
            c("工程高等数学1", "03238021-09", "教B-303", "吴清太", 5, 2,
                    new int[]{1, 2, 5, 6, 7, 8, 9, 10, 11, 12, 13}, "1-2,5-13周", KIND_THEORY),
            c("C程序设计与应用", "02345011-04", "教B-406", "荣百川", 1, 3,
                    new int[]{1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14}, "1-4,6-14周", KIND_THEORY),
            c("中国近现代史纲要", "05002021-18", "实训楼-203", "王利娟", 2, 3,
                    new int[]{1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12}, "1-4,6-12周", KIND_THEORY),
            c("模拟电子线路", "02002011-02", "教B-107", "陈姝君", 4, 3,
                    new int[]{1, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15}, "1-3,5-15周", KIND_THEORY),
            c("模拟电子线路", "02002011-02", "教B-107", "陈姝君", 1, 4,
                    new int[]{1, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15}, "1-4,6-15周", KIND_THEORY),
            c("中国近现代史纲要", "05002021-18", "实训楼-203", "王利娟", 5, 4,
                    new int[]{1, 2, 5, 6, 7, 8, 9, 10, 11}, "1-2,5-11周", KIND_THEORY),

            // ---- 补充 1：数电硬件实验 ----
            // 通知：6-17周周四、周五上午第二大节；6-10周周五晚上。教室和老师未注明。
            c("数电硬件实验", "", "待定", "", 4, 2, w(6, 17), "6-17周", KIND_LAB),
            c("数电硬件实验", "", "待定", "", 5, 2, w(6, 17), "6-17周", KIND_LAB),
            c("数电硬件实验", "", "待定", "", 5, 5, w(6, 10), "6-10周", KIND_LAB),

            // ---- 补充 2：C程序设计与应用 实验课（学校机房 教C-215）----
            // 6-11周周一第二大节；12-14周周一第一大节、第二大节。共 12 大节。
            c("C程序设计实验", "02345011-04", "教C-215", "", 1, 2, w(6, 14), "6-14周", KIND_LAB),
            c("C程序设计实验", "02345011-04", "教C-215", "", 1, 1, w(12, 14), "12-14周", KIND_LAB),
    };

    /* 启动时算一遍：哪些课时间重叠 */
    static {
        for (int i = 0; i < ALL.length; i++) {
            for (int j = i + 1; j < ALL.length; j++) {
                Course a = ALL[i], b = ALL[j];
                if (a.day != b.day || a.slot != b.slot) continue;
                if (a.name.equals(b.name)) continue;         // 同一门课自己不算
                if (!overlap(a.weeks, b.weeks)) continue;
                a.conflict = true;
                b.conflict = true;
                a.conflictWith.add(b.name);
                b.conflictWith.add(a.name);
            }
        }
    }

    private static boolean overlap(int[] a, int[] b) {
        for (int x : a) for (int y : b) if (x == y) return true;
        return false;
    }

    /** 这两门课重叠的周次，用于详情里写清楚 */
    static List<Integer> overlapWeeks(Course a, Course b) {
        List<Integer> out = new ArrayList<>();
        for (int x : a.weeks) {
            for (int y : b.weeks) {
                if (x == y && !out.contains(x)) out.add(x);
            }
        }
        java.util.Collections.sort(out);
        return out;
    }

    /** 某一周、某一天、某一大节有哪些课 */
    static List<Course> at(int day, int slot, int week) {
        List<Course> out = new ArrayList<>();
        for (Course x : ALL) {
            if (x.day != day || x.slot != slot) continue;
            for (int ww : x.weeks) {
                if (ww == week) {
                    out.add(x);
                    break;
                }
            }
        }
        return out;
    }

    /** 今天是第几周（1..WEEKS_TOTAL）。开学前算第 1 周 */
    static int currentWeek() {
        Calendar t = Calendar.getInstance();
        Calendar a = new GregorianCalendar(t.get(Calendar.YEAR), t.get(Calendar.MONTH), t.get(Calendar.DAY_OF_MONTH));
        long days = (a.getTimeInMillis() - START.getTimeInMillis()) / 86400000L;
        if (days < 0) return 1;
        int wk = (int) (days / 7) + 1;
        return Math.max(1, Math.min(WEEKS_TOTAL, wk));
    }

    /** 今天星期几（1=周一 … 7=周日） */
    static int todayDay() {
        int dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);   // 1=周日
        return dow == Calendar.SUNDAY ? 7 : dow - 1;
    }

    /** 第 week 周、星期 day 的日期，形如 9/7 */
    static String dateLabel(int week, int day) {
        Calendar c = new GregorianCalendar(START.get(Calendar.YEAR), START.get(Calendar.MONTH), START.get(Calendar.DAY_OF_MONTH));
        c.add(Calendar.DAY_OF_MONTH, (week - 1) * 7 + (day - 1));
        return (c.get(Calendar.MONTH) + 1) + "/" + c.get(Calendar.DAY_OF_MONTH);
    }

    static String slotTime(int slot) {
        return SLOTS[slot - 1][1] + "-" + SLOTS[slot - 1][2];
    }

    static String slotSections(int slot) {
        int a = (slot - 1) * 2 + 1;
        return a + "-" + (a + 1) + "节";
    }

    /** 「1-4,6-15周」这种，用来给周次数组生成说明文字 */
    static String weeksText(int[] weeks) {
        if (weeks.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        int start = weeks[0], prev = weeks[0];
        for (int i = 1; i <= weeks.length; i++) {
            int cur = i < weeks.length ? weeks[i] : Integer.MIN_VALUE;
            if (cur != prev + 1) {
                if (sb.length() > 0) sb.append(',');
                sb.append(start == prev ? String.valueOf(start) : start + "-" + prev);
                start = cur;
            }
            prev = cur;
        }
        return sb + "周";
    }

    static String weekdayLabel(int day) {
        return DAY_NAMES[Math.max(1, Math.min(7, day))];
    }

    static String weekLabel(int week) {
        return String.format(Locale.CHINA, "第 %d 周", week);
    }
}
