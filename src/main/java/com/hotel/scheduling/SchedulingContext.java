package com.hotel.scheduling;

import com.hotel.dao.*;
import com.hotel.model.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 排班上下文：包含算法需要的全部数据
 * シフトコンテキスト：アルゴリズムが必要とするすべてのデータを保持
 * Scheduling context: holds all data required by the algorithm
 *
 * <p>设计思路：把分散在多个 DAO 里的数据预先加载到内存，
 * 算法执行时只做内存查询，避免反复查询数据库导致性能瓶颈。</p>
 *
 * <p>設計思想：複数の DAO に散らばったデータを事前にメモリへロードし、
 * アルゴリズム実行中はメモリ検索のみとして、DB 繰り返しアクセスによる性能ボトルネックを回避。</p>
 *
 * <p>Design rationale: preload data scattered across multiple DAOs into memory,
 * so the algorithm only performs in-memory lookups and avoids DB round-trips.</p>
 *
 * <p>使用方式 / 使い方 / Usage:
 * <ol>
 *   <li>new SchedulingContext(year, month)</li>
 *   <li>context.load()  // 从数据库读所有数据 / DB から全データ読込 / Load everything from DB</li>
 *   <li>传给算法使用 / アルゴリズムに渡す / Pass to the algorithm</li>
 * </ol>
 * </p>
 */
public class SchedulingContext {

    // 日期格式常量 / 日付フォーマット定数 / Date format constant
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ===== 输入：目标月份 / 入力：対象年月 / Input: target year-month =====
    private final int year;
    private final int month;

    // ===== 加载后的数据 / ロード済みデータ / Loaded data =====

    /**
     * 目标月份所有日期列表，从 1 号到月末
     * 対象月の全日付リスト、1 日から月末まで
     * All dates in the target month, from day 1 to month end
     */
    private List<LocalDate> allDates;

    /**
     * 所有在职员工
     * 在職中の全従業員
     * All active employees
     */
    private List<Employee> employees;

    /**
     * 所有设施
     * 全施設
     * All facilities
     */
    private List<Facility> facilities;

    /**
     * 所有班次类型
     * 全シフト種別
     * All shift types
     */
    private List<ShiftType> shiftTypes;

    /**
     * 员工 ID → 员工对象，方便按 ID 快速查找（空间换时间）
     * 従業員 ID → 従業員オブジェクト、ID による高速検索用（空間と引き換えに時間）
     * Employee ID → Employee object, for fast lookup by ID (trade space for time)
     */
    private Map<Integer, Employee> employeeById;

    /** 设施 ID → 设施对象 / 施設 ID → 施設オブジェクト / Facility ID → Facility object */
    private Map<Integer, Facility> facilityById;

    /** 班次 ID → 班次对象 / シフト ID → シフトオブジェクト / Shift ID → ShiftType object */
    private Map<Integer, ShiftType> shiftTypeById;

    /**
     * 每个设施的所有需求配置：facilityId → [FacilityDemand, ...]
     * 施設ごとの全需要設定：facilityId → [FacilityDemand, ...]
     * All demand rules per facility: facilityId → [FacilityDemand, ...]
     */
    private Map<Integer, List<FacilityDemand>> demandsByFacility;

    /**
     * 每个设施每天的稼働率：facilityId → (日期字符串 → 稼働率)
     * 施設・日別の稼働率：facilityId → (日付文字列 → 稼働率)
     * Per-facility daily occupancy: facilityId → (date string → occupancy)
     */
    private Map<Integer, Map<String, Integer>> occupancyByFacility;

    /**
     * 每个设施每天完整数据（含稼働率/CI/CO）
     * 施設・日別の完全データ（稼働率/CI/CO 含む）
     * Per-facility daily full data (occupancy + CI + CO)
     */
    private Map<Integer, Map<String, com.hotel.model.Occupancy>> fullOccupancyByFacility;

    /**
     * 每个员工的希望休日期集合：employeeId → Set<日期字符串>
     * 従業員ごとの希望休日付：employeeId → Set<日付文字列>
     * Preferred days off per employee: employeeId → Set<date string>
     */
    private Map<Integer, Set<String>> wishOffByEmployee;

    /**
     * 疲劳权重：(facilityId → (shiftTypeId → weight))
     * 疲労度ウェイト：(facilityId → (shiftTypeId → weight))
     * Fatigue weights: (facilityId → (shiftTypeId → weight))
     */
    private Map<Integer, Map<Integer, Double>> fatigueWeightByFacility;

