package com.hotel.ui;

import com.hotel.dao.EmployeeDao;
import com.hotel.dao.FacilityDao;
import com.hotel.dao.PaidLeaveDao;
import com.hotel.dao.WishOffDao;
import com.hotel.model.Employee;
import com.hotel.model.Facility;
import com.hotel.model.ShiftType;
import com.hotel.scheduling.Assignment;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * 排班统计报告：员工维度 + 设施维度 + 总体维度
 * シフト統計レポート：従業員別 + 施設別 + 全体
 * Schedule statistics report: by employee + by facility + overall
 *
 * <p>核心特性：</p>
 * <ul>
 *   <li><b>合计工时 = 排班工时 + 有休小时</b>（日本劳基法要求，有休算工时）</li>
 *   <li><b>缺口基于合计工时</b>（请了有休的员工缺口自然缩小）</li>
 *   <li><b>视觉强调</b>：工时不足红字、有休蓝字、未排班红字</li>
 * </ul>
 *
 * <p>コア特性：</p>
 * <ul>
 *   <li><b>合計時間 = シフト時間 + 有給時間</b>（労働基準法要求、有給は勤務時間に計上）</li>
 *   <li><b>差分は合計時間基準</b>（有給を取得した従業員は差分が自然に縮小）</li>
 *   <li><b>視覚強調</b>：時間不足は赤字、有給は青字、未配分は赤字</li>
 * </ul>
 */
public class StatisticsDialog extends JDialog {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // 员工类型显示顺序 / 従業員種別の表示順 / Employee type display order
    private static final Map<String, Integer> TYPE_ORDER = new HashMap<>();
    static {
        TYPE_ORDER.put("正社員", 1);
        TYPE_ORDER.put("アルバイト", 2);
        TYPE_ORDER.put("契約社員", 3);
        TYPE_ORDER.put("夜勤専属", 4);
    }

    /**
     * 构造方法：模态对话框，显示指定月份的统计
     * コンストラクタ：モーダルダイアログ、指定月の統計を表示
     * Constructor: modal dialog showing statistics for the given month
     */
    public StatisticsDialog(Frame owner, String yearMonth, List<Assignment> assignments) {
        super(owner, "シフト統計 —— " + yearMonth, true);
        setSize(1200, 700);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        // 顶部：总体卡片 / 上部：全体カード / Top: overall cards
        add(buildOverallPanel(yearMonth, assignments), BorderLayout.NORTH);

        // 中间：员工 / 设施 Tab / 中央：従業員・施設タブ / Center: employee/facility tabs
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("従業員別", buildEmployeePanel(yearMonth, assignments));
        tabs.addTab("施設別", buildFacilityPanel(assignments));
        add(tabs, BorderLayout.CENTER);

        // 底部关闭按钮 / 下部閉じるボタン / Bottom close button
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("閉じる");
        closeBtn.addActionListener(e -> dispose());
        bottom.add(closeBtn);
        add(bottom, BorderLayout.SOUTH);
    }

    // ===== 总体维度 / 全体ビュー / Overall view =====

