package com.hotel.ui;

import com.hotel.scheduling.SchedulingResult;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * 排班结果的问题报告窗口
 * シフト結果の問題レポートダイアログ
 * Schedule result problem report dialog
 *
 * <p>排班生成后自动弹出，显示 2 类问题：</p>
 * <ul>
 *   <li><b>硬性缺人</b>：某日某班次人数不足（算法已尽力，需要人工调整）</li>
 *   <li><b>软性未满足</b>：希望休被占用等（员工权益未完全满足）</li>
 * </ul>
 *
 * <p>シフト生成後に自動表示、2 種類の問題を一覧：</p>
 * <ul>
 *   <li><b>ハード人手不足</b>：ある日のシフトで人員不足（アルゴリズム最善尽力後、手動調整必要）</li>
 *   <li><b>ソフト未達</b>：希望休が割当てられたなど（従業員権利の部分未達）</li>
 * </ul>
 *
 * <p>Automatically shown after schedule generation; lists 2 problem types:</p>
 * <ul>
 *   <li><b>Hard shortage</b>: insufficient headcount (algorithm did its best, manual fix needed)</li>
 *   <li><b>Soft unmet</b>: preferred days off occupied, etc. (employee rights partially unmet)</li>
 * </ul>
 */
public class ProblemReportDialog extends JDialog {

    /**
     * 构造方法：模态对话框，显示指定排班结果的问题
     * コンストラクタ：モーダルダイアログ、指定シフト結果の問題を表示
     * Constructor: modal dialog showing problems of the given scheduling result
     */
    public ProblemReportDialog(Frame owner, SchedulingResult result) {
        // true = 模态（阻塞父窗口） / true = モーダル（親ウィンドウブロック） / true = modal (blocks parent)
        super(owner, "シフト問題レポート", true);
        setSize(700, 500);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        // 顶部汇总 / 上部サマリー / Top summary
        // HTML 格式便于用粗体 + 彩色突出关键数字
        // HTML 形式で太字 + カラーで重要数値を強調
        // HTML format allows bold + color to highlight key numbers
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel summaryLabel = new JLabel(String.format(
                "<html>合計 <b>%d</b> 件の割当 &nbsp;&nbsp; " +
                        "人手不足 <b style='color:red'>%d</b> 件 &nbsp;&nbsp; " +
                        "ソフト問題 <b style='color:orange'>%d</b> 件</html>",
                result.getAssignments().size(),
                result.getShortageProblems().size(),
                result.getSoftProblems().size()));
        summaryLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        topPanel.add(summaryLabel);
        add(topPanel, BorderLayout.NORTH);

        // 中间：两栏问题列表（左红=硬性，右橙=软性）
        // 中央：2 列の問題リスト（左赤=ハード、右橙=ソフト）
        // Center: two-column problem lists (left red = hard, right orange = soft)
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        centerPanel.add(makeListPanel("[削除] 人手不足（ハード競合、手動対応が必要）",
                result.getShortageProblems(), Color.RED.darker()));
        centerPanel.add(makeListPanel("[!] ソフト問題（手動調整を検討）",
                result.getSoftProblems(), new Color(200, 130, 0)));

        add(centerPanel, BorderLayout.CENTER);

        // 底部按钮 / 下部ボタン / Bottom button
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("閉じる");
        closeBtn.addActionListener(e -> dispose());
        bottomPanel.add(closeBtn);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * 构建一个带标题的问题列表面板
     * タイトル付きの問題リストパネルを作成
     * Build a titled problem list panel
     *
     * @param title       面板标题 / パネルタイトル / Panel title
     * @param items       问题列表 / 問題リスト / Problem items
     * @param titleColor  标题颜色（未使用，保留以便后续扩展）/ タイトル色（未使用、将来拡張用）/ Title color (unused, kept for future)
     */
    private JPanel makeListPanel(String title, List<String> items, Color titleColor) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));

        DefaultListModel<String> model = new DefaultListModel<>();
        if (items.isEmpty()) {
            // 空列表显示占位文字，避免空白面板让用户误以为出错
            // 空リストはプレースホルダ表示、空白パネルで誤解を防止
            // Empty list shows placeholder to avoid confusion from blank panel
            model.addElement("（問題なし）");
        } else {
            for (String s : items) model.addElement(s);
        }
        JList<String> list = new JList<>(model);
        list.setFont(new Font("SansSerif", Font.PLAIN, 12));
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        return panel;
    }
}