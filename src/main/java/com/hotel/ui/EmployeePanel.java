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
 * 従業員管理パネル
 * Employee management panel
 *
 * 提供员工 CRUD（增删改查）操作，以及设施分配管理。
 * 従業員の CRUD 操作と、担当施設の割当管理を提供。
 * Provides CRUD operations for employees and facility assignment management.
 */
public class EmployeePanel extends JPanel {

    private final EmployeeDao employeeDao = new EmployeeDao();
    private final FacilityDao facilityDao = new FacilityDao();
    private DefaultTableModel tableModel;
    private JTable table;

    // 员工类型：数据库存日文原文，界面直接显示日文（日本客户友好）
    // 従業員種別：DB には日本語で保存、画面は日本語そのまま表示（日本顧客向け）
    // Employee types: stored as Japanese in DB, displayed as Japanese in UI (Japan-customer friendly)
    private static final String[] TYPE_DB_VALUES = {"正社員", "契約社員", "アルバイト", "夜勤専属"};
    private static final String[] TYPE_DISPLAY = {"正社員", "契約社員", "アルバイト", "夜勤専属"};

    /**
     * 构造方法：初始化 UI 并加载数据
     * コンストラクタ：UI 初期化とデータ読み込み
     * Constructor: initializes UI and loads data
     */
    public EmployeePanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 顶部按钮区 / 上部ボタンエリア / Top button area
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton("新規追加");
        JButton editBtn = new JButton("編集");
        JButton deleteBtn = new JButton("削除");
        JButton refreshBtn = new JButton("更新");

        topPanel.add(addBtn);
        topPanel.add(editBtn);
        topPanel.add(deleteBtn);
        topPanel.add(refreshBtn);
        add(topPanel, BorderLayout.NORTH);

        // 表格 / テーブル / Table
        String[] columns = {"ID", "氏名", "種別", "月間上限", "週間上限", "在職", "勤務可能施設", "備考"};
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

        // 事件绑定 / イベントバインド / Event binding
        addBtn.addActionListener(e -> showEditDialog(null));
        editBtn.addActionListener(e -> editSelected());
        deleteBtn.addActionListener(e -> deleteSelected());
        refreshBtn.addActionListener(e -> loadData());

        // 双击行 → 打开编辑对话框
        // 行ダブルクリック → 編集ダイアログを開く
        // Double-click row → open edit dialog
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) editSelected();
            }
        });

        loadData();
    }

    /**
     * 从数据库加载员工数据到表格
     * DB から従業員データをテーブルへ読み込み
     * Load employee data from DB into the table
     */
    private void loadData() {
        tableModel.setRowCount(0);

        // 先加载所有设施，用于 ID→名称 的转换
        // 全施設を先に読み込み、ID → 名称変換用マップを作成
        // Preload all facilities to build ID → name lookup map
        Map<Integer, String> facilityMap = new HashMap<>();
        for (Facility f : facilityDao.findAll()) {
            facilityMap.put(f.getId(), f.getName());
        }

        List<Employee> employees = employeeDao.findAll();
        for (Employee e : employees) {
            // 把员工的设施 ID 列表转成名称字符串
            // 従業員の施設 ID リストを名称文字列に変換
            // Convert employee's facility ID list to name string
            StringBuilder facilityNames = new StringBuilder();
            if (e.getFacilityIds().isEmpty()) {
                facilityNames.append("[!] 未設定");
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

    /**
     * DB 存储值 → 界面显示值转换
     * DB 保存値 → 画面表示値への変換
     * Convert DB storage value → UI display value
     */
    private String typeToDisplay(String dbValue) {
        for (int i = 0; i < TYPE_DB_VALUES.length; i++) {
            if (TYPE_DB_VALUES[i].equals(dbValue)) return TYPE_DISPLAY[i];
        }
        return dbValue;
    }

    /**
     * 编辑选中行 / 選択行を編集 / Edit the selected row
     */
    private void editSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "行を選択してください");
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

    /**
     * 删除选中行（含二次确认）
     * 選択行を削除（確認ダイアログあり）
     * Delete the selected row (with confirmation)
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
                "従業員「" + name + "」を削除してもよろしいですか？\n"
                        + "注意：関連する希望休・シフト表データにも影響します。",
                "削除確認", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            if (employeeDao.delete(id)) {
                loadData();
                JOptionPane.showMessageDialog(this, "削除しました");
            } else {
                JOptionPane.showMessageDialog(this, "削除に失敗しました", "エラー", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 显示新增/编辑对话框（isNew = true 时为新增）
     * 新規・編集ダイアログを表示（isNew = true なら新規）
     * Show add/edit dialog (isNew = true means adding a new one)
     */
    private void showEditDialog(Employee employee) {
        boolean isNew = (employee == null);
        if (isNew) {
            employee = new Employee();
            employee.setActive(true);
            employee.setEmployeeType(TYPE_DB_VALUES[0]);
        }

        // ===== 表单字段 / フォームフィールド / Form fields =====
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

        JCheckBox activeBox = new JCheckBox("在職中", employee.isActive());
        JTextField noteField = new JTextField(employee.getNote() == null ? "" : employee.getNote(), 30);

        // ===== 可工作设施 - 复选框列表 =====
        // ===== 勤務可能施設 - チェックボックスリスト =====
        // ===== Workable facilities - checkbox list =====
        List<Facility> facilities = facilityDao.findAll();
        Map<Integer, JCheckBox> facilityCheckboxes = new HashMap<>();
        JPanel facilityPanel = new JPanel();
        facilityPanel.setLayout(new BoxLayout(facilityPanel, BoxLayout.Y_AXIS));
        facilityPanel.setBorder(BorderFactory.createTitledBorder("勤務可能施設（デフォルトは未選択）"));

        if (facilities.isEmpty()) {
            facilityPanel.add(new JLabel("（施設が未登録です。先に「施設管理」で追加してください）"));
        } else {
            for (Facility f : facilities) {
                JCheckBox cb = new JCheckBox(f.getName(), employee.getFacilityIds().contains(f.getId()));
                facilityCheckboxes.put(f.getId(), cb);
                facilityPanel.add(cb);
            }

            // 全选/全不选按钮 / 全選択・全解除ボタン / Select-all / Deselect-all buttons
            JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton selectAllBtn = new JButton("全選択");
            JButton deselectAllBtn = new JButton("全解除");
            selectAllBtn.addActionListener(ev -> facilityCheckboxes.values().forEach(cb -> cb.setSelected(true)));
            deselectAllBtn.addActionListener(ev -> facilityCheckboxes.values().forEach(cb -> cb.setSelected(false)));
            btnPanel.add(selectAllBtn);
            btnPanel.add(deselectAllBtn);
            facilityPanel.add(btnPanel);
        }
        JScrollPane facilityScroll = new JScrollPane(facilityPanel);
        facilityScroll.setPreferredSize(new Dimension(400, 180));

        // ===== 整体表单布局 / フォーム全体レイアウト / Overall form layout =====
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        gbc.gridx = 0; gbc.gridy = row; formPanel.add(new JLabel("氏名："), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; formPanel.add(nameField, gbc); gbc.gridwidth = 1;

        row++;
        gbc.gridx = 0; gbc.gridy = row; formPanel.add(new JLabel("種別："), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; formPanel.add(typeBox, gbc); gbc.gridwidth = 1;

        row++;
        gbc.gridx = 0; gbc.gridy = row; formPanel.add(new JLabel("月間上限："), gbc);
        gbc.gridx = 1; formPanel.add(monthlyField, gbc);
        gbc.gridx = 2; formPanel.add(new JLabel("空欄なら制限なし"), gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; formPanel.add(new JLabel("週間上限："), gbc);
        gbc.gridx = 1; formPanel.add(weeklyField, gbc);
        gbc.gridx = 2; formPanel.add(new JLabel("外国人ビザの場合は 28 が一般的"), gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; formPanel.add(new JLabel("備考："), gbc);
        gbc.gridx = 1; gbc.gridwidth = 2; formPanel.add(noteField, gbc); gbc.gridwidth = 1;

        row++;
        gbc.gridx = 1; gbc.gridy = row; formPanel.add(activeBox, gbc);

        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 3;
        formPanel.add(facilityScroll, gbc);

        int result = JOptionPane.showConfirmDialog(this, formPanel,
                isNew ? "従業員の新規追加" : "従業員の編集",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "氏名は必須です", "エラー", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // 验证并解析工时（可空）
            // 勤務時間上限を検証・解析（空欄可）
            // Validate and parse hour limits (blank allowed)
            Integer monthly = parseHourLimit(monthlyField.getText().trim(), "月間上限");
            if (monthlyField.getText().trim().length() > 0 && monthly == null) return;
            Integer weekly = parseHourLimit(weeklyField.getText().trim(), "週間上限");
            if (weeklyField.getText().trim().length() > 0 && weekly == null) return;

            // 收集勾选的设施 ID
            // チェック済み施設 ID を収集
            // Collect checked facility IDs
            List<Integer> selectedFids = new ArrayList<>();
            for (Map.Entry<Integer, JCheckBox> entry : facilityCheckboxes.entrySet()) {
                if (entry.getValue().isSelected()) selectedFids.add(entry.getKey());
            }

            // 未勾选任何设施 → 警告（该员工无法被排班）
            // 施設未選択 → 警告（当該従業員はシフトに割当不可）
            // No facility selected → warn (employee cannot be scheduled)
            if (selectedFids.isEmpty()) {
                int warn = JOptionPane.showConfirmDialog(this,
                        "勤務可能施設が未指定です。この従業員はシフトに割り当てられません。\n保存してもよろしいですか？",
                        "確認", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
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
                JOptionPane.showMessageDialog(this, "保存に失敗しました", "エラー", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 解析工时输入
     *   - 空字符串返回 null（表示不限）
     *   - 非法返回 null 并弹错
     *
     * 勤務時間入力を解析
     *   - 空文字列は null を返却（制限なし）
     *   - 不正値は null を返却しエラー表示
     *
     * Parse hour limit input
     *   - Empty string returns null (no limit)
     *   - Invalid input returns null with error dialog
     */
    private Integer parseHourLimit(String input, String fieldName) {
        if (input == null || input.isEmpty()) return null;
        try {
            int val = Integer.parseInt(input);
            if (val < 0 || val > 999) {
                JOptionPane.showMessageDialog(this, fieldName + " は 0-999 の整数で入力してください", "エラー", JOptionPane.ERROR_MESSAGE);
                return null;
            }
            return val;
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, fieldName + " は整数で入力してください", "エラー", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }
}