package com.hotel.ui;

import com.hotel.dao.ShiftTypeDao;
import com.hotel.model.ShiftType;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * 班次设置面板
 */
public class ShiftTypePanel extends JPanel {

    private final ShiftTypeDao shiftDao = new ShiftTypeDao();
    private DefaultTableModel tableModel;
    private JTable table;

    // 时段选项：数据库存日文，界面显示日中对照
    private static final String[] PERIOD_DB_VALUES = {"朝", "昼", "夕", "夜"};
    private static final String[] PERIOD_DISPLAY = {"朝（早）", "昼（中）", "夕（晚）", "夜（夜）"};

    public ShiftTypePanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 顶部按钮区
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton("新增班次");
        JButton editBtn = new JButton("编辑");
        JButton deleteBtn = new JButton("删除");
        JButton refreshBtn = new JButton("刷新");

        topPanel.add(addBtn);
        topPanel.add(editBtn);
        topPanel.add(deleteBtn);
        topPanel.add(refreshBtn);
        add(topPanel, BorderLayout.NORTH);

        // 表格
        String[] columns = {"ID", "班次名称", "开始时间", "结束时间", "跨日", "时段"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(100);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(60);
        table.getColumnModel().getColumn(5).setPreferredWidth(100);

        add(new JScrollPane(table), BorderLayout.CENTER);

        // 绑定事件
        addBtn.addActionListener(e -> showEditDialog(null));
        editBtn.addActionListener(e -> editSelected());
        deleteBtn.addActionListener(e -> deleteSelected());
        refreshBtn.addActionListener(e -> loadData());

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    editSelected();
                }
            }
        });

        loadData();
    }

    private void loadData() {
        tableModel.setRowCount(0);
        List<ShiftType> shifts = shiftDao.findAll();
        for (ShiftType s : shifts) {
            tableModel.addRow(new Object[]{
                    s.getId(),
                    s.getName(),
                    s.getStartTime(),
                    s.getEndTime(),
                    s.isCrossDay() ? "是" : "否",
                    periodToDisplay(s.getPeriod())
            });
        }
    }

    /** 把数据库里的日文时段转成"日（中）"显示 */
    private String periodToDisplay(String dbValue) {
        for (int i = 0; i < PERIOD_DB_VALUES.length; i++) {
            if (PERIOD_DB_VALUES[i].equals(dbValue)) {
                return PERIOD_DISPLAY[i];
            }
        }
        return dbValue;  // 找不到就原样返回
    }

    private void editSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一行");
            return;
        }
        // 从数据库重新读，避免显示转换造成数据丢失
        int id = (int) tableModel.getValueAt(row, 0);
        ShiftType target = null;
        for (ShiftType s : shiftDao.findAll()) {
            if (s.getId() == id) {
                target = s;
                break;
            }
        }
        if (target != null) {
            showEditDialog(target);
        }
    }

    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一行");
            return;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        String name = (String) tableModel.getValueAt(row, 1);

        int confirm = JOptionPane.showConfirmDialog(this,
                "确定要删除班次「" + name + "」吗？\n注意：相关的设施配置、排班数据也会受影响。",
                "确认删除",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            if (shiftDao.delete(id)) {
                loadData();
                JOptionPane.showMessageDialog(this, "删除成功");
            } else {
                JOptionPane.showMessageDialog(this, "删除失败", "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showEditDialog(ShiftType shift) {
        boolean isNew = (shift == null);
        if (isNew) {
            shift = new ShiftType();
            shift.setPeriod(PERIOD_DB_VALUES[0]);  // 默认朝
        }

        JTextField nameField = new JTextField(shift.getName() == null ? "" : shift.getName(), 20);
        JTextField startField = new JTextField(shift.getStartTime() == null ? "08:00" : shift.getStartTime(), 10);
        JTextField endField = new JTextField(shift.getEndTime() == null ? "17:00" : shift.getEndTime(), 10);
        JCheckBox crossDayBox = new JCheckBox("跨日（夜班从21点上到次日9点请勾选）", shift.isCrossDay());

        // 时段下拉框
        JComboBox<String> periodBox = new JComboBox<>(PERIOD_DISPLAY);
        for (int i = 0; i < PERIOD_DB_VALUES.length; i++) {
            if (PERIOD_DB_VALUES[i].equals(shift.getPeriod())) {
                periodBox.setSelectedIndex(i);
                break;
            }
        }

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("班次名称："), gbc);
        gbc.gridx = 1;
        formPanel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("开始时间："), gbc);
        gbc.gridx = 1;
        formPanel.add(startField, gbc);
        gbc.gridx = 2;
        formPanel.add(new JLabel("格式 HH:MM，如 08:00"), gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("结束时间："), gbc);
        gbc.gridx = 1;
        formPanel.add(endField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("时段分类："), gbc);
        gbc.gridx = 1;
        formPanel.add(periodBox, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 3;
        formPanel.add(crossDayBox, gbc);

        int result = JOptionPane.showConfirmDialog(this, formPanel,
                isNew ? "新增班次" : "编辑班次",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String start = startField.getText().trim();
            String end = endField.getText().trim();

            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "班次名称不能为空", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!isValidTime(start) || !isValidTime(end)) {
                JOptionPane.showMessageDialog(this, "时间格式不正确，应为 HH:MM（如 08:00）", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            shift.setName(name);
            shift.setStartTime(start);
            shift.setEndTime(end);
            shift.setCrossDay(crossDayBox.isSelected());
            shift.setPeriod(PERIOD_DB_VALUES[periodBox.getSelectedIndex()]);

            boolean success = isNew ? shiftDao.insert(shift) : shiftDao.update(shift);

            if (success) {
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, "保存失败", "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /** 简单验证时间格式 HH:MM */
    private boolean isValidTime(String time) {
        if (time == null || !time.matches("\\d{2}:\\d{2}")) {
            return false;
        }
        try {
            String[] parts = time.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            return h >= 0 && h <= 23 && m >= 0 && m <= 59;
        } catch (Exception e) {
            return false;
        }
    }
}
