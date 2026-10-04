package com.hotel.scheduling;

import com.hotel.model.Employee;
import com.hotel.model.Facility;
import com.hotel.model.ShiftType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

/**
 * 排班算法核心：贪心分配
 */
public class SchedulingEngine {

    private final SchedulingContext ctx;

    // ===== 运行时状态 =====

    /** 每个员工被分配的所有 Assignment（用于计算疲劳、连续天数等） */
    private Map<Integer, List<Assignment>> assignmentsByEmployee;

    /** 每个员工累计疲劳分 */
    private Map<Integer, Double> fatigueByEmployee;

    /** 每个员工本月分配总时长（分钟数） */
    private Map<Integer, Integer> minutesByEmployee;

    /** 员工每周工时（分钟）：employeeId → (周起始日期 → 分钟数) */
    private Map<Integer, Map<LocalDate, Integer>> weeklyMinutesByEmployee;

    /** 每个员工每个班次类型的次数：employeeId → (shiftTypeId → 次数) */
    private Map<Integer, Map<Integer, Integer>> shiftCountByEmployee;

    public SchedulingEngine(SchedulingContext ctx) {
        this.ctx = ctx;
    }

    /**
     * 执行排班算法
     */
    public SchedulingResult run() {
        // 初始化状态
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

        // ========== 1. 生成任务列表 ==========
        List<Task> tasks = generateTasks();

        // ========== 2. 遍历任务，逐个分配员工 ==========
        for (Task task : tasks) {
            while (!task.isFull()) {
                Employee picked = pickBestEmployee(task);
                if (picked == null) {
                    // 找不到可用员工，记录缺人问题
                    result.addShortageProblem(task.getDate() + " "
                            + task.getFacility().getName() + " "
                            + task.getShiftType().getName()
                            + " 需要 " + task.getRequiredCount() + " 人，仅排到 " + task.getAssignedCount() + " 人");
                    break;
                }
                // 分配
                Assignment a = new Assignment(task.getDate(), picked, task.getFacility(), task.getShiftType());
                result.addAssignment(a);
                task.incrementAssigned();

                // 更新员工状态
                updateEmployeeStateAfterAssign(picked, a);
            }
        }

        // ========== 3. 汇总软性问题 ==========
        collectSoftProblems(result);

        return result;
    }

    /** ① 生成所有任务 */
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

    /** ② 挑选最合适的员工，找不到返回 null */
    private Employee pickBestEmployee(Task task) {
        List<Employee> candidates = new ArrayList<>();
        for (Employee e : ctx.getEmployees()) {
            if (canAssign(e, task)) {
                candidates.add(e);
            }
        }
        if (candidates.isEmpty()) return null;

        // 按评分排序，得分高的优先
        candidates.sort((a, b) -> Double.compare(score(b, task), score(a, task)));
        return candidates.get(0);
    }

    /**
     * 硬性约束检查：某员工能不能被分配到这个任务
     */
    private boolean canAssign(Employee e, Task task) {
        // 硬性1：员工必须能去这个设施
        if (!e.getFacilityIds().contains(task.getFacility().getId())) return false;

        // 硬性2：该员工当天不能已经有班次
        List<Assignment> myAssignments = assignmentsByEmployee.get(e.getId());
        for (Assignment a : myAssignments) {
            if (a.getDate().equals(task.getDate())) return false;
        }

        // 硬性3：月工时不超上限
        if (e.getMonthlyHourLimit() != null) {
            int myMinutes = minutesByEmployee.get(e.getId());
            int shiftMinutes = shiftDurationMinutes(task.getShiftType());
            if (myMinutes + shiftMinutes > e.getMonthlyHourLimit() * 60) return false;
        }

        // 硬性4：周工时不超上限
        if (e.getWeeklyHourLimit() != null) {
            LocalDate weekStart = getWeekStart(task.getDate());
            int weekMinutes = weeklyMinutesByEmployee.get(e.getId()).getOrDefault(weekStart, 0);
            int shiftMinutes = shiftDurationMinutes(task.getShiftType());
            if (weekMinutes + shiftMinutes > e.getWeeklyHourLimit() * 60) return false;
        }

        // 硬性5：不能超过最大连续上班天数
        int continuous = countContinuousDaysBefore(e, task.getDate());
        if (continuous >= ctx.getMaxContinuousWorkDays()) return false;

        // 硬性6：夜班次日不接早班（如果这个任务是"朝"班且昨天上过夜班）
        if ("朝".equals(task.getShiftType().getPeriod())) {
            LocalDate yesterday = task.getDate().minusDays(1);
            for (Assignment a : myAssignments) {
                if (a.getDate().equals(yesterday) && "夜".equals(a.getShiftType().getPeriod())) {
                    return false;
                }
            }
        }

        // 硬性7：专职夜勤员工只能上夜班
        if ("夜勤専属".equals(e.getEmployeeType()) && !"夜".equals(task.getShiftType().getPeriod())) {
            return false;
        }

        // 硬性8：夜班只有专职夜勤能上（正社员/契约社员/兼职都不参与）
        if ("夜".equals(task.getShiftType().getPeriod()) && !"夜勤専属".equals(e.getEmployeeType())) {
            return false;
        }

        return true;
    }

