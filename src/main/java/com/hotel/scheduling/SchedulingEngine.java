package com.hotel.scheduling;

import com.hotel.model.Employee;
import com.hotel.model.Facility;
import com.hotel.model.ShiftType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * 排班算法核心：贪心分配
 * シフトアルゴリズムのコア：貪欲法による割当
 * Scheduling algorithm core: greedy assignment
 *
 * <p>算法策略：</p>
 * <ol>
 *   <li>生成任务列表（每日 × 每设施 × 每班次 × 需求人数）</li>
 *   <li>遍历每个任务，从候选员工中选评分最高者填入</li>
 *   <li>硬性约束（canAssign）过滤不可行的员工</li>
 *   <li>软性约束（score）通过评分函数引导最优解</li>
 * </ol>
 *
 * <p>アルゴリズム戦略：</p>
 * <ol>
 *   <li>タスクリストを生成（日付 × 施設 × シフト × 必要人数）</li>
 *   <li>各タスクを走査、候補従業員からスコア最高を配置</li>
 *   <li>ハード制約（canAssign）で不可能な従業員を除外</li>
 *   <li>ソフト制約（score）でスコア関数により最適解へ誘導</li>
 * </ol>
 *
 * <p>Algorithm strategy:</p>
 * <ol>
 *   <li>Generate task list (per day × facility × shift × required count)</li>
 *   <li>For each task, pick the highest-scored candidate employee</li>
 *   <li>Hard constraints (canAssign) filter out infeasible employees</li>
 *   <li>Soft constraints (score) guide toward optimum via scoring function</li>
 * </ol>
 *
 * <p>取舍：贪心法不保证全局最优，但复杂度低（O(任务数 × 员工数)），
 * 对于单月数百任务 × 几十员工的规模，几秒内可完成；支持后续手动调整补足最优性。</p>
 *
 * <p>トレードオフ：貪欲法は大域最適を保証しないが、計算量が低く（O(タスク数 × 従業員数)）、
 * 月数百タスク × 数十従業員の規模では数秒で完了。後の手動調整で最適性を補う。</p>
 *
 * <p>Trade-off: greedy doesn't guarantee global optimum, but has low complexity
 * (O(tasks × employees)); completes in seconds for hundreds of tasks × dozens of employees;
 * optimality is supplemented by manual adjustment afterwards.</p>
 */
public class SchedulingEngine {

    private final SchedulingContext ctx;

    // ===== 运行时状态（算法执行中累加的数据）=====
    // ===== 実行時状態（アルゴリズム実行中に累積するデータ）=====
    // ===== Runtime state (data accumulated during algorithm execution) =====

    /** 员工 ID → 已分配的任务列表 / 従業員 ID → 割当済みタスクリスト / Employee ID → list of assignments */
    private Map<Integer, List<Assignment>> assignmentsByEmployee;

    /** 员工 ID → 累计疲劳分 / 従業員 ID → 累積疲労スコア / Employee ID → accumulated fatigue score */
    private Map<Integer, Double> fatigueByEmployee;

    /** 员工 ID → 已累计工时（分钟）/ 従業員 ID → 累計勤務時間（分）/ Employee ID → total minutes worked */
    private Map<Integer, Integer> minutesByEmployee;

    /** 员工 ID → (周起始日 → 当周工时分钟数)，用于判断周工时上限 */
    /** 従業員 ID → (週開始日 → 当週勤務分)、週間上限判定用 */
    /** Employee ID → (week start → minutes in that week), for weekly limit check */
    private Map<Integer, Map<LocalDate, Integer>> weeklyMinutesByEmployee;

    /** 员工 ID → (班次 ID → 该班次次数)，用于班次均衡 */
    /** 従業員 ID → (シフト ID → シフト回数)、シフトバランス調整用 */
    /** Employee ID → (shift ID → count of that shift), for shift balancing */
    private Map<Integer, Map<Integer, Integer>> shiftCountByEmployee;

    /**
     * 构造方法：注入已加载好数据的上下文
     * コンストラクタ：データロード済みのコンテキストを注入
     * Constructor: inject a pre-loaded context
     */
    public SchedulingEngine(SchedulingContext ctx) {
        this.ctx = ctx;
    }