    /**
     * 构建总体统计面板（8 张卡片）
     * 全体統計パネルを構築（8 枚のカード）
     * Build the overall statistics panel (8 cards)
     */
    private JPanel buildOverallPanel(String yearMonth, List<Assignment> assignments) {
        EmployeeDao empDao = new EmployeeDao();
        PaidLeaveDao paidDao = new PaidLeaveDao();
        List<Employee> allEmployees = new ArrayList<>();
        for (Employee e : empDao.findAll()) {
            if (e.isActive()) allEmployees.add(e);
        }

        // 每人出勤日 Set（用 Set 自动去重）
        // 従業員ごとの出勤日 Set（Set で自動重複排除）
        // Work-day Set per employee (Set auto-dedupes)
        Map<Integer, Set<LocalDate>> workDaysByEmp = new HashMap<>();
        for (Assignment a : assignments) {
            workDaysByEmp.computeIfAbsent(a.getEmployee().getId(), k -> new HashSet<>())
                    .add(a.getDate());
        }

        int totalEmployees = allEmployees.size();
        int usedEmployees = workDaysByEmp.size();
        int unusedEmployees = totalEmployees - usedEmployees;

        // 单次遍历累加所有指标 / 1 回の走査で全指標を累積 / Single pass to accumulate all metrics
        int totalDays = 0;
        int maxDays = 0;
        int minDays = Integer.MAX_VALUE;
        String maxName = "";
        String minName = "";
        int totalPaidLeaveHours = 0;

        for (Employee e : allEmployees) {
            int days = workDaysByEmp.getOrDefault(e.getId(), Collections.emptySet()).size();
            totalDays += days;
            if (days > maxDays) {
                maxDays = days;
                maxName = e.getName();
            }
            if (days < minDays) {
                minDays = days;
                minName = e.getName();
            }
            // 累计有休小时 / 有給時間累積 / Accumulate paid leave hours
            for (Integer h : paidDao.findByEmployeeAndMonth(e.getId(), yearMonth).values()) {
                totalPaidLeaveHours += h;
            }
        }

        double avgDays = totalEmployees == 0 ? 0 : (double) totalDays / totalEmployees;

        JPanel panel = new JPanel(new GridLayout(2, 4, 10, 5));
        panel.setBorder(BorderFactory.createTitledBorder("全体統計"));

        panel.add(makeStatCard("在職従業員", totalEmployees + " 人"));
        panel.add(makeStatCard("シフト参加", usedEmployees + " 人"));
        panel.add(makeStatCard("未配分", unusedEmployees + " 人",
                unusedEmployees > 0 ? Color.RED : null));
        panel.add(makeStatCard("総割当件数", assignments.size() + " 件"));

        panel.add(makeStatCard("平均出勤日数", String.format("%.1f 日", avgDays)));
        panel.add(makeStatCard("最多出勤", maxDays + " 日 (" + maxName + ")"));
        panel.add(makeStatCard("最少出勤",
                (minDays == Integer.MAX_VALUE ? 0 : minDays) + " 日 (" + minName + ")"));
        panel.add(makeStatCard("有休合計", totalPaidLeaveHours + " h",
                totalPaidLeaveHours > 0 ? new Color(0, 120, 180) : null));

        return panel;
    }

    private JPanel makeStatCard(String label, String value) {
        return makeStatCard(label, value, null);
    }

