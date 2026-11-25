package com.hotel.ui;

import com.hotel.dao.EmployeeDao;
import com.hotel.dao.WishOffDao;
import com.hotel.model.Employee;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 希望休录入面板：月历视图，每天一个复选框
 * 希望休入力パネル：月カレンダー表示、1 日 1 つのチェックボックス
 * Wish-off input panel: monthly calendar view, one checkbox per day
 *
 * <p>希望休 = 员工希望休息的日期（软性目标，未必能被算法满足）。
 * 算法会尽量在希望休日不排班，但当硬性约束强迫时仍可能被排 → 统计里会标红</p>
 *
 * <p>希望休 = 従業員が休みたい日付（ソフト目標、必ずしも充足されない）。
 * アルゴリズムは希望休日に配分を避けるが、ハード制約に押されて配分される場合あり → 統計で赤表示</p>
 *
 * <p>Wish-off = date an employee wishes to rest (soft goal, not always granted).
 * Algorithm avoids scheduling on wish-off days, but may still assign under hard constraints → shown red in stats</p>
 *
 * <p>与「有休 (Paid Leave)」的区别：有休是法定带薪假、硬性禁排、算工时；
 * 希望休仅是偏好、软性目标、不算工时。</p>
 *
 * <p>「有給休暇」との違い：有給は法定有給、ハード禁止、勤務時間に計上；
 * 希望休は単なる希望、ソフト目標、勤務時間未計上。</p>
 *
 * <p>Difference from paid leave: paid leave is statutory, hard-blocked, counts as hours;
 * wish-off is just a preference, soft goal, doesn't count.</p>
 */
public class WishOffPanel extends JPanel {

    private final EmployeeDao employeeDao = new EmployeeDao();
    private final WishOffDao wishOffDao = new WishOffDao();

    private JComboBox<String> yearBox;
    private JComboBox<String> monthBox;
    private JComboBox<Employee> employeeBox;
    private JPanel calendarPanel;
    private JLabel countLabel;

