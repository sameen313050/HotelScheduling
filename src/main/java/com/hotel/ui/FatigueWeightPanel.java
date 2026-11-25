package com.hotel.ui;

import com.hotel.dao.FacilityDao;
import com.hotel.dao.FatigueWeightDao;
import com.hotel.dao.ShiftTypeDao;
import com.hotel.model.Facility;
import com.hotel.model.ShiftType;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 疲劳权重配置面板：按"设施 × 班次"二维配置疲劳系数
 * 疲労度ウェイト設定パネル：「施設 × シフト」の 2 次元で疲労係数を設定
 * Fatigue weight panel: configure fatigue coefficients per "facility × shift" combination
 *
 * <p>排班算法会根据这些权重计算每位员工的疲劳累积，优先把新班次分给疲劳低的员工。
 * 数字越大 = 该班次越累 = 员工做完后累积更多疲劳。</p>
 *
 * <p>シフトアルゴリズムはこれらのウェイトで従業員ごとの疲労を累積計算し、
 * 疲労の低い従業員に新しいシフトを優先配置する。
 * 数値が大きい = 当該シフトの疲労度が高い = 従業員がこなした後の疲労累積が多い。</p>
 *
 * <p>The scheduling algorithm accumulates fatigue per employee using these weights,
 * prioritizing less-fatigued employees for new shifts.
 * Higher value = more tiring shift = more fatigue accumulated after each one.</p>
 */
public class FatigueWeightPanel extends JPanel {

    private final FacilityDao facilityDao = new FacilityDao();
    private final ShiftTypeDao shiftTypeDao = new ShiftTypeDao();
    private final FatigueWeightDao weightDao = new FatigueWeightDao();

    private JComboBox<Facility> facilityBox;
    private JPanel weightFormPanel;
    private final Map<Integer, JTextField> shiftWeightFields = new HashMap<>();

    // 默认疲劳权重（按时段类型）
    // 時間帯別デフォルト疲労ウェイト
    // Default fatigue weights by time slot
    //   朝/昼 = 1.0（基准）、夕 = 1.2（稍高）、夜 = 2.0（夜班最累）
    //   朝/昼 = 1.0（基準）、夕 = 1.2（やや高）、夜 = 2.0（夜勤が最も疲労）
    //   Morning/noon = 1.0 (baseline), evening = 1.2 (slightly higher), night = 2.0 (most tiring)
    private static final Map<String, Double> DEFAULT_BY_PERIOD = new HashMap<>();
    static {
        DEFAULT_BY_PERIOD.put("朝", 1.0);
        DEFAULT_BY_PERIOD.put("昼", 1.0);
        DEFAULT_BY_PERIOD.put("夕", 1.2);
        DEFAULT_BY_PERIOD.put("夜", 2.0);
    }