    /**
     * 构造一张统计卡片（标签 + 数值，可选高亮色）
     * 統計カードを構築（ラベル + 数値、ハイライト色オプション）
     * Build a statistics card (label + value, optional highlight color)
     */
    private JPanel makeStatCard(String label, String value, Color highlight) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
        JLabel l = new JLabel(label, SwingConstants.CENTER);
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        l.setForeground(Color.GRAY);
        JLabel v = new JLabel(value, SwingConstants.CENTER);
        v.setFont(new Font("SansSerif", Font.BOLD, 14));
        if (highlight != null) v.setForeground(highlight);
        p.add(l, BorderLayout.NORTH);
        p.add(v, BorderLayout.CENTER);
        return p;
    }

    // ===== 员工维度 / 従業員別ビュー / By-employee view =====

    /**
     * 构建员工维度表格（14 列详细信息）
     * 従業員別テーブルを構築（14 列の詳細情報）
     * Build by-employee table (14 columns of details)
     */
    private JPanel buildEmployeePanel(String yearMonth, List<Assignment> assignments) {
        EmployeeDao empDao = new EmployeeDao();
        WishOffDao wishDao = new WishOffDao();
        PaidLeaveDao paidDao = new PaidLeaveDao();

        List<Employee> allEmployees = new ArrayList<>();
        for (Employee e : empDao.findAll()) {
            if (e.isActive()) allEmployees.add(e);
        }

        // 按类型 → ID 排序 / 種別 → ID 順ソート / Sort by type → ID
        allEmployees.sort((a, b) -> {
            int oa = TYPE_ORDER.getOrDefault(a.getEmployeeType(), 99);
            int ob = TYPE_ORDER.getOrDefault(b.getEmployeeType(), 99);
            if (oa != ob) return Integer.compare(oa, ob);
            return Integer.compare(a.getId(), b.getId());
        });

        String[] columns = {"氏名", "種別", "出勤日数", "シフト時間", "有休", "合計時間", "目標時間", "差分", "朝", "昼", "夕", "夜", "希望休充足", "備考"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };

        Map<Integer, Set<LocalDate>> workDaysByEmp = new HashMap<>();
        for (Assignment a : assignments) {
            workDaysByEmp.computeIfAbsent(a.getEmployee().getId(), k -> new HashSet<>())
                    .add(a.getDate());
        }

        for (Employee e : allEmployees) {
            int days = workDaysByEmp.getOrDefault(e.getId(), Collections.emptySet()).size();

            // 按时段计数 + 累计分钟数（单次遍历）
            // 時間帯別カウント + 分累積（1 回走査）
            // Count by period + accumulate minutes (single pass)
            int cntAsa = 0, cntHiru = 0, cntYuu = 0, cntYoru = 0;
            int totalMinutes = 0;
            for (Assignment a : assignments) {
                if (a.getEmployee().getId() != e.getId()) continue;
                String period = a.getShiftType().getPeriod();
                switch (period) {
                    case "朝": cntAsa++; break;
                    case "昼": cntHiru++; break;
                    case "夕": cntYuu++; break;
                    case "夜": cntYoru++; break;
                }
                totalMinutes += shiftDurationMinutes(a.getShiftType());
            }

            // 累加有休小时数（日本劳基法：有休算工时）
            // 有給時間を累積（労基法：有給は勤務時間に計上）
            // Accumulate paid leave hours (labor law: counts as working hours)
            int paidLeaveHours = 0;
            for (Integer h : paidDao.findByEmployeeAndMonth(e.getId(), yearMonth).values()) {
                paidLeaveHours += h;
            }

            double scheduledHours = totalMinutes / 60.0;
            double totalHoursWithPaidLeave = scheduledHours + paidLeaveHours;

            // 希望休满足情况 + 未满足日期列表
            // 希望休充足状況 + 未達日付リスト
            // Wish-off fulfillment + unmet date list
            Set<String> wishDates = wishDao.findByEmployeeAndMonth(e.getId(), yearMonth);
            int wishTotal = wishDates.size();
            int wishMet = 0;
            List<String> unmetDates = new ArrayList<>();
            Set<LocalDate> workDates = workDaysByEmp.getOrDefault(e.getId(), Collections.emptySet());
            for (String wishDateStr : wishDates) {
                LocalDate wishDate = LocalDate.parse(wishDateStr, DATE_FMT);
                if (!workDates.contains(wishDate)) {
                    wishMet++;
                } else {
                    // 只保留月-日部分（11 月 15 日 → "11-15"）
                    // 月-日部分のみ保持（11 月 15 日 → "11-15"）
                    // Keep only month-day (Nov 15 → "11-15")
                    unmetDates.add(wishDateStr.substring(5));
                }
            }
            Collections.sort(unmetDates);
            String wishText = wishTotal == 0 ? "—" : wishMet + " / " + wishTotal;

            // 计算目标工时和缺口（仅正社员；缺口基于合计工时 = 含有休）
            // 目標時間と差分を計算（正社員のみ、差分は合計時間基準 = 有給込）
            // Target hours and gap (regular only; gap based on total incl. paid leave)
            String targetText = "—";
            String gapText = "—";
            boolean shortOfTarget = false;
            if ("正社員".equals(e.getEmployeeType())) {
                int target = getTargetHoursForRegular(yearMonth);
                double gap = totalHoursWithPaidLeave - target;
                targetText = target + " h";
                gapText = String.format("%+.1f h", gap);
                if (gap < -1) {
                    shortOfTarget = true;
                }
            }

            // 备注：3 种警告 + 希望休未满足明细
            // 備考：3 種類の警告 + 希望休未達詳細
            // Notes: 3 warnings + wish-off unmet details
            StringBuilder note = new StringBuilder();
            if (e.getMonthlyHourLimit() != null && totalHoursWithPaidLeave > e.getMonthlyHourLimit()) {
                note.append("[!]月間時間超過 ");
            }
            if (days == 0 && paidLeaveHours == 0) {
                note.append("[!]未配分 ");
            }
            if (shortOfTarget) {
                note.append("[!]時間不足 ");
            }
            if (!unmetDates.isEmpty()) {
                note.append("希望休未達(");
                for (int i = 0; i < unmetDates.size(); i++) {
                    if (i > 0) note.append("、");
                    String dateShort = unmetDates.get(i);
                    // 把 MM-DD 还原成 LocalDate，再找出当天的班次详情
                    // MM-DD から LocalDate 復元、当日のシフト詳細を検索
                    // Restore MM-DD → LocalDate, then find shift details for that day
                    LocalDate d = LocalDate.parse(yearMonth + "-" + dateShort.substring(3));
                    for (Assignment a : assignments) {
                        if (a.getEmployee().getId() == e.getId() && a.getDate().equals(d)) {
                            note.append(dateShort)
                                    .append("[")
                                    .append(a.getFacility().getName())
                                    .append(a.getShiftType().getPeriod())
                                    .append("]");
                            break;
                        }
                    }
                }
                note.append(") ");
            }

            model.addRow(new Object[]{
                    e.getName(),
                    typeShort(e.getEmployeeType()),
                    days + " 日",
                    String.format("%.1f h", scheduledHours),
                    paidLeaveHours == 0 ? "—" : paidLeaveHours + " h",
                    String.format("%.1f h", totalHoursWithPaidLeave),
                    targetText,
                    gapText,
                    cntAsa, cntHiru, cntYuu, cntYoru,
                    wishText,
                    note.toString()
            });
        }

        JTable table = new JTable(model);
        table.setRowHeight(26);

        // 单元格渲染：备注含 [!] → 红字；有休列蓝字
        // セルレンダラー：備考に [!] → 赤字、有給列は青字
        // Cell renderer: notes with [!] → red text; paid leave column → blue text
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                // 姓名/类型/备注列左对齐，其他居中
                // 氏名・種別・備考列は左寄せ、他は中央
                // Name/type/notes left-aligned, others centered
                setHorizontalAlignment(column == 0 || column == 1 || column == 13 ? SwingConstants.LEFT : SwingConstants.CENTER);

                if (!isSelected) {
                    c.setBackground(Color.WHITE);
                    c.setForeground(Color.BLACK);
                    Object noteVal = table.getValueAt(row, 13);
                    if (noteVal != null && noteVal.toString().contains("[!]")) {
                        c.setForeground(Color.RED);
                    }
                    // 有休列用蓝色（视觉上和其他数字区分）
                    // 有給列は青色（他の数字と視覚的に区別）
                    // Paid leave column in blue (visually distinct from other numbers)
                    if (column == 4) {
                        Object v = table.getValueAt(row, 4);
                        if (v != null && !v.toString().equals("—")) {
                            c.setForeground(new Color(0, 120, 180));
                        }
                    }
                }
                return c;
            }
        });

        // 列宽设置 / 列幅設定 / Column width
        table.getColumnModel().getColumn(0).setPreferredWidth(70);   // 氏名
        table.getColumnModel().getColumn(1).setPreferredWidth(70);   // 種別
        table.getColumnModel().getColumn(2).setPreferredWidth(60);   // 出勤日数
        table.getColumnModel().getColumn(3).setPreferredWidth(70);   // シフト時間
        table.getColumnModel().getColumn(4).setPreferredWidth(55);   // 有休
        table.getColumnModel().getColumn(5).setPreferredWidth(70);   // 合計時間
        table.getColumnModel().getColumn(6).setPreferredWidth(70);   // 目標時間
        table.getColumnModel().getColumn(7).setPreferredWidth(65);   // 差分
        table.getColumnModel().getColumn(8).setPreferredWidth(35);   // 朝
        table.getColumnModel().getColumn(9).setPreferredWidth(35);   // 昼
        table.getColumnModel().getColumn(10).setPreferredWidth(35);  // 夕
        table.getColumnModel().getColumn(11).setPreferredWidth(35);  // 夜
        table.getColumnModel().getColumn(12).setPreferredWidth(75);  // 希望休充足
        table.getColumnModel().getColumn(13).setPreferredWidth(250); // 備考

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    // ===== 设施维度 / 施設別ビュー / By-facility view =====

    /**
     * 构建设施维度表格（每设施按时段统计班次数）
     * 施設別テーブルを構築（施設ごと時間帯別の件数集計）
     * Build by-facility table (count by period per facility)
     */
    private JPanel buildFacilityPanel(List<Assignment> assignments) {
        FacilityDao facDao = new FacilityDao();
        List<Facility> facilities = facDao.findAll();

        String[] columns = {"施設", "総件数", "朝番", "中番", "遅番", "夜番"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };

        for (Facility f : facilities) {
            int total = 0, asa = 0, hiru = 0, yuu = 0, yoru = 0;
            for (Assignment a : assignments) {
                if (a.getFacility().getId() != f.getId()) continue;
                total++;
                switch (a.getShiftType().getPeriod()) {
                    case "朝": asa++; break;
                    case "昼": hiru++; break;
                    case "夕": yuu++; break;
                    case "夜": yoru++; break;
                }
            }
            model.addRow(new Object[]{f.getName(), total, asa, hiru, yuu, yoru});
        }

        JTable table = new JTable(model);
        table.setRowHeight(26);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    /**
     * 员工类型短名（日文客户直接显示日文原文）
     * 従業員種別短縮名（日本顧客向けに日本語そのまま表示）
     * Employee type short name (Japan-customer friendly: keep Japanese as-is)
     */
    private String typeShort(String type) {
        if (type == null) return "";
        switch (type) {
            case "正社員": return "正社員";
            case "契約社員": return "契約社員";
            case "アルバイト": return "アルバイト";
            case "夜勤専属": return "夜勤専属";
            default: return type;
        }
    }

    /**
     * 班次时长（分钟），跨日 +24h
     * シフト時間（分）、日跨ぎは +24h
     * Shift duration in minutes; cross-day adds 24h
     */
    private int shiftDurationMinutes(ShiftType s) {
        String[] start = s.getStartTime().split(":");
        String[] end = s.getEndTime().split(":");
        int startMin = Integer.parseInt(start[0]) * 60 + Integer.parseInt(start[1]);
        int endMin = Integer.parseInt(end[0]) * 60 + Integer.parseInt(end[1]);
        int diff = endMin - startMin;
        if (diff <= 0) diff += 24 * 60;
        return diff;
    }

    /**
     * 计算正社员目标工时（日本劳基法：40h/周 × 月天数 / 7）
     * 正社員の目標時間を算出（労働基準法：40h/週 × 月日数 / 7）
     * Target hours for regular staff (labor law: 40h/week × days in month / 7)
     *
     * 例：31 天月 → 177h、30 天月 → 171h、29 天月 → 166h、28 天月 → 160h
     * 例：31 日月 → 177h、30 日月 → 171h、29 日月 → 166h、28 日月 → 160h
     * Example: 31-day → 177h, 30-day → 171h, 29-day → 166h, 28-day → 160h
     */
    private int getTargetHoursForRegular(String yearMonth) {
        try {
            String[] parts = yearMonth.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int daysInMonth = java.time.YearMonth.of(year, month).lengthOfMonth();
            return (int) Math.round(40.0 * daysInMonth / 7.0);
        } catch (Exception e) {
            return 177;  // 默认 31 天月 / デフォルト 31 日月 / Default to 31-day month
        }
    }
}