package com.hotel.ui;

import com.hotel.dao.EmployeeDao;
import com.hotel.dao.FacilityDao;
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
 */
public class StatisticsDialog extends JDialog {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // 员工类型显示顺序
    private static final Map<String, Integer> TYPE_ORDER = new HashMap<>();
    static {
        TYPE_ORDER.put("正社員", 1);
        TYPE_ORDER.put("アルバイト", 2);
        TYPE_ORDER.put("契約社員", 3);
        TYPE_ORDER.put("夜勤専属", 4);
    }

    public StatisticsDialog(Frame owner, String yearMonth, List<Assignment> assignments) {
        super(owner, "排班统计 —— " + yearMonth, true);
        setSize(1100, 700);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        // 顶部：总体维度
        add(buildOverallPanel(assignments), BorderLayout.NORTH);

        // 中间：员工维度 + 设施维度（左右分栏）
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("员工维度", buildEmployeePanel(yearMonth, assignments));
        tabs.addTab("设施维度", buildFacilityPanel(assignments));
        add(tabs, BorderLayout.CENTER);

        // 底部关闭
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("关闭");
        closeBtn.addActionListener(e -> dispose());
        bottom.add(closeBtn);
        add(bottom, BorderLayout.SOUTH);
    }

    // ===== 总体维度 =====
    private JPanel buildOverallPanel(List<Assignment> assignments) {
        EmployeeDao empDao = new EmployeeDao();
        List<Employee> allEmployees = new ArrayList<>();
        for (Employee e : empDao.findAll()) {
            if (e.isActive()) allEmployees.add(e);
        }

        // 统计每人上班天数
        Map<Integer, Set<LocalDate>> workDaysByEmp = new HashMap<>();
        for (Assignment a : assignments) {
            workDaysByEmp.computeIfAbsent(a.getEmployee().getId(), k -> new HashSet<>())
                    .add(a.getDate());
        }

        int totalEmployees = allEmployees.size();
        int usedEmployees = workDaysByEmp.size();
        int unusedEmployees = totalEmployees - usedEmployees;

        int totalDays = 0;
        int maxDays = 0;
        int minDays = Integer.MAX_VALUE;
        String maxName = "";
        String minName = "";

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
        }

        double avgDays = totalEmployees == 0 ? 0 : (double) totalDays / totalEmployees;

        JPanel panel = new JPanel(new GridLayout(2, 4, 10, 5));
        panel.setBorder(BorderFactory.createTitledBorder("总体统计"));

        panel.add(makeStatCard("在职员工", totalEmployees + " 人"));
        panel.add(makeStatCard("参与排班", usedEmployees + " 人"));
        panel.add(makeStatCard("未被排上", unusedEmployees + " 人",
                unusedEmployees > 0 ? Color.RED : null));
        panel.add(makeStatCard("总分配班次", assignments.size() + " 次"));

        panel.add(makeStatCard("平均上班天数", String.format("%.1f 天", avgDays)));
        panel.add(makeStatCard("最多上班", maxDays + " 天 (" + maxName + ")"));
        panel.add(makeStatCard("最少上班",
                (minDays == Integer.MAX_VALUE ? 0 : minDays) + " 天 (" + minName + ")"));
        panel.add(makeStatCard("天数差距",
                (maxDays - (minDays == Integer.MAX_VALUE ? 0 : minDays)) + " 天",
                (maxDays - (minDays == Integer.MAX_VALUE ? 0 : minDays)) > 5 ? new Color(200, 130, 0) : null));