    /**
     * 执行排班算法（算法入口）
     * シフトアルゴリズムを実行（アルゴリズムのエントリーポイント）
     * Execute the scheduling algorithm (algorithm entry point)
     *
     * @return 排班结果（包含成功的分配 + 硬/软性问题列表）
     *         シフト結果（成功した割当 + ハード/ソフト問題リスト）
     *         Scheduling result (successful assignments + hard/soft problem list)
     */
    public SchedulingResult run() {
        // 初始化运行时状态
        // 実行時状態を初期化
        // Initialize runtime state
        assignmentsByEmployee = new HashMap<>();
        fatigueByEmployee = new HashMap<>();
        minutesByEmployee = new HashMap<>();
        weeklyMinutesByEmployee = new HashMap<>();
        shiftCountByEmployee = new HashMap<>();
        for (Employee e : ctx.getEmployees()) {
            assignmentsByEmployee.put(e.getId(), new ArrayList<>());
            fatigueByEmployee.put(e.getId(), 0.0);
            minutesByEmployee.put(e.getId(), 0);
            weeklyMinutesByEmployee.put(e.getId(), new HashMap<>());
            shiftCountByEmployee.put(e.getId(), new HashMap<>());
        }

        SchedulingResult result = new SchedulingResult();
        List<Task> tasks = generateTasks();

        // 主循环：逐任务分配员工
        // メインループ：タスクごとに従業員を割当
        // Main loop: assign employees to each task
        for (Task task : tasks) {
            while (!task.isFull()) {
                Employee picked = pickBestEmployee(task);
                if (picked == null) {
                    // 找不到候选员工 → 记录人手不足问题
                    // 候補従業員が見つからない → 人手不足問題として記録
                    // No candidate found → record staff shortage problem
                    result.addShortageProblem(task.getDate() + " "
                            + task.getFacility().getName() + " "
                            + task.getShiftType().getName()
                            + " 需要 " + task.getRequiredCount() + " 人，仅排到 " + task.getAssignedCount() + " 人");
                    break;
                }
                Assignment a = new Assignment(task.getDate(), picked, task.getFacility(), task.getShiftType());
                result.addAssignment(a);
                task.incrementAssigned();

                // 立即更新状态，影响下一轮的评分
                // 即座に状態更新、次ラウンドのスコアに反映
                // Update state immediately so next round's scores reflect this
                updateEmployeeStateAfterAssign(picked, a);
            }
        }

        collectSoftProblems(result);
        return result;
    }

    /**
     * 生成任务列表：遍历每日 × 每设施 × 每班次，根据需求配置生成 Task
     * タスクリスト生成：日付 × 施設 × シフトを走査、需要設定に基づいて Task 生成
     * Generate task list: iterate date × facility × shift and create tasks per demand
     */
    private List<Task> generateTasks() {
        List<Task> tasks = new ArrayList<>();
        for (LocalDate date : ctx.getAllDates()) {
            for (Facility f : ctx.getFacilities()) {
                for (ShiftType s : ctx.getShiftTypes()) {
                    int needed = ctx.getRequiredCount(f.getId(), s.getId(), date);
                    if (needed > 0) {
                        tasks.add(new Task(date, f, s, needed));
                    }
                }
            }
        }
        return tasks;
    }

    /**
     * 为任务挑选最合适的员工：硬性约束过滤后，按评分排序取第一
     * タスクに最適な従業員を選択：ハード制約で絞り込み後、スコアで並べ替えて先頭を取得
     * Pick the best employee: filter by hard constraints, sort by score, take the top one
     */
    private Employee pickBestEmployee(Task task) {
        List<Employee> candidates = new ArrayList<>();
        for (Employee e : ctx.getEmployees()) {
            if (canAssign(e, task)) {
                candidates.add(e);
            }
        }
        if (candidates.isEmpty()) return null;

        // 评分降序排序（分数高的在前）
        // スコア降順ソート（高スコアが先頭）
        // Sort by score descending (highest first)
        candidates.sort((a, b) -> Double.compare(score(b, task), score(a, task)));
        return candidates.get(0);
    }

