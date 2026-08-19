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
 */
public class FatigueWeightPanel extends JPanel {

    private final FacilityDao facilityDao = new FacilityDao();
    private final ShiftTypeDao shiftTypeDao = new ShiftTypeDao();
    private final FatigueWeightDao weightDao = new FatigueWeightDao();

    private JComboBox<Facility> facilityBox;
    private JPanel weightFormPanel;
    private final Map<Integer, JTextField> shiftWeightFields = new HashMap<>();

    // 默认疲劳权重（按时段类型）
    private static final Map<String, Double> DEFAULT_BY_PERIOD = new HashMap<>();
    static {
        DEFAULT_BY_PERIOD.put("朝", 1.0);
        DEFAULT_BY_PERIOD.put("昼", 1.0);
        DEFAULT_BY_PERIOD.put("夕", 1.2);
        DEFAULT_BY_PERIOD.put("夜", 2.0);
    }

    public FatigueWeightPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 顶部：说明 + 设施下拉
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));

        JLabel info = new JLabel("<html>为每个「设施 × 班次」组合设置疲劳系数。数字越大表示越累。<br>" +
                "排班算法会根据此值计算每个员工的疲劳累积，优先把新班次分给疲劳低的员工。</html>");
        topPanel.add(info, BorderLayout.NORTH);

        JPanel selectPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        selectPanel.add(new JLabel("选择设施："));
        facilityBox = new JComboBox<>();
        facilityBox.setPreferredSize(new Dimension(250, 28));
        selectPanel.add(facilityBox);
        topPanel.add(selectPanel, BorderLayout.SOUTH);

        add(topPanel, BorderLayout.NORTH);

        // 中间：班次权重输入区
        weightFormPanel = new JPanel(new GridBagLayout());
        JScrollPane scroll = new JScrollPane(weightFormPanel);
        scroll.setBorder(BorderFactory.createTitledBorder("各班次疲劳权重"));
        add(scroll, BorderLayout.CENTER);

        // 底部：按钮
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton resetBtn = new JButton("重置为默认值");
        JButton saveBtn = new JButton("保存");
        buttonPanel.add(resetBtn);
        buttonPanel.add(saveBtn);
        add(buttonPanel, BorderLayout.SOUTH);

        // 加载设施列表
        loadFacilities();

        // 事件
        facilityBox.addActionListener(e -> loadWeightsForCurrentFacility());
        saveBtn.addActionListener(e -> saveCurrent());
        resetBtn.addActionListener(e -> resetCurrent());

        // 初次显示
        loadWeightsForCurrentFacility();
    }

    private void loadFacilities() {
        facilityBox.removeAllItems();
        for (Facility f : facilityDao.findAll()) {
            facilityBox.addItem(f);
        }
    }

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
            weightFormPanel.add(new JLabel("尚未配置任何班次，请先去「班次设置」添加班次"));
            weightFormPanel.revalidate();
            weightFormPanel.repaint();
            return;
        }

        Map<Integer, Double> existing = weightDao.findByFacility(selected.getId());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 10, 6, 10);
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;

        // 表头
        gbc.gridx = 0; gbc.gridy = row;
        JLabel headerName = new JLabel("班次");
        headerName.setFont(new Font("SansSerif", Font.BOLD, 13));
        weightFormPanel.add(headerName, gbc);

        gbc.gridx = 1;
        JLabel headerWeight = new JLabel("疲劳权重");
        headerWeight.setFont(new Font("SansSerif", Font.BOLD, 13));
        weightFormPanel.add(headerWeight, gbc);

        gbc.gridx = 2;
        JLabel headerHint = new JLabel("说明");
        headerHint.setFont(new Font("SansSerif", Font.BOLD, 13));
        weightFormPanel.add(headerHint, gbc);

        row++;

        for (ShiftType s : shifts) {
            double existingWeight = existing.containsKey(s.getId())
                    ? existing.get(s.getId())
                    : DEFAULT_BY_PERIOD.getOrDefault(s.getPeriod(), 1.0);

            gbc.gridx = 0; gbc.gridy = row;
            weightFormPanel.add(new JLabel(s.getName()), gbc);

            JTextField field = new JTextField(String.valueOf(existingWeight), 6);
            gbc.gridx = 1;
            weightFormPanel.add(field, gbc);
            shiftWeightFields.put(s.getId(), field);

            // 提示文字
            String periodLabel = periodToLabel(s.getPeriod());
            JLabel hint = new JLabel("时段：" + periodLabel + "（" + s.getStartTime() + "-" + s.getEndTime() + "）");
            hint.setForeground(Color.GRAY);
            hint.setFont(new Font("SansSerif", Font.PLAIN, 11));
            gbc.gridx = 2;
            weightFormPanel.add(hint, gbc);

            row++;
        }

        weightFormPanel.revalidate();
        weightFormPanel.repaint();
    }

    private String periodToLabel(String period) {
        switch (period) {
            case "朝": return "朝（早班）";
            case "昼": return "昼（中班）";
            case "夕": return "夕（晚班）";
            case "夜": return "夜（夜班）";
            default: return period;
        }
    }

    private void saveCurrent() {
        Facility selected = (Facility) facilityBox.getSelectedItem();
        if (selected == null) return;

        // 先验证所有输入
        Map<Integer, Double> toSave = new HashMap<>();
        for (Map.Entry<Integer, JTextField> entry : shiftWeightFields.entrySet()) {
            String text = entry.getValue().getText().trim();
            try {
                double w = Double.parseDouble(text);
                if (w < 0 || w > 10) {
                    JOptionPane.showMessageDialog(this,
                            "权重必须在 0-10 之间，发现非法值：" + w,
                            "错误", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                toSave.put(entry.getKey(), w);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        "「" + text + "」不是有效数字",
                        "错误", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        // 全部合法才写入
        int saved = 0;
        for (Map.Entry<Integer, Double> entry : toSave.entrySet()) {
            if (weightDao.save(selected.getId(), entry.getKey(), entry.getValue())) {
                saved++;
            }
        }

        JOptionPane.showMessageDialog(this,
                "设施「" + selected.getName() + "」的 " + saved + " 项疲劳权重已保存");
    }

    private void resetCurrent() {
        Facility selected = (Facility) facilityBox.getSelectedItem();
        if (selected == null) return;

        int confirm = JOptionPane.showConfirmDialog(this,
                "确定要重置设施「" + selected.getName() + "」的疲劳权重为默认值吗？",
                "确认重置", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        weightDao.deleteByFacility(selected.getId());
        loadWeightsForCurrentFacility();
        JOptionPane.showMessageDialog(this, "已重置");
    }
}
