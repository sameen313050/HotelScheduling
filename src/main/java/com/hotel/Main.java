package com.hotel;

import com.hotel.db.DatabaseManager;
import com.hotel.ui.MainFrame;

import javax.swing.*;

/**
 * 程序入口
 */
public class Main {
    public static void main(String[] args) {
        // 1. 初始化数据库
        DatabaseManager.initialize();

        // 2. 设置 UI 风格为系统原生（Mac 上会用 Mac 风格，Windows 上用 Win 风格）
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // 忽略，用默认风格
        }

        // 3. 在事件分发线程上启动界面（Swing 规范）
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}
