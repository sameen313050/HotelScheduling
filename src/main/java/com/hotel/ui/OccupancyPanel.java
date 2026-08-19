package com.hotel.ui;

import com.hotel.dao.FacilityDao;
import com.hotel.dao.OccupancyDao;
import com.hotel.model.Facility;
import com.hotel.model.Occupancy;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * CI/CO/稼働率录入面板（月历视图，每天 3 个输入框）
 */
public class OccupancyPanel extends JPanel {

    private final FacilityDao facilityDao = new FacilityDao();
    private final OccupancyDao occupancyDao = new OccupancyDao();

    private JComboBox<String> yearBox;
    private JComboBox<String> monthBox;
    private JComboBox<Facility> facilityBox;
    private JPanel calendarPanel;
    private JScrollPane calendarScroll;

    /** 每天的三个输入框：日期 → [稼働率, CI, CO] */
    private final Map<String, JTextField[]> dateFields = new HashMap<>();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] WEEK_NAMES = {"日", "月", "火", "水", "木", "金", "土"};

    public OccupancyPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // ===== 顶部区域：包含选择器 + 图例说明 =====
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));

        // 第一行：选择器 + 按钮
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        controlPanel.add(new JLabel("年份："));
        yearBox = new JComboBox<>();
        int currentYear = LocalDate.now().getYear();
        for (int y = currentYear - 1; y <= currentYear + 3; y++) {
            yearBox.addItem(y + " 年");
        }
        yearBox.setSelectedItem(currentYear + " 年");
        controlPanel.add(yearBox);

        controlPanel.add(new JLabel("月份："));
        monthBox = new JComboBox<>();
        for (int m = 1; m <= 12; m++) monthBox.addItem(m + " 月");
        monthBox.setSelectedIndex(LocalDate.now().getMonthValue() - 1);
        controlPanel.add(monthBox);

        controlPanel.add(new JLabel("设施："));
        facilityBox = new JComboBox<>();
        facilityBox.setPreferredSize(new Dimension(200, 28));
        for (Facility f : facilityDao.findAll()) facilityBox.addItem(f);
        controlPanel.add(facilityBox);

        JButton saveBtn = new JButton("保存当月");
        controlPanel.add(saveBtn);

        JButton fillAllBtn = new JButton("整月填同值");
        controlPanel.add(fillAllBtn);

        northPanel.add(controlPanel);

        // 第二行：图例说明
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JLabel legend = new JLabel("<html>每天 3 个数字：<b>稼働率</b>(%) | <b>CI</b>(入住) | <b>CO</b>(退房)。留空表示未录入。</html>");
        legend.setForeground(Color.DARK_GRAY);
        legendPanel.add(legend);
        northPanel.add(legendPanel);

        add(northPanel, BorderLayout.NORTH);

        // ===== 中间月历 =====
        calendarPanel = new JPanel();
        calendarScroll = new JScrollPane(calendarPanel);
        calendarScroll.setBorder(BorderFactory.createTitledBorder("月历"));
        add(calendarScroll, BorderLayout.CENTER);

        // 事件绑定
        yearBox.addActionListener(e -> rebuildCalendar());
        monthBox.addActionListener(e -> rebuildCalendar());
        facilityBox.addActionListener(e -> rebuildCalendar());
        saveBtn.addActionListener(e -> saveCurrent());
        fillAllBtn.addActionListener(e -> fillAll());

        rebuildCalendar();
    }

    private void rebuildCalendar() {
        calendarPanel.removeAll();
        dateFields.clear();

        Facility selectedFacility = (Facility) facilityBox.getSelectedItem();
        if (selectedFacility == null) {
            calendarPanel.add(new JLabel("请先在「设施管理」添加设施"));
            calendarPanel.revalidate();
            calendarPanel.repaint();
            return;
        }

        int year = Integer.parseInt(((String) yearBox.getSelectedItem()).replace(" 年", "").trim());
        int month = Integer.parseInt(((String) monthBox.getSelectedItem()).replace(" 月", "").trim());

        // 更新月历标题
        calendarScroll.setBorder(BorderFactory.createTitledBorder(
                year + " 年 " + month + " 月 —— " + selectedFacility.getName()));

        YearMonth ym = YearMonth.of(year, month);
        int daysInMonth = ym.lengthOfMonth();
        LocalDate firstDay = ym.atDay(1);
        int firstDayOfWeek = firstDay.getDayOfWeek().getValue() % 7;

        String yearMonthStr = String.format("%04d-%02d", year, month);
        Map<String, Occupancy> existing = occupancyDao.findFullByFacilityAndMonth(selectedFacility.getId(), yearMonthStr);

        calendarPanel.setLayout(new GridLayout(0, 7, 5, 5));

        // 星期表头
        for (int i = 0; i < 7; i++) {
            JLabel weekLabel = new JLabel(WEEK_NAMES[i], SwingConstants.CENTER);
            weekLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
            if (i == 0) weekLabel.setForeground(Color.RED);
            else if (i == 6) weekLabel.setForeground(Color.BLUE);
            calendarPanel.add(weekLabel);
        }

        // 1 号前面的空格
        for (int i = 0; i < firstDayOfWeek; i++) {
            calendarPanel.add(new JLabel(""));
        }

        // 每天单元格
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

            Occupancy existingData = existing.get(dateStr);

            JTextField rateField = createSmallField(
                    existingData != null ? String.valueOf(existingData.getOccupancyRate()) : "", "稼働率 %");
            JTextField ciField = createSmallField(
                    (existingData != null && existingData.getCiCount() != null)
                            ? String.valueOf(existingData.getCiCount()) : "", "CI 入住数");
            JTextField coField = createSmallField(
                    (existingData != null && existingData.getCoCount() != null)
                            ? String.valueOf(existingData.getCoCount()) : "", "CO 退房数");

            dayCell.add(labeledRow("率:", rateField));
            dayCell.add(labeledRow("CI:", ciField));
            dayCell.add(labeledRow("CO:", coField));

            dateFields.put(dateStr, new JTextField[]{rateField, ciField, coField});
            calendarPanel.add(dayCell);
        }

        calendarPanel.revalidate();
        calendarPanel.repaint();
    }

    private JTextField createSmallField(String initialValue, String tooltip) {
        JTextField f = new JTextField(initialValue, 4);
        f.setHorizontalAlignment(JTextField.CENTER);
        f.setMaximumSize(new Dimension(60, 22));
        f.setToolTipText(tooltip);
        return f;
    }

    private JPanel labeledRow(String label, JTextField field) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
        JLabel l = new JLabel(label);
        l.setFont(new Font("SansSerif", Font.PLAIN, 10));
        p.add(l);
        p.add(field);
        p.setAlignmentX(Component.CENTER_ALIGNMENT);
        return p;
    }

    private void saveCurrent() {
        Facility selectedFacility = (Facility) facilityBox.getSelectedItem();
        if (selectedFacility == null) return;

        Map<String, int[]> toSave = new HashMap<>();
        java.util.Set<String> toDelete = new java.util.HashSet<>();  // 需要删除的日期

        // 加载数据库当前状态，用来判断哪些"清空"是真的删除、哪些本来就是空
        int year = Integer.parseInt(((String) yearBox.getSelectedItem()).replace(" 年", "").trim());
        int month = Integer.parseInt(((String) monthBox.getSelectedItem()).replace(" 月", "").trim());
        String yearMonthStr = String.format("%04d-%02d", year, month);
        Map<String, com.hotel.model.Occupancy> existingDb =
                occupancyDao.findFullByFacilityAndMonth(selectedFacility.getId(), yearMonthStr);

        for (Map.Entry<String, JTextField[]> entry : dateFields.entrySet()) {
            String date = entry.getKey();
            JTextField[] fs = entry.getValue();
            String rateStr = fs[0].getText().trim();
            String ciStr = fs[1].getText().trim();
            String coStr = fs[2].getText().trim();

            // 全空表示要删除这天的数据（而不是跳过）
            if (rateStr.isEmpty() && ciStr.isEmpty() && coStr.isEmpty()) {
                toDelete.add(date);
                continue;
            }

            int rate = 0;
            int ci = -1;
            int co = -1;

            if (!rateStr.isEmpty()) {
                try {
                    rate = Integer.parseInt(rateStr);
                    if (rate < 0 || rate > 100) {
                        JOptionPane.showMessageDialog(this,
                                date + " 的稼働率必须在 0-100 之间", "错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this,
                            date + " 的稼働率不是有效数字", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            if (!ciStr.isEmpty()) {
                try {
                    ci = Integer.parseInt(ciStr);
                    if (ci < 0 || ci > 999) {
                        JOptionPane.showMessageDialog(this,
                                date + " 的 CI 必须在 0-999 之间", "错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this,
                            date + " 的 CI 不是有效数字", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            if (!coStr.isEmpty()) {
                try {
                    co = Integer.parseInt(coStr);
                    if (co < 0 || co > 999) {
                        JOptionPane.showMessageDialog(this,
                                date + " 的 CO 必须在 0-999 之间", "错误", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this,
                            date + " 的 CO 不是有效数字", "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            toSave.put(date, new int[]{rate, ci, co});
        }

        int saved = 0;
        for (Map.Entry<String, int[]> entry : toSave.entrySet()) {
            int[] vals = entry.getValue();
            Integer ciVal = vals[1] < 0 ? null : vals[1];
            Integer coVal = vals[2] < 0 ? null : vals[2];
            if (occupancyDao.saveFull(selectedFacility.getId(), entry.getKey(), vals[0], ciVal, coVal)) {
                saved++;
            }
        }

        // 删除清空的日期（只统计"原本有数据"的清空）
        int deleted = 0;
        for (String date : toDelete) {
            boolean hadData = existingDb.containsKey(date);
            if (occupancyDao.deleteOne(selectedFacility.getId(), date) && hadData) {
                deleted++;
            }
        }

        String msg;
        if (saved == 0 && deleted == 0) {
            msg = "没有需要保存或清空的数据";
        } else if (deleted == 0) {
            msg = "已保存 " + saved + " 天的数据";
        } else if (saved == 0) {
            msg = "已清空 " + deleted + " 天的数据";
        } else {
            msg = "已保存 " + saved + " 天，清空 " + deleted + " 天的数据";
        }
        JOptionPane.showMessageDialog(this, msg);
    }

    private void fillAll() {
        JTextField rateField = new JTextField(5);
        JTextField ciField = new JTextField(5);
        JTextField coField = new JTextField(5);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; form.add(new JLabel("稼働率（%，0-100）："), gbc);
        gbc.gridx = 1; form.add(rateField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; form.add(new JLabel("CI 人数："), gbc);
        gbc.gridx = 1; form.add(ciField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; form.add(new JLabel("CO 人数："), gbc);
        gbc.gridx = 1; form.add(coField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        form.add(new JLabel("<html><small>留空的字段不会覆盖已有值。</small></html>"), gbc);

        int result = JOptionPane.showConfirmDialog(this, form, "整月填同值",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String rateStr = rateField.getText().trim();
        String ciStr = ciField.getText().trim();
        String coStr = coField.getText().trim();

        for (JTextField[] fs : dateFields.values()) {
            if (!rateStr.isEmpty()) fs[0].setText(rateStr);
            if (!ciStr.isEmpty()) fs[1].setText(ciStr);
            if (!coStr.isEmpty()) fs[2].setText(coStr);
        }
    }
}