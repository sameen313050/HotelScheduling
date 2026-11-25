package com.hotel;

import com.hotel.db.DatabaseManager;
import com.hotel.ui.MainFrame;

import javax.swing.*;

/**
 * 程序入口
 * アプリケーションエントリーポイント
 * Application entry point
 *
 * <p>启动流程：</p>
 * <ol>
 *   <li>初始化数据库（首次启动时自动建表）</li>
 *   <li>设置系统原生 UI 风格（Mac/Windows/Linux 自适应）</li>
 *   <li>在 EDT（事件分发线程）上创建并显示主窗口</li>
 * </ol>
 *
 * <p>起動フロー：</p>
 * <ol>
 *   <li>データベース初期化（初回起動時にテーブル自動作成）</li>
 *   <li>システムネイティブの UI スタイルを設定（Mac/Windows/Linux に自動対応）</li>
 *   <li>EDT（イベントディスパッチスレッド）上でメインウィンドウを作成・表示</li>
 * </ol>
 *
 * <p>Startup flow:</p>
 * <ol>
 *   <li>Initialize database (tables auto-created on first run)</li>
 *   <li>Apply system native UI look & feel (auto-adapts to Mac/Windows/Linux)</li>
 *   <li>Create and show main window on EDT (Event Dispatch Thread)</li>
 * </ol>
 */
public class Main {

    /**
     * main 方法：JVM 调用的程序入口
     * main メソッド：JVM が呼び出すエントリーポイント
     * main method: entry point invoked by the JVM
     */
    public static void main(String[] args) {
        // 1. 初始化数据库（如果 hotel_schedule.db 不存在则创建，存在则跳过建表）
        // 1. データベース初期化（hotel_schedule.db がなければ作成、あれば CREATE はスキップ）
        // 1. Initialize database (create hotel_schedule.db if missing, skip CREATE if exists)
        DatabaseManager.initialize();

        // 2. 设置 UI 风格为系统原生
        //    Mac → Aqua（Mac 原生风格）
        //    Windows → Windows 风格
        //    Linux → GTK 风格
        //
        // 2. システムネイティブ UI スタイルを設定
        //    Mac → Aqua（Mac ネイティブ）
        //    Windows → Windows スタイル
        //    Linux → GTK スタイル
        //
        // 2. Apply system native UI look & feel
        //    Mac → Aqua (native)
        //    Windows → Windows style
        //    Linux → GTK style
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // 失败时使用默认（Metal）风格，不影响程序运行
            // 失敗時はデフォルト（Metal）スタイルを使用、動作に影響なし
            // On failure, fall back to default (Metal); doesn't affect operation
        }

        // 3. 在 EDT（Event Dispatch Thread）上启动 UI（Swing 规范要求）
        //    Swing 组件不是线程安全的，所有 UI 操作必须在 EDT 上进行
        //
        // 3. EDT（イベントディスパッチスレッド）上で UI 起動（Swing 規約）
        //    Swing コンポーネントはスレッドセーフではなく、UI 操作は全て EDT 上で行う必要あり
        //
        // 3. Launch UI on EDT (Event Dispatch Thread) — required by Swing contract
        //    Swing components are not thread-safe; all UI ops must happen on the EDT
        SwingUtilities.invokeLater(() -> {
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}