    // 当月每一天的复选框：日期字符串 → 复选框
    // 当月各日のチェックボックス：日付文字列 → チェックボックス
    // Checkbox per day in current month: date string → checkbox
    private final Map<String, JCheckBox> dateChecks = new HashMap<>();

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] WEEK_NAMES = {"日", "月", "火", "水", "木", "金", "土"};

    /**
     * 构造方法：初始化 UI 并加载数据
     * コンストラクタ：UI 初期化とデータ読み込み
     * Constructor: initializes UI and loads data
     */
    public WishOffPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 顶部控件区 / 上部コントロール / Top controls
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
        for (int m = 1; m <= 12; m++) {
            monthBox.addItem(m + " 月");
        }
        monthBox.setSelectedIndex(LocalDate.now().getMonthValue() - 1);
        topPanel.add(monthBox);

        topPanel.add(new JLabel("従業員："));
        employeeBox = new JComboBox<>();
        employeeBox.setPreferredSize(new Dimension(200, 28));
        topPanel.add(employeeBox);
        reloadEmployees();  // 抽出来的加载方法 / 独立化したロードメソッド / Extracted loading method

        add(topPanel, BorderLayout.NORTH);

        // 中间月历 / 中央カレンダー / Center calendar
        calendarPanel = new JPanel();
        JScrollPane scroll = new JScrollPane(calendarPanel);
        scroll.setBorder(BorderFactory.createTitledBorder("月カレンダー（チェック = 希望休）"));
        add(scroll, BorderLayout.CENTER);

        // 底部：统计 + 按钮 / 下部：集計 + ボタン / Bottom: count + buttons
        JPanel bottomPanel = new JPanel(new BorderLayout());

        countLabel = new JLabel("当月希望休日数：0 日");
        countLabel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        countLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        bottomPanel.add(countLabel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton clearBtn = new JButton("全てクリア");
        JButton saveBtn = new JButton("当月保存");
        buttonPanel.add(clearBtn);
        buttonPanel.add(saveBtn);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);

        add(bottomPanel, BorderLayout.SOUTH);

        // 事件绑定 / イベントバインド / Event binding
        yearBox.addActionListener(e -> rebuildCalendar());
        monthBox.addActionListener(e -> rebuildCalendar());
        employeeBox.addActionListener(e -> rebuildCalendar());
        saveBtn.addActionListener(e -> saveCurrent());
        clearBtn.addActionListener(e -> clearAll());

        rebuildCalendar();
    }

    /**
     * 根据当前选中的年/月/员工重新构建月历视图
     * 選択中の年・月・従業員に応じてカレンダーを再構築
     * Rebuild calendar view based on selected year/month/employee
     */
    private void rebuildCalendar() {
        calendarPanel.removeAll();
        dateChecks.clear();

        Employee selectedEmployee = (Employee) employeeBox.getSelectedItem();
        if (selectedEmployee == null) {
            calendarPanel.add(new JLabel("在職中の従業員がいません。先に「従業員管理」で追加してください"));
            calendarPanel.revalidate();
            calendarPanel.repaint();
            updateCountLabel();
            return;
        }

        int year = Integer.parseInt(((String) yearBox.getSelectedItem()).replace(" 年", "").trim());
        int month = Integer.parseInt(((String) monthBox.getSelectedItem()).replace(" 月", "").trim());
        YearMonth ym = YearMonth.of(year, month);
        int daysInMonth = ym.lengthOfMonth();
        LocalDate firstDay = ym.atDay(1);
        // 一号是周几（日=0、月=1、...、土=6）
        // 1 日の曜日（日=0、月=1、...、土=6）
        // Day-of-week of the 1st (Sun=0, Mon=1, ..., Sat=6)
        int firstDayOfWeek = firstDay.getDayOfWeek().getValue() % 7;

        String yearMonthStr = String.format("%04d-%02d", year, month);
        Set<String> existing = wishOffDao.findByEmployeeAndMonth(selectedEmployee.getId(), yearMonthStr);

        calendarPanel.setLayout(new GridLayout(0, 7, 5, 5));

        // 星期标题（日曜红、土曜蓝）/ 曜日ヘッダ（日曜赤、土曜青）/ Weekday header (Sun red, Sat blue)
        for (int i = 0; i < 7; i++) {
            JLabel weekLabel = new JLabel(WEEK_NAMES[i], SwingConstants.CENTER);
            weekLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
            if (i == 0) weekLabel.setForeground(Color.RED);
            else if (i == 6) weekLabel.setForeground(Color.BLUE);
            calendarPanel.add(weekLabel);
        }

        // 1 号之前的空格 / 1 日より前の空セル / Empty cells before the 1st
        for (int i = 0; i < firstDayOfWeek; i++) {
            calendarPanel.add(new JLabel(""));
        }

        // 每一天的复选框单元格 / 各日のチェックボックスセル / Checkbox cell for each day
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

            JCheckBox check = new JCheckBox("希望休");
            check.setSelected(existing.contains(dateStr));
            check.setAlignmentX(Component.CENTER_ALIGNMENT);
            // 勾选状态变化时实时更新计数 / チェック変更時にリアルタイム更新 / Update count in real-time on toggle
            check.addActionListener(e -> updateCountLabel());
            dayCell.add(check);

            dateChecks.put(dateStr, check);
            calendarPanel.add(dayCell);
        }

        calendarPanel.revalidate();
        calendarPanel.repaint();
        updateCountLabel();
    }

    /**
     * 更新底部"当月希望休数"统计
     * 下部「当月希望休日数」の集計を更新
     * Update bottom "wish-off days this month" count
     */
    private void updateCountLabel() {
        int count = 0;
        for (JCheckBox c : dateChecks.values()) {
            if (c.isSelected()) count++;
        }
        countLabel.setText("当月希望休日数：" + count + " 日");
    }

    /**
     * 保存当前月的所有希望休（replaceMonth 会先删后插）
     * 当月の全希望休を保存（replaceMonth は削除後挿入）
     * Save all wish-offs for current month (replaceMonth deletes then inserts)
     */
    private void saveCurrent() {
        Employee selectedEmployee = (Employee) employeeBox.getSelectedItem();
        if (selectedEmployee == null) return;

        int year = Integer.parseInt(((String) yearBox.getSelectedItem()).replace(" 年", "").trim());
        int month = Integer.parseInt(((String) monthBox.getSelectedItem()).replace(" 月", "").trim());
        String yearMonthStr = String.format("%04d-%02d", year, month);

        // 收集所有勾选的日期 / 全チェック済み日付を収集 / Collect all checked dates
        Set<String> selectedDates = new HashSet<>();
        for (Map.Entry<String, JCheckBox> entry : dateChecks.entrySet()) {
            if (entry.getValue().isSelected()) {
                selectedDates.add(entry.getKey());
            }
        }

        // DAO 的 replaceMonth 是事务操作（删旧 + 插新），保证原子性
        // DAO の replaceMonth はトランザクション（旧削除 + 新挿入）、原子性を保証
        // DAO's replaceMonth is transactional (delete + insert), ensures atomicity
        if (wishOffDao.replaceMonth(selectedEmployee.getId(), yearMonthStr, selectedDates)) {
            JOptionPane.showMessageDialog(this,
                    "従業員「" + selectedEmployee.getName() + "」の " +
                            yearMonthStr + " の希望休 " + selectedDates.size() + " 日分を保存しました");
        } else {
            JOptionPane.showMessageDialog(this, "保存に失敗しました", "エラー", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 清空所有勾选（仅操作 UI，不直接写 DB）
     * 全チェックを解除（UI 操作のみ、DB 書込なし）
     * Clear all checks (UI only, no DB write)
     *
     * 需要用户点"保存"才真正清空数据库，防止误操作。
     * 「保存」ボタンでのみ DB クリア、誤操作防止。
     * DB cleared only on "Save" click, prevents accidental data loss.
     */
    private void clearAll() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "当月の全希望休をクリアしてもよろしいですか？\n（「保存」をクリックしないと DB に反映されません）",
                "クリア確認", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        for (JCheckBox c : dateChecks.values()) {
            c.setSelected(false);
        }
        updateCountLabel();
    }

    /**
     * 重新从数据库加载员工列表，保留当前选中项
     * DB から従業員リストを再読み込み、現在の選択を維持
     * Reload employee list from DB, preserve current selection
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
        // 用 HierarchyListener 精确监听"变可见"事件
        // HierarchyListener で「可視化」イベントを精確に監視
        // Use HierarchyListener to precisely watch "became visible" events
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (isShowing()) {
                    reloadEmployees();
                }
            }
        });
    }
}