package com.hotel.ui;

import com.hotel.dao.EmployeeDao;
import com.hotel.dao.FacilityDao;
import com.hotel.model.Employee;
import com.hotel.model.Facility;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 员工管理面板
 */
public class EmployeePanel extends JPanel {

    private final EmployeeDao employeeDao = new EmployeeDao();
    private final FacilityDao facilityDao = new FacilityDao();
    private DefaultTableModel tableModel;
    private JTable table;

    // 员工类型：数据库存日文，界面显示"日文（中文）"对照
    private static final String[] TYPE_DB_VALUES = {"正社員", "契約社員", "アルバイト", "夜勤専属"};
    private static final String[] TYPE_DISPLAY = {"正社員（正社员）", "契約社員（契约社员）", "アルバイト（兼职）", "夜勤専属（专职夜勤）"};

    public EmployeePanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 顶部按钮区
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton("新增员工");
        JButton editBtn = new JButton("编辑");
        JButton deleteBtn = new JButton("删除");
        JButton refreshBtn = new JButton("刷新");

        topPanel.add(addBtn);
        topPanel.add(editBtn);
        topPanel.add(deleteBtn);
        topPanel.add(refreshBtn);
        add(topPanel, BorderLayout.NORTH);

        // 表格
        String[] columns = {"ID", "姓名", "类型", "月工时上限", "周工时上限", "在职", "可工作设施", "备注"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(150);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setPreferredWidth(90);
        table.getColumnModel().getColumn(5).setPreferredWidth(50);
        table.getColumnModel().getColumn(6).setPreferredWidth(200);
        table.getColumnModel().getColumn(7).setPreferredWidth(150);

        add(new JScrollPane(table), BorderLayout.CENTER);

        // 事件
        addBtn.addActionListener(e -> showEditDialog(null));
        editBtn.addActionListener(e -> editSelected());
        deleteBtn.addActionListener(e -> deleteSelected());
        refreshBtn.addActionListener(e -> loadData());

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) editSelected();
            }
        });

        loadData();
    }

    private void loadData() {
        tableModel.setRowCount(0);

        // 先加载所有设施，用于 ID→名称 的转换
        Map<Integer, String> facilityMap = new HashMap<>();
        for (Facility f : facilityDao.findAll()) {
            facilityMap.put(f.getId(), f.getName());
        }

        List<Employee> employees = employeeDao.findAll();
        for (Employee e : employees) {
            // 把员工的设施 ID 列表转成名称字符串
            StringBuilder facilityNames = new StringBuilder();
            if (e.getFacilityIds().isEmpty()) {
                facilityNames.append("⚠️ 未配置");
            } else {
                for (Integer fid : e.getFacilityIds()) {
                    if (facilityNames.length() > 0) facilityNames.append(", ");
                    facilityNames.append(facilityMap.getOrDefault(fid, "?"));
                }
            }

            tableModel.addRow(new Object[]{
                    e.getId(),
                    e.getName(),
                    typeToDisplay(e.getEmployeeType()),
                    e.getMonthlyHourLimit() == null ? "—" : e.getMonthlyHourLimit() + " h",
                    e.getWeeklyHourLimit() == null ? "—" : e.getWeeklyHourLimit() + " h",
                    e.isActive() ? "✓" : "✗",
                    facilityNames.toString(),
                    e.getNote() == null ? "" : e.getNote()
            });
        }
    }

    private String typeToDisplay(String dbValue) {
        for (int i = 0; i < TYPE_DB_VALUES.length; i++) {
            if (TYPE_DB_VALUES[i].equals(dbValue)) return TYPE_DISPLAY[i];
        }
        return dbValue;
    }

    private void editSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一行");
            return;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        for (Employee e : employeeDao.findAll()) {
            if (e.getId() == id) {
                showEditDialog(e);
                return;
            }
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
                "确定要删除员工「" + name + "」吗？\n注意：相关的希望休、排班数据也会受影响。",
                "确认删除", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            if (employeeDao.delete(id)) {
                loadData();
                JOptionPane.showMessageDialog(this, "删除成功");
            } else {
                JOptionPane.showMessageDialog(this, "删除失败", "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showEditDialog(Employee employee) {
        boolean isNew = (employee == null);
        if (isNew) {
            employee = new Employee();
            employee.setActive(true);
            employee.setEmployeeType(TYPE_DB_VALUES[0]);
        }

        // 表单字段
        JTextField nameField = new JTextField(employee.getName() == null ? "" : employee.getName(), 20);

        JComboBox<String> typeBox = new JComboBox<>(TYPE_DISPLAY);
        for (int i = 0; i < TYPE_DB_VALUES.length; i++) {
            if (TYPE_DB_VALUES[i].equals(employee.getEmployeeType())) {
                typeBox.setSelectedIndex(i);
                break;
            }
        }

        JTextField monthlyField = new JTextField(
                employee.getMonthlyHourLimit() == null ? "" : String.valueOf(employee.getMonthlyHourLimit()), 8);
        JTextField weeklyField = new JTextField(
                employee.getWeeklyHourLimit() == null ? "" : String.valueOf(employee.getWeeklyHourLimit()), 8);

        JCheckBox activeBox = new JCheckBox("在职", employee.isActive());
        JTextField noteField = new JTextField(employee.getNote() == null ? "" : employee.getNote(), 30);

        // 可工作设施 - 复选框列表
        List<Facility> facilities = facilityDao.findAll();
        Map<Integer, JCheckBox> facilityCheckboxes = new HashMap<>();
        JPanel facilityPanel = new JPanel();
        facilityPanel.setLayout(new BoxLayout(facilityPanel, BoxLayout.Y_AXIS));
        facilityPanel.setBorder(BorderFactory.createTitledBorder("可工作设施（默认不勾选）"));

        if (facilities.isEmpty()) {
            facilityPanel.add(new JLabel("（暂无设施，请先去设施管理页面添加）"));
        } else {
            for (Facility f : facilities) {
                JCheckBox cb = new JCheckBox(f.getName(), employee.getFacilityIds().contains(f.getId()));
                facilityCheckboxes.put(f.getId(), cb);
                facilityPanel.add(cb);
            }

            // 全选/全不选按钮
            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton selectAllBtn = new JButton("全选");
            JButton deselectAllBtn = new JButton("全不选");
            selectAllBtn.addActionListener(ev -> facilityCheckboxes.values().forEach(cb -> cb.setSelected(true)));
            deselectAllBtn.addActionListener(ev -> facilityCheckboxes.values().forEach(cb -> cb.setSelected(false)));
            btnPanel.add(selectAllBtn);
            btnPanel.add(deselectAllBtn);
            facilityPanel.add(btnPanel);
        }
        JScrollPane facilityScroll = new JScrollPane(facilityPanel);
        facilityScroll.setPreferredSize(new Dimension(400, 180));

        // 整体表单布局
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        gbc.gridx = 0; gbc.gridy = row; formPanel.add(new JLabel("姓名："), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; formPanel.add(nameField, gbc); gbc.gridwidth = 1;

        row++;
        gbc.gridx = 0; gbc.gridy = row; formPanel.add(new JLabel("类型："), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; formPanel.add(typeBox, gbc); gbc.gridwidth = 1;

        row++;
        gbc.gridx = 0; gbc.gridy = row; formPanel.add(new JLabel("月工时上限："), gbc);
        gbc.gridx = 1; formPanel.add(monthlyField, gbc);
        gbc.gridx = 2; formPanel.add(new JLabel("留空表示不限"), gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; formPanel.add(new JLabel("周工时上限："), gbc);
        gbc.gridx = 1; formPanel.add(weeklyField, gbc);
        gbc.gridx = 2; formPanel.add(new JLabel("外国人签证常见 28"), gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; formPanel.add(new JLabel("备注："), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; formPanel.add(noteField, gbc); gbc.gridwidth = 1;

        row++;
        gbc.gridx = 1; gbc.gridy = row; formPanel.add(activeBox, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        formPanel.add(facilityScroll, gbc);

        int result = JOptionPane.showConfirmDialog(this, formPanel,
                isNew ? "新增员工" : "编辑员工",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "姓名不能为空", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 验证并解析工时（可空）
            Integer monthly = parseHourLimit(monthlyField.getText().trim(), "月工时上限");
            if (monthlyField.getText().trim().length() > 0 && monthly == null) return;
            Integer weekly = parseHourLimit(weeklyField.getText().trim(), "周工时上限");
            if (weeklyField.getText().trim().length() > 0 && weekly == null) return;

            // 收集勾选的设施 ID
            List<Integer> selectedFids = new ArrayList<>();
            for (Map.Entry<Integer, JCheckBox> entry : facilityCheckboxes.entrySet()) {
                if (entry.getValue().isSelected()) selectedFids.add(entry.getKey());
            }

            // 检查：未勾选任何设施时给予警告
            if (selectedFids.isEmpty()) {
                int warn = JOptionPane.showConfirmDialog(this,
                        "未指定任何可工作设施，该员工将无法被排入任何班次。\n是否仍要保存？",
                        "提示", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (warn != JOptionPane.YES_OPTION) return;
            }

            employee.setName(name);
            employee.setEmployeeType(TYPE_DB_VALUES[typeBox.getSelectedIndex()]);
            employee.setMonthlyHourLimit(monthly);
            employee.setWeeklyHourLimit(weekly);
            employee.setActive(activeBox.isSelected());
            employee.setNote(noteField.getText().trim());
            employee.setFacilityIds(selectedFids);

            boolean success = isNew ? employeeDao.insert(employee) : employeeDao.update(employee);

            if (success) {
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, "保存失败", "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /** 解析工时输入；空字符串返回 null（表示不限）；非法返回 null 并弹错 */
    private Integer parseHourLimit(String input, String fieldName) {
        if (input == null || input.isEmpty()) return null;
        try {
            int val = Integer.parseInt(input);
            if (val < 0 || val > 999) {
                JOptionPane.showMessageDialog(this, fieldName + " 应为 0-999 之间的整数", "错误", JOptionPane.ERROR_MESSAGE);
                return null;
            }
            return val;
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, fieldName + " 必须是整数", "错误", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }
}
