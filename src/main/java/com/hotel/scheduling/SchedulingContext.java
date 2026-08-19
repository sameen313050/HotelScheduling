package com.hotel.scheduling;

import com.hotel.dao.*;
import com.hotel.model.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 排班上下文：包含算法需要的全部数据
 * <p>
 * 使用方式：
 * 1. new SchedulingContext(year, month)
 * 2. context.load()  // 从数据库读所有数据
 * 3. 传给算法使用
 */
public class SchedulingContext {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // ===== 输入：目标月份 =====
    private final int year;
    private final int month;

    // ===== 加载后的数据 =====

    /** 目标月份所有日期列表，从 1 号到月末 */
    private List<LocalDate> allDates;

    /** 所有在职员工 */
    private List<Employee> employees;

    /** 所有设施 */
    private List<Facility> facilities;

    /** 所有班次类型 */
    private List<ShiftType> shiftTypes;

    /** 员工 ID → 员工对象，方便按 ID 快速查找 */
    private Map<Integer, Employee> employeeById;

    /** 设施 ID → 设施对象 */
    private Map<Integer, Facility> facilityById;

    /** 班次 ID → 班次对象 */
    private Map<Integer, ShiftType> shiftTypeById;

    /** 每个设施的所有需求配置：facilityId → [FacilityDemand, ...] */
    private Map<Integer, List<FacilityDemand>> demandsByFacility;

    /** 每个设施每天的稼働率：facilityId → (日期字符串 → 稼働率) */
    private Map<Integer, Map<String, Integer>> occupancyByFacility;

    /** 每个设施每天完整数据（含稼働率/CI/CO）：facilityId → (日期字符串 → Occupancy) */
    private Map<Integer, Map<String, com.hotel.model.Occupancy>> fullOccupancyByFacility;

    /** 每个员工的希望休日期集合：employeeId → Set<日期字符串> */
    private Map<Integer, Set<String>> wishOffByEmployee;

    /** 每个员工的疲劳权重：employeeId 无关，看的是 (facilityId, shiftTypeId) → weight */
    private Map<Integer, Map<Integer, Double>> fatigueWeightByFacility;

    /** 全局配置参数 */
    private double fatigueContinuous3DayMultiplier;
    private double fatigueContinuous5DayMultiplier;
    private double fatigueNightNextDayBonus;
    private double fatigueShortRestBonus;
    private int maxContinuousWorkDays;

    private int priorityWishOff;
    private int priorityFatigueBalance;
    private int priorityShiftBalance;
    private int priorityWeekdayHolidayBalance;

    public SchedulingContext(int year, int month) {
        this.year = year;
        this.month = month;
    }

    /**
     * 从数据库加载所有数据
     */
    public void load() {
        // ===== 1. 生成月份日期列表 =====
        YearMonth ym = YearMonth.of(year, month);
        allDates = new ArrayList<>();
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            allDates.add(ym.atDay(day));
        }

        // ===== 2. 加载员工（只在职） =====
        EmployeeDao empDao = new EmployeeDao();
        employees = new ArrayList<>();
        for (Employee e : empDao.findAll()) {
            if (e.isActive()) employees.add(e);
        }
        employeeById = new HashMap<>();
        for (Employee e : employees) {
            employeeById.put(e.getId(), e);
        }

        // ===== 3. 加载设施 =====
        FacilityDao facDao = new FacilityDao();
        facilities = facDao.findAll();
        facilityById = new HashMap<>();
        for (Facility f : facilities) {
            facilityById.put(f.getId(), f);
        }

        // ===== 4. 加载班次类型 =====
        ShiftTypeDao shiftDao = new ShiftTypeDao();
        shiftTypes = shiftDao.findAll();
        shiftTypeById = new HashMap<>();
        for (ShiftType s : shiftTypes) {
            shiftTypeById.put(s.getId(), s);
        }

        // ===== 5. 加载每个设施的需求配置 =====
        FacilityDemandDao demandDao = new FacilityDemandDao();
        demandsByFacility = new HashMap<>();
        for (Facility f : facilities) {
            demandsByFacility.put(f.getId(), demandDao.findByFacility(f.getId()));
        }

        // ===== 6. 加载每个设施该月的稼働率 + CI + CO =====
        OccupancyDao occDao = new OccupancyDao();
        String yearMonthStr = String.format("%04d-%02d", year, month);
        occupancyByFacility = new HashMap<>();
        fullOccupancyByFacility = new HashMap<>();
        for (Facility f : facilities) {
            occupancyByFacility.put(f.getId(), occDao.findByFacilityAndMonth(f.getId(), yearMonthStr));
            fullOccupancyByFacility.put(f.getId(), occDao.findFullByFacilityAndMonth(f.getId(), yearMonthStr));
        }

        // ===== 7. 加载每个员工的希望休 =====
        WishOffDao wishDao = new WishOffDao();
        wishOffByEmployee = new HashMap<>();
        for (Employee e : employees) {
            wishOffByEmployee.put(e.getId(), wishDao.findByEmployeeAndMonth(e.getId(), yearMonthStr));
        }

        // ===== 8. 加载每个设施的疲劳权重 =====
        FatigueWeightDao weightDao = new FatigueWeightDao();
        fatigueWeightByFacility = new HashMap<>();
        for (Facility f : facilities) {
            fatigueWeightByFacility.put(f.getId(), weightDao.findByFacility(f.getId()));
        }

        // ===== 9. 加载全局配置 =====
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

    // ===== 便捷查询方法 =====

    /** 某设施某天的稼働率，找不到返回 0 */
    public int getOccupancy(int facilityId, LocalDate date) {
        Map<String, Integer> map = occupancyByFacility.get(facilityId);
        if (map == null) return 0;
        return map.getOrDefault(date.format(DATE_FMT), 0);
    }

    /** 某员工某天是否希望休 */
    public boolean isWishOff(int employeeId, LocalDate date) {
        Set<String> set = wishOffByEmployee.get(employeeId);
        return set != null && set.contains(date.format(DATE_FMT));
    }

    /** 某设施某班次的疲劳权重，找不到返回 1.0 */
    public double getFatigueWeight(int facilityId, int shiftTypeId) {
        Map<Integer, Double> map = fatigueWeightByFacility.get(facilityId);
        if (map == null) return 1.0;
        return map.getOrDefault(shiftTypeId, 1.0);
    }

    /**
     * 根据稼働率算出某设施某天某班次需要几人
     * 遍历该设施的所有 demand 配置，找到匹配的返回人数，找不到返回 0
     */
    public int getRequiredCount(int facilityId, int shiftTypeId, LocalDate date) {
        List<FacilityDemand> demands = demandsByFacility.get(facilityId);
        if (demands == null) return 0;

        for (FacilityDemand d : demands) {
            // 只看该班次的规则
            if (d.getShiftTypeId() != shiftTypeId) continue;

            // 根据 metric_type 决定用哪个指标匹配
            String metric = d.getMetricType();
            if (metric == null) metric = "occupancy";

            int value = getMetricValue(facilityId, date, metric);
            if (value < 0) continue;  // 该指标没数据，跳过

            if (value >= d.getOccupancyMin() && value <= d.getOccupancyMax()) {
                return d.getRequiredCount();
            }
        }
        return 0;  // 没匹配到任何规则
    }

    /**
     * 根据 metric 类型返回对应值，找不到返回 -1
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