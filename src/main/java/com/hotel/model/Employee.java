package com.hotel.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 员工实体类
 */
public class Employee {

    private int id;
    private String name;              // 姓名
    private String employeeType;      // 员工类型（日文存储）
    private Integer monthlyHourLimit; // 用包装类，月工时上限，null 表示不限，int不能为null
    private Integer weeklyHourLimit;  // 周工时上限，null 表示不限
    private boolean active;           // 是否在职
    private String note;              // 备注（如签证类型）

    // 员工可工作的设施 ID 列表
    private List<Integer> facilityIds = new ArrayList<>();

    public Employee() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmployeeType() { return employeeType; }
    public void setEmployeeType(String employeeType) { this.employeeType = employeeType; }

    public Integer getMonthlyHourLimit() { return monthlyHourLimit; }
    public void setMonthlyHourLimit(Integer monthlyHourLimit) { this.monthlyHourLimit = monthlyHourLimit; }

    public Integer getWeeklyHourLimit() { return weeklyHourLimit; }
    public void setWeeklyHourLimit(Integer weeklyHourLimit) { this.weeklyHourLimit = weeklyHourLimit; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public List<Integer> getFacilityIds() { return facilityIds; }
    public void setFacilityIds(List<Integer> facilityIds) { this.facilityIds = facilityIds; }

    @Override
    public String toString() {
        return name;
    }
}
