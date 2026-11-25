package com.hotel.ui;

import com.hotel.dao.FacilityDao;
import com.hotel.dao.OccupancyDao;
import com.hotel.model.Facility;
import com.hotel.model.Occupancy;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * CI/CO/稼働率录入面板（月历视图，每天 3 个输入框）
 * 稼働率・CI・CO 入力パネル（月カレンダー表示、1 日 3 つの入力欄）
 * Occupancy / CI / CO input panel (monthly calendar view, 3 input fields per day)
 *
 * <p>排班算法会根据这些数据 + 需求配置规则，决定每天每班次需要几人。
 * 空字段表示该天未录入（算法会跳过该天）。</p>
 *
 * <p>シフトアルゴリズムはこれらのデータ + 需要設定ルールを基に、
 * 各日・各シフトの必要人数を決定。空欄は未入力を意味し、アルゴリズムはその日をスキップ。</p>
 *
 * <p>The scheduling algorithm uses this data + demand rules to decide headcount per day/shift.
 * Empty fields mean "not entered" — the algorithm skips that day.</p>
 */
public class OccupancyPanel extends JPanel {

    private final FacilityDao facilityDao = new FacilityDao();
    private final OccupancyDao occupancyDao = new OccupancyDao();

    private JComboBox<String> yearBox;
    private JComboBox<String> monthBox;
    private JComboBox<Facility> facilityBox;
    private JPanel calendarPanel;
    private JScrollPane calendarScroll;

