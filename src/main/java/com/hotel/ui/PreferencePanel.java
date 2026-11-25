package com.hotel.ui;

import com.hotel.dao.ConfigDao;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 偏好设置面板：调整疲劳规则参数和软性目标优先级
 * 優先度設定パネル：疲労ルールパラメータとソフト目標の優先度を調整
 * Preference panel: adjust fatigue rule parameters and soft goal priorities
 *
 * <p>这些参数直接影响排班算法的行为。数字越大 → 该因素越被重视。
 * 调整后不需重启，下次排班生成时自动生效（SchedulingContext 每次 load() 时读取最新值）。</p>
 *
 * <p>これらのパラメータはシフトアルゴリズムの動作に直接影響する。数値が大きいほど重視される。
 * 調整後に再起動は不要、次回シフト生成時に自動反映（SchedulingContext が load() 時に最新値を読込）。</p>
 *
 * <p>These parameters directly affect the scheduling algorithm. Higher value = more weight.
 * No restart needed after changes; takes effect on next schedule generation
 * (SchedulingContext reads the latest values on every load()).</p>
 */
public class PreferencePanel extends JPanel {

    private final ConfigDao configDao = new ConfigDao();

    // 输入框集合：key = 配置项 key、value = 对应的输入框
    // 入力欄コレクション：key = 設定キー、value = 対応する入力欄
    // Input fields collection: key = config key, value = corresponding JTextField
    // 使用 LinkedHashMap 保持插入顺序 / 挿入順を保持するため LinkedHashMap / LinkedHashMap preserves insertion order
    private final Map<String, JTextField> fields = new LinkedHashMap<>();

    /**
     * 构造方法：初始化 UI 并加载数据
     * コンストラクタ：UI 初期化とデータ読み込み
     * Constructor: initializes UI and loads data
     */
    public PreferencePanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 主体面板（垂直布局）/ メインパネル（垂直レイアウト）/ Main panel (vertical layout)
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // ===== 疲劳规则区 / 疲労ルールエリア / Fatigue rules section =====
        JPanel fatiguePanel = createGroupPanel("疲労ルールパラメータ");

        addField(fatiguePanel, "3 日連続勤務後の疲労倍率：", "fatigue_continuous_3day_multiplier", "1.1", "推奨 1.0-1.5");
        addField(fatiguePanel, "5 日連続勤務後の疲労倍率：", "fatigue_continuous_5day_multiplier", "1.3", "推奨 1.2-2.0");
        addField(fatiguePanel, "夜勤翌日勤務の追加疲労：", "fatigue_night_next_day_bonus", "0.5", "推奨 0-1.0");
        addField(fatiguePanel, "シフト間隔 11 時間未満の追加疲労：", "fatigue_short_rest_bonus", "0.5", "推奨 0-1.0");
        addField(fatiguePanel, "最大連続勤務日数（ハード制約）：", "max_continuous_work_days", "5", "推奨 4-7 日");

        mainPanel.add(fatiguePanel);
        mainPanel.add(Box.createVerticalStrut(15));

        // ===== 软性目标优先级区 / ソフト目標優先度エリア / Soft goal priority section =====
        JPanel priorityPanel = createGroupPanel("ソフト目標優先度（数値が大きいほど重要）");

        addField(priorityPanel, "希望休充足：", "priority_wish_off", "4", "1-10");
        addField(priorityPanel, "疲労バランス：", "priority_fatigue_balance", "3", "1-10");
        addField(priorityPanel, "シフト回数バランス：", "priority_shift_balance", "2", "1-10");
        addField(priorityPanel, "平日・休日バランス：", "priority_weekday_holiday_balance", "1", "1-10");

        mainPanel.add(priorityPanel);
        mainPanel.add(Box.createVerticalStrut(15));

        // ===== 底部按钮 / 下部ボタン / Bottom buttons =====
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton("保存");
        JButton resetBtn = new JButton("デフォルトに戻す");
        buttonPanel.add(resetBtn);
        buttonPanel.add(saveBtn);
        mainPanel.add(buttonPanel);

        // 放进可滚动容器，防止窗口太小看不到
        // スクロール可能コンテナに配置、小ウィンドウ対策
        // Wrap in scroll pane to handle small windows
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        // 事件绑定 / イベントバインド / Event binding
        saveBtn.addActionListener(e -> saveAll());
        resetBtn.addActionListener(e -> resetToDefaults());

