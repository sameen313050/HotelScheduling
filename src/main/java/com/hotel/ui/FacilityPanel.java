package com.hotel.ui;

import com.hotel.dao.FacilityDao;
import com.hotel.model.Facility;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * 设施管理面板
 */
public class FacilityPanel extends JPanel {

    private final FacilityDao facilityDao = new FacilityDao();
    private DefaultTableModel tableModel;
    private JTable table;

    public FacilityPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 顶部按钮区
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton("新增设施");
        JButton editBtn = new JButton("编辑");
        JButton deleteBtn = new JButton("删除");
        JButton refreshBtn = new JButton("刷新");
        JButton demandBtn = new JButton("配置需求");

        topPanel.add(addBtn);
        topPanel.add(editBtn);
        topPanel.add(deleteBtn);
        topPanel.add(refreshBtn);
        topPanel.add(demandBtn);
        add(topPanel, BorderLayout.NORTH);

        // 中间表格
        String[] columns = {"ID", "名称", "备注"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;  // 表格只读，编辑通过弹窗
            }
        };
        table = new JTable(tableModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(400);

        add(new JScrollPane(table), BorderLayout.CENTER);

        // 绑定按钮事件
        addBtn.addActionListener(e -> showEditDialog(null));
        editBtn.addActionListener(e -> editSelected());
        deleteBtn.addActionListener(e -> deleteSelected());
        refreshBtn.addActionListener(e -> loadData());
        demandBtn.addActionListener(e -> showDemandDialog());


        // 双击行也触发编辑
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    editSelected();
                }
            }
        });

        // 初始加载数据
        loadData();


    }

    /**
     * 加载数据填充表格
     */
    private void loadData() {
        tableModel.setRowCount(0);
        List<Facility> facilities = facilityDao.findAll();
        for (Facility f : facilities) {
            tableModel.addRow(new Object[]{f.getId(), f.getName(), f.getNote()});
        }
    }

    /**
     * 编辑选中行
     */
    private void editSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一行");
            return;
        }
        Facility f = new Facility();
        f.setId((int) tableModel.getValueAt(row, 0));
        f.setName((String) tableModel.getValueAt(row, 1));
        f.setNote((String) tableModel.getValueAt(row, 2));
        showEditDialog(f);
    }

    /**
     * 删除选中行
     */
    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一行");
            return;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        String name = (String) tableModel.getValueAt(row, 1);

        int confirm = JOptionPane.showConfirmDialog(this,
                "确定要删除设施「" + name + "」吗？\n注意：相关的班次配置、稼働率、排班数据也会受影响。",
                "确认删除",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            if (facilityDao.delete(id)) {
                loadData();
                JOptionPane.showMessageDialog(this, "删除成功");
            } else {
                JOptionPane.showMessageDialog(this, "删除失败", "错误", JOptionPane.ERROR_MESSAGE);
            }
        }



    }

    /**
     * 显示新增/编辑对话框
     * @param facility null 表示新增，非 null 表示编辑
     */
    private void showEditDialog(Facility facility) {
        boolean isNew = (facility == null);
        if (isNew) {
            facility = new Facility();
        }

        JTextField nameField = new JTextField(facility.getName() == null ? "" : facility.getName(), 20);
        JTextField noteField = new JTextField(facility.getNote() == null ? "" : facility.getNote(), 30);

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("设施名称："), gbc);
        gbc.gridx = 1;
        formPanel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("备注："), gbc);
        gbc.gridx = 1;
        formPanel.add(noteField, gbc);

        int result = JOptionPane.showConfirmDialog(this, formPanel,
                isNew ? "新增设施" : "编辑设施",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "设施名称不能为空", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            facility.setName(name);
            facility.setNote(noteField.getText().trim());

            boolean success;
            if (isNew) {
                success = facilityDao.insert(facility);
            } else {
                success = facilityDao.update(facility);
            }

            if (success) {
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, "保存失败", "错误", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    /** 显示设施需求配置对话框 */
    private void showDemandDialog() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "请先选择一个设施");
            return;
        }
        Facility f = new Facility();
        f.setId((int) tableModel.getValueAt(row, 0));
        f.setName((String) tableModel.getValueAt(row, 1));
        f.setNote((String) tableModel.getValueAt(row, 2));

        // 找到父窗口（顶层 Frame）作为对话框的 owner
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        FacilityDemandDialog dialog = new FacilityDemandDialog(owner, f);
        dialog.setVisible(true);
    }

}
