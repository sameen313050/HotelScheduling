package com.hotel.ui;

import com.hotel.dao.*;
import com.hotel.model.Employee;
import com.hotel.model.Facility;
import com.hotel.model.ShiftType;
import com.hotel.scheduling.Assignment;
import com.hotel.scheduling.SchedulingContext;
import com.hotel.scheduling.SchedulingEngine;
import com.hotel.scheduling.SchedulingResult;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * 排班面板：生成排班 + 显示结果 + 手动调整
 * シフト表パネル：シフト生成 + 結果表示 + 手動調整
 * Schedule panel: generate schedule + display results + manual adjustment
 *
 * <p>整个系统最核心的 UI。关键设计：</p>
 * <ul>
 *   <li><b>固定列</b>：左表 + 右表 + 假滚动条，员工/设施名永远可见</li>
 *   <li><b>双视图</b>：按员工看（行=员工）/ 按设施看（行=设施）</li>
 *   <li><b>手动调整</b>：双击格子弹菜单，替换/删除/新增班次</li>
 *   <li><b>视觉提示</b>：希望休未满足用红框标注</li>
 * </ul>
 *
 * <p>システムの最も重要な UI。主要設計：</p>
 * <ul>
 *   <li><b>固定列</b>：左テーブル + 右テーブル + ダミースクロールバー、従業員・施設名を常時表示</li>
 *   <li><b>2 ビュー</b>：従業員別（行=従業員）/ 施設別（行=施設）</li>
 *   <li><b>手動調整</b>：セルをダブルクリックでメニュー、置換・削除・追加</li>
 *   <li><b>視覚強調</b>：希望休未達は赤枠で表示</li>
 * </ul>
 */
public class SchedulePanel extends JPanel {

    private final ScheduleDao scheduleDao = new ScheduleDao();
    private final EmployeeDao employeeDao = new EmployeeDao();
    private final FacilityDao facilityDao = new FacilityDao();
    private final ShiftTypeDao shiftTypeDao = new ShiftTypeDao();

    private JComboBox<String> yearBox;
    private JComboBox<String> monthBox;
    private JComboBox<String> viewBox;

    // 固定列实现：左表显示员工/设施名，右表显示日期数据
    // 固定列実装：左テーブルに従業員・施設名、右テーブルに日付データ
    // Fixed column: left table for names, right table for date data
    private JTable leftTable;
    private JTable rightTable;
    private DefaultTableModel leftModel;
    private DefaultTableModel rightModel;
    private JScrollPane rightScroll;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String[] WEEK_NAMES = {"日", "月", "火", "水", "木", "金", "土"};
    private static final int LEFT_COL_WIDTH = 120;

    // 当前内存中的排班（编辑操作直接修改这个 list，再 save 到 DB）
    // 現在のメモリ上のシフト（編集操作は直接このリストを変更、その後 DB へ保存）
    // In-memory assignments (edit ops modify this list directly, then save to DB)
    private List<Assignment> currentAssignments = new ArrayList<>();

    // 当月希望休缓存（渲染红框时查询用）
    // 当月希望休キャッシュ（赤枠描画時に参照）
    // Monthly wish-off cache (used when rendering red borders)
    private Map<Integer, Set<String>> wishOffMap = new HashMap<>();