    /**
     * 员工有休：employeeId → Map<日期字符串, 小时数>
     * 従業員の有給休暇：employeeId → Map<日付文字列, 時間数>
     * Employee paid leave: employeeId → Map<date string, hours>
     */
    private Map<Integer, Map<String, Integer>> paidLeaveByEmployee = new HashMap<>();

    // ===== 全局配置参数（算法行为调节）=====
    // ===== グローバル設定パラメータ（アルゴリズム動作調整）=====
    // ===== Global configuration parameters (algorithm tuning) =====

    private double fatigueContinuous3DayMultiplier;   // 连续 3 天工作疲劳倍率 / 3 日連続勤務疲労倍率 / 3-day consecutive fatigue multiplier
    private double fatigueContinuous5DayMultiplier;   // 连续 5 天工作疲劳倍率 / 5 日連続勤務疲労倍率 / 5-day consecutive fatigue multiplier
    private double fatigueNightNextDayBonus;          // 夜班次日疲劳附加 / 夜勤翌日の疲労加算 / Extra fatigue the day after a night shift
    private double fatigueShortRestBonus;             // 短休息疲劳附加 / 短休息時の疲労加算 / Extra fatigue when rest is short
    private int maxContinuousWorkDays;                // 最大连续上班天数（硬性）/ 最大連続勤務日数（ハード）/ Max consecutive work days (hard)

    private int priorityWishOff;                      // 希望休优先级权重 / 希望休の優先度ウェイト / Priority weight for preferred day-off
    private int priorityFatigueBalance;               // 疲劳均衡优先级权重 / 疲労度バランスの優先度ウェイト / Priority weight for fatigue balancing
    private int priorityShiftBalance;                 // 班次均衡优先级权重 / シフトバランスの優先度ウェイト / Priority weight for shift balancing
    private int priorityWeekdayHolidayBalance;        // 平日/公休均衡优先级权重 / 平日・休日バランスの優先度ウェイト / Priority weight for weekday/holiday balance

    /**
     * 构造方法：仅指定目标月份，数据通过 load() 加载
     * コンストラクタ：対象年月のみ指定、データは load() で読み込む
     * Constructor: specify target year-month only; data is loaded via load()
     */
    public SchedulingContext(int year, int month) {
        this.year = year;
        this.month = month;
    }

    /**
     * 从数据库加载所有数据
     * データベースから全データを読み込み
     * Loads all data from the database
     *
     * 分 9 步进行，每步独立且可追溯，便于后续排查性能瓶颈。
     * 9 ステップで実施、各ステップは独立で追跡可能、性能ボトルネック調査に便利。
     * Performed in 9 steps, each independent and traceable to facilitate performance analysis.
     */
    public void load() {
        // ===== 1. 生成月份日期列表 / 月の日付リスト生成 / Generate list of dates for the month =====
        YearMonth ym = YearMonth.of(year, month);
        allDates = new ArrayList<>();
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            allDates.add(ym.atDay(day));
        }

        // ===== 2. 加载员工（只在职）/ 従業員ロード（在職者のみ）/ Load employees (active only) =====
        EmployeeDao empDao = new EmployeeDao();
        employees = new ArrayList<>();
        for (Employee e : empDao.findAll()) {
            if (e.isActive()) employees.add(e);
        }
        employeeById = new HashMap<>();
        for (Employee e : employees) {
            employeeById.put(e.getId(), e);
        }

        // ===== 3. 加载设施 / 施設ロード / Load facilities =====
        FacilityDao facDao = new FacilityDao();
        facilities = facDao.findAll();
        facilityById = new HashMap<>();
        for (Facility f : facilities) {
            facilityById.put(f.getId(), f);
        }

        // ===== 4. 加载班次类型 / シフト種別ロード / Load shift types =====
        ShiftTypeDao shiftDao = new ShiftTypeDao();
        shiftTypes = shiftDao.findAll();
        shiftTypeById = new HashMap<>();
        for (ShiftType s : shiftTypes) {
            shiftTypeById.put(s.getId(), s);
        }

        // ===== 5. 加载每个设施的需求配置 / 施設ごとの需要設定ロード / Load demand per facility =====
        FacilityDemandDao demandDao = new FacilityDemandDao();
        demandsByFacility = new HashMap<>();
        for (Facility f : facilities) {
            demandsByFacility.put(f.getId(), demandDao.findByFacility(f.getId()));
        }