        return panel;
    }

    private JPanel makeStatCard(String label, String value) {
        return makeStatCard(label, value, null);
    }

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

    // ===== 员工维度 =====
    private JPanel buildEmployeePanel(String yearMonth, List<Assignment> assignments) {
        EmployeeDao empDao = new EmployeeDao();
        WishOffDao wishDao = new WishOffDao();

        List<Employee> allEmployees = new ArrayList<>();
        for (Employee e : empDao.findAll()) {
            if (e.isActive()) allEmployees.add(e);
        }

        // 按类型排序
        allEmployees.sort((a, b) -> {
            int oa = TYPE_ORDER.getOrDefault(a.getEmployeeType(), 99);
            int ob = TYPE_ORDER.getOrDefault(b.getEmployeeType(), 99);
            if (oa != ob) return Integer.compare(oa, ob);
            return Integer.compare(a.getId(), b.getId());
        });

        String[] columns = {"姓名", "类型", "上班天数", "总工时", "目标工时", "缺口", "早", "中", "晚", "夜", "希望休满足", "备注"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };

        // 平均天数用于判断"太多/太少"
        Map<Integer, Set<LocalDate>> workDaysByEmp = new HashMap<>();
        for (Assignment a : assignments) {
            workDaysByEmp.computeIfAbsent(a.getEmployee().getId(), k -> new HashSet<>())
                    .add(a.getDate());
        }

        for (Employee e : allEmployees) {
            int days = workDaysByEmp.getOrDefault(e.getId(), Collections.emptySet()).size();

            // 统计每种时段的次数
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

            // 希望休满足率 + 未满足的具体日期
            Set<String> wishDates = wishDao.findByEmployeeAndMonth(e.getId(), yearMonth);
            int wishTotal = wishDates.size();
            int wishMet = 0;
            List<String> unmetDates = new ArrayList<>();  // 未满足的日期列表
            Set<LocalDate> workDates = workDaysByEmp.getOrDefault(e.getId(), Collections.emptySet());
            for (String wishDateStr : wishDates) {
                LocalDate wishDate = LocalDate.parse(wishDateStr, DATE_FMT);
                if (!workDates.contains(wishDate)) {
                    wishMet++;
                } else {
                    // 希望休了但被排班了 → 记下来
                    unmetDates.add(wishDateStr.substring(5));  // 只取 "MM-DD"
                }
            }
            Collections.sort(unmetDates);
            String wishText = wishTotal == 0 ? "—" : wishMet + " / " + wishTotal;

            // 计算目标工时和缺口（只对正社员有意义）
            String targetText = "—";
            String gapText = "—";
            boolean shortOfTarget = false;
            if ("正社員".equals(e.getEmployeeType())) {
                int target = getTargetHoursForRegular(yearMonth);
                double actual = totalMinutes / 60.0;
                double gap = actual - target;
                targetText = target + " h";
                gapText = String.format("%+.1f h", gap);
                if (gap < -1) {
                    shortOfTarget = true;
                }
            }

            // 备注：工时超限、天数异常、目标缺口
            StringBuilder note = new StringBuilder();
            if (e.getMonthlyHourLimit() != null && totalMinutes > e.getMonthlyHourLimit() * 60) {
                note.append("⚠️月工时超限 ");
            }
            if (days == 0) {
                note.append("⚠️未排班 ");
            }
            if (shortOfTarget) {
                note.append("⚠️工时不足 ");
            }
            if (!unmetDates.isEmpty()) {
                note.append("希望休未满足(");
                for (int i = 0; i < unmetDates.size(); i++) {
                    if (i > 0) note.append("、");
                    String dateShort = unmetDates.get(i);
                    // 找出这天被排的班次和设施
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
                    days + " 天",
                    String.format("%.1f h", totalMinutes / 60.0),
                    targetText,
                    gapText,
                    cntAsa, cntHiru, cntYuu, cntYoru,
                    wishText,
                    note.toString()
            });
        }

        JTable table = new JTable(model);
        table.setRowHeight(26);

        // 单元格渲染：未排班/超限用红字
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(column == 0 || column == 1 || column == 11 ? SwingConstants.LEFT : SwingConstants.CENTER);

                if (!isSelected) {
                    c.setBackground(Color.WHITE);
                    c.setForeground(Color.BLACK);
                    // 备注列有 ⚠️ 就红字
                    Object noteVal = table.getValueAt(row, 11);
                    if (noteVal != null && noteVal.toString().contains("⚠️")) {
                        c.setForeground(Color.RED);
                    }
                }
                return c;
            }
        });

        table.getColumnModel().getColumn(0).setPreferredWidth(80);   // 姓名
        table.getColumnModel().getColumn(1).setPreferredWidth(80);   // 类型
        table.getColumnModel().getColumn(2).setPreferredWidth(70);   // 上班天数
        table.getColumnModel().getColumn(3).setPreferredWidth(70);   // 总工时
        table.getColumnModel().getColumn(4).setPreferredWidth(70);   // 目标工时
        table.getColumnModel().getColumn(5).setPreferredWidth(70);   // 缺口
        table.getColumnModel().getColumn(6).setPreferredWidth(40);   // 早
        table.getColumnModel().getColumn(7).setPreferredWidth(40);   // 中
        table.getColumnModel().getColumn(8).setPreferredWidth(40);   // 晚
        table.getColumnModel().getColumn(9).setPreferredWidth(40);   // 夜
        table.getColumnModel().getColumn(10).setPreferredWidth(80);  // 希望休满足
        table.getColumnModel().getColumn(11).setPreferredWidth(250); // 备注

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    // ===== 设施维度 =====
    private JPanel buildFacilityPanel(List<Assignment> assignments) {
        FacilityDao facDao = new FacilityDao();
        List<Facility> facilities = facDao.findAll();

        String[] columns = {"设施", "总班次", "早班", "中班", "晚班", "夜班"};
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

    private String typeShort(String type) {
        if (type == null) return "";
        switch (type) {
            case "正社員": return "正社员";
            case "契約社員": return "契约社员";
            case "アルバイト": return "兼职";
            case "夜勤専属": return "专职夜勤";
            default: return type;
        }
    }

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
     * 计算某月的正社员目标工时（按日本劳基法 40h/周，四舍五入）
     * 28天=160, 29天=166, 30天=172, 31天=177
     */
    private int getTargetHoursForRegular(String yearMonth) {
        try {
            String[] parts = yearMonth.split("-");
            int year = Integer.parseInt(parts[0]);
            int month = Integer.parseInt(parts[1]);
            int daysInMonth = java.time.YearMonth.of(year, month).lengthOfMonth();
            return (int) Math.round(40.0 * daysInMonth / 7.0);
        } catch (Exception e) {
            return 177;  // 默认
        }
    }
}