    /**
     * 评分：分数越高越应该被优先分配
     */
    private double score(Employee e, Task task) {
        double s = 0;

        // 因素1：疲劳低的优先（分数用当前疲劳的负值）
        s -= fatigueByEmployee.get(e.getId()) * ctx.getPriorityFatigueBalance();

        // 因素2：这个班次上过的次数少的优先
        int shiftCount = shiftCountByEmployee.get(e.getId()).getOrDefault(task.getShiftType().getId(), 0);
        s -= shiftCount * ctx.getPriorityShiftBalance();

        // 因素3：如果这天是员工希望休，扣大分
        if (ctx.isWishOff(e.getId(), task.getDate())) {
            s -= 100 * ctx.getPriorityWishOff();
        }

        // 因素4：普通员工尽量不排夜班（专职夜勤会被硬性优先，不需要在这里加分）
        // 专职夜勤员工只能上夜班（在 canAssign 里已硬性限制），所以到这里的都是夜班任务
        boolean isNightShift = "夜".equals(task.getShiftType().getPeriod());
        boolean isNightSpecialist = "夜勤専属".equals(e.getEmployeeType());
        if (!isNightSpecialist && isNightShift) s -= 5;  // 普通员工上夜班小扣分（专职夜勤不够时才让普通员工顶）

        // 因素5：平日/公休均衡
        boolean isWeekend = task.getDate().getDayOfWeek() == DayOfWeek.SATURDAY
                || task.getDate().getDayOfWeek() == DayOfWeek.SUNDAY;
        // 简单实现：暂时不加复杂逻辑，占个位（后续可扩展）
        s -= 0 * ctx.getPriorityWeekdayHolidayBalance();

        return s;
    }

    /** 分配后更新员工状态 */
    private void updateEmployeeStateAfterAssign(Employee e, Assignment a) {
        assignmentsByEmployee.get(e.getId()).add(a);

        // 累计时长
        int minutes = shiftDurationMinutes(a.getShiftType());
        minutesByEmployee.merge(e.getId(), minutes, Integer::sum);

        // 累计周时长
        LocalDate weekStart = getWeekStart(a.getDate());
        weeklyMinutesByEmployee.get(e.getId())
                .merge(weekStart, minutes, Integer::sum);

        // 累计班次次数
        shiftCountByEmployee.get(e.getId())
                .merge(a.getShiftType().getId(), 1, Integer::sum);

        // 累计疲劳
        double weight = ctx.getFatigueWeight(a.getFacility().getId(), a.getShiftType().getId());
        int continuous = countContinuousDaysBefore(e, a.getDate()) + 1;
        double multiplier = 1.0;
        if (continuous >= 5) multiplier = ctx.getFatigueContinuous5DayMultiplier();
        else if (continuous >= 3) multiplier = ctx.getFatigueContinuous3DayMultiplier();

        double fatigueAdd = weight * multiplier;

        // 夜班次日再上班的额外分
        LocalDate yesterday = a.getDate().minusDays(1);
        for (Assignment prev : assignmentsByEmployee.get(e.getId())) {
            if (prev.getDate().equals(yesterday) && "夜".equals(prev.getShiftType().getPeriod())) {
                fatigueAdd += ctx.getFatigueNightNextDayBonus();
                break;
            }
        }

        fatigueByEmployee.merge(e.getId(), fatigueAdd, Double::sum);
    }

    /** 计算某员工在某天之前连续上了几天班 */
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

    /** 计算某日期所在周的周一 */
    private LocalDate getWeekStart(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return date.minusDays(dow.getValue() - 1);  // 周一 = 1，往回退 0 天
    }

    /** 班次时长（分钟） */
    private int shiftDurationMinutes(ShiftType s) {
        String[] start = s.getStartTime().split(":");
        String[] end = s.getEndTime().split(":");
        int startMin = Integer.parseInt(start[0]) * 60 + Integer.parseInt(start[1]);
        int endMin = Integer.parseInt(end[0]) * 60 + Integer.parseInt(end[1]);
        int diff = endMin - startMin;
        if (diff <= 0) diff += 24 * 60;  // 跨日班次
        return diff;
    }

    /** ③ 检查所有软性问题 */
    private void collectSoftProblems(SchedulingResult result) {
        for (Employee e : ctx.getEmployees()) {
            // 检查希望休未满足
            List<Assignment> myAssignments = assignmentsByEmployee.get(e.getId());
            for (Assignment a : myAssignments) {
                if (ctx.isWishOff(e.getId(), a.getDate())) {
                    result.addSoftProblem(e.getName() + " 希望休 " + a.getDate() + " 未满足");
                }
            }
        }
    }
}