        // ===== 6. 加载每个设施该月的稼働率 + CI + CO =====
        // ===== 施設ごとの当月稼働率 + CI + CO をロード =====
        // ===== Load occupancy + CI + CO per facility for the month =====
        OccupancyDao occDao = new OccupancyDao();
        String yearMonthStr = String.format("%04d-%02d", year, month);
        occupancyByFacility = new HashMap<>();
        fullOccupancyByFacility = new HashMap<>();
        for (Facility f : facilities) {
            occupancyByFacility.put(f.getId(), occDao.findByFacilityAndMonth(f.getId(), yearMonthStr));
            fullOccupancyByFacility.put(f.getId(), occDao.findFullByFacilityAndMonth(f.getId(), yearMonthStr));
        }

        // ===== 7. 加载每个员工的希望休 / 従業員ごとの希望休ロード / Load preferred days off per employee =====
        WishOffDao wishDao = new WishOffDao();
        wishOffByEmployee = new HashMap<>();
        for (Employee e : employees) {
            wishOffByEmployee.put(e.getId(), wishDao.findByEmployeeAndMonth(e.getId(), yearMonthStr));
        }

        // 加载有休 / 有給休暇ロード / Load paid leave
        com.hotel.dao.PaidLeaveDao paidDao = new com.hotel.dao.PaidLeaveDao();
        paidLeaveByEmployee = new HashMap<>();
        for (Employee e : employees) {
            paidLeaveByEmployee.put(e.getId(), paidDao.findByEmployeeAndMonth(e.getId(), yearMonthStr));
        }

        // ===== 8. 加载每个设施的疲劳权重 / 施設ごとの疲労度ウェイトロード / Load fatigue weights per facility =====
        FatigueWeightDao weightDao = new FatigueWeightDao();
        fatigueWeightByFacility = new HashMap<>();
        for (Facility f : facilities) {
            fatigueWeightByFacility.put(f.getId(), weightDao.findByFacility(f.getId()));
        }

