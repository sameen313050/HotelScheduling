package com.hotel.ui;

import javax.swing.*;
import java.awt.*;

/**
 * 主窗口：带 Tab 切换不同功能模块
 * メインウィンドウ：タブで各機能モジュールを切り替え
 * Main window: tabbed interface for switching between functional modules
 */
public class MainFrame extends JFrame {

    /**
     * 构造方法：初始化窗口和 Tab
     * コンストラクタ：ウィンドウとタブを初期化
     * Constructor: initializes the window and tabs
     */
    public MainFrame() {
        // 窗口标题 / ウィンドウタイトル / Window title
        setTitle("ホテルシフト管理システム v1.0");
        // 窗口大小 / ウィンドウサイズ / Window size
        setSize(1200, 750);
        // 居中显示 / 画面中央に表示 / Center on screen
        setLocationRelativeTo(null);
        // 关闭窗口时退出程序 / 閉じるボタンでアプリ終了 / Exit on close
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Tab 容器 / タブコンテナ / Tab container
        JTabbedPane tabbedPane = new JTabbedPane();

        // 添加各功能 Tab / 各機能タブを追加 / Add function tabs
        tabbedPane.addTab("従業員管理", new EmployeePanel());
        tabbedPane.addTab("施設管理", new FacilityPanel());
        tabbedPane.addTab("シフト種別", new ShiftTypePanel());
        tabbedPane.addTab("疲労度設定", new FatigueWeightPanel());
        tabbedPane.addTab("優先度設定", new PreferencePanel());
        tabbedPane.addTab("稼働率", new OccupancyPanel());
        tabbedPane.addTab("希望休", new WishOffPanel());
        tabbedPane.addTab("有休", new PaidLeavePanel());
        tabbedPane.addTab("シフト表", new SchedulePanel());

        add(tabbedPane);
    }

    /**
     * 临时占位面板，后续会替换成真正的功能界面
     * 一時的なプレースホルダーパネル、後で本物の機能画面に置き換え
     * Temporary placeholder panel, to be replaced with real functional UI later
     */
    private JPanel createPlaceholder(String text) {
        JPanel panel = new JPanel(new GridBagLayout());
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 18));
        label.setForeground(Color.GRAY);
        panel.add(label);
        return panel;
    }
}