package com.hotel.ui;

import javax.swing.*;
import java.awt.*;

/**
 * 主窗口：带 Tab 切换不同功能模块
 */
public class MainFrame extends JFrame {

    public MainFrame() {
        setTitle("酒店排班系统 v1.0");
        setSize(1200, 750);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JTabbedPane tabbedPane = new JTabbedPane();

        tabbedPane.addTab("员工管理", new EmployeePanel());
        tabbedPane.addTab("设施管理", new FacilityPanel());
        tabbedPane.addTab("班次设置", new ShiftTypePanel());
        tabbedPane.addTab("疲劳权重", new FatigueWeightPanel());
        tabbedPane.addTab("偏好设置", new PreferencePanel());
        tabbedPane.addTab("稼働率", new OccupancyPanel());
        tabbedPane.addTab("希望休", new WishOffPanel());
        tabbedPane.addTab("排班", new SchedulePanel());
        tabbedPane.addTab("导出", createPlaceholder("Excel 导出（第4阶段实现）"));

        add(tabbedPane);
    }

    /** 临时占位面板，后续会替换成真正的功能界面 */
    private JPanel createPlaceholder(String text) {
        JPanel panel = new JPanel(new GridBagLayout());
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 18));
        label.setForeground(Color.GRAY);
        panel.add(label);
        return panel;
    }
}