    /**
     * 构造方法：初始化 UI 并加载数据
     * コンストラクタ：UI 初期化とデータ読み込み
     * Constructor: initializes UI and loads data
     */
    public FatigueWeightPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 顶部：说明 + 设施下拉 / 上部：説明 + 施設ドロップダウン / Top: description + facility dropdown
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));

        JLabel info = new JLabel("<html>各「施設 × シフト」の組合せに疲労係数を設定します。数値が大きいほど疲労度が高いことを表します。<br>" +
                "シフトアルゴリズムはこの値を基に各従業員の疲労を累積し、疲労の低い従業員に新シフトを優先配置します。</html>");
        topPanel.add(info, BorderLayout.NORTH);

        JPanel selectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectPanel.add(new JLabel("施設選択："));
        facilityBox = new JComboBox<>();
        facilityBox.setPreferredSize(new Dimension(250, 28));
        selectPanel.add(facilityBox);
        topPanel.add(selectPanel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);

        // 中间：班次权重输入区 / 中央：シフトウェイト入力エリア / Center: shift weight input area
        weightFormPanel = new JPanel(new GridBagLayout());
        JScrollPane scroll = new JScrollPane(weightFormPanel);
        scroll.setBorder(BorderFactory.createTitledBorder("シフト別疲労ウェイト"));
        add(scroll, BorderLayout.CENTER);

        // 底部：按钮 / 下部：ボタン / Bottom: buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton resetBtn = new JButton("デフォルトに戻す");
        JButton saveBtn = new JButton("保存");
        buttonPanel.add(resetBtn);
        buttonPanel.add(saveBtn);
        add(buttonPanel, BorderLayout.SOUTH);

        // 加载设施列表 / 施設リストロード / Load facility list
        loadFacilities();

        // 事件 / イベント / Events
        facilityBox.addActionListener(e -> loadWeightsForCurrentFacility());
        saveBtn.addActionListener(e -> saveCurrent());
        resetBtn.addActionListener(e -> resetCurrent());

        // 初次显示 / 初回表示 / Initial display
        loadWeightsForCurrentFacility();
    }

    /**
     * 加载所有设施到下拉
     * 全施設をドロップダウンに読み込み
     * Load all facilities into dropdown
     */
    private void loadFacilities() {
        facilityBox.removeAllItems();
        for (Facility f : facilityDao.findAll()) {
            facilityBox.addItem(f);
        }
    }

    /**
     * 为当前选中设施加载各班次的权重输入框
     * 選択中の施設に対して、各シフトのウェイト入力欄を読み込み
     * Load weight input fields for each shift of the currently selected facility
     */
    private void loadWeightsForCurrentFacility() {
        weightFormPanel.removeAll();
        shiftWeightFields.clear();

        Facility selected = (Facility) facilityBox.getSelectedItem();
        if (selected == null) {
            weightFormPanel.revalidate();
            weightFormPanel.repaint();
            return;
        }

        List<ShiftType> shifts = shiftTypeDao.findAll();
        if (shifts.isEmpty()) {
            weightFormPanel.add(new JLabel("シフトが未登録です。先に「シフト種別」ページで追加してください"));
            weightFormPanel.revalidate();
            weightFormPanel.repaint();
            return;
        }

        // 读取该设施已有权重，未配置的使用默认值
        // 当該施設の既存ウェイトを取得、未設定はデフォルト値を使用
        // Load existing weights for the facility; use defaults for unconfigured
        Map<Integer, Double> existing = weightDao.findByFacility(selected.getId());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 10, 6, 10);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        // 表头 / ヘッダ / Header
        gbc.gridx = 0; gbc.gridy = row;
        JLabel headerName = new JLabel("シフト");
        headerName.setFont(new Font("SansSerif", Font.BOLD, 13));
        weightFormPanel.add(headerName, gbc);

        gbc.gridx = 1;
        JLabel headerWeight = new JLabel("疲労ウェイト");
        headerWeight.setFont(new Font("SansSerif", Font.BOLD, 13));
        weightFormPanel.add(headerWeight, gbc);

        gbc.gridx = 2;
        JLabel headerHint = new JLabel("説明");
        headerHint.setFont(new Font("SansSerif", Font.BOLD, 13));
        weightFormPanel.add(headerHint, gbc);

        row++;

        for (ShiftType s : shifts) {
            // 优先使用已存储的值，否则按时段取默认值
            // 保存済みの値を優先使用、無ければ時間帯別のデフォルト値を使用
            // Prefer stored value, otherwise use the period's default
            double existingWeight = existing.containsKey(s.getId())
                    ? existing.get(s.getId())
                    : DEFAULT_BY_PERIOD.getOrDefault(s.getPeriod(), 1.0);

            gbc.gridx = 0; gbc.gridy = row;
            weightFormPanel.add(new JLabel(s.getName()), gbc);

            JTextField field = new JTextField(String.valueOf(existingWeight), 6);
            gbc.gridx = 1;
            weightFormPanel.add(field, gbc);
            shiftWeightFields.put(s.getId(), field);

            // 时段提示 / 時間帯ヒント / Period hint
            String periodLabel = periodToLabel(s.getPeriod());
            JLabel hint = new JLabel("時間帯：" + periodLabel + "（" + s.getStartTime() + "-" + s.getEndTime() + "）");
            hint.setForeground(Color.GRAY);
            hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
            gbc.gridx = 2;
            weightFormPanel.add(hint, gbc);

            row++;
        }

        weightFormPanel.revalidate();
        weightFormPanel.repaint();
    }

    /**
     * 时段代码转可读标签
     * 時間帯コードを可読ラベルに変換
     * Convert period code to readable label
     */
    private String periodToLabel(String period) {
        switch (period) {
            case "朝": return "朝（早番）";
            case "昼": return "昼（中番）";
            case "夕": return "夕（遅番）";
            case "夜": return "夜（夜番）";
            default: return period;
        }
    }

    /**
     * 保存当前设施的所有权重（先验证全部合法，再统一写入）
     * 選択中の施設の全ウェイトを保存（先に全てをバリデート、その後一括書き込み）
     * Save all weights for the current facility (validate all first, then write together)
     */
    private void saveCurrent() {
        Facility selected = (Facility) facilityBox.getSelectedItem();
        if (selected == null) return;

        // 先验证所有输入，避免部分写入导致数据不一致
        // 先に全入力をバリデート、部分書き込みによるデータ不整合を回避
        // Validate all inputs first to avoid partial writes causing inconsistency
        Map<Integer, Double> toSave = new HashMap<>();
        for (Map.Entry<Integer, JTextField> entry : shiftWeightFields.entrySet()) {
            String text = entry.getValue().getText().trim();
            try {
                double w = Double.parseDouble(text);
                if (w < 0 || w > 10) {
                    JOptionPane.showMessageDialog(this,
                            "ウェイトは 0-10 の範囲で指定してください。不正な値：" + w,
                            "エラー", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                toSave.put(entry.getKey(), w);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "「" + text + "」は有効な数値ではありません",
                        "エラー", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        // 全部合法才写入 / 全てが有効な場合のみ書き込み / Write only when all are valid
        int saved = 0;
        for (Map.Entry<Integer, Double> entry : toSave.entrySet()) {
            if (weightDao.save(selected.getId(), entry.getKey(), entry.getValue())) {
                saved++;
            }
        }

        JOptionPane.showMessageDialog(this,
                "施設「" + selected.getName() + "」の " + saved + " 件の疲労ウェイトを保存しました");
    }

    /**
     * 重置当前设施的权重（删除所有记录，下次加载时使用默认值）
     * 選択中の施設のウェイトをリセット（全レコード削除、次回ロード時にデフォルト値使用）
     * Reset current facility's weights (delete all records; defaults will apply on next load)
     */
    private void resetCurrent() {
        Facility selected = (Facility) facilityBox.getSelectedItem();
        if (selected == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "施設「" + selected.getName() + "」の疲労ウェイトをデフォルトに戻してもよろしいですか？",
                "リセット確認", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        weightDao.deleteByFacility(selected.getId());
        loadWeightsForCurrentFacility();
        JOptionPane.showMessageDialog(this, "リセットしました");
    }
}