    /**
     * 构造方法：初始化 UI 并加载当月数据
     * コンストラクタ：UI 初期化と当月データロード
     * Constructor: initialize UI and load current month data
     */
    public SchedulePanel() {
        setLayout(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

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

        topPanel.add(new JLabel("表示："));
        viewBox = new JComboBox<>(new String[]{"従業員別", "施設別"});
        topPanel.add(viewBox);

        JButton generateBtn = new JButton("シフト生成");
        JButton loadBtn = new JButton("既存を読込");
        JButton clearBtn = new JButton("当月クリア");
        JButton statsBtn = new JButton("統計表示");
        JButton exportBtn = new JButton("Excel 出力");
        topPanel.add(exportBtn);
        topPanel.add(generateBtn);
        topPanel.add(loadBtn);
        topPanel.add(clearBtn);
        topPanel.add(statsBtn);

        add(topPanel, BorderLayout.NORTH);

        buildTablesArea();

        generateBtn.addActionListener(e -> generateSchedule());
        loadBtn.addActionListener(e -> loadFromDatabase());
        clearBtn.addActionListener(e -> clearMonth());
        statsBtn.addActionListener(e -> showStatistics());
        exportBtn.addActionListener(e -> exportToExcel());
        viewBox.addActionListener(e -> renderCurrent());
        yearBox.addActionListener(e -> loadFromDatabase());
        monthBox.addActionListener(e -> loadFromDatabase());

        loadFromDatabase();
    }

    /**
     * 构建左右两张表的布局（固定列核心实现）
     * 左右 2 つのテーブルレイアウトを構築（固定列のコア実装）
     * Build the two-table layout (core of the fixed-column implementation)
     *
     * 技术难点：Swing 原生不支持"固定列"，用两个 JTable + 垂直滚动同步 + 假水平滚动条实现。
     * 技術的な難点：Swing は「固定列」をネイティブ対応しないため、2 つの JTable + 垂直スクロール同期 + ダミー水平スクロールバーで実現。
     * Technical challenge: Swing has no native fixed-column support; achieved via 2 JTables + synced vertical scroll + dummy horizontal bar.
     */
    private void buildTablesArea() {
        // 左表：员工/设施名（固定宽度、禁拖、禁调整）
        // 左テーブル：従業員・施設名（固定幅、ドラッグ禁止、リサイズ禁止）
        // Left table: names (fixed width, no drag, no resize)
        leftModel = new DefaultTableModel(new String[]{"従業員"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        leftTable = new JTable(leftModel);
        leftTable.setRowHeight(45);
        leftTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        leftTable.getTableHeader().setReorderingAllowed(false);
        leftTable.getTableHeader().setResizingAllowed(false);
        leftTable.getColumnModel().getColumn(0).setPreferredWidth(LEFT_COL_WIDTH);
        leftTable.getColumnModel().getColumn(0).setMinWidth(LEFT_COL_WIDTH);
        leftTable.getColumnModel().getColumn(0).setMaxWidth(LEFT_COL_WIDTH);
        leftTable.setIntercellSpacing(new Dimension(0, 1));
        leftTable.setShowGrid(true);
        leftTable.setGridColor(new Color(220, 220, 220));

        // 右表：日期数据（可横向滚动）
        // 右テーブル：日付データ（横スクロール可）
        // Right table: date data (horizontally scrollable)
        rightModel = new DefaultTableModel(new String[]{""}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        rightTable = new JTable(rightModel);
        rightTable.setRowHeight(45);
        rightTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        rightTable.getTableHeader().setReorderingAllowed(false);
        rightTable.getTableHeader().setResizingAllowed(false);
        rightTable.setIntercellSpacing(new Dimension(0, 1));
        rightTable.setShowGrid(true);
        rightTable.setGridColor(new Color(220, 220, 220));

        // 共用的单元格渲染器（根据内容和状态设置颜色、红框）
        // 共通セルレンダラー（内容と状態に応じて色・赤枠を設定）
        // Shared cell renderer (colors and red border based on content/state)
        DefaultTableCellRenderer cellRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                           boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.CENTER);
                ((JComponent) c).setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));

                if (!isSelected) {
                    if (table == leftTable) {
                        // 左表：灰色背景 / 左テーブル：グレー背景 / Left table: grey background
                        c.setBackground(new Color(240, 240, 240));
                        c.setForeground(Color.BLACK);
                    } else {
                        String cellStr = value == null ? "" : value.toString();
                        if (cellStr.isEmpty() || cellStr.equals("休")) {
                            // 空格子（休息日）/ 空セル（休息日）/ Empty cell (rest day)
                            c.setBackground(new Color(250, 250, 250));
                            c.setForeground(Color.LIGHT_GRAY);
                        } else {
                            c.setBackground(Color.WHITE);
                            c.setForeground(Color.BLACK);

                            // 希望休未满足 → 红框 + 红字
                            // 希望休未達 → 赤枠 + 赤字
                            // Unmet wish-off → red border + red text
                            if ("従業員別".equals(viewBox.getSelectedItem())) {
                                if (isWishOffUnmet(row, column)) {
                                    ((JComponent) c).setBorder(BorderFactory.createLineBorder(Color.RED, 2));
                                    c.setForeground(new Color(180, 0, 0));
                                }
                            }
                        }
                    }
                }
                return c;
            }
        };
        leftTable.setDefaultRenderer(Object.class, cellRenderer);
        rightTable.setDefaultRenderer(Object.class, cellRenderer);

        // 双击事件（手动调整入口）
        // ダブルクリックイベント（手動調整のエントリーポイント）
        // Double-click event (entry point for manual adjustment)
        java.awt.event.MouseAdapter dblClick = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    JTable src = (JTable) e.getSource();
                    int row = src.rowAtPoint(e.getPoint());
                    int col = src.columnAtPoint(e.getPoint());
                    if (row < 0) return;
                    if (src == leftTable) return;  // 左表（名字列）不处理
                    if (col < 0) return;
                    // col + 1 是因为显示在右表的 col=0 对应月份第 1 天
                    // col + 1 は右テーブルの col=0 が月の 1 日に対応するため
                    // col + 1 because right table's col=0 corresponds to day 1 of month
                    handleCellDoubleClick(row, col + 1, e);
                }
            }
        };
        leftTable.addMouseListener(dblClick);
        rightTable.addMouseListener(dblClick);

        // 左右表选中行联动
        // 左右テーブルの選択行を連動
        // Sync selected row between left and right tables
        leftTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int sel = leftTable.getSelectedRow();
                if (sel >= 0 && sel < rightTable.getRowCount()) {
                    rightTable.getSelectionModel().setSelectionInterval(sel, sel);
                }
            }
        });
        rightTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int sel = rightTable.getSelectedRow();
                if (sel >= 0 && sel < leftTable.getRowCount()) {
                    leftTable.getSelectionModel().setSelectionInterval(sel, sel);
                }
            }
        });

        // 左表 ScrollPane（无滚动条）/ 左テーブル ScrollPane（スクロールバー無し）/ Left ScrollPane (no scrollbars)
        JScrollPane leftScroll = new JScrollPane(leftTable);
        leftScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
        leftScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        leftScroll.setBorder(null);
        leftScroll.setViewportBorder(null);
        leftScroll.setPreferredSize(new Dimension(LEFT_COL_WIDTH, 400));
        leftScroll.setMinimumSize(new Dimension(LEFT_COL_WIDTH, 100));
        leftScroll.setMaximumSize(new Dimension(LEFT_COL_WIDTH, Integer.MAX_VALUE));
        leftScroll.getViewport().setBackground(Color.WHITE);

        // 右表 ScrollPane（垂直永远显示，水平按需）
        // 右テーブル ScrollPane（垂直常時表示、水平は必要時）
        // Right ScrollPane (vertical always, horizontal as needed)
        rightScroll = new JScrollPane(rightTable);
        rightScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        rightScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        rightScroll.setBorder(null);
        rightScroll.setViewportBorder(null);
        rightScroll.getViewport().setBackground(Color.WHITE);

        // 假水平滚动条：左表底部占位，保证左右高度对齐
        // ダミー水平スクロールバー：左テーブル底部のプレースホルダ、左右の高さ揃え
        // Dummy horizontal scrollbar: placeholder at left table bottom to align heights
        JScrollBar fakeHBar = new JScrollBar(JScrollBar.HORIZONTAL);
        fakeHBar.setPreferredSize(new Dimension(0, 15));
        fakeHBar.setVisible(true);
        fakeHBar.setEnabled(false);

        JPanel leftWrapper = new JPanel(new BorderLayout());
        leftWrapper.setBorder(null);
        leftWrapper.add(leftScroll, BorderLayout.CENTER);
        leftWrapper.add(fakeHBar, BorderLayout.SOUTH);
        leftWrapper.setPreferredSize(new Dimension(LEFT_COL_WIDTH, 400));
        leftWrapper.setMinimumSize(new Dimension(LEFT_COL_WIDTH, 100));

        // 垂直滚动同步：右表滚动 → 左表跟随
        // 垂直スクロール同期：右テーブル → 左テーブル追従
        // Sync vertical scroll: right table → left table follows
        rightScroll.getVerticalScrollBar().addAdjustmentListener(e -> {
            leftScroll.getVerticalScrollBar().setValue(e.getValue());
        });

        JPanel combined = new JPanel(new BorderLayout(0, 0));
        combined.setBorder(null);
        combined.add(leftWrapper, BorderLayout.WEST);
        combined.add(rightScroll, BorderLayout.CENTER);

        add(combined, BorderLayout.CENTER);
    }

    // ===== 下拉值解析 / ドロップダウン値解析 / Dropdown value parsing =====

    private int getSelectedYear() {
        return Integer.parseInt(((String) yearBox.getSelectedItem()).replace(" 年", "").trim());
    }

    private int getSelectedMonth() {
        return Integer.parseInt(((String) monthBox.getSelectedItem()).replace(" 月", "").trim());
    }

    private String getSelectedYearMonth() {
        return String.format("%04d-%02d", getSelectedYear(), getSelectedMonth());
    }

    /**
     * 执行排班生成（算法入口 + 前置校验 + 结果展示）
     * シフト生成を実行（アルゴリズム入口 + 事前バリデーション + 結果表示）
     * Run schedule generation (algorithm entry + pre-validation + result display)
     */
    private void generateSchedule() {
        int year = getSelectedYear();
        int month = getSelectedMonth();

        int confirm = JOptionPane.showConfirmDialog(this,
                year + " 年 " + month + " 月のシフト草案を生成します。\n" +
                        "既存の当月シフトがあれば上書きされます。\n\n続行してもよろしいですか？",
                "生成確認", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        SchedulingContext ctx = new SchedulingContext(year, month);
        try {
            ctx.load();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this,
                    "データ読み込みに失敗しました：" + e.getMessage(),
                    "エラー", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 前置校验：必须有员工/设施/班次
        // 事前バリデーション：従業員・施設・シフトが必須
        // Pre-check: employees/facilities/shift types required
        if (ctx.getEmployees().isEmpty()) {
            JOptionPane.showMessageDialog(this, "在職中の従業員がいません");
            return;
        }
        if (ctx.getFacilities().isEmpty()) {
            JOptionPane.showMessageDialog(this, "施設がありません");
            return;
        }
        if (ctx.getShiftTypes().isEmpty()) {
            JOptionPane.showMessageDialog(this, "シフト種別がありません");
            return;
        }

        // 校验：各设施是否有当月的 CI/CO/稼働率 数据
        // バリデーション：各施設の当月 CI/CO/稼働率 データ有無
        // Validate: each facility has monthly CI/CO/occupancy data
        List<String> missingFacilities = new ArrayList<>();
        OccupancyDao occDao = new OccupancyDao();
        String yearMonthStr = String.format("%04d-%02d", year, month);
        for (Facility f : ctx.getFacilities()) {
            Map<String, com.hotel.model.Occupancy> data =
                    occDao.findFullByFacilityAndMonth(f.getId(), yearMonthStr);
            if (data.isEmpty()) {
                missingFacilities.add(f.getName());
            }
        }

        // 全部设施缺数据 → 终止（否则会生成空白排班）
        // 全施設データ欠落 → 中止（さもなければ空白シフトが生成される）
        // All facilities missing data → abort (otherwise generates blank schedule)
        if (missingFacilities.size() == ctx.getFacilities().size()) {
            JOptionPane.showMessageDialog(this,
                    "当月はまだ CI/CO/稼働率 データが未入力です。\n" +
                            "先に「稼働率」ページで翌月の予測データを入力してください。\n" +
                            "システムはそのデータを基に各日・各シフトの必要人数を算出します。",
                    "データ欠落", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!missingFacilities.isEmpty()) {
            String list = String.join("、", missingFacilities);
            int confirm2 = JOptionPane.showConfirmDialog(this,
                    "以下の施設は当月の CI/CO/稼働率 データが未入力です：\n" +
                            "  " + list + "\n\n" +
                            "これらの施設はシフト生成できません（スキップされます）。\n" +
                            "他施設のシフト生成を続行しますか？",
                    "一部施設のデータ欠落",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (confirm2 != JOptionPane.YES_OPTION) return;
        }

        SchedulingEngine engine = new SchedulingEngine(ctx);
        SchedulingResult result = engine.run();

        if (!scheduleDao.saveMonth(getSelectedYearMonth(), result.getAssignments())) {
            JOptionPane.showMessageDialog(this, "保存に失敗しました", "エラー", JOptionPane.ERROR_MESSAGE);
            return;
        }

        currentAssignments = result.getAssignments();
        renderCurrent();

        // 自动弹出问题报告（硬性缺人 + 软性未满足）
        // 問題レポート自動表示（ハード不足 + ソフト未達）
        // Auto-show problem report (hard shortage + soft unmet)
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        ProblemReportDialog dialog = new ProblemReportDialog(owner, result);
        dialog.setVisible(true);
    }

    /**
     * 从数据库加载当前选中年月的排班数据
     * DB から選択中の年月のシフトデータを読み込み
     * Load schedule data for the selected year/month from DB
     */
    private void loadFromDatabase() {
        currentAssignments = scheduleDao.loadMonth(
                getSelectedYearMonth(), employeeDao, facilityDao, shiftTypeDao);
        renderCurrent();
    }

    /**
     * 清空当月所有排班（含二次确认）
     * 当月の全シフトをクリア（確認ダイアログあり）
     * Clear all schedules for current month (with confirmation)
     */
    private void clearMonth() {
        int confirm = JOptionPane.showConfirmDialog(this,
                getSelectedYear() + " 年 " + getSelectedMonth() + " 月のシフトをクリアしてもよろしいですか？",
                "クリア確認", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        if (scheduleDao.clearMonth(getSelectedYearMonth())) {
            currentAssignments = new ArrayList<>();
            renderCurrent();
            JOptionPane.showMessageDialog(this, "クリアしました");
        }
    }

    /**
     * 根据当前视图渲染数据
     * 現在のビューに応じてデータを描画
     * Render data according to current view
     */
    private void renderCurrent() {
        loadWishOffMap();
        String view = (String) viewBox.getSelectedItem();
        if ("従業員別".equals(view)) {
            renderByEmployee();
        } else {
            renderByFacility();
        }
    }

    /**
     * 加载当月所有在职员工的希望休（渲染时的查询缓存）
     * 当月の全在職従業員の希望休をロード（描画時の参照キャッシュ）
     * Load wish-off data for all active employees (cache used during rendering)
     */
    private void loadWishOffMap() {
        wishOffMap.clear();
        WishOffDao wishDao = new WishOffDao();
        String yearMonth = getSelectedYearMonth();
        for (Employee emp : employeeDao.findAll()) {
            if (emp.isActive()) {
                wishOffMap.put(emp.getId(), wishDao.findByEmployeeAndMonth(emp.getId(), yearMonth));
            }
        }
    }

    /**
     * 按员工视图渲染：每行一个员工，列为日期
     * 従業員別ビューで描画：1 行 1 従業員、列は日付
     * Render by-employee view: one row per employee, columns are dates
     */
    private void renderByEmployee() {
        int year = getSelectedYear();
        int month = getSelectedMonth();
        YearMonth ym = YearMonth.of(year, month);
        int daysInMonth = ym.lengthOfMonth();

        leftModel.setDataVector(new Object[0][1], new String[]{"従業員"});
        leftTable.getColumnModel().getColumn(0).setPreferredWidth(LEFT_COL_WIDTH);
        leftTable.getColumnModel().getColumn(0).setMinWidth(LEFT_COL_WIDTH);
        leftTable.getColumnModel().getColumn(0).setMaxWidth(LEFT_COL_WIDTH);

        // 列头：1(日) 2(月) ... 31(土) 出勤日数
        // 列ヘッダ：1(日) 2(月) ... 31(土) 出勤日数
        // Column headers: 1(Sun) 2(Mon) ... 31(Sat) WorkDays
        String[] rightCols = new String[daysInMonth + 1];
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate d = ym.atDay(day);
            int wdIdx = d.getDayOfWeek().getValue() % 7;
            rightCols[day - 1] = day + "(" + WEEK_NAMES[wdIdx] + ")";
        }
        rightCols[daysInMonth] = "出勤日数";
        rightModel.setDataVector(new Object[0][rightCols.length], rightCols);

        List<Employee> employees = getSortedActiveEmployees();

        for (Employee emp : employees) {
            leftModel.addRow(new Object[]{emp.getName()});

            Object[] row = new Object[rightCols.length];
            int workDays = 0;
            for (int day = 1; day <= daysInMonth; day++) {
                LocalDate date = ym.atDay(day);
                Assignment found = null;
                for (Assignment a : currentAssignments) {
                    if (a.getDate().equals(date) && a.getEmployee().getId() == emp.getId()) {
                        found = a;
                        break;
                    }
                }
                if (found != null) {
                    // 单元格显示：时段 + 设施简称（取前 3 字）
                    // セル表示：時間帯 + 施設略称（先頭 3 文字）
                    // Cell: period + facility short name (first 3 chars)
                    String facName = found.getFacility().getName();
                    row[day - 1] = periodShort(found.getShiftType().getPeriod())
                            + "\n" + facName.substring(0, Math.min(3, facName.length()));
                    workDays++;
                } else {
                    row[day - 1] = "";
                }
            }
            row[daysInMonth] = workDays + " 日";
            rightModel.addRow(row);
        }

        for (int i = 0; i < daysInMonth; i++) {
            rightTable.getColumnModel().getColumn(i).setPreferredWidth(80);
        }
        rightTable.getColumnModel().getColumn(daysInMonth).setPreferredWidth(80);
        syncRowHeight(45);
    }

    /**
     * 按设施视图渲染：每行一个设施，列为日期，格内列出员工
     * 施設別ビューで描画：1 行 1 施設、列は日付、セル内に従業員を列挙
     * Render by-facility view: one row per facility, columns are dates, cells list employees
     */
    private void renderByFacility() {
        int year = getSelectedYear();
        int month = getSelectedMonth();
        YearMonth ym = YearMonth.of(year, month);
        int daysInMonth = ym.lengthOfMonth();

        leftModel.setDataVector(new Object[0][1], new String[]{"施設"});
        leftTable.getColumnModel().getColumn(0).setPreferredWidth(LEFT_COL_WIDTH);
        leftTable.getColumnModel().getColumn(0).setMinWidth(LEFT_COL_WIDTH);
        leftTable.getColumnModel().getColumn(0).setMaxWidth(LEFT_COL_WIDTH);

        String[] rightCols = new String[daysInMonth];
        for (int day = 1; day <= daysInMonth; day++) {
            LocalDate d = ym.atDay(day);
            int wdIdx = d.getDayOfWeek().getValue() % 7;
            rightCols[day - 1] = day + "(" + WEEK_NAMES[wdIdx] + ")";
        }
        rightModel.setDataVector(new Object[0][rightCols.length], rightCols);

        List<Facility> facilities = facilityDao.findAll();
        // 行高动态调整：根据单日最多员工数
        // 行高を動的調整：1 日の最大従業員数に応じて
        // Dynamic row height based on max employees per day
        int maxPerCell = 0;
        for (Facility f : facilities) {
            leftModel.addRow(new Object[]{f.getName()});

            Object[] row = new Object[rightCols.length];
            for (int day = 1; day <= daysInMonth; day++) {
                LocalDate date = ym.atDay(day);
                List<Assignment> cellAssignments = new ArrayList<>();
                for (Assignment a : currentAssignments) {
                    if (a.getDate().equals(date) && a.getFacility().getId() == f.getId()) {
                        cellAssignments.add(a);
                    }
                }
                if (cellAssignments.size() > maxPerCell) maxPerCell = cellAssignments.size();

                // HTML 实现多行显示（员工名 + 时段）
                // HTML で複数行表示（従業員名 + 時間帯）
                // HTML enables multi-line display (name + period)
                StringBuilder cell = new StringBuilder("<html>");
                for (Assignment a : cellAssignments) {
                    cell.append(a.getEmployee().getName())
                            .append("<br><small>")
                            .append(periodShort(a.getShiftType().getPeriod()))
                            .append("</small><br>");
                }
                cell.append("</html>");
                row[day - 1] = cell.toString();
            }
            rightModel.addRow(row);
        }

        for (int i = 0; i < daysInMonth; i++) {
            rightTable.getColumnModel().getColumn(i).setPreferredWidth(100);
        }
        syncRowHeight(Math.max(60, maxPerCell * 32));
    }

    /**
     * 同步左右表的行高
     * 左右テーブルの行高を同期
     * Sync row height between left and right tables
     */
    private void syncRowHeight(int h) {
        leftTable.setRowHeight(h);
        rightTable.setRowHeight(h);
    }

    private String periodShort(String period) {
        if (period == null) return "";
        return period;
    }

    /**
     * 显示统计对话框
     * 統計ダイアログを表示
     * Show statistics dialog
     */
    private void showStatistics() {
        if (currentAssignments.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "当月のシフトデータがありません。先に生成または読み込みしてください");
            return;
        }
        Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
        StatisticsDialog dialog = new StatisticsDialog(owner, getSelectedYearMonth(), currentAssignments);
        dialog.setVisible(true);
    }

    /**
     * 判断某格子的员工在这天是否有希望休未满足
     * 指定セルの従業員がこの日に希望休未達か判定
     * Check whether the employee of this cell has an unmet wish-off on this date
     */
    private boolean isWishOffUnmet(int row, int col) {
        try {
            int year = getSelectedYear();
            int month = getSelectedMonth();
            YearMonth ym = YearMonth.of(year, month);
            int daysInMonth = ym.lengthOfMonth();
            if (col < 0 || col >= daysInMonth) return false;
            LocalDate date = ym.atDay(col + 1);

            List<Employee> employees = getSortedActiveEmployees();
            if (row >= employees.size()) return false;
            Employee emp = employees.get(row);
            Set<String> wishDates = wishOffMap.get(emp.getId());
            if (wishDates == null || wishDates.isEmpty()) return false;
            return wishDates.contains(date.toString());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取按类型排序的在职员工列表（正社员 → 兼职 → 契约 → 夜勤）
     * 種別順で並べた在職従業員リスト（正社員 → アルバイト → 契約 → 夜勤）
     * Return active employees sorted by type (regular → part-time → contract → night)
     */
    private List<Employee> getSortedActiveEmployees() {
        List<Employee> employees = new ArrayList<>();
        for (Employee e : employeeDao.findAll()) {
            if (e.isActive()) employees.add(e);
        }
        Map<String, Integer> typeOrder = new HashMap<>();
        typeOrder.put("正社員", 1);
        typeOrder.put("アルバイト", 2);
        typeOrder.put("契約社員", 3);
        typeOrder.put("夜勤専属", 4);
        employees.sort((a, b) -> {
            int oa = typeOrder.getOrDefault(a.getEmployeeType(), 99);
            int ob = typeOrder.getOrDefault(b.getEmployeeType(), 99);
            if (oa != ob) return Integer.compare(oa, ob);
            return Integer.compare(a.getId(), b.getId());
        });
        return employees;
    }

    // ========== 双击事件处理 / ダブルクリックイベント処理 / Double-click handling ==========

    private void handleCellDoubleClick(int row, int col, java.awt.event.MouseEvent e) {
        String view = (String) viewBox.getSelectedItem();
        boolean isEmployeeView = "従業員別".equals(view);

        int year = getSelectedYear();
        int month = getSelectedMonth();
        YearMonth ym = YearMonth.of(year, month);
        int daysInMonth = ym.lengthOfMonth();

        if (col < 1 || col > daysInMonth) return;
        LocalDate date = ym.atDay(col);

        if (isEmployeeView) {
            handleEmployeeViewClick(row, date, e);
        } else {
            handleFacilityViewClick(row, date, e);
        }
    }

    /**
     * 按员工视图下的双击：有班次 → 替换/删除菜单；空 → 分配菜单
     * 従業員別ビューのダブルクリック：シフトあり → 置換・削除メニュー、空 → 割当メニュー
     * By-employee double-click: existing shift → replace/delete menu; empty → assign menu
     */
    private void handleEmployeeViewClick(int row, LocalDate date, java.awt.event.MouseEvent e) {
        List<Employee> employees = getSortedActiveEmployees();
        if (row >= employees.size()) return;
        Employee emp = employees.get(row);

        Assignment existing = null;
        for (Assignment a : currentAssignments) {
            if (a.getDate().equals(date) && a.getEmployee().getId() == emp.getId()) {
                existing = a;
                break;
            }
        }

        if (existing != null) {
            showReplaceOrDeleteMenu(emp, date, existing, e);
        } else {
            showAssignMenu(emp, date, e);
        }
    }

    /**
     * 按设施视图下的双击：弹对话框让用户选编辑哪个班次
     * 施設別ビューのダブルクリック：どのシフトを編集するかダイアログで選択
     * By-facility double-click: dialog to select which assignment to edit
     */
    private void handleFacilityViewClick(int row, LocalDate date, java.awt.event.MouseEvent e) {
        List<Facility> facilities = facilityDao.findAll();
        if (row >= facilities.size()) return;
        Facility facility = facilities.get(row);

        List<Assignment> cellAssignments = new ArrayList<>();
        for (Assignment a : currentAssignments) {
            if (a.getDate().equals(date) && a.getFacility().getId() == facility.getId()) {
                cellAssignments.add(a);
            }
        }

        showFacilityAdjustDialog(facility, date, cellAssignments);
    }

    // ========== 场景 1：替换或删除现有班次 / シナリオ 1：既存シフトの置換・削除 / Scenario 1: replace or delete existing ==========

    private void showReplaceOrDeleteMenu(Employee currentEmp, LocalDate date, Assignment existing, java.awt.event.MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();

        // 标题（禁用项，纯信息展示）
        // タイトル（無効項目、情報表示のみ）
        // Title (disabled item, info only)
        JMenuItem title = new JMenuItem("現在：" + currentEmp.getName() + " → "
                + existing.getFacility().getName() + " " + existing.getShiftType().getName());
        title.setEnabled(false);
        menu.add(title);
        menu.addSeparator();

        // 替换子菜单 / 置換サブメニュー / Replace submenu
        JMenu replaceMenu = new JMenu("[置換] 他の従業員に変更");
        List<EmployeeCandidate> candidates = buildCandidates(date, existing.getFacility(), existing.getShiftType(), currentEmp);

        if (candidates.isEmpty()) {
            JMenuItem none = new JMenuItem("（置換可能な従業員なし）");
            none.setEnabled(false);
            replaceMenu.add(none);
        } else {
            for (EmployeeCandidate cand : candidates) {
                String label = cand.employee.getName() + "  (勤務" + String.format("%.0f", cand.totalHours)
                        + "h, 当月" + existing.getShiftType().getPeriod() + "番 " + cand.sameShiftCount + "回)"
                        + cand.warningSymbol;
                JMenuItem item = new JMenuItem(label);
                item.addActionListener(ae -> executeReplace(existing, cand.employee));
                replaceMenu.add(item);
            }
        }
        menu.add(replaceMenu);

        menu.addSeparator();

        // 删除班次 / シフト削除 / Delete shift
        JMenuItem delete = new JMenuItem("[削除] このシフトを削除（" + currentEmp.getName() + " は休みに）");
        delete.addActionListener(ae -> executeDelete(existing));
        menu.add(delete);

        menu.addSeparator();

        JMenuItem cancel = new JMenuItem("キャンセル");
        menu.add(cancel);

        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    // ========== 场景 2：空格子分配班次 / シナリオ 2：空セルにシフト割当 / Scenario 2: assign to empty cell ==========

    private void showAssignMenu(Employee emp, LocalDate date, java.awt.event.MouseEvent e) {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem title = new JMenuItem(emp.getName() + " " + date + "（現在休み）");
        title.setEnabled(false);
        menu.add(title);
        menu.addSeparator();

        JMenu assignMenu = new JMenu("[+] シフト割当");

        // 列出该员工能上的"设施 × 班次"组合
        // この従業員が入れる「施設 × シフト」組合せを列挙
        // List "facility × shift" combinations this employee can take
        List<Facility> facilities = facilityDao.findAll();
        List<ShiftType> shiftTypes = shiftTypeDao.findAll();
        boolean hasAny = false;

        for (Facility f : facilities) {
            if (!emp.getFacilityIds().contains(f.getId())) continue;
            for (ShiftType s : shiftTypes) {
                // 硬性约束过滤 / ハード制約フィルタ / Hard constraint filter
                String reason = checkHardConstraints(emp, date, f, s);
                if (reason != null) continue;

                hasAny = true;
                JMenuItem item = new JMenuItem(f.getName() + " " + s.getName() + " (" + s.getPeriod() + ")");
                item.addActionListener(ae -> executeAssign(emp, date, f, s));
                assignMenu.add(item);
            }
        }

        if (!hasAny) {
            JMenuItem none = new JMenuItem("（割当可能なシフトなし）");
            none.setEnabled(false);
            assignMenu.add(none);
        }

        menu.add(assignMenu);
        menu.addSeparator();

        JMenuItem cancel = new JMenuItem("キャンセル");
        menu.add(cancel);

        menu.show(e.getComponent(), e.getX(), e.getY());
    }

    // ========== 场景 3：设施视图下的编辑 / シナリオ 3：施設別ビューでの編集 / Scenario 3: edit under facility view ==========

    private void showFacilityAdjustDialog(Facility facility, LocalDate date, List<Assignment> cellAssignments) {
        if (cellAssignments.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    facility.getName() + " " + date + " は現在シフトなし。「従業員別」ビューに切り替えて割当してください");
            return;
        }

        String[] options = new String[cellAssignments.size() + 1];
        for (int i = 0; i < cellAssignments.size(); i++) {
            Assignment a = cellAssignments.get(i);
            options[i] = a.getEmployee().getName() + " → " + a.getShiftType().getName();
        }
        options[cellAssignments.size()] = "キャンセル";

        int choice = JOptionPane.showOptionDialog(this,
                facility.getName() + " " + date + "\n編集するシフトを選択：",
                "施設シフト編集",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]);

        if (choice < 0 || choice >= cellAssignments.size()) return;

        Assignment selected = cellAssignments.get(choice);
        showReplaceOrDeleteMenuAsDialog(selected);
    }

    private void showReplaceOrDeleteMenuAsDialog(Assignment existing) {
        List<EmployeeCandidate> candidates = buildCandidates(
                existing.getDate(), existing.getFacility(), existing.getShiftType(), existing.getEmployee());

        String[] options = new String[candidates.size() + 2];
        for (int i = 0; i < candidates.size(); i++) {
            EmployeeCandidate cand = candidates.get(i);
            options[i] = cand.employee.getName() + "  (勤務" + String.format("%.0f", cand.totalHours)
                    + "h)" + cand.warningSymbol;
        }
        options[candidates.size()] = "[削除] このシフトを削除";
        options[candidates.size() + 1] = "キャンセル";

        int choice = JOptionPane.showOptionDialog(this,
                "現在：" + existing.getEmployee().getName() + " → "
                        + existing.getFacility().getName() + " " + existing.getShiftType().getName()
                        + "\n\n置換する従業員を選択、または削除：",
                "シフト編集",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.PLAIN_MESSAGE,
                null, options, options[0]);

        if (choice < 0) return;
        if (choice < candidates.size()) {
            executeReplace(existing, candidates.get(choice).employee);
        } else if (choice == candidates.size()) {
            executeDelete(existing);
        }
    }

    // ========== 候选员工数据结构 / 候補従業員データ構造 / Candidate employee data structure ==========

    /**
     * 替换候选员工 + 关联统计信息
     * 置換候補従業員 + 関連統計情報
     * Replacement candidate + related stats
     */
    private static class EmployeeCandidate {
        Employee employee;
        double totalHours;         // 本月已排工时 / 当月既配分勤務時間 / Scheduled hours this month
        int sameShiftCount;        // 本月同类班次次数 / 当月同種シフト回数 / Same-shift count this month
        int targetHours;           // 目标工时（正社员才有）/ 目標時間（正社員のみ）/ Target hours (regular only)
        String warningSymbol;      // 警告符号串 / 警告記号 / Warning symbols
    }

    /**
     * 构建替换候选员工列表（按工时满足度排序，不足的在前）
     * 置換候補従業員リストを構築（勤務時間充足度順、不足を優先）
     * Build candidate list (sorted by hour fulfillment; under-target first)
     */
    private List<EmployeeCandidate> buildCandidates(LocalDate date, Facility facility, ShiftType shiftType, Employee excludeEmp) {
        List<EmployeeCandidate> list = new ArrayList<>();
        int daysInMonth = YearMonth.of(date.getYear(), date.getMonthValue()).lengthOfMonth();
        // 日本劳基法：40h/周 × 当月天数 / 7
        // 労働基準法：40h/週 × 当月日数 / 7
        // Japan labor law: 40h/week × days in month / 7
        int targetHours = (int) Math.round(40.0 * daysInMonth / 7.0);

        for (Employee emp : getSortedActiveEmployees()) {
            if (emp.getId() == excludeEmp.getId()) continue;

            String hardFail = checkHardConstraints(emp, date, facility, shiftType);
            if (hardFail != null) continue;

            EmployeeCandidate cand = new EmployeeCandidate();
            cand.employee = emp;
            cand.totalHours = calculateMonthlyHours(emp.getId(), date);
            cand.sameShiftCount = countSameShift(emp.getId(), date, shiftType.getId());
            cand.targetHours = "正社員".equals(emp.getEmployeeType()) ? targetHours : -1;

            StringBuilder warn = new StringBuilder();
            // 希望休警告 / 希望休警告 / Wish-off warning
            Set<String> wishDates = wishOffMap.get(emp.getId());
            if (wishDates != null && wishDates.contains(date.toString())) {
                warn.append(" [!]希望休");
            }
            // 工时不足警告（仅正社员）/ 時間不足警告（正社員のみ）/ Under-target warning (regular only)
            if ("正社員".equals(emp.getEmployeeType()) && cand.totalHours < cand.targetHours - 8) {
                warn.append(" [!]時間不足");
            }
            cand.warningSymbol = warn.toString();

            list.add(cand);
        }

        // 排序：工时不足优先，然后按工时少的优先
        // ソート：時間不足優先、次に時間少ない順
        // Sort: under-target first, then by less hours
        list.sort((a, b) -> {
            boolean aShort = "正社員".equals(a.employee.getEmployeeType()) && a.totalHours < a.targetHours;
            boolean bShort = "正社員".equals(b.employee.getEmployeeType()) && b.totalHours < b.targetHours;
            if (aShort != bShort) return aShort ? -1 : 1;
            return Double.compare(a.totalHours, b.totalHours);
        });

        return list;
    }

    /**
     * 检查硬性约束，返回失败原因（null = 通过）
     * ハード制約をチェック、失敗理由を返却（null = 通過）
     * Check hard constraints; return failure reason (null = passed)
     *
     * 和 SchedulingEngine.canAssign 的约束集合保持一致，确保手动调整和算法逻辑统一。
     * SchedulingEngine.canAssign と同じ制約セット、手動調整とアルゴリズムの整合性を保証。
     * Matches SchedulingEngine.canAssign to keep manual edits consistent with algorithm.
     */
    private String checkHardConstraints(Employee emp, LocalDate date, Facility facility, ShiftType shiftType) {
        // 1. 必须能去这个设施 / 当該施設で勤務可能 / Must be able to work at this facility
        if (!emp.getFacilityIds().contains(facility.getId())) return "この施設で勤務不可";

        // 2. 当天不能已经有班次 / 同日に他シフトなし / No existing shift today
        for (Assignment a : currentAssignments) {
            if (a.getDate().equals(date) && a.getEmployee().getId() == emp.getId()) {
                return "同日に既にシフトあり";
            }
        }

        // 3. 夜勤规则 / 夜勤ルール / Night rules
        String period = shiftType.getPeriod();
        if ("夜".equals(period) && !"夜勤専属".equals(emp.getEmployeeType())) return "夜番は夜勤専属のみ";
        if (!"夜".equals(period) && "夜勤専属".equals(emp.getEmployeeType())) return "夜勤専属は夜番のみ";

        // 4. 夜班次日不接早班 / 夜勤翌日は早番不可 / No morning shift after night
        if ("朝".equals(period)) {
            LocalDate yesterday = date.minusDays(1);
            for (Assignment a : currentAssignments) {
                if (a.getDate().equals(yesterday) && a.getEmployee().getId() == emp.getId()
                        && "夜".equals(a.getShiftType().getPeriod())) {
                    return "前日夜勤のため当日の早番不可";
                }
            }
        }

        return null;
    }

    /** 计算某员工本月总工时（小时）/ 当月の従業員の総勤務時間（時間）/ Monthly total hours for an employee */
    private double calculateMonthlyHours(int employeeId, LocalDate inDate) {
        double total = 0;
        for (Assignment a : currentAssignments) {
            if (a.getEmployee().getId() == employeeId
                    && a.getDate().getYear() == inDate.getYear()
                    && a.getDate().getMonthValue() == inDate.getMonthValue()) {
                total += shiftDurationMinutes(a.getShiftType()) / 60.0;
            }
        }
        return total;
    }

    /** 计算某员工本月某班次次数 / 当月の従業員の特定シフト回数 / Monthly count of a specific shift */
    private int countSameShift(int employeeId, LocalDate inDate, int shiftTypeId) {
        int count = 0;
        for (Assignment a : currentAssignments) {
            if (a.getEmployee().getId() == employeeId
                    && a.getShiftType().getId() == shiftTypeId
                    && a.getDate().getYear() == inDate.getYear()
                    && a.getDate().getMonthValue() == inDate.getMonthValue()) {
                count++;
            }
        }
        return count;
    }

    /** 班次时长（分钟），跨日 +24h / シフト時間（分）、日跨ぎは +24h / Shift minutes, cross-day +24h */
    private int shiftDurationMinutes(ShiftType s) {
        String[] start = s.getStartTime().split(":");
        String[] end = s.getEndTime().split(":");
        int startMin = Integer.parseInt(start[0]) * 60 + Integer.parseInt(start[1]);
        int endMin = Integer.parseInt(end[0]) * 60 + Integer.parseInt(end[1]);
        int diff = endMin - startMin;
        if (diff <= 0) diff += 24 * 60;
        return diff;
    }

    // ========== 执行操作 / 実行操作 / Execute operations ==========

    /**
     * 执行替换：内存删旧 + 内存加新 + 保存 DB + 刷新
     * 置換実行：メモリから旧削除 + 新追加 + DB 保存 + 再描画
     * Execute replace: remove old + add new in memory + save DB + refresh
     */
    private void executeReplace(Assignment old, Employee newEmp) {
        currentAssignments.remove(old);
        Assignment newAssign = new Assignment(old.getDate(), newEmp, old.getFacility(), old.getShiftType());
        currentAssignments.add(newAssign);

        if (scheduleDao.saveMonth(getSelectedYearMonth(), currentAssignments)) {
            renderCurrent();
        } else {
            JOptionPane.showMessageDialog(this, "保存に失敗しました", "エラー", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 执行删除
     * 削除実行
     * Execute delete
     */
    private void executeDelete(Assignment target) {
        currentAssignments.remove(target);
        if (scheduleDao.saveMonth(getSelectedYearMonth(), currentAssignments)) {
            renderCurrent();
        } else {
            JOptionPane.showMessageDialog(this, "保存に失敗しました", "エラー", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 执行新增分配
     * 新規割当実行
     * Execute new assignment
     */
    private void executeAssign(Employee emp, LocalDate date, Facility facility, ShiftType shiftType) {
        Assignment newAssign = new Assignment(date, emp, facility, shiftType);
        currentAssignments.add(newAssign);
        if (scheduleDao.saveMonth(getSelectedYearMonth(), currentAssignments)) {
            renderCurrent();
        } else {
            JOptionPane.showMessageDialog(this, "保存に失敗しました", "エラー", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * 导出 Excel（弹文件选择器 + 调用 ExcelExporter + 可选打开）
     * Excel 出力（ファイル選択ダイアログ + ExcelExporter 呼出 + オプション開く）
     * Export Excel (file chooser + call ExcelExporter + optional open)
     */
    private void exportToExcel() {
        if (currentAssignments.isEmpty()) {
            JOptionPane.showMessageDialog(this, "当月のシフトデータがありません。先に生成または読み込みしてください");
            return;
        }

        String defaultName = "シフト表_" + getSelectedYearMonth() + ".xlsx";
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("シフト表を保存");
        chooser.setSelectedFile(new java.io.File(System.getProperty("user.home") + "/Desktop/" + defaultName));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Excel ファイル (*.xlsx)", "xlsx"));

        int ret = chooser.showSaveDialog(this);
        if (ret != JFileChooser.APPROVE_OPTION) return;

        java.io.File file = chooser.getSelectedFile();
        // 自动补 .xlsx 扩展名 / 拡張子 .xlsx を自動補完 / Auto-append .xlsx extension
        if (!file.getName().endsWith(".xlsx")) {
            file = new java.io.File(file.getAbsolutePath() + ".xlsx");
        }

        try {
            com.hotel.export.ExcelExporter exporter = new com.hotel.export.ExcelExporter();
            exporter.export(getSelectedYearMonth(), currentAssignments, file);

            int open = JOptionPane.showConfirmDialog(this,
                    "出力しました：\n" + file.getAbsolutePath() + "\n\n今すぐ開きますか？",
                    "出力成功", JOptionPane.YES_NO_OPTION);
            if (open == JOptionPane.YES_OPTION) {
                try {
                    java.awt.Desktop.getDesktop().open(file);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(this, "ファイルを開けません：" + ex.getMessage());
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "出力に失敗しました：" + ex.getMessage(),
                    "エラー", JOptionPane.ERROR_MESSAGE);
        }
    }
}