        // ===== 9. 加载全局配置（算法调节参数）=====
        // ===== グローバル設定ロード（アルゴリズム調整パラメータ）=====
        // ===== Load global configuration (algorithm tuning parameters) =====
        ConfigDao configDao = new ConfigDao();
        fatigueContinuous3DayMultiplier = configDao.getDouble("fatigue_continuous_3day_multiplier", 1.1);
        fatigueContinuous5DayMultiplier = configDao.getDouble("fatigue_continuous_5day_multiplier", 1.3);
        fatigueNightNextDayBonus = configDao.getDouble("fatigue_night_next_day_bonus", 0.5);
        fatigueShortRestBonus = configDao.getDouble("fatigue_short_rest_bonus", 0.5);
        maxContinuousWorkDays = configDao.getInt("max_continuous_work_days", 5);
        priorityWishOff = configDao.getInt("priority_wish_off", 4);
        priorityFatigueBalance = configDao.getInt("priority_fatigue_balance", 3);
        priorityShiftBalance = configDao.getInt("priority_shift_balance", 2);
        priorityWeekdayHolidayBalance = configDao.getInt("priority_weekday_holiday_balance", 1);
    }

    // ===== 便捷查询方法 / 便利検索メソッド / Convenient lookup methods =====

    /**
     * 某设施某天的稼働率，找不到返回 0
     * 施設・日別の稼働率を取得、未登録は 0
     * Get occupancy for a facility on a date; returns 0 if missing
     */
    public int getOccupancy(int facilityId, LocalDate date) {
        Map<String, Integer> map = occupancyByFacility.get(facilityId);
        if (map == null) return 0;
        return map.getOrDefault(date.format(DATE_FMT), 0);
    }

    /**
     * 某员工某天是否希望休
     * 従業員が該当日を希望休としているか
     * Whether the employee requested this date as a preferred day off
     */
    public boolean isWishOff(int employeeId, LocalDate date) {
        Set<String> set = wishOffByEmployee.get(employeeId);
        return set != null && set.contains(date.format(DATE_FMT));
    }

    /**
     * 判断某员工某天是否请了有休
     * 従業員が該当日に有給休暇を申請しているか
     * Whether the employee has paid leave on this date
     */
    public boolean isPaidLeave(int employeeId, java.time.LocalDate date) {
        Map<String, Integer> map = paidLeaveByEmployee.get(employeeId);
        if (map == null) return false;
        return map.containsKey(date.toString());
    }

    /**
     * 获取某员工某月所有有休小时数之和
     * 従業員の当月の有給休暇時間合計を取得
     * Returns total paid leave hours for the employee in the target month
     */
    public int getPaidLeaveHoursInMonth(int employeeId) {
        Map<String, Integer> map = paidLeaveByEmployee.get(employeeId);
        if (map == null) return 0;
        int sum = 0;
        for (Integer h : map.values()) sum += h;
        return sum;
    }

    /**
     * 获取某员工某月所有有休日期
     * 従業員の当月の全有給休暇日付を取得
     * Returns all paid leave dates for the employee in the target month
     */
    public Set<String> getPaidLeaveDates(int employeeId) {
        Map<String, Integer> map = paidLeaveByEmployee.get(employeeId);
        if (map == null) return java.util.Collections.emptySet();
        return map.keySet();
    }

    /**
     * 某设施某班次的疲劳权重，找不到返回 1.0（基准值）
     * 施設・シフト別の疲労ウェイト、未登録は 1.0（基準値）
     * Fatigue weight for facility + shift; returns 1.0 (baseline) if missing
     */
    public double getFatigueWeight(int facilityId, int shiftTypeId) {
        Map<Integer, Double> map = fatigueWeightByFacility.get(facilityId);
        if (map == null) return 1.0;
        return map.getOrDefault(shiftTypeId, 1.0);
    }

    /**
     * 根据稼働率/CI/CO 算出某设施某天某班次需要几人
     * 稼働率/CI/CO に基づき、施設・日・シフトの必要人数を算出
     * Compute required headcount for a facility/date/shift based on occupancy/CI/CO
     *
     * 遍历该设施的所有 demand 配置，找到匹配区间的返回人数，找不到返回 0。
     * 当該施設の全 demand 設定を走査、マッチする区間の人数を返却、無ければ 0。
     * Iterates all demand rules for the facility; returns matched headcount, or 0 if no match.
     */
    public int getRequiredCount(int facilityId, int shiftTypeId, LocalDate date) {
        List<FacilityDemand> demands = demandsByFacility.get(facilityId);
        if (demands == null) return 0;

        for (FacilityDemand d : demands) {
            // 只看该班次的规则 / 当該シフトのルールのみ対象 / Only consider rules for this shift
            if (d.getShiftTypeId() != shiftTypeId) continue;

            // 根据 metric_type 决定用哪个指标匹配
            // metric_type に基づいて使用する指標を決定
            // Decide which metric to use based on metric_type
            String metric = d.getMetricType();
            if (metric == null) metric = "occupancy";

            int value = getMetricValue(facilityId, date, metric);
            if (value < 0) continue;  // 该指标没数据，跳过 / 指標データ無し、スキップ / No data for this metric, skip

            if (value >= d.getOccupancyMin() && value <= d.getOccupancyMax()) {
                return d.getRequiredCount();
            }
        }
        return 0;  // 没匹配到任何规则 / どのルールにもマッチせず / No rule matched
    }

    /**
     * 根据 metric 类型返回对应值，找不到返回 -1
     * metric 種別に応じて対応する値を返却、未登録は -1
     * Returns the value for the given metric type; returns -1 if missing
     */
    private int getMetricValue(int facilityId, LocalDate date, String metric) {
        Map<String, com.hotel.model.Occupancy> map = fullOccupancyByFacility.get(facilityId);
        if (map == null) return -1;
        com.hotel.model.Occupancy o = map.get(date.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        if (o == null) return -1;

        switch (metric) {
            case "ci_count":
                return o.getCiCount() == null ? -1 : o.getCiCount();
            case "co_count":
                return o.getCoCount() == null ? -1 : o.getCoCount();
            case "occupancy":
            default:
                return o.getOccupancyRate();
        }
    }

    // ===== Getter =====

    public int getYear() { return year; }
    public int getMonth() { return month; }
    public List<LocalDate> getAllDates() { return allDates; }
    public List<Employee> getEmployees() { return employees; }
    public List<Facility> getFacilities() { return facilities; }
    public List<ShiftType> getShiftTypes() { return shiftTypes; }
    public Employee getEmployee(int id) { return employeeById.get(id); }
    public Facility getFacility(int id) { return facilityById.get(id); }
    public ShiftType getShiftType(int id) { return shiftTypeById.get(id); }

    public double getFatigueContinuous3DayMultiplier() { return fatigueContinuous3DayMultiplier; }
    public double getFatigueContinuous5DayMultiplier() { return fatigueContinuous5DayMultiplier; }
    public double getFatigueNightNextDayBonus() { return fatigueNightNextDayBonus; }
    public double getFatigueShortRestBonus() { return fatigueShortRestBonus; }
    public int getMaxContinuousWorkDays() { return maxContinuousWorkDays; }

    public int getPriorityWishOff() { return priorityWishOff; }
    public int getPriorityFatigueBalance() { return priorityFatigueBalance; }
    public int getPriorityShiftBalance() { return priorityShiftBalance; }
    public int getPriorityWeekdayHolidayBalance() { return priorityWeekdayHolidayBalance; }
}