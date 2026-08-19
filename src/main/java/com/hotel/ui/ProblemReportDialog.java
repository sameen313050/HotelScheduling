package com.hotel.ui;

import com.hotel.scheduling.SchedulingResult;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * 排班结果的问题报告窗口
 */
public class ProblemReportDialog extends JDialog {

    public ProblemReportDialog(Frame owner, SchedulingResult result) {
        super(owner, "排班问题报告", true);
        setSize(700, 500);
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout(10, 10));

        // 顶部汇总
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel summaryLabel = new JLabel(String.format(
                "<html>共分配 <b>%d</b> 个班次 &nbsp;&nbsp; " +
                        "缺人问题 <b style='color:red'>%d</b> 处 &nbsp;&nbsp; " +
                        "软性问题 <b style='color:orange'>%d</b> 处</html>",
                result.getAssignments().size(),
                result.getShortageProblems().size(),
                result.getSoftProblems().size()));
        summaryLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        topPanel.add(summaryLabel);
        add(topPanel, BorderLayout.NORTH);

        // 中间：两栏问题列表
        JPanel centerPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 10, 10));

        centerPanel.add(makeListPanel("❌ 缺人问题（硬性冲突，需人工解决）",
                result.getShortageProblems(), Color.RED.darker()));
        centerPanel.add(makeListPanel("⚠️ 软性问题（可考虑手动调整）",
                result.getSoftProblems(), new Color(200, 130, 0)));

        add(centerPanel, BorderLayout.CENTER);

        // 底部按钮
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton("关闭");
        closeBtn.addActionListener(e -> dispose());
        bottomPanel.add(closeBtn);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    private JPanel makeListPanel(String title, List<String> items, Color titleColor) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));

        DefaultListModel<String> model = new DefaultListModel<>();
        if (items.isEmpty()) {
            model.addElement("（无问题）");
        } else {
            for (String s : items) model.addElement(s);
        }
        JList<String> list = new JList<>(model);
        list.setFont(new Font("SansSerif", Font.PLAIN, 12));
        panel.add(new JScrollPane(list), BorderLayout.CENTER);
        return panel;
    }
}