package com.hotel.ui;

import com.hotel.dao.*;
import com.hotel.model.Employee;
import com.hotel.model.Facility;
import com.hotel.model.ShiftType;
import com.hotel.scheduling.Assignment;
import com.hotel.scheduling.SchedulingContext;
import com.hotel.scheduling.SchedulingEngine;
import com.hotel.scheduling.SchedulingResult;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * 排班面板：生成排班 + 显示结果（支持两种视图）
 */
public class SchedulePanel extends JPanel {

    private final ScheduleDao scheduleDao = new ScheduleDao();
    private final EmployeeDao employeeDao = new EmployeeDao();
    private final FacilityDao facilityDao = new FacilityDao();
    private final ShiftTypeDao shiftTypeDao = new ShiftTypeDao();

    private JComboBox<String> yearBox;
    private JComboBox<String> monthBox;
    private JComboBox<String> viewBox;
    private JTable scheduleTable;
    private DefaultTableModel tableModel;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] WEEK_NAMES = {"日", "月", "火", "水", "木", "金", "土"};

    /** 缓存当前显示的分配数据 */
    private List<Assignment> currentAssignments = new ArrayList<>();

    public SchedulePanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 顶部控件
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        topPanel.add(new JLabel("年份："));
        yearBox = new JComboBox<>();
        int currentYear = LocalDate.now().getYear();
        for (int y = currentYear - 1; y <= currentYear + 3; y++) {
            yearBox.addItem(y + " 年");
        }
        yearBox.setSelectedItem(currentYear + " 年");
        topPanel.add(yearBox);

        topPanel.add(new JLabel("月份："));
        monthBox = new JComboBox<>();
        for (int m = 1; m <= 12; m++) monthBox.addItem(m + " 月");
        monthBox.setSelectedIndex(LocalDate.now().getMonthValue() - 1);
        topPanel.add(monthBox);

        topPanel.add(new JLabel("视图："));
        viewBox = new JComboBox<>(new String[]{"按员工看", "按设施看"});
        topPanel.add(viewBox);

        JButton generateBtn = new JButton("生成排班");
        JButton loadBtn = new JButton("加载已有");
        JButton clearBtn = new JButton("清空当月");
        JButton statsBtn = new JButton("查看统计");
        topPanel.add(generateBtn);
        topPanel.add(loadBtn);
        topPanel.add(clearBtn);
        topPanel.add(statsBtn);

        add(topPanel, BorderLayout.NORTH);

        // 中间表格
        tableModel = new DefaultTableModel(new String[]{""}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        scheduleTable = new JTable(tableModel);
        scheduleTable.setRowHeight(40);
        scheduleTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        scheduleTable.getTableHeader().setReorderingAllowed(false);

        // 表格渲染器：休息日/周末高亮
        scheduleTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.CENTER);

                if (!isSelected) {
                    // 表头列（第 0 列）用浅灰
                    if (column == 0) {
                        c.setBackground(new Color(240, 240, 240));
                    } else {
                        String cellStr = value == null ? "" : value.toString();
                        // 空白 = 休息 → 淡灰色
                        if (cellStr.isEmpty() || cellStr.equals("休")) {
                            c.setBackground(new Color(250, 250, 250));
                            c.setForeground(Color.LIGHT_GRAY);
                        } else {
                            c.setBackground(Color.WHITE);
                            c.setForeground(Color.BLACK);
                        }
                    }
                }
                return c;
            }
        });

        add(new JScrollPane(scheduleTable), BorderLayout.CENTER);

        // 事件
        generateBtn.addActionListener(e -> generateSchedule());
        loadBtn.addActionListener(e -> loadFromDatabase());
        clearBtn.addActionListener(e -> clearMonth());
        viewBox.addActionListener(e -> renderCurrent());
        statsBtn.addActionListener(e -> showStatistics());
        yearBox.addActionListener(e -> loadFromDatabase());
        monthBox.addActionListener(e -> loadFromDatabase());

        loadFromDatabase();
    }

    private int getSelectedYear() {
        return Integer.parseInt(((String) yearBox.getSelectedItem()).replace(" 年", "").trim());
    }

    private int getSelectedMonth() {
        return Integer.parseInt(((String) monthBox.getSelectedItem()).replace(" 月", "").trim());
    }

    private String getSelectedYearMonth() {
        return String.format("%04d-%02d", getSelectedYear(), getSelectedMonth());
    }

    private void generateSchedule() {
        int year = getSelectedYear();
        int month = getSelectedMonth();

        int confirm = JOptionPane.showConfirmDialog(this,
                "将为 " + year + " 年 " + month + " 月生成排班草稿。\n" +
                        "如果当月已有排班数据，会被覆盖。\n\n确定继续吗？",
                "确认生成", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        SchedulingContext ctx = new SchedulingContext(year, month);
        try {
            ctx.load();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "加载数据失败：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (ctx.getEmployees().isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有在职员工");
            return;
        }
        if (ctx.getFacilities().isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有设施");
            return;
        }
        if (ctx.getShiftTypes().isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有班次");
            return;
        }

        SchedulingEngine engine = new SchedulingEngine(ctx);
        SchedulingResult result = engine.run();

        if (!scheduleDao.saveMonth(getSelectedYearMonth(), result.getAssignments())) {
            JOptionPane.showMessageDialog(this, "保存失败", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        currentAssignments = result.getAssignments();
        renderCurrent();

        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        ProblemReportDialog dialog = new ProblemReportDialog(owner, result);
        dialog.setVisible(true);
    }

    private void loadFromDatabase() {
        currentAssignments = scheduleDao.loadMonth(
                getSelectedYearMonth(), employeeDao, facilityDao, shiftTypeDao);
        renderCurrent();
    }

    private void clearMonth() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "确定清空 " + getSelectedYear() + " 年 " + getSelectedMonth() + " 月的排班？",
                "确认清空", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        if (scheduleDao.clearMonth(getSelectedYearMonth())) {
            currentAssignments = new ArrayList<>();
            renderCurrent();
            JOptionPane.showMessageDialog(this, "已清空");
        }
    }

    /** 根据当前选中的视图渲染 */
    private void renderCurrent() {
        String view = (String) viewBox.getSelectedItem();
        if ("按员工看".equals(view)) {
            renderByEmployee();
        } else {
            renderByFacility();
        }
    }

    /** 视图 A：以员工为行（推荐视图） */
    private void renderByEmployee() {
        int year = getSelectedYear();
        int month = getSelectedMonth();
        YearMonth ym = YearMonth.of(year, month);
        int daysInMonth = ym.lengthOfMonth();

        // 表头：员工姓名 | 1(日) | 2(月) | ...
        String[] columns = new String[daysInMonth + 2];
        columns[0] = "员工";
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate d = ym.atDay(day);
            int wdIdx = d.getDayOfWeek().getValue() % 7;
            columns[day] = day + "(" + WEEK_NAMES[wdIdx] + ")";
        }
        columns[daysInMonth + 1] = "上班天数";

        tableModel.setDataVector(new Object[0][columns.length], columns);

        // 每个员工一行
        List<Employee> employees = employeeDao.findAll();

        // 按员工类型排序：正社员 → アルバイト → 契約社員 → 夜勤専属
        Map<String, Integer> typeOrder = new HashMap<>();
        typeOrder.put("正社員", 1);
        typeOrder.put("アルバイト", 2);
        typeOrder.put("契約社員", 3);
        typeOrder.put("夜勤専属", 4);
        employees.sort((a, b) -> {
            int oa = typeOrder.getOrDefault(a.getEmployeeType(), 99);
            int ob = typeOrder.getOrDefault(b.getEmployeeType(), 99);
            if (oa != ob) return Integer.compare(oa, ob);
            // 同类型按 ID 排（也可以改成按名字）
            return Integer.compare(a.getId(), b.getId());
        });

        for (Employee emp : employees) {
            if (!emp.isActive()) continue;

            Object[] row = new Object[columns.length];
            row[0] = emp.getName();

            int workDays = 0;
            for (int day = 1; day <= daysInMonth; day++) {
                LocalDate date = ym.atDay(day);
                // 找当天该员工的分配
                Assignment found = null;
                for (Assignment a : currentAssignments) {
                    if (a.getDate().equals(date) && a.getEmployee().getId() == emp.getId()) {
                        found = a;
                        break;
                    }
                }

                if (found != null) {
                    row[day] = periodShort(found.getShiftType().getPeriod())
                            + "\n" + found.getFacility().getName().substring(0, Math.min(3, found.getFacility().getName().length()));
                    workDays++;
                } else {
                    row[day] = "";  // 空白 = 休息
                }
            }
            row[daysInMonth + 1] = workDays + " 天";

            tableModel.addRow(row);
        }

        // 列宽
        scheduleTable.getColumnModel().getColumn(0).setPreferredWidth(90);
        for (int i = 1; i <= daysInMonth; i++) {
            scheduleTable.getColumnModel().getColumn(i).setPreferredWidth(55);
        }
        scheduleTable.getColumnModel().getColumn(daysInMonth + 1).setPreferredWidth(70);
        scheduleTable.setRowHeight(45);
    }

    /** 视图 B：以设施为行（原视图） */
    private void renderByFacility() {
        int year = getSelectedYear();
        int month = getSelectedMonth();
        YearMonth ym = YearMonth.of(year, month);
        int daysInMonth = ym.lengthOfMonth();

        String[] columns = new String[daysInMonth + 1];
        columns[0] = "设施";
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate d = ym.atDay(day);
            int wdIdx = d.getDayOfWeek().getValue() % 7;
            columns[day] = day + "(" + WEEK_NAMES[wdIdx] + ")";
        }

        tableModel.setDataVector(new Object[0][columns.length], columns);

        List<Facility> facilities = facilityDao.findAll();
        int maxPerCell = 0;
        for (Facility f : facilities) {
            Object[] row = new Object[columns.length];
            row[0] = f.getName();

            for (int day = 1; day <= daysInMonth; day++) {
                LocalDate date = ym.atDay(day);
                List<Assignment> cellAssignments = new ArrayList<>();
                for (Assignment a : currentAssignments) {
                    if (a.getDate().equals(date) && a.getFacility().getId() == f.getId()) {
                        cellAssignments.add(a);
                    }
                }
                if (cellAssignments.size() > maxPerCell) maxPerCell = cellAssignments.size();

                StringBuilder cell = new StringBuilder("<html>");
                for (Assignment a : cellAssignments) {
                    cell.append(a.getEmployee().getName())
                            .append("<br><small>")
                            .append(periodShort(a.getShiftType().getPeriod()))
                            .append("</small><br>");
                }
                cell.append("</html>");
                row[day] = cell.toString();
            }
            tableModel.addRow(row);
        }

        scheduleTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        for (int i = 1; i < columns.length; i++) {
            scheduleTable.getColumnModel().getColumn(i).setPreferredWidth(80);
        }
        scheduleTable.setRowHeight(Math.max(60, maxPerCell * 32));
    }

    /** 把时段（朝/昼/夕/夜）转成简写 */
    private String periodShort(String period) {
        switch (period) {
            case "朝": return "朝";
            case "昼": return "昼";
            case "夕": return "夕";
            case "夜": return "夜";
            default: return period;
        }
    }

    /** 显示统计报告 */
    private void showStatistics() {
        if (currentAssignments.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "当月还没有排班数据，请先生成或加载排班");
            return;
        }
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        StatisticsDialog dialog = new StatisticsDialog(owner, getSelectedYearMonth(), currentAssignments);
        dialog.setVisible(true);
    }
}