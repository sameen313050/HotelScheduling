package com.hotel.ui;

import com.hotel.dao.ConfigDao;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 偏好设置面板：调整疲劳规则参数和软性目标优先级
 */
public class PreferencePanel extends JPanel {

    private final ConfigDao configDao = new ConfigDao();

    // 输入框集合：key = 配置项 key，value = 对应的输入框
    private final Map<String, JTextField> fields = new LinkedHashMap<>();

    public PreferencePanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 主体面板（垂直布局）
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // ===== 疲劳规则区 =====
        JPanel fatiguePanel = createGroupPanel("疲劳规则参数");

        addField(fatiguePanel, "连续上班 3 天后疲劳倍率：", "fatigue_continuous_3day_multiplier", "1.1", "建议 1.0-1.5");
        addField(fatiguePanel, "连续上班 5 天后疲劳倍率：", "fatigue_continuous_5day_multiplier", "1.3", "建议 1.2-2.0");
        addField(fatiguePanel, "夜班次日再上班的额外分：", "fatigue_night_next_day_bonus", "0.5", "建议 0-1.0");
        addField(fatiguePanel, "班间隔不足 11 小时额外分：", "fatigue_short_rest_bonus", "0.5", "建议 0-1.0");
        addField(fatiguePanel, "最多连续上班天数（硬性限制）：", "max_continuous_work_days", "5", "建议 4-7 天");

        mainPanel.add(fatiguePanel);
        mainPanel.add(Box.createVerticalStrut(15));

        // ===== 软性目标优先级区 =====
        JPanel priorityPanel = createGroupPanel("软性目标优先级（数字越大越重要）");

        addField(priorityPanel, "满足希望休：", "priority_wish_off", "4", "1-10");
        addField(priorityPanel, "疲劳值均衡：", "priority_fatigue_balance", "3", "1-10");
        addField(priorityPanel, "班次次数均衡：", "priority_shift_balance", "2", "1-10");
        addField(priorityPanel, "平日 / 公休均衡：", "priority_weekday_holiday_balance", "1", "1-10");

        mainPanel.add(priorityPanel);
        mainPanel.add(Box.createVerticalStrut(15));

        // ===== 底部按钮 =====
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton("保存");
        JButton resetBtn = new JButton("重置为默认值");
        buttonPanel.add(resetBtn);
        buttonPanel.add(saveBtn);
        mainPanel.add(buttonPanel);

        // 放进可滚动容器，防止窗口太小看不到
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        add(scrollPane, BorderLayout.CENTER);

        // 事件绑定
        saveBtn.addActionListener(e -> saveAll());
        resetBtn.addActionListener(e -> resetToDefaults());

        // 初始加载数据
        loadData();
    }

    /** 创建一个带标题的分组面板 */
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

    /** 在面板上添加一行：标签 + 输入框 + 提示 */
    private void addField(JPanel parent, String label, String configKey, String defaultValue, String hint) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 8, 5, 8);
        gbc.anchor = GridBagConstraints.WEST;

        int row = fields.size() % 100;  // 大概的行号

        // 计算当前 parent 已有几行（用 component 数量大致估算）
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

    /** 从数据库加载所有配置到输入框 */
    private void loadData() {
        Map<String, String> all = configDao.getAll();
        for (Map.Entry<String, JTextField> entry : fields.entrySet()) {
            String value = all.get(entry.getKey());
            entry.getValue().setText(value == null ? "" : value);
        }
    }

    /** 保存所有输入到数据库 */
    private void saveAll() {
        // 先验证所有输入都是合法数字
        for (Map.Entry<String, JTextField> entry : fields.entrySet()) {
            String value = entry.getValue().getText().trim();
            if (value.isEmpty()) {
                JOptionPane.showMessageDialog(this, "所有字段都需要填值", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
            try {
                Double.parseDouble(value);  // 不抛错就说明合法
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "「" + value + "」不是有效的数字", "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        // 全部合法才写入
        int saved = 0;
        for (Map.Entry<String, JTextField> entry : fields.entrySet()) {
            if (configDao.set(entry.getKey(), entry.getValue().getText().trim())) {
                saved++;
            }
        }

        JOptionPane.showMessageDialog(this, "已保存 " + saved + " 项配置");
    }

    /** 重置为默认值 */
    private void resetToDefaults() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "确定要重置所有配置为默认值吗？", "确认重置", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

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
        JOptionPane.showMessageDialog(this, "已重置为默认值");
    }
}