    /**
     * 每天的三个输入框：日期字符串 → [稼働率, CI, CO]
     * 日ごとの 3 つの入力欄：日付文字列 → [稼働率, CI, CO]
     * Three input fields per day: date string → [occupancy, CI, CO]
     */
    private final Map<String, JTextField[]> dateFields = new HashMap<>();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] WEEK_NAMES = {"日", "月", "火", "水", "木", "金", "土"};

    /**
     * 构造方法：初始化 UI 并加载数据
     * コンストラクタ：UI 初期化とデータ読み込み
     * Constructor: initializes UI and loads data
     */
    public OccupancyPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // ===== 顶部区域：选择器 + 图例 / 上部エリア：セレクタ + 凡例 / Top area: selectors + legend =====
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));

        // 第一行：选择器 + 按钮 / 1 段目：セレクタ + ボタン / Row 1: selectors + buttons
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        controlPanel.add(new JLabel("年："));
        yearBox = new JComboBox<>();
        int currentYear = LocalDate.now().getYear();
        for (int y = currentYear - 1; y <= currentYear + 3; y++) {
            yearBox.addItem(y + " 年");
        }
        yearBox.setSelectedItem(currentYear + " 年");
        controlPanel.add(yearBox);

        controlPanel.add(new JLabel("月："));
        monthBox = new JComboBox<>();
        for (int m = 1; m <= 12; m++) monthBox.addItem(m + " 月");
        monthBox.setSelectedIndex(LocalDate.now().getMonthValue() - 1);
        controlPanel.add(monthBox);

        controlPanel.add(new JLabel("施設："));
        facilityBox = new JComboBox<>();
        facilityBox.setPreferredSize(new Dimension(200, 28));
        for (Facility f : facilityDao.findAll()) facilityBox.addItem(f);
        controlPanel.add(facilityBox);

        JButton saveBtn = new JButton("当月保存");
        controlPanel.add(saveBtn);

        JButton fillAllBtn = new JButton("月全体に同値");
        controlPanel.add(fillAllBtn);

        northPanel.add(controlPanel);

        // 第二行：图例说明 / 2 段目：凡例 / Row 2: legend
        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JLabel legend = new JLabel("<html>1 日 3 つの数値：<b>稼働率</b>(%) | <b>CI</b>(チェックイン) | <b>CO</b>(チェックアウト)。空欄は未入力を意味します。</html>");
        legend.setForeground(Color.DARK_GRAY);
        legendPanel.add(legend);
        northPanel.add(legendPanel);

        add(northPanel, BorderLayout.NORTH);

        // ===== 中间月历 / 中央カレンダー / Center calendar =====
        calendarPanel = new JPanel();
        calendarScroll = new JScrollPane(calendarPanel);
        calendarScroll.setBorder(BorderFactory.createTitledBorder("月カレンダー"));
        add(calendarScroll, BorderLayout.CENTER);

        // 事件绑定 / イベントバインド / Event binding
        yearBox.addActionListener(e -> rebuildCalendar());
        monthBox.addActionListener(e -> rebuildCalendar());
        facilityBox.addActionListener(e -> rebuildCalendar());
        saveBtn.addActionListener(e -> saveCurrent());
        fillAllBtn.addActionListener(e -> fillAll());

        rebuildCalendar();
    }

    /**
     * 根据当前年/月/设施重新构建月历视图
     * 選択中の年・月・施設に応じてカレンダーを再構築
     * Rebuild calendar view based on current year/month/facility selection
     */
    private void rebuildCalendar() {
        calendarPanel.removeAll();
        dateFields.clear();

        Facility selectedFacility = (Facility) facilityBox.getSelectedItem();
        if (selectedFacility == null) {
            calendarPanel.add(new JLabel("先に「施設管理」で施設を追加してください"));
            calendarPanel.revalidate();
            calendarPanel.repaint();
            return;
        }

        int year = Integer.parseInt(((String) yearBox.getSelectedItem()).replace(" 年", "").trim());
        int month = Integer.parseInt(((String) monthBox.getSelectedItem()).replace(" 月", "").trim());

        // 更新月历标题 / カレンダータイトル更新 / Update calendar title
        calendarScroll.setBorder(BorderFactory.createTitledBorder(
                year + " 年 " + month + " 月 —— " + selectedFacility.getName()));

        YearMonth ym = YearMonth.of(year, month);
        int daysInMonth = ym.lengthOfMonth();
        LocalDate firstDay = ym.atDay(1);
        // 一号是周几（日=0、月=1、...、土=6）
        // 1 日の曜日（日=0、月=1、...、土=6）
        // Day-of-week of the 1st (Sun=0, Mon=1, ..., Sat=6)
        int firstDayOfWeek = firstDay.getDayOfWeek().getValue() % 7;

        String yearMonthStr = String.format("%04d-%02d", year, month);
        Map<String, Occupancy> existing = occupancyDao.findFullByFacilityAndMonth(selectedFacility.getId(), yearMonthStr);

        calendarPanel.setLayout(new GridLayout(0, 7, 5, 5));

        // 星期表头（日曜红、土曜蓝）/ 曜日ヘッダ（日曜赤、土曜青）/ Weekday header (Sun red, Sat blue)
        for (int i = 0; i < 7; i++) {
            JLabel weekLabel = new JLabel(WEEK_NAMES[i], SwingConstants.CENTER);
            weekLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
            if (i == 0) weekLabel.setForeground(Color.RED);
            else if (i == 6) weekLabel.setForeground(Color.BLUE);
            calendarPanel.add(weekLabel);
        }

        // 1 号前面的空格 / 1 日より前の空セル / Empty cells before the 1st
        for (int i = 0; i < firstDayOfWeek; i++) {
            calendarPanel.add(new JLabel(""));
        }

        // 每天单元格 / 各日セル / Cell for each day
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate date = ym.atDay(day);
            String dateStr = date.format(DATE_FMT);

            JPanel dayCell = new JPanel();
            dayCell.setLayout(new BoxLayout(dayCell, BoxLayout.Y_AXIS));
            dayCell.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));

            JLabel dayLabel = new JLabel(String.valueOf(day), SwingConstants.CENTER);
            dayLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            int weekday = date.getDayOfWeek().getValue() % 7;
            if (weekday == 0) dayLabel.setForeground(Color.RED);
            else if (weekday == 6) dayLabel.setForeground(Color.BLUE);
            dayCell.add(dayLabel);

            Occupancy existingData = existing.get(dateStr);

            JTextField rateField = createSmallField(
                    existingData != null ? String.valueOf(existingData.getOccupancyRate()) : "", "稼働率 %");
            JTextField ciField = createSmallField(
                    (existingData != null && existingData.getCiCount() != null)
                            ? String.valueOf(existingData.getCiCount()) : "", "CI チェックイン数");
            JTextField coField = createSmallField(
                    (existingData != null && existingData.getCoCount() != null)
                            ? String.valueOf(existingData.getCoCount()) : "", "CO チェックアウト数");

            dayCell.add(labeledRow("率:", rateField));
            dayCell.add(labeledRow("CI:", ciField));
            dayCell.add(labeledRow("CO:", coField));

            dateFields.put(dateStr, new JTextField[]{rateField, ciField, coField});
            calendarPanel.add(dayCell);
        }

        calendarPanel.revalidate();
        calendarPanel.repaint();
    }

    /**
     * 创建一个小尺寸输入框（带 tooltip）
     * 小サイズ入力欄を作成（ツールチップ付き）
     * Create a small input field (with tooltip)
     */
    private JTextField createSmallField(String initialValue, String tooltip) {
        JTextField f = new JTextField(initialValue, 4);
        f.setHorizontalAlignment(JTextField.CENTER);
        f.setMaximumSize(new Dimension(60, 22));
        f.setToolTipText(tooltip);
        return f;
    }

    /**
     * 创建"标签 + 输入框"水平行
     * 「ラベル + 入力欄」の水平行を作成
     * Create a horizontal "label + input" row
     */
    private JPanel labeledRow(String label, JTextField field) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
        JLabel l = new JLabel(label);
        l.setFont(new Font("SansSerif", Font.PLAIN, 10));
        p.add(l);
        p.add(field);
        p.setAlignmentX(Component.CENTER_ALIGNMENT);
        return p;
    }

    /**
     * 保存当前月所有数据（先验证全部合法，再统一写入 + 清空处理）
     * 当月の全データを保存（先に全てをバリデート、書き込みとクリア処理）
     * Save all data for the current month (validate all first, then write + clear)
     */
    private void saveCurrent() {
        Facility selectedFacility = (Facility) facilityBox.getSelectedItem();
        if (selectedFacility == null) return;

        Map<String, int[]> toSave = new HashMap<>();
        // 全空的日期要删除 / 全空欄の日付は削除 / Date with all empty fields should be deleted
        java.util.Set<String> toDelete = new java.util.HashSet<>();

        // 加载 DB 当前状态，用来判断哪些"清空"是真的删除、哪些本来就是空
        // DB の現状を取得、「クリア」が実際の削除か元から空欄かを判別
        // Load DB state to distinguish genuine deletes from inputs that were empty all along
        int year = Integer.parseInt(((String) yearBox.getSelectedItem()).replace(" 年", "").trim());
        int month = Integer.parseInt(((String) monthBox.getSelectedItem()).replace(" 月", "").trim());
        String yearMonthStr = String.format("%04d-%02d", year, month);
        Map<String, com.hotel.model.Occupancy> existingDb =
                occupancyDao.findFullByFacilityAndMonth(selectedFacility.getId(), yearMonthStr);

        for (Map.Entry<String, JTextField[]> entry : dateFields.entrySet()) {
            String date = entry.getKey();
            JTextField[] fs = entry.getValue();
            String rateStr = fs[0].getText().trim();
            String ciStr = fs[1].getText().trim();
            String coStr = fs[2].getText().trim();

            // 三个全空 → 删除这天 / 3 つ全て空欄 → この日を削除 / All three empty → delete this day
            if (rateStr.isEmpty() && ciStr.isEmpty() && coStr.isEmpty()) {
                toDelete.add(date);
                continue;
            }

            int rate = 0;
            // -1 作为 null 的占位符，后面转成 Integer 时还原
            // -1 は null のプレースホルダ、後で Integer 変換時に復元
            // -1 as placeholder for null, converted back to Integer later
            int ci = -1;
            int co = -1;

            if (!rateStr.isEmpty()) {
                try {
                    rate = Integer.parseInt(rateStr);
                    if (rate < 0 || rate > 100) {
                        JOptionPane.showMessageDialog(this,
                                date + " の稼働率は 0-100 で指定してください", "エラー", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this,
                            date + " の稼働率は有効な数値ではありません", "エラー", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            if (!ciStr.isEmpty()) {
                try {
                    ci = Integer.parseInt(ciStr);
                    if (ci < 0 || ci > 999) {
                        JOptionPane.showMessageDialog(this,
                                date + " の CI は 0-999 で指定してください", "エラー", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this,
                            date + " の CI は有効な数値ではありません", "エラー", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            if (!coStr.isEmpty()) {
                try {
                    co = Integer.parseInt(coStr);
                    if (co < 0 || co > 999) {
                        JOptionPane.showMessageDialog(this,
                                date + " の CO は 0-999 で指定してください", "エラー", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                } catch (NumberFormatException e) {
                    JOptionPane.showMessageDialog(this,
                            date + " の CO は有効な数値ではありません", "エラー", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            toSave.put(date, new int[]{rate, ci, co});
        }

        // 全部验证通过才开始写入 / 全バリデート通過後に書き込み開始 / Write only after all validation passes
        int saved = 0;
        for (Map.Entry<String, int[]> entry : toSave.entrySet()) {
            int[] vals = entry.getValue();
            Integer ciVal = vals[1] < 0 ? null : vals[1];
            Integer coVal = vals[2] < 0 ? null : vals[2];
            if (occupancyDao.saveFull(selectedFacility.getId(), entry.getKey(), vals[0], ciVal, coVal)) {
                saved++;
            }
        }

        // 删除清空的日期（只统计"原本有数据"的清空）
        // クリアされた日付を削除（元々データがあったもののみカウント）
        // Delete cleared dates (only count those that originally had data)
        int deleted = 0;
        for (String date : toDelete) {
            boolean hadData = existingDb.containsKey(date);
            if (occupancyDao.deleteOne(selectedFacility.getId(), date) && hadData) {
                deleted++;
            }
        }

        String msg;
        if (saved == 0 && deleted == 0) {
            msg = "保存またはクリアするデータがありません";
        } else if (deleted == 0) {
            msg = saved + " 日分のデータを保存しました";
        } else if (saved == 0) {
            msg = deleted + " 日分のデータをクリアしました";
        } else {
            msg = saved + " 日分を保存、" + deleted + " 日分をクリアしました";
        }
        JOptionPane.showMessageDialog(this, msg);
    }

    /**
     * 整月填同值（弹对话框输入 3 个数，再逐一填充）
     * 月全体に同値を入力（ダイアログで 3 値を入力後、一括反映）
     * Fill the whole month with the same values (prompt for 3 values, then apply to all)
     */
    private void fillAll() {
        JTextField rateField = new JTextField(5);
        JTextField ciField = new JTextField(5);
        JTextField coField = new JTextField(5);

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; form.add(new JLabel("稼働率（%、0-100）："), gbc);
        gbc.gridx = 1; form.add(rateField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; form.add(new JLabel("CI 人数："), gbc);
        gbc.gridx = 1; form.add(ciField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; form.add(new JLabel("CO 人数："), gbc);
        gbc.gridx = 1; form.add(coField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        form.add(new JLabel("<html><small>空欄のフィールドは既存値を上書きしません。</small></html>"), gbc);

        int result = JOptionPane.showConfirmDialog(this, form, "月全体に同値を入力",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String rateStr = rateField.getText().trim();
        String ciStr = ciField.getText().trim();
        String coStr = coField.getText().trim();

        // 空字段跳过，不覆盖已有值（UX 友好）
        // 空欄フィールドはスキップ、既存値を上書きしない（UX フレンドリー）
        // Skip empty fields; don't overwrite existing values (user-friendly UX)
        for (JTextField[] fs : dateFields.values()) {
            if (!rateStr.isEmpty()) fs[0].setText(rateStr);
            if (!ciStr.isEmpty()) fs[1].setText(ciStr);
            if (!coStr.isEmpty()) fs[2].setText(coStr);
        }
    }
}