package com.hotel.ui;

import com.hotel.dao.EmployeeDao;
import com.hotel.dao.PaidLeaveDao;
import com.hotel.model.Employee;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 有休录入面板：月历视图，每天一个小时数输入框
 * 有給休暇入力パネル：月カレンダー表示、1 日 1 つの時間入力欄
 * Paid leave input panel: monthly calendar view with one hour-input field per day
 *
 * <p>有休日员工不会被排班（硬性约束），且有休小时数计入工时统计。
 * 这是日本劳基法的要求 —— 带薪假是员工的法定权利，且必须算入工时。</p>
 *
 * <p>有休日は従業員がシフトに入らない（ハード制約）、かつ有休時間は勤務時間統計に計上される。
 * これは労働基準法の要求 —— 有給休暇は従業員の法定権利であり、勤務時間に算入する必要がある。</p>
 *
 * <p>Employees are not scheduled on paid leave days (hard constraint), and paid leave hours
 * count toward working hour totals. This reflects Japanese labor law — paid leave is a
 * statutory right and must be counted as working hours.</p>
 */
public class PaidLeavePanel extends JPanel {

    private final EmployeeDao employeeDao = new EmployeeDao();
    private final PaidLeaveDao paidLeaveDao = new PaidLeaveDao();

    private JComboBox<String> yearBox;
    private JComboBox<String> monthBox;
    private JComboBox<Employee> employeeBox;
    private JPanel calendarPanel;
    private JScrollPane calendarScroll;
    private JLabel totalLabel;

