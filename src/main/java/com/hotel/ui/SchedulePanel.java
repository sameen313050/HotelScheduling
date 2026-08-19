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
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * 排班面板：生成排班 + 显示结果
 */
public class SchedulePanel extends JPanel {

    private final ScheduleDao scheduleDao = new ScheduleDao();
    private final EmployeeDao employeeDao = new EmployeeDao();
    private final FacilityDao facilityDao = new FacilityDao();
    private final ShiftTypeDao shiftTypeDao = new ShiftTypeDao();

    private JComboBox<String> yearBox;
    private JComboBox<String> monthBox;
    private JTable scheduleTable;
    private DefaultTableModel tableModel;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] WEEK_NAMES = {"日", "月", "火", "水", "木", "金", "土"};

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

        JButton generateBtn = new JButton("生成排班");
        JButton loadBtn = new JButton("加载已有");
        JButton clearBtn = new JButton("清空当月");
        topPanel.add(generateBtn);
        topPanel.add(loadBtn);
        topPanel.add(clearBtn);

        add(topPanel, BorderLayout.NORTH);

        // 中间表格
        tableModel = new DefaultTableModel(new String[]{"设施"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        scheduleTable = new JTable(tableModel);
        scheduleTable.setRowHeight(60);
        scheduleTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        add(new JScrollPane(scheduleTable), BorderLayout.CENTER);

        // 事件
        generateBtn.addActionListener(e -> generateSchedule());
        loadBtn.addActionListener(e -> loadFromDatabase());
        clearBtn.addActionListener(e -> clearMonth());

        // 初次显示：尝试加载已有
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

    /** 生成排班 */
    private void generateSchedule() {
        int year = getSelectedYear();
        int month = getSelectedMonth();

        // 确认
        int confirm = JOptionPane.showConfirmDialog(this,
                "将为 " + year + " 年 " + month + " 月生成排班草稿。\n" +
                        "如果当月已有排班数据，会被覆盖。\n\n" +
                        "确定继续吗？",
                "确认生成", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        // 跑算法
        SchedulingContext ctx = new SchedulingContext(year, month);
        try {
            ctx.load();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "加载数据失败：" + e.getMessage(),
                    "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 基本检查
        if (ctx.getEmployees().isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有在职员工，请先在「员工管理」添加");
            return;
        }
        if (ctx.getFacilities().isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有设施，请先在「设施管理」添加");
            return;
        }
        if (ctx.getShiftTypes().isEmpty()) {
            JOptionPane.showMessageDialog(this, "没有班次，请先在「班次设置」添加");
            return;
        }

        SchedulingEngine engine = new SchedulingEngine(ctx);
        SchedulingResult result = engine.run();

        // 保存
        if (!scheduleDao.saveMonth(getSelectedYearMonth(), result.getAssignments())) {
            JOptionPane.showMessageDialog(this, "保存失败", "错误", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 显示
        renderSchedule(result.getAssignments());

        // 弹出问题报告
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        ProblemReportDialog dialog = new ProblemReportDialog(owner, result);
        dialog.setVisible(true);
    }

    /** 从数据库加载已有排班 */
    private void loadFromDatabase() {
        List<Assignment> assignments = scheduleDao.loadMonth(
                getSelectedYearMonth(), employeeDao, facilityDao, shiftTypeDao);
        renderSchedule(assignments);
    }

    /** 清空当月排班 */
    private void clearMonth() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "确定清空 " + getSelectedYear() + " 年 " + getSelectedMonth() + " 月的排班？\n" +
                        "此操作不可恢复。",
                "确认清空", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        if (scheduleDao.clearMonth(getSelectedYearMonth())) {
            renderSchedule(new ArrayList<>());
            JOptionPane.showMessageDialog(this, "已清空");
        }
    }

    /**
     * 渲染排班表：横向 = 日期（1-31）， 纵向 = 设施
     * 每个格子显示当天该设施所有员工 + 班次
     */
    private void renderSchedule(List<Assignment> assignments) {
        int year = getSelectedYear();
        int month = getSelectedMonth();
        YearMonth ym = YearMonth.of(year, month);
        int daysInMonth = ym.lengthOfMonth();

        // 建立表头：设施 | 1(日) | 2(月) | ... | 31(N)
        String[] columns = new String[daysInMonth + 1];
        columns[0] = "设施";
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate d = ym.atDay(day);
            int wdIdx = d.getDayOfWeek().getValue() % 7;
            columns[day] = day + "(" + WEEK_NAMES[wdIdx] + ")";
        }

        tableModel.setDataVector(new Object[0][columns.length], columns);

        // 每设施一行
        List<Facility> facilities = facilityDao.findAll();
        for (Facility f : facilities) {
            Object[] row = new Object[columns.length];
            row[0] = f.getName();

            for (int day = 1; day <= daysInMonth; day++) {
                LocalDate date = ym.atDay(day);
                // 找当天该设施的所有分配
                List<Assignment> cellAssignments = new ArrayList<>();
                for (Assignment a : assignments) {
                    if (a.getDate().equals(date) && a.getFacility().getId() == f.getId()) {
                        cellAssignments.add(a);
                    }
                }

                // 格式化：员工名(班次) 换行
                StringBuilder cell = new StringBuilder("<html>");
                // 按班次分组显示
                for (Assignment a : cellAssignments) {
                    cell.append(a.getEmployee().getName())
                            .append("<br><small>")
                            .append(shortShiftName(a.getShiftType()))
                            .append("</small><br>");
                }
                cell.append("</html>");
                row[day] = cell.toString();
            }
            tableModel.addRow(row);
        }

        // 设置列宽
        scheduleTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        for (int i = 1; i < columns.length; i++) {
            scheduleTable.getColumnModel().getColumn(i).setPreferredWidth(80);
        }

        // 根据每格内容量调整行高
        scheduleTable.setRowHeight(Math.max(60, findMaxAssignmentsPerCell(assignments) * 32));
    }

    private String shortShiftName(ShiftType s) {
        // 用班次名和时段简写显示
        return s.getStartTime() + "-" + s.getEndTime();
    }

    private int findMaxAssignmentsPerCell(List<Assignment> assignments) {
        Map<String, Integer> counter = new HashMap<>();
        for (Assignment a : assignments) {
            String key = a.getDate() + "_" + a.getFacility().getId();
            counter.merge(key, 1, Integer::sum);
        }
        int max = 0;
        for (int v : counter.values()) {
            if (v > max) max = v;
        }
        return max;
    }
}