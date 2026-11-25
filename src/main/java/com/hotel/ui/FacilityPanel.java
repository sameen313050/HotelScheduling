package com.hotel.ui;

import com.hotel.dao.FacilityDao;
import com.hotel.model.Facility;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * 设施管理面板
 * 施設管理パネル
 * Facility management panel
 *
 * <p>提供设施 CRUD 操作，并支持为每个设施配置需求规则（跳转到 FacilityDemandDialog）。</p>
 * <p>施設の CRUD 操作を提供、各施設の需要設定（FacilityDemandDialog へ遷移）も可能。</p>
 * <p>Provides CRUD operations for facilities, and allows configuring demand rules per facility (opens FacilityDemandDialog).</p>
 */
public class FacilityPanel extends JPanel {

    private final FacilityDao facilityDao = new FacilityDao();
    private DefaultTableModel tableModel;
    private JTable table;

    /**
     * 构造方法：初始化 UI 并加载数据
     * コンストラクタ：UI 初期化とデータ読み込み
     * Constructor: initializes UI and loads data
     */
    public FacilityPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 顶部按钮区 / 上部ボタンエリア / Top button area
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton("新規追加");
        JButton editBtn = new JButton("編集");
        JButton deleteBtn = new JButton("削除");
        JButton refreshBtn = new JButton("更新");
        JButton demandBtn = new JButton("需要設定");

        topPanel.add(addBtn);
        topPanel.add(editBtn);
        topPanel.add(deleteBtn);
        topPanel.add(refreshBtn);
        topPanel.add(demandBtn);
        add(topPanel, BorderLayout.NORTH);

        // 中间表格 / 中央テーブル / Center table
        String[] columns = {"ID", "名称", "備考"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // 表格只读，编辑通过弹窗进行
                // テーブルは読み取り専用、編集はダイアログで実施
                // Table is read-only; edits are made via dialog
                return false;
            }
        };
        table = new JTable(tableModel);
        table.getColumnModel().getColumn(0).setPreferredWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(200);
        table.getColumnModel().getColumn(2).setPreferredWidth(400);

        add(new JScrollPane(table), BorderLayout.CENTER);

        // 绑定按钮事件 / ボタンイベントバインド / Bind button events
        addBtn.addActionListener(e -> showEditDialog(null));
        editBtn.addActionListener(e -> editSelected());
        deleteBtn.addActionListener(e -> deleteSelected());
        refreshBtn.addActionListener(e -> loadData());
        demandBtn.addActionListener(e -> showDemandDialog());

        // 双击行 → 编辑
        // 行ダブルクリック → 編集
        // Double-click row → edit
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    editSelected();
                }
            }
        });

        // 初始加载数据 / 初期データロード / Initial data load
        loadData();
    }

    /**
     * 加载数据填充表格
     * データを読み込みテーブルに表示
     * Load data into the table
     */
    private void loadData() {
        tableModel.setRowCount(0);
        List<Facility> facilities = facilityDao.findAll();
        for (Facility f : facilities) {
            tableModel.addRow(new Object[]{f.getId(), f.getName(), f.getNote()});
        }
    }

    /**
     * 编辑选中行 / 選択行を編集 / Edit selected row
     */
    private void editSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "行を選択してください");
            return;
        }
        Facility f = new Facility();
        f.setId((int) tableModel.getValueAt(row, 0));
        f.setName((String) tableModel.getValueAt(row, 1));
        f.setNote((String) tableModel.getValueAt(row, 2));
        showEditDialog(f);
    }

    /**
     * 删除选中行（含二次确认）
     * 選択行を削除（確認ダイアログあり）
     * Delete selected row (with confirmation)
     */
    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "行を選択してください");
            return;
        }
        int id = (int) tableModel.getValueAt(row, 0);
        String name = (String) tableModel.getValueAt(row, 1);

        int confirm = JOptionPane.showConfirmDialog(this,
                "施設「" + name + "」を削除してもよろしいですか？\n"
                        + "注意：関連するシフト設定・稼働率・シフト表データにも影響します。",
                "削除確認",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            if (facilityDao.delete(id)) {
                loadData();
                JOptionPane.showMessageDialog(this, "削除しました");
            } else {
                JOptionPane.showMessageDialog(this, "削除に失敗しました", "エラー", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 显示新增/编辑对话框
     * 新規・編集ダイアログを表示
     * Show add/edit dialog
     *
     * @param facility null 表示新增，非 null 表示编辑
     *                 null なら新規、非 null なら編集
     *                 null means adding, non-null means editing
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
        formPanel.add(new JLabel("施設名："), gbc);
        gbc.gridx = 1;
        formPanel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("備考："), gbc);
        gbc.gridx = 1;
        formPanel.add(noteField, gbc);

        int result = JOptionPane.showConfirmDialog(this, formPanel,
                isNew ? "施設の新規追加" : "施設の編集",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "施設名は必須です", "エラー", JOptionPane.ERROR_MESSAGE);
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
                JOptionPane.showMessageDialog(this, "保存に失敗しました", "エラー", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 显示设施需求配置对话框
     * 施設需要設定ダイアログを表示
     * Show the facility demand configuration dialog
     */
    private void showDemandDialog() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "施設を選択してください");
            return;
        }
        Facility f = new Facility();
        f.setId((int) tableModel.getValueAt(row, 0));
        f.setName((String) tableModel.getValueAt(row, 1));
        f.setNote((String) tableModel.getValueAt(row, 2));

        // 找到父窗口（顶层 Frame）作为对话框的 owner
        // 親ウィンドウ（トップレベル Frame）をダイアログの owner に設定
        // Find the parent window (top-level Frame) as the dialog's owner
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        FacilityDemandDialog dialog = new FacilityDemandDialog(owner, f);
        dialog.setVisible(true);
    }
}