    // 每天的输入框：日期字符串 → JTextField
    // 日ごとの入力欄：日付文字列 → JTextField
    // Input field per day: date string → JTextField
    private final Map<String, JTextField> dateFields = new HashMap<>();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] WEEK_NAMES = {"日", "月", "火", "水", "木", "金", "土"};

    /**
     * 构造方法：初始化 UI 并加载数据
     * コンストラクタ：UI 初期化とデータ読み込み
     * Constructor: initializes UI and loads data
     */
    public PaidLeavePanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 顶部控件 / 上部コントロール / Top controls
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        topPanel.add(new JLabel("年："));
        yearBox = new JComboBox<>();
        int currentYear = LocalDate.now().getYear();
        for (int y = currentYear - 1; y <= currentYear + 3; y++) {
            yearBox.addItem(y + " 年");
        }
        yearBox.setSelectedItem(currentYear + " 年");
        topPanel.add(yearBox);

        topPanel.add(new JLabel("月："));
        monthBox = new JComboBox<>();
        for (int m = 1; m <= 12; m++) monthBox.addItem(m + " 月");
        monthBox.setSelectedIndex(LocalDate.now().getMonthValue() - 1);
        topPanel.add(monthBox);

        topPanel.add(new JLabel("従業員："));
        employeeBox = new JComboBox<>();
        employeeBox.setPreferredSize(new Dimension(200, 28));
        topPanel.add(employeeBox);
        reloadEmployees();

        JButton saveBtn = new JButton("当月保存");
        topPanel.add(saveBtn);

        add(topPanel, BorderLayout.NORTH);

        // 月历区 / カレンダーエリア / Calendar area
        calendarPanel = new JPanel();
        calendarScroll = new JScrollPane(calendarPanel);
        calendarScroll.setBorder(BorderFactory.createTitledBorder("月カレンダー（時間数を入力、空欄 = 有休なし）"));
        add(calendarScroll, BorderLayout.CENTER);

        // 底部统计 / 下部サマリー / Bottom summary
        totalLabel = new JLabel("当月有休合計：0 時間");
        totalLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        totalLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        add(totalLabel, BorderLayout.SOUTH);

        // 事件 / イベント / Events
        yearBox.addActionListener(e -> rebuildCalendar());
        monthBox.addActionListener(e -> rebuildCalendar());
        employeeBox.addActionListener(e -> rebuildCalendar());
        saveBtn.addActionListener(e -> saveCurrent());

        rebuildCalendar();
    }

    /**
     * 重新加载员工下拉（保持当前选中项不变）
     * 従業員ドロップダウンを再読み込み（現在の選択を維持）
     * Reload employee dropdown (preserve current selection)
     */
    private void reloadEmployees() {
        Employee currentSelected = (Employee) employeeBox.getSelectedItem();
        int selectedId = (currentSelected == null) ? -1 : currentSelected.getId();

        employeeBox.removeAllItems();
        for (Employee e : employeeDao.findAll()) {
            if (e.isActive()) {
                employeeBox.addItem(e);
                if (e.getId() == selectedId) {
                    employeeBox.setSelectedItem(e);
                }
            }
        }
    }

    /**
     * 每次 Tab 被显示时自动刷新员工列表
     * （员工在其他 Tab 新增/修改后，切回这里能立刻看到）
     *
     * タブ表示時に従業員リストを自動更新
     * （他タブで従業員を追加・変更後、ここに戻ると即反映）
     *
     * Auto-refresh employee list whenever this tab is shown
     * (so changes made in other tabs appear immediately on return)
     */
    @Override
    public void addNotify() {
        super.addNotify();
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) {
                    reloadEmployees();
                }
            }
        });
    }

    /**
     * 根据当前选中的年/月/员工重新构建月历视图
     * 選択中の年・月・従業員に応じてカレンダーを再構築
     * Rebuild calendar view based on selected year/month/employee
     */
    private void rebuildCalendar() {
        calendarPanel.removeAll();
        dateFields.clear();

        Employee selectedEmployee = (Employee) employeeBox.getSelectedItem();
        if (selectedEmployee == null) {
            calendarPanel.add(new JLabel("在職中の従業員がいません"));
            calendarPanel.revalidate();
            calendarPanel.repaint();
            updateTotal();
            return;
        }

        int year = Integer.parseInt(((String) yearBox.getSelectedItem()).replace(" 年", "").trim());
        int month = Integer.parseInt(((String) monthBox.getSelectedItem()).replace(" 月", "").trim());

        calendarScroll.setBorder(BorderFactory.createTitledBorder(
                year + " 年 " + month + " 月 —— " + selectedEmployee.getName() + " の有休"));

        YearMonth ym = YearMonth.of(year, month);
        int daysInMonth = ym.lengthOfMonth();
        LocalDate firstDay = ym.atDay(1);
        // 一号是周几（日=0、月=1、...、土=6）
        // 1 日の曜日（日=0、月=1、...、土=6）
        // Day-of-week of the 1st (Sun=0, Mon=1, ..., Sat=6)
        int firstDayOfWeek = firstDay.getDayOfWeek().getValue() % 7;

        String yearMonthStr = String.format("%04d-%02d", year, month);
        Map<String, Integer> existing = paidLeaveDao.findByEmployeeAndMonth(selectedEmployee.getId(), yearMonthStr);

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

            JTextField field = new JTextField(3);
            field.setHorizontalAlignment(JTextField.CENTER);
            field.setMaximumSize(new Dimension(60, 24));
            field.setAlignmentX(Component.CENTER_ALIGNMENT);
            Integer existingValue = existing.get(dateStr);
            if (existingValue != null) {
                field.setText(String.valueOf(existingValue));
            }

            JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
            row.add(field);
            JLabel h = new JLabel("h");
            h.setFont(new Font("SansSerif", Font.PLAIN, 10));
            row.add(h);
            row.setAlignmentX(Component.CENTER_ALIGNMENT);
            dayCell.add(row);

            // 失焦时更新合计（无需点击保存就能看到实时统计）
            // フォーカスロスト時に合計更新（保存前にリアルタイム集計表示）
            // Update total on focus loss (real-time stats without needing to save)
            field.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    updateTotal();
                }
            });

            dateFields.put(dateStr, field);
            calendarPanel.add(dayCell);
        }

        calendarPanel.revalidate();
        calendarPanel.repaint();
        updateTotal();
    }

    /**
     * 累加所有输入框的小时数，更新底部合计标签
     * 全入力欄の時間数を累積、下部合計ラベルを更新
     * Sum hours across all fields and update the bottom total label
     *
     * 换算规则：8h = 1 天（日本标准全日有休）
     * 換算ルール：8h = 1 日（日本標準の全日有給）
     * Conversion: 8h = 1 day (Japan standard full-day leave)
     */
    private void updateTotal() {
        int total = 0;
        for (JTextField f : dateFields.values()) {
            String s = f.getText().trim();
            if (!s.isEmpty()) {
                try {
                    total += Integer.parseInt(s);
                } catch (NumberFormatException ignored) {
                    // 无效输入忽略（统计时不计入，保存时会弹错）
                    // 無効な入力は無視（集計時は除外、保存時にエラー表示）
                    // Ignore invalid input (excluded from total; error shown at save time)
                }
            }
        }
        totalLabel.setText("当月有休合計：" + total + " 時間（約 " + String.format("%.1f", total / 8.0) + " 日）");
    }

    /**
     * 保存当前月所有数据（先验证全部合法，再统一写入 + 清空处理）
     * 当月の全データを保存（先に全てをバリデート、書き込みとクリア処理）
     * Save all data for the current month (validate all first, then write + clear)
     */
    private void saveCurrent() {
        Employee selectedEmployee = (Employee) employeeBox.getSelectedItem();
        if (selectedEmployee == null) return;

        int year = Integer.parseInt(((String) yearBox.getSelectedItem()).replace(" 年", "").trim());
        int month = Integer.parseInt(((String) monthBox.getSelectedItem()).replace(" 月", "").trim());
        String yearMonthStr = String.format("%04d-%02d", year, month);

        // 加载现有数据，用来判断哪些是"真正清空"
        // 既存データを取得、「真のクリア」判定用
        // Load existing data to detect "real clears"
        Map<String, Integer> existing = paidLeaveDao.findByEmployeeAndMonth(selectedEmployee.getId(), yearMonthStr);

        // 验证 + 分类：待保存 / 待删除
        // バリデート + 分類：保存対象 / 削除対象
        // Validate + classify: to-save / to-delete
        Map<String, Integer> toSave = new HashMap<>();
        java.util.Set<String> toDelete = new java.util.HashSet<>();

        for (Map.Entry<String, JTextField> entry : dateFields.entrySet()) {
            String date = entry.getKey();
            String text = entry.getValue().getText().trim();

            if (text.isEmpty()) {
                // 空输入 → 原本有数据则删除、本来就是空则跳过
                // 空入力 → 既存あれば削除、元々空ならスキップ
                // Empty input → delete if existing, skip if already empty
                if (existing.containsKey(date)) toDelete.add(date);
                continue;
            }
            try {
                int hours = Integer.parseInt(text);
                if (hours < 0 || hours > 24) {
                    JOptionPane.showMessageDialog(this,
                            date + " の時間数は 0-24 で指定してください", "エラー", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (hours == 0) {
                    // 0 等同于空（用户用 0 表示"取消有休"也合理）
                    // 0 は空欄と同じ扱い（「有休取消」の意図として妥当）
                    // 0 is treated as empty (reasonable intent: "cancel leave")
                    if (existing.containsKey(date)) toDelete.add(date);
                } else {
                    toSave.put(date, hours);
                }
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this,
                        date + " の値は有効な数値ではありません", "エラー", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        // 全部验证通过才开始写入 / 全バリデート通過後に書き込み / Write only after all validation passes
        int saved = 0, deleted = 0;
        for (Map.Entry<String, Integer> entry : toSave.entrySet()) {
            if (paidLeaveDao.save(selectedEmployee.getId(), entry.getKey(), entry.getValue())) {
                saved++;
            }
        }
        for (String date : toDelete) {
            if (paidLeaveDao.delete(selectedEmployee.getId(), date)) {
                deleted++;
            }
        }

        String msg;
        if (saved == 0 && deleted == 0) {
            msg = "保存またはクリアするデータがありません";
        } else if (deleted == 0) {
            msg = saved + " 日分の有休を保存しました";
        } else if (saved == 0) {
            msg = deleted + " 日分の有休をクリアしました";
        } else {
            msg = saved + " 日分を保存、" + deleted + " 日分をクリアしました";
        }
        JOptionPane.showMessageDialog(this, msg);
    }
}