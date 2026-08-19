package com.hotel.ui;

import com.hotel.dao.FacilityDemandDao;
import com.hotel.dao.ShiftTypeDao;
import com.hotel.model.Facility;
import com.hotel.model.FacilityDemand;
import com.hotel.model.ShiftType;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设施需求配置对话框
 * 显示某个设施的所有需求配置，支持增删改
 */
public class FacilityDemandDialog extends JDialog {

    private final Facility facility;
    private final FacilityDemandDao demandDao = new FacilityDemandDao();
    private final ShiftTypeDao shiftTypeDao = new ShiftTypeDao();

    private DefaultTableModel tableModel;
    private JTable table;
    private List<ShiftType> allShifts;

    public FacilityDemandDialog(Frame owner, Facility facility) {
        super(owner, "设施需求配置：" + facility.getName(), true);  // true 表示模态对话框
        this.facility = facility;
        this.allShifts = shiftTypeDao.findAll();

        setSize(800, 500);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        // 顶部说明
        JLabel info = new JLabel("""
                <html>配置规则：每行表示「在某稼働率区间内，该班次需要多少人」<br>
                例：早番 8-17，稼働率 50-79%，需 2 人 → 排班时若当日稼働率在该区间，则需安排 2 人</html>
                """);
        info.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        add(info, BorderLayout.NORTH);

        // 中间表格
        String[] columns = {"ID", "班次", "判断依据", "范围下限", "范围上限", "所需人数"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(40);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(100);
        table.getColumnModel().getColumn(4).setPreferredWidth(100);
        table.getColumnModel().getColumn(5).setPreferredWidth(80);

        add(new JScrollPane(table), BorderLayout.CENTER);

        // 底部按钮
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton("新增需求");
        JButton editBtn = new JButton("编辑");
        JButton deleteBtn = new JButton("删除");
        JButton closeBtn = new JButton("关闭");

        bottomPanel.add(addBtn);
        bottomPanel.add(editBtn);
        bottomPanel.add(deleteBtn);
        bottomPanel.add(Box.createHorizontalStrut(30));  // 间隔
        bottomPanel.add(closeBtn);
        add(bottomPanel, BorderLayout.SOUTH);

        addBtn.addActionListener(e -> showEditForm(null));
        editBtn.addActionListener(e -> editSelected());
        deleteBtn.addActionListener(e -> deleteSelected());
        closeBtn.addActionListener(e -> dispose());

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) editSelected();
            }
        });

        // 提示用户：如果没有班次，无法配置
        if (allShifts.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "尚未配置任何班次，请先在「班次设置」页面添加班次。",
                    "提示", JOptionPane.WARNING_MESSAGE);
            addBtn.setEnabled(false);
        }

        loadData();
    }

    private void loadData() {
        tableModel.setRowCount(0);
        List<FacilityDemand> demands = demandDao.findByFacility(facility.getId());
        for (FacilityDemand d : demands) {
            String metric = d.getMetricType();
            String metricLabel;
            String unit;
            switch (metric == null ? "occupancy" : metric) {
                case "ci_count":
                    metricLabel = "CI 人数"; unit = " 人"; break;
                case "co_count":
                    metricLabel = "CO 人数"; unit = " 人"; break;
                default:
                    metricLabel = "稼働率"; unit = "%"; break;
            }
            tableModel.addRow(new Object[]{
                    d.getId(),
                    d.getShiftTypeName(),
                    metricLabel,
                    d.getOccupancyMin() + unit,
                    d.getOccupancyMax() + unit,
                    d.getRequiredCount() + " 人"
            });
        }
    }

    private void editSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一行");
            return;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        for (FacilityDemand d : demandDao.findByFacility(facility.getId())) {
            if (d.getId() == id) {
                showEditForm(d);
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

        int confirm = JOptionPane.showConfirmDialog(this,
                "确定要删除这条需求配置吗？", "确认删除", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            if (demandDao.delete(id)) {
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, "删除失败", "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showEditForm(FacilityDemand demand) {
        boolean isNew = (demand == null);
        if (isNew) {
            demand = new FacilityDemand();
            demand.setFacilityId(facility.getId());
            demand.setShiftTypeId(allShifts.get(0).getId());
            demand.setOccupancyMin(0);
            demand.setOccupancyMax(100);
            demand.setRequiredCount(1);
            demand.setMetricType("occupancy");
        }

        // 班次下拉
        JComboBox<ShiftType> shiftBox = new JComboBox<>();
        Map<Integer, Integer> shiftIdToIndex = new HashMap<>();
        for (int i = 0; i < allShifts.size(); i++) {
            shiftBox.addItem(allShifts.get(i));
            shiftIdToIndex.put(allShifts.get(i).getId(), i);
        }
        Integer idx = shiftIdToIndex.get(demand.getShiftTypeId());
        if (idx != null) shiftBox.setSelectedIndex(idx);

        // 判断依据下拉（3 选 1）
        String[] metricDisplay = {"稼働率（%）", "CI 人数（入住）", "CO 人数（退房）"};
        String[] metricValue = {"occupancy", "ci_count", "co_count"};
        JComboBox<String> metricBox = new JComboBox<>(metricDisplay);
        for (int i = 0; i < metricValue.length; i++) {
            if (metricValue[i].equals(demand.getMetricType())) {
                metricBox.setSelectedIndex(i);
                break;
            }
        }

        // 范围输入
        JLabel minLabel = new JLabel(getRangeLabel(metricBox.getSelectedIndex(), true));
        JLabel maxLabel = new JLabel(getRangeLabel(metricBox.getSelectedIndex(), false));

        JSpinner minSpinner = new JSpinner(new SpinnerNumberModel(demand.getOccupancyMin(), 0, 999, 1));
        JSpinner maxSpinner = new JSpinner(new SpinnerNumberModel(demand.getOccupancyMax(), 0, 999, 1));

        metricBox.addActionListener(ev -> {
            minLabel.setText(getRangeLabel(metricBox.getSelectedIndex(), true));
            maxLabel.setText(getRangeLabel(metricBox.getSelectedIndex(), false));
        });

        JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(demand.getRequiredCount(), 0, 99, 1));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; formPanel.add(new JLabel("班次："), gbc);
        gbc.gridx = 1; formPanel.add(shiftBox, gbc);

        gbc.gridx = 0; gbc.gridy = 1; formPanel.add(new JLabel("判断依据："), gbc);
        gbc.gridx = 1; formPanel.add(metricBox, gbc);

        gbc.gridx = 0; gbc.gridy = 2; formPanel.add(minLabel, gbc);
        gbc.gridx = 1; formPanel.add(minSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 3; formPanel.add(maxLabel, gbc);
        gbc.gridx = 1; formPanel.add(maxSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 4; formPanel.add(new JLabel("所需人数："), gbc);
        gbc.gridx = 1; formPanel.add(countSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        formPanel.add(new JLabel("<html><small>提示：所需人数填 0 表示该条件下不安排任何人。</small></html>"), gbc);

        int result = JOptionPane.showConfirmDialog(this, formPanel,
                isNew ? "新增需求" : "编辑需求",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            int min = (int) minSpinner.getValue();
            int max = (int) maxSpinner.getValue();
            int count = (int) countSpinner.getValue();
            String metric = metricValue[metricBox.getSelectedIndex()];

            if (min > max) {
                JOptionPane.showMessageDialog(this, "下限不能大于上限", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if ("occupancy".equals(metric) && max > 100) {
                JOptionPane.showMessageDialog(this, "稼働率不能超过 100%", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }

            ShiftType selectedShift = (ShiftType) shiftBox.getSelectedItem();
            demand.setShiftTypeId(selectedShift.getId());
            demand.setOccupancyMin(min);
            demand.setOccupancyMax(max);
            demand.setRequiredCount(count);
            demand.setMetricType(metric);

            boolean success = isNew ? demandDao.insert(demand) : demandDao.update(demand);
            if (success) {
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, "保存失败", "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /** 根据判断依据类型返回范围标签文字 */
    private String getRangeLabel(int metricIndex, boolean isMin) {
        String prefix = isMin ? "下限" : "上限";
        switch (metricIndex) {
            case 0: return "稼働率" + prefix + "（%）：";
            case 1: return "CI 人数" + prefix + "：";
            case 2: return "CO 人数" + prefix + "：";
            default: return prefix + "：";
        }
    }
}