        // 初始加载数据 / 初期データロード / Initial data load
        loadData();
    }

    /**
     * 创建一个带标题的分组面板
     * タイトル付きグループパネルを作成
     * Create a titled group panel
     */
    private JPanel createGroupPanel(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(),
                title,
                javax.swing.border.TitledBorder.LEFT,
                javax.swing.border.TitledBorder.TOP,
                new Font("SansSerif", Font.BOLD, 13)
        ));
        return panel;
    }

    /**
     * 在面板上添加一行：标签 + 输入框 + 提示
     * パネルに 1 行追加：ラベル + 入力欄 + ヒント
     * Add a row to the panel: label + input + hint
     */
    private void addField(JPanel parent, String label, String configKey, String defaultValue, String hint) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 8, 5, 8);
        gbc.anchor = GridBagConstraints.WEST;

        // 根据 parent 已有的子组件数量算出当前行号（每行有 3 个组件：标签/输入框/提示）
        // parent の既存子コンポーネント数から現在行を算出（1 行 3 コンポーネント：ラベル/入力欄/ヒント）
        // Calculate current row from existing child count (3 components per row: label/input/hint)
        int currentRow = parent.getComponentCount() / 3;

        gbc.gridx = 0; gbc.gridy = currentRow;
        parent.add(new JLabel(label), gbc);

        JTextField field = new JTextField(8);
        gbc.gridx = 1;
        parent.add(field, gbc);

        gbc.gridx = 2;
        JLabel hintLabel = new JLabel(hint);
        hintLabel.setForeground(Color.GRAY);
        hintLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        parent.add(hintLabel, gbc);

        fields.put(configKey, field);
    }

    /**
     * 从数据库加载所有配置到输入框
     * DB から全設定を読み込み入力欄に反映
     * Load all config from DB into input fields
     */
    private void loadData() {
        Map<String, String> all = configDao.getAll();
        for (Map.Entry<String, JTextField> entry : fields.entrySet()) {
            String value = all.get(entry.getKey());
            entry.getValue().setText(value == null ? "" : value);
        }
    }

    /**
     * 保存所有输入到数据库（先验证全部合法，再统一写入）
     * 全入力を DB に保存（先に全てをバリデート、その後一括書き込み）
     * Save all inputs to DB (validate all first, then write together)
     */
    private void saveAll() {
        // 先验证所有输入都是合法数字，避免部分写入
        // 先に全入力が有効な数値かバリデート、部分書き込みを防止
        // Validate all inputs first to prevent partial writes
        for (Map.Entry<String, JTextField> entry : fields.entrySet()) {
            String value = entry.getValue().getText().trim();
            if (value.isEmpty()) {
                JOptionPane.showMessageDialog(this, "全ての項目に値を入力してください", "エラー", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                Double.parseDouble(value);  // 不抛错就说明合法 / エラーなしなら有効 / Valid if no exception
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "「" + value + "」は有効な数値ではありません", "エラー", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        // 全部合法才写入 / 全て有効な場合のみ書き込み / Write only when all are valid
        int saved = 0;
        for (Map.Entry<String, JTextField> entry : fields.entrySet()) {
            if (configDao.set(entry.getKey(), entry.getValue().getText().trim())) {
                saved++;
            }
        }

        JOptionPane.showMessageDialog(this, saved + " 件の設定を保存しました");
    }

    /**
     * 重置为默认值（含二次确认）
     * デフォルト値にリセット（確認ダイアログあり）
     * Reset to default values (with confirmation)
     */
    private void resetToDefaults() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "全ての設定をデフォルト値にリセットしてもよろしいですか？", "リセット確認", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        // 默认值与 DatabaseManager 初始化时的值保持一致
        // デフォルト値は DatabaseManager の初期化値と一致
        // Defaults match the initialization values in DatabaseManager
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("fatigue_continuous_3day_multiplier", "1.1");
        defaults.put("fatigue_continuous_5day_multiplier", "1.3");
        defaults.put("fatigue_night_next_day_bonus", "0.5");
        defaults.put("fatigue_short_rest_bonus", "0.5");
        defaults.put("max_continuous_work_days", "5");
        defaults.put("priority_wish_off", "4");
        defaults.put("priority_fatigue_balance", "3");
        defaults.put("priority_shift_balance", "2");
        defaults.put("priority_weekday_holiday_balance", "1");

        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            configDao.set(entry.getKey(), entry.getValue());
        }
        loadData();
        JOptionPane.showMessageDialog(this, "デフォルト値にリセットしました");
    }
}