    /**
     * 硬性约束检查：员工能否被分配到此任务
     * ハード制約チェック：従業員を当該タスクに割当可能か
     * Hard constraint check: whether an employee can be assigned to this task
     *
     * 任意一条不满足即返回 false，全部通过才返回 true。
     * 一つでも不合格なら false、全てパスで true を返す。
     * Returns false if any check fails; true only when all pass.
     */
    private boolean canAssign(Employee e, Task task) {
        // 硬性 1：员工必须能去该设施
        // ハード 1：従業員が当該施設で勤務可能であること
        // Hard 1: employee must be allowed to work at this facility
        if (!e.getFacilityIds().contains(task.getFacility().getId())) return false;

        // 硬性 2：当天不能已经有其他班次（1 人 1 日 1 班）
        // ハード 2：同日に他のシフトを持たない（1 人 1 日 1 シフト）
        // Hard 2: no existing shift on the same day (one person, one shift per day)
        List<Assignment> myAssignments = assignmentsByEmployee.get(e.getId());
        for (Assignment a : myAssignments) {
            if (a.getDate().equals(task.getDate())) return false;
        }

        // 硬性 3：月工时不超上限（有休小时数也计入配额）
        // ハード 3：月間勤務時間が上限超過しない（有給時間も配分に計上）
        // Hard 3: monthly hours must not exceed limit (paid leave hours count toward the quota)
        if (e.getMonthlyHourLimit() != null) {
            int myMinutes = minutesByEmployee.get(e.getId());
            int shiftMinutes = shiftDurationMinutes(task.getShiftType());
            int paidLeaveMinutes = ctx.getPaidLeaveHoursInMonth(e.getId()) * 60;
            if (myMinutes + paidLeaveMinutes + shiftMinutes > e.getMonthlyHourLimit() * 60) return false;
        }

        // 硬性 4：周工时不超上限（如外国兼职 28h/周签证限制）
        // ハード 4：週間勤務時間が上限超過しない（例：外国人アルバイトのビザ制限 週 28h）
        // Hard 4: weekly hours must not exceed limit (e.g. foreign part-timer visa cap 28h/week)
        if (e.getWeeklyHourLimit() != null) {
            LocalDate weekStart = getWeekStart(task.getDate());
            int weekMinutes = weeklyMinutesByEmployee.get(e.getId()).getOrDefault(weekStart, 0);
            int shiftMinutes = shiftDurationMinutes(task.getShiftType());
            if (weekMinutes + shiftMinutes > e.getWeeklyHourLimit() * 60) return false;
        }

        // 硬性 5：不超过最大连续上班天数（日本劳基法要求定期休息）
        // ハード 5：最大連続勤務日数を超過しない（労基法により定期休息を要求）
        // Hard 5: must not exceed max consecutive work days (labor law requires regular rest)
        int continuous = countContinuousDaysBefore(e, task.getDate());
        if (continuous >= ctx.getMaxContinuousWorkDays()) return false;

        // 硬性 6：夜班次日不能接早班（防止休息时间不足）
        // ハード 6：夜勤翌日に早番を割当禁止（休息時間不足を防止）
        // Hard 6: no morning shift the day after a night shift (prevent insufficient rest)
        if ("朝".equals(task.getShiftType().getPeriod())) {
            LocalDate yesterday = task.getDate().minusDays(1);
            for (Assignment a : myAssignments) {
                if (a.getDate().equals(yesterday) && "夜".equals(a.getShiftType().getPeriod())) {
                    return false;
                }
            }
        }

        // 硬性 7：专职夜勤只能上夜班
        // ハード 7：夜勤専属は夜番のみ勤務可能
        // Hard 7: night-only staff can only take night shifts
        if ("夜勤専属".equals(e.getEmployeeType()) && !"夜".equals(task.getShiftType().getPeriod())) {
            return false;
        }

        // 硬性 8：夜班只有专职夜勤能上
        // ハード 8：夜番は夜勤専属のみ担当可能
        // Hard 8: night shifts can only be filled by night-only staff
        if ("夜".equals(task.getShiftType().getPeriod()) && !"夜勤専属".equals(e.getEmployeeType())) {
            return false;
        }

        // 硬性 9：有休日不排班（员工请了带薪假，这天硬性屏蔽）
        // ハード 9：有給取得日はシフトを割当禁止（有給申請日は強制除外）
        // Hard 9: no shifts on paid leave days (paid leave hard-blocks scheduling)
        if (ctx.isPaidLeave(e.getId(), task.getDate())) {
            return false;
        }

        return true;
    }

    /**
     * 评分函数：计算员工对该任务的"合适度"分数（越高越优先）
     * スコア関数：従業員の当該タスクへの「適性度」を算出（高いほど優先）
     * Score function: computes the "fitness" score of an employee for the task (higher = preferred)
     *
     * 多因素加权组合，通过调整权重可以引导算法行为。
     * 多要素の重み付け組合せ、重み調整でアルゴリズム動作を誘導可能。
     * Weighted combination of multiple factors; adjusting weights steers algorithm behavior.
     */
    private double score(Employee e, Task task) {
        double s = 0;

        // 因素 1：疲劳低的优先（已累积疲劳扣分）
        // 要素 1：疲労低い方を優先（累積疲労を減点）
        // Factor 1: prefer less-fatigued employees (subtract accumulated fatigue)
        s -= fatigueByEmployee.get(e.getId()) * ctx.getPriorityFatigueBalance();

        // 因素 2：这个班次上过的次数少的优先（班次均衡）
        // 要素 2：当該シフト回数が少ない方を優先（シフトバランス）
        // Factor 2: prefer employees with fewer of this shift (shift balance)
        int shiftCount = shiftCountByEmployee.get(e.getId()).getOrDefault(task.getShiftType().getId(), 0);
        s -= shiftCount * ctx.getPriorityShiftBalance();

        // 因素 3：希望休扣大分（但不是硬性禁止，仍可被排 = 希望休未满足）
        // 要素 3：希望休は大幅減点（ハード禁止ではなく、割当可能 = 希望休未達）
        // Factor 3: heavy penalty for preferred day-off (soft; still schedulable = unmet preference)
        if (ctx.isWishOff(e.getId(), task.getDate())) {
            s -= 100 * ctx.getPriorityWishOff();
        }

        // 因素 4：普通员工尽量不排夜班（夜勤专属没有此限制）
        // 要素 4：一般従業員は夜勤を極力避ける（夜勤専属は対象外）
        // Factor 4: regular staff avoid night shifts (night-only staff exempt)
        boolean isNightShift = "夜".equals(task.getShiftType().getPeriod());
        boolean isNightSpecialist = "夜勤専属".equals(e.getEmployeeType());
        if (!isNightSpecialist && isNightShift) s -= 5;

        // 因素 5：连续上班天数软性惩罚（避免某人连续上太多天）
        // 要素 5：連続勤務日数のソフト減点（連続勤務の偏り抑制）
        // Factor 5: soft penalty for consecutive work days (prevents over-concentration)
        int continuous = countContinuousDaysBefore(e, task.getDate());
        if (continuous >= 4) s -= 15;
        else if (continuous >= 3) s -= 8;
        else if (continuous >= 2) s -= 3;

        // 因素 6：按员工类型调整基础优先级（正社员优先，兼职压制）
        // 要素 6：従業員種別で基本優先度調整（正社員優先、アルバイト抑制）
        // Factor 6: adjust base priority by employee type (prioritize regular, suppress part-time)
        String empType = e.getEmployeeType();
        int myWorkDays = assignmentsByEmployee.get(e.getId()).size();

        if ("正社員".equals(empType)) {
            // 正社员：强力加分，鼓励多排
            // 正社員：強く加点、シフト多めを奨励
            // Regular staff: strong bonus to encourage more shifts
            s += 8;

            // 工时未达目标时，继续加分
            // 目標時間未達の場合、さらに加点
            // Additional bonus when under target hours
            if (e.getMonthlyHourLimit() != null) {
                int currentMinutes = minutesByEmployee.get(e.getId());
                int paidLeaveMinutes = ctx.getPaidLeaveHoursInMonth(e.getId()) * 60;
                int totalMinutes = currentMinutes + paidLeaveMinutes;
                int targetMinutes = e.getMonthlyHourLimit() * 60;
                int shortageMinutes = targetMinutes - totalMinutes;
                if (shortageMinutes > 0) {
                    // 缺口越大加越多，上限 6 分
                    // 不足分が大きいほど加点、上限 6 点
                    // Bigger shortage = bigger bonus, capped at 6
                    double bonus = Math.min(6.0, shortageMinutes / 1200.0);
                    s += bonus;
                }
            }

            // 已排 20 天以上降低优先度，给其他正社员机会
            // 20 日以上出勤済みは優先度ダウン、他の正社員にチャンスを
            // Lower priority after 20 shifts to give chance to other regular staff
            if (myWorkDays >= 20) s -= 3;
        }
        else if ("アルバイト".equals(empType)) {
            // 兼职：压制，只在正社员上不了时才排
            // アルバイト：抑制、正社員が不可能な時のみ割当
            // Part-time: suppress, only assigned when regular staff unavailable
            s -= 5;

            // 兼职工时越多，越不优先（防止兼职过度劳累）
            // アルバイト勤務時間が多いほど優先度低下（過労防止）
            // The more hours, the less priority (prevents part-time overwork)
            int myMinutes = minutesByEmployee.get(e.getId());
            double myHours = myMinutes / 60.0;
            if (myHours >= 120) s -= 10;
            else if (myHours >= 100) s -= 6;
            else if (myHours >= 80) s -= 3;
        }
        else if ("契約社員".equals(empType)) {
            // 契约社员：中间定位，微微加分
            // 契約社員：中間ポジション、僅かに加点
            // Contract staff: middle position, slight bonus
            s += 2;
        }
        // 夜勤専属：中立（硬性约束已经让他们只上夜班）
        // 夜勤専属：中立（ハード制約で夜番のみに制限済み）
        // Night-only: neutral (hard constraint already restricts them to night shifts)

        return s;
    }

    /**
     * 分配后更新员工状态（关键：累加所有统计，影响后续决策）
     * 割当後に従業員状態を更新（重要：全統計を累積、後続判断に影響）
     * Update employee state after assignment (critical: accumulates all stats affecting later decisions)
     */
    private void updateEmployeeStateAfterAssign(Employee e, Assignment a) {
        assignmentsByEmployee.get(e.getId()).add(a);

        int minutes = shiftDurationMinutes(a.getShiftType());
        minutesByEmployee.merge(e.getId(), minutes, Integer::sum);

        LocalDate weekStart = getWeekStart(a.getDate());
        weeklyMinutesByEmployee.get(e.getId())
                .merge(weekStart, minutes, Integer::sum);

        shiftCountByEmployee.get(e.getId())
                .merge(a.getShiftType().getId(), 1, Integer::sum);

        // 累加疲劳：基础权重 × 连续天数倍率
        // 疲労累積：基本ウェイト × 連続勤務倍率
        // Accumulate fatigue: base weight × consecutive-day multiplier
        double weight = ctx.getFatigueWeight(a.getFacility().getId(), a.getShiftType().getId());
        int continuous = countContinuousDaysBefore(e, a.getDate()) + 1;
        double multiplier = 1.0;
        if (continuous >= 5) multiplier = ctx.getFatigueContinuous5DayMultiplier();
        else if (continuous >= 3) multiplier = ctx.getFatigueContinuous3DayMultiplier();

        double fatigueAdd = weight * multiplier;

        // 夜班次日再上班的额外疲劳加分
        // 夜勤翌日の勤務に追加疲労加算
        // Extra fatigue when working the day after a night shift
        LocalDate yesterday = a.getDate().minusDays(1);
        for (Assignment prev : assignmentsByEmployee.get(e.getId())) {
            if (prev.getDate().equals(yesterday) && "夜".equals(prev.getShiftType().getPeriod())) {
                fatigueAdd += ctx.getFatigueNightNextDayBonus();
                break;
            }
        }

        fatigueByEmployee.merge(e.getId(), fatigueAdd, Double::sum);
    }

    /**
     * 计算某员工在某天之前连续上了几天班
     * 指定従業員が指定日前にどれだけ連続勤務したかを計算
     * Counts consecutive work days before a given date for an employee
     */
    private int countContinuousDaysBefore(Employee e, LocalDate date) {
        List<Assignment> myAssignments = assignmentsByEmployee.get(e.getId());
        Set<LocalDate> workDates = new HashSet<>();
        for (Assignment a : myAssignments) {
            workDates.add(a.getDate());
        }

        int count = 0;
        LocalDate check = date.minusDays(1);
        while (workDates.contains(check)) {
            count++;
            check = check.minusDays(1);
        }
        return count;
    }

    /**
     * 计算某日期所在周的周一（周工时统计用）
     * 指定日が属する週の月曜日を計算（週勤務時間集計用）
     * Compute the Monday of the week containing the given date (for weekly stats)
     */
    private LocalDate getWeekStart(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return date.minusDays(dow.getValue() - 1);  // 周一=1，往回退 0 天 / 月曜=1 → 0 日戻し / Monday=1, subtract 0
    }

    /**
     * 班次时长（分钟），跨日班次自动 +24h
     * シフト時間（分）、日跨ぎシフトは自動的に +24h
     * Shift duration in minutes; cross-day shifts automatically add 24h
     */
    private int shiftDurationMinutes(ShiftType s) {
        String[] start = s.getStartTime().split(":");
        String[] end = s.getEndTime().split(":");
        int startMin = Integer.parseInt(start[0]) * 60 + Integer.parseInt(start[1]);
        int endMin = Integer.parseInt(end[0]) * 60 + Integer.parseInt(end[1]);
        int diff = endMin - startMin;
        if (diff <= 0) diff += 24 * 60;  // 跨日班次 / 日跨ぎシフト / Cross-day shift
        return diff;
    }

    /**
     * 收集软性问题（如希望休未满足），用于结果报告
     * ソフト問題を収集（例：希望休未達）、結果レポート用
     * Collect soft problems (e.g. unmet preferred day-off) for the result report
     */
    private void collectSoftProblems(SchedulingResult result) {
        for (Employee e : ctx.getEmployees()) {
            List<Assignment> myAssignments = assignmentsByEmployee.get(e.getId());
            for (Assignment a : myAssignments) {
                if (ctx.isWishOff(e.getId(), a.getDate())) {
                    result.addSoftProblem(e.getName() + " 希望休 " + a.getDate() + " 未満足");
                }
            }
        }
    }
}