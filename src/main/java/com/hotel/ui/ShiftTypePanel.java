package com.hotel.ui;

import com.hotel.dao.ShiftTypeDao;
import com.hotel.model.ShiftType;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * 班次设置面板
 * シフト種別管理パネル
 * Shift type management panel
 *
 * <p>提供班次类型的 CRUD 操作。班次 = 一段固定的工作时间段（例：早番 8:00-17:00）。
 * 每个班次属于某个时段（朝/昼/夕/夜），时段用于算法的硬性约束判定
 * （比如"夜勤専属只能上夜班"、"夜班次日不能接早班"）。</p>
 *
 * <p>シフト種別の CRUD 操作を提供。シフト = 固定の勤務時間帯（例：早番 8:00-17:00）。
 * 各シフトは時間帯（朝/昼/夕/夜）に所属し、時間帯はアルゴリズムのハード制約判定に使用
 * （例：「夜勤専属は夜番のみ」「夜番翌日は早番不可」）。</p>
 *
 * <p>Provides CRUD for shift types. A shift = a fixed working time period (e.g. Morning 8:00-17:00).
 * Each shift belongs to a period (朝/昼/夕/夜), used by the algorithm for hard constraints
 * (e.g. "night-only staff work night only", "no morning after night").</p>
 */
public class ShiftTypePanel extends JPanel {

    private final ShiftTypeDao shiftDao = new ShiftTypeDao();
    private DefaultTableModel tableModel;
    private JTable table;

    // 时段选项：数据库存日文，界面直接显示日文（日本客户友好）
    // 時間帯選択肢：DB に日本語保存、画面も日本語表示（日本顧客向け）
    // Period options: stored as Japanese in DB, displayed in Japanese (Japan-customer friendly)
    private static final String[] PERIOD_DB_VALUES = {"朝", "昼", "夕", "夜"};
    private static final String[] PERIOD_DISPLAY = {"朝（早番）", "昼（中番）", "夕（遅番）", "夜（夜番）"};

    /**
     * 构造方法：初始化 UI 并加载数据
     * コンストラクタ：UI 初期化とデータ読み込み
     * Constructor: initializes UI and loads data
     */
    public ShiftTypePanel() {
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
        String[] columns = {"ID", "シフト名", "開始時刻", "終了時刻", "日跨ぎ", "時間帯"};
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
        table.getColumnModel().getColumn(5).setPreferredWidth(120);

        add(new JScrollPane(table), BorderLayout.CENTER);

        // 事件绑定 / イベントバインド / Event binding
        addBtn.addActionListener(e -> showEditDialog(null));
        editBtn.addActionListener(e -> editSelected());
        deleteBtn.addActionListener(e -> deleteSelected());
        refreshBtn.addActionListener(e -> loadData());

        // 双击行 → 编辑 / 行ダブルクリック → 編集 / Double-click row → edit
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

    /**
     * 从数据库加载数据到表格
     * DB からデータを読み込みテーブルに表示
     * Load data from DB into table
     */
    private void loadData() {
        tableModel.setRowCount(0);
        List<ShiftType> shifts = shiftDao.findAll();
        for (ShiftType s : shifts) {
            tableModel.addRow(new Object[]{
                    s.getId(),
                    s.getName(),
                    s.getStartTime(),
                    s.getEndTime(),
                    s.isCrossDay() ? "はい" : "いいえ",
                    periodToDisplay(s.getPeriod())
            });
        }
    }

    /**
     * 把数据库里的时段代码转成可读显示
     * DB 保存の時間帯コードを可読表示に変換
     * Convert DB period code to readable display
     */
    private String periodToDisplay(String dbValue) {
        for (int i = 0; i < PERIOD_DB_VALUES.length; i++) {
            if (PERIOD_DB_VALUES[i].equals(dbValue)) {
                return PERIOD_DISPLAY[i];
            }
        }
        return dbValue;  // 找不到就原样返回 / 見つからなければそのまま返却 / Return as-is if not found
    }

    /**
     * 编辑选中行
     * 選択行を編集
     * Edit selected row
     *
     * 从数据库重新读取对象，避免显示转换造成的数据丢失。
     * DB から再取得、表示変換によるデータ損失を回避。
     * Re-fetch from DB to avoid data loss caused by display conversion.
     */
    private void editSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "行を選択してください");
            return;
        }
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
                "シフト「" + name + "」を削除してもよろしいですか？\n"
                        + "注意：関連する施設設定・シフト表データにも影響します。",
                "削除確認",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            if (shiftDao.delete(id)) {
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
     */
    private void showEditDialog(ShiftType shift) {
        boolean isNew = (shift == null);
        if (isNew) {
            shift = new ShiftType();
            shift.setPeriod(PERIOD_DB_VALUES[0]);  // 默认朝 / デフォルトは朝 / Default to 朝
        }

        JTextField nameField = new JTextField(shift.getName() == null ? "" : shift.getName(), 20);
        JTextField startField = new JTextField(shift.getStartTime() == null ? "08:00" : shift.getStartTime(), 10);
        JTextField endField = new JTextField(shift.getEndTime() == null ? "17:00" : shift.getEndTime(), 10);
        JCheckBox crossDayBox = new JCheckBox("日跨ぎ（夜勤 21:00 〜 翌 09:00 等はチェック）", shift.isCrossDay());

        // 时段下拉框 / 時間帯ドロップダウン / Period dropdown
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
        formPanel.add(new JLabel("シフト名："), gbc);
        gbc.gridx = 1;
        formPanel.add(nameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("開始時刻："), gbc);
        gbc.gridx = 1;
        formPanel.add(startField, gbc);
        gbc.gridx = 2;
        formPanel.add(new JLabel("形式 HH:MM、例：08:00"), gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("終了時刻："), gbc);
        gbc.gridx = 1;
        formPanel.add(endField, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel("時間帯区分："), gbc);
        gbc.gridx = 1;
        formPanel.add(periodBox, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 3;
        formPanel.add(crossDayBox, gbc);

        int result = JOptionPane.showConfirmDialog(this, formPanel,
                isNew ? "シフトの新規追加" : "シフトの編集",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String name = nameField.getText().trim();
            String start = startField.getText().trim();
            String end = endField.getText().trim();

            if (name.isEmpty()) {
                JOptionPane.showMessageDialog(this, "シフト名は必須です", "エラー", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (!isValidTime(start) || !isValidTime(end)) {
                JOptionPane.showMessageDialog(this, "時刻の形式が正しくありません。HH:MM で指定してください（例：08:00）", "エラー", JOptionPane.ERROR_MESSAGE);
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
                JOptionPane.showMessageDialog(this, "保存に失敗しました", "エラー", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 简单验证时间格式 HH:MM
     * 時刻形式 HH:MM の簡易バリデーション
     * Simple validation for time format HH:MM
     */
    private boolean isValidTime(String time) {
        // 先用正则快速判断格式 / まず正規表現で形式チェック / Regex format check first
        if (time == null || !time.matches("\\d{2}:\\d{2}")) {
            return false;
        }
        try {
            String[] parts = time.split(":");
            int h = Integer.parseInt(parts[0]);
            int m = Integer.parseInt(parts[1]);
            // 时间范围：00-23 时、00-59 分 / 時刻範囲：00-23 時、00-59 分 / Time range: 0-23 hours, 0-59 minutes
            return h >= 0 && h <= 23 && m >= 0 && m <= 59;
        } catch (Exception e) {
            return false;
        }
    }
}