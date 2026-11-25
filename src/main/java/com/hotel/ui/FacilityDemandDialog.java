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
 * 施設需要設定ダイアログ
 * Facility demand configuration dialog
 *
 * <p>显示某个设施的所有需求配置，支持增删改。
 * 排班算法根据这些规则 + 当日稼働率/CI/CO 决定需要多少人。</p>
 *
 * <p>特定施設の全需要設定を表示、追加・編集・削除可能。
 * シフトアルゴリズムはこれらのルール + 当日稼働率/CI/CO に基づき必要人数を決定。</p>
 *
 * <p>Displays all demand rules for a facility; supports add/edit/delete.
 * The scheduling algorithm uses these rules + daily occupancy/CI/CO to decide headcount.</p>
 */
public class FacilityDemandDialog extends JDialog {

    private final Facility facility;
    private final FacilityDemandDao demandDao = new FacilityDemandDao();
    private final ShiftTypeDao shiftTypeDao = new ShiftTypeDao();

    private DefaultTableModel tableModel;
    private JTable table;
    private List<ShiftType> allShifts;

    /**
     * 构造方法：模态对话框，绑定到指定设施
     * コンストラクタ：モーダルダイアログ、指定施設にバインド
     * Constructor: modal dialog bound to the specified facility
     */
    public FacilityDemandDialog(Frame owner, Facility facility) {
        // true = 模态对话框（阻塞父窗口交互）
        // true = モーダルダイアログ（親ウィンドウの操作をブロック）
        // true = modal dialog (blocks parent window interaction)
        super(owner, "施設需要設定：" + facility.getName(), true);
        this.facility = facility;
        this.allShifts = shiftTypeDao.findAll();

        setSize(800, 500);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        // 顶部说明 / 上部説明 / Top description
        JLabel info = new JLabel("""
                <html>設定ルール：各行は「ある稼働率区間において、該当シフトに必要な人数」を表します<br>
                例：早番 8-17、稼働率 50-79%、2 人必要 → 当日の稼働率がこの区間内の場合、2 人を配置<br>
                例：Morning 8-17, occupancy 50-79%, 2 staff required → assign 2 staff when occupancy falls in this range</html>
                """);
        info.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
        add(info, BorderLayout.NORTH);

        // 中间表格 / 中央テーブル / Center table
        String[] columns = {"ID", "シフト", "判定基準", "範囲下限", "範囲上限", "必要人数"};
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

        // 底部按钮 / 下部ボタン / Bottom buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addBtn = new JButton("新規追加");
        JButton editBtn = new JButton("編集");
        JButton deleteBtn = new JButton("削除");
        JButton closeBtn = new JButton("閉じる");

        bottomPanel.add(addBtn);
        bottomPanel.add(editBtn);
        bottomPanel.add(deleteBtn);
        bottomPanel.add(Box.createHorizontalStrut(30));  // 间隔 / スペーサー / Spacer
        bottomPanel.add(closeBtn);
        add(bottomPanel, BorderLayout.SOUTH);

        addBtn.addActionListener(e -> showEditForm(null));
        editBtn.addActionListener(e -> editSelected());
        deleteBtn.addActionListener(e -> deleteSelected());
        closeBtn.addActionListener(e -> dispose());

        // 双击行 → 编辑
        // 行ダブルクリック → 編集
        // Double-click row → edit
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) editSelected();
            }
        });

        // 没有班次时无法配置需求
        // シフト未登録時は需要設定不可
        // Cannot configure demand without any shift types
        if (allShifts.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "シフトが未登録です。先に「シフト種別」ページで追加してください。",
                    "確認", JOptionPane.WARNING_MESSAGE);
            addBtn.setEnabled(false);
        }

        loadData();
    }

    /**
     * 从数据库加载当前设施的需求配置到表格
     * DB から当該施設の需要設定を読み込みテーブルへ
     * Load this facility's demand rules from DB into the table
     */
    private void loadData() {
        tableModel.setRowCount(0);
        List<FacilityDemand> demands = demandDao.findByFacility(facility.getId());
        for (FacilityDemand d : demands) {
            String metric = d.getMetricType();
            String metricLabel;
            String unit;
            // 根据 metric 类型决定显示文字和单位
            // metric 種別により表示文字と単位を決定
            // Determine display text and unit by metric type
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

    /**
     * 编辑选中行 / 選択行を編集 / Edit selected row
     */
    private void editSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "行を選択してください");
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

        int confirm = JOptionPane.showConfirmDialog(this,
                "この需要設定を削除してもよろしいですか？", "削除確認", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            if (demandDao.delete(id)) {
                loadData();
            } else {
                JOptionPane.showMessageDialog(this, "削除に失敗しました", "エラー", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 显示新增/编辑表单（demand = null 时为新增）
     * 新規・編集フォーム表示（demand = null なら新規）
     * Show add/edit form (demand = null means adding new)
     */
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

        // 班次下拉 / シフト選択ドロップダウン / Shift dropdown
        JComboBox<ShiftType> shiftBox = new JComboBox<>();
        Map<Integer, Integer> shiftIdToIndex = new HashMap<>();
        for (int i = 0; i < allShifts.size(); i++) {
            shiftBox.addItem(allShifts.get(i));
            shiftIdToIndex.put(allShifts.get(i).getId(), i);
        }
        Integer idx = shiftIdToIndex.get(demand.getShiftTypeId());
        if (idx != null) shiftBox.setSelectedIndex(idx);

        // 判断依据下拉（3 选 1）
        // 判定基準ドロップダウン（3 択）
        // Metric dropdown (3 choices)
        String[] metricDisplay = {"稼働率（%）", "CI 人数（チェックイン）", "CO 人数（チェックアウト）"};
        String[] metricValue = {"occupancy", "ci_count", "co_count"};
        JComboBox<String> metricBox = new JComboBox<>(metricDisplay);
        for (int i = 0; i < metricValue.length; i++) {
            if (metricValue[i].equals(demand.getMetricType())) {
                metricBox.setSelectedIndex(i);
                break;
            }
        }

        // 范围输入（标签会随 metric 变化）
        // 範囲入力（ラベルは metric に応じて変化）
        // Range inputs (label changes with metric)
        JLabel minLabel = new JLabel(getRangeLabel(metricBox.getSelectedIndex(), true));
        JLabel maxLabel = new JLabel(getRangeLabel(metricBox.getSelectedIndex(), false));

        JSpinner minSpinner = new JSpinner(new SpinnerNumberModel(demand.getOccupancyMin(), 0, 999, 1));
        JSpinner maxSpinner = new JSpinner(new SpinnerNumberModel(demand.getOccupancyMax(), 0, 999, 1));

        // metric 切换时同步更新范围标签
        // metric 変更時に範囲ラベルを同期更新
        // Update range labels when metric changes
        metricBox.addActionListener(ev -> {
            minLabel.setText(getRangeLabel(metricBox.getSelectedIndex(), true));
            maxLabel.setText(getRangeLabel(metricBox.getSelectedIndex(), false));
        });

        JSpinner countSpinner = new JSpinner(new SpinnerNumberModel(demand.getRequiredCount(), 0, 99, 1));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; formPanel.add(new JLabel("シフト："), gbc);
        gbc.gridx = 1; formPanel.add(shiftBox, gbc);

        gbc.gridx = 0; gbc.gridy = 1; formPanel.add(new JLabel("判定基準："), gbc);
        gbc.gridx = 1; formPanel.add(metricBox, gbc);

        gbc.gridx = 0; gbc.gridy = 2; formPanel.add(minLabel, gbc);
        gbc.gridx = 1; formPanel.add(minSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 3; formPanel.add(maxLabel, gbc);
        gbc.gridx = 1; formPanel.add(maxSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 4; formPanel.add(new JLabel("必要人数："), gbc);
        gbc.gridx = 1; formPanel.add(countSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2;
        formPanel.add(new JLabel("<html><small>ヒント：必要人数を 0 にすると、この条件下では誰も配置しません。</small></html>"), gbc);

        int result = JOptionPane.showConfirmDialog(this, formPanel,
                isNew ? "需要の新規追加" : "需要の編集",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            int min = (int) minSpinner.getValue();
            int max = (int) maxSpinner.getValue();
            int count = (int) countSpinner.getValue();
            String metric = metricValue[metricBox.getSelectedIndex()];

            // 验证 / バリデーション / Validation
            if (min > max) {
                JOptionPane.showMessageDialog(this, "下限は上限以下にしてください", "エラー", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if ("occupancy".equals(metric) && max > 100) {
                JOptionPane.showMessageDialog(this, "稼働率は 100% を超えられません", "エラー", JOptionPane.ERROR_MESSAGE);
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
                JOptionPane.showMessageDialog(this, "保存に失敗しました", "エラー", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 根据判断依据类型返回范围标签文字
     * 判定基準種別により範囲ラベル文字を返却
     * Returns the range label text based on the metric type
     */
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