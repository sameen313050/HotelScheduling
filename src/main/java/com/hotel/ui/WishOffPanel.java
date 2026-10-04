package com.hotel.ui;

import com.hotel.dao.EmployeeDao;
import com.hotel.dao.WishOffDao;
import com.hotel.model.Employee;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 希望休录入面板：月历视图，每天一个复选框
 */
public class WishOffPanel extends JPanel {

    private final EmployeeDao employeeDao = new EmployeeDao();
    private final WishOffDao wishOffDao = new WishOffDao();

    private JComboBox<String> yearBox;
    private JComboBox<String> monthBox;
    private JComboBox<Employee> employeeBox;
    private JPanel calendarPanel;
    private JLabel countLabel;

    // 当月每一天的复选框：Map<日期字符串, 复选框>
    private final Map<String, JCheckBox> dateChecks = new HashMap<>();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] WEEK_NAMES = {"日", "月", "火", "水", "木", "金", "土"};

    public WishOffPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 顶部控件区
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
        for (int m = 1; m <= 12; m++) {
            monthBox.addItem(m + " 月");
        }
        monthBox.setSelectedIndex(LocalDate.now().getMonthValue() - 1);
        topPanel.add(monthBox);

        topPanel.add(new JLabel("员工："));
        employeeBox = new JComboBox<>();
        employeeBox.setPreferredSize(new Dimension(200, 28));
        topPanel.add(employeeBox);
        reloadEmployees();  // 抽出来的加载方法

        add(topPanel, BorderLayout.NORTH);

        // 中间月历
        calendarPanel = new JPanel();
        JScrollPane scroll = new JScrollPane(calendarPanel);
        scroll.setBorder(BorderFactory.createTitledBorder("月历（勾选 = 希望休）"));
        add(scroll, BorderLayout.CENTER);

        // 底部统计 + 按钮
        JPanel bottomPanel = new JPanel(new BorderLayout());

        countLabel = new JLabel("当月希望休数：0 天");
        countLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        countLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        bottomPanel.add(countLabel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearBtn = new JButton("全部清空");
        JButton saveBtn = new JButton("保存当月");
        buttonPanel.add(clearBtn);
        buttonPanel.add(saveBtn);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        // 事件
        yearBox.addActionListener(e -> rebuildCalendar());
        monthBox.addActionListener(e -> rebuildCalendar());
        employeeBox.addActionListener(e -> rebuildCalendar());
        saveBtn.addActionListener(e -> saveCurrent());
        clearBtn.addActionListener(e -> clearAll());

        rebuildCalendar();
    }

    private void rebuildCalendar() {
        calendarPanel.removeAll();
        dateChecks.clear();

        Employee selectedEmployee = (Employee) employeeBox.getSelectedItem();
        if (selectedEmployee == null) {
            calendarPanel.add(new JLabel("没有在职员工，请先在「员工管理」添加"));
            calendarPanel.revalidate();
            calendarPanel.repaint();
            updateCountLabel();
            return;
        }

        int year = Integer.parseInt(((String) yearBox.getSelectedItem()).replace(" 年", "").trim());
        int month = Integer.parseInt(((String) monthBox.getSelectedItem()).replace(" 月", "").trim());
        YearMonth ym = YearMonth.of(year, month);
        int daysInMonth = ym.lengthOfMonth();
        LocalDate firstDay = ym.atDay(1);
        int firstDayOfWeek = firstDay.getDayOfWeek().getValue() % 7;

        String yearMonthStr = String.format("%04d-%02d", year, month);
        Set<String> existing = wishOffDao.findByEmployeeAndMonth(selectedEmployee.getId(), yearMonthStr);

        calendarPanel.setLayout(new GridLayout(0, 7, 5, 5));

        // 星期标题
        for (int i = 0; i < 7; i++) {
            JLabel weekLabel = new JLabel(WEEK_NAMES[i], SwingConstants.CENTER);
            weekLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
            if (i == 0) weekLabel.setForeground(Color.RED);
            else if (i == 6) weekLabel.setForeground(Color.BLUE);
            calendarPanel.add(weekLabel);
        }

        // 1 号之前的空格
        for (int i = 0; i < firstDayOfWeek; i++) {
            calendarPanel.add(new JLabel(""));
        }

        // 每一天的复选框单元格
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = ym.atDay(day);
            String dateStr = date.format(DATE_FMT);

            JPanel dayCell = new JPanel();
            dayCell.setLayout(new BoxLayout(dayCell, BoxLayout.Y_AXIS));
            dayCell.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

            JLabel dayLabel = new JLabel(String.valueOf(day), SwingConstants.CENTER);
            dayLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            int weekday = date.getDayOfWeek().getValue() % 7;
            if (weekday == 0) dayLabel.setForeground(Color.RED);
            else if (weekday == 6) dayLabel.setForeground(Color.BLUE);
            dayCell.add(dayLabel);

            JCheckBox check = new JCheckBox("希望休");
            check.setSelected(existing.contains(dateStr));
            check.setAlignmentX(Component.CENTER_ALIGNMENT);
            check.addActionListener(e -> updateCountLabel());
            dayCell.add(check);

            dateChecks.put(dateStr, check);
            calendarPanel.add(dayCell);
        }

        calendarPanel.revalidate();
        calendarPanel.repaint();
        updateCountLabel();
    }

    /** 更新底部"当月希望休数"统计 */
    private void updateCountLabel() {
        int count = 0;
        for (JCheckBox c : dateChecks.values()) {
            if (c.isSelected()) count++;
        }
        countLabel.setText("当月希望休数：" + count + " 天");
    }

    private void saveCurrent() {
        Employee selectedEmployee = (Employee) employeeBox.getSelectedItem();
        if (selectedEmployee == null) return;

        int year = Integer.parseInt(((String) yearBox.getSelectedItem()).replace(" 年", "").trim());
        int month = Integer.parseInt(((String) monthBox.getSelectedItem()).replace(" 月", "").trim());
        String yearMonthStr = String.format("%04d-%02d", year, month);

        Set<String> selectedDates = new HashSet<>();
        for (Map.Entry<String, JCheckBox> entry : dateChecks.entrySet()) {
            if (entry.getValue().isSelected()) {
                selectedDates.add(entry.getKey());
            }
        }

        if (wishOffDao.replaceMonth(selectedEmployee.getId(), yearMonthStr, selectedDates)) {
            JOptionPane.showMessageDialog(this,
                    "已保存员工「" + selectedEmployee.getName() + "」" +
                            yearMonthStr + " 月的 " + selectedDates.size() + " 个希望休日");
        } else {
            JOptionPane.showMessageDialog(this, "保存失败", "错误", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void clearAll() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "确定要清空当月所有希望休吗？\n（点保存后才会真正写入数据库）",
                "确认清空", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        for (JCheckBox c : dateChecks.values()) {
            c.setSelected(false);
        }
        updateCountLabel();
    }

    /** 重新从数据库加载员工列表，保留当前选中项 */
    private void reloadEmployees() {
        Employee currentSelected = (Employee) employeeBox.getSelectedItem();
        int selectedId = (currentSelected == null) ? -1 : currentSelected.getId();

        employeeBox.removeAllItems();
        for (Employee e : employeeDao.findAll()) {
            if (e.isActive()) {
                employeeBox.addItem(e);
                if (e.getId() == selectedId) {
                    employeeBox.setSelectedItem(e);
                }
            }
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        // 每次面板被加到窗口显示时触发一次刷新
        // 用 hierarchy listener 精确监听"变可见"
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) {
                    reloadEmployees();
                }
            }
        });
    }

}

