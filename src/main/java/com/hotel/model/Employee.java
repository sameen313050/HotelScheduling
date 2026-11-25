package com.hotel.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 员工实体类
 * 従業員エンティティクラス
 * Employee entity class
 */
public class Employee {

    // 主键 ID / 主キー ID / Primary key ID
    private int id;

    // 姓名 / 氏名 / Name
    private String name;

    // 员工类型（日文存储：正社員 / 契約社員 / アルバイト / 夜勤専属）
    // 従業員種別（日本語で保存：正社員 / 契約社員 / アルバイト / 夜勤専属）
    // Employee type (stored in Japanese: regular / contract / part-time / night-only)
    private String employeeType;

    // 月工时上限，用包装类（Integer）支持 null，null 表示不限
    // 月間勤務時間の上限、ラッパークラス Integer を使用（null 可）、null は制限なしを意味する
    // Monthly hour limit; uses Integer (nullable) — null means no limit
    private Integer monthlyHourLimit;

    // 周工时上限，null 表示不限（比如外国兼职受签证限制 28h/周）
    // 週間勤務時間の上限、null は制限なし（例：外国人アルバイトはビザ制限で週 28h まで）
    // Weekly hour limit; null means no limit (e.g. foreign part-timers capped at 28h/week by visa)
    private Integer weeklyHourLimit;

    // 是否在职 / 在職中か / Whether currently employed
    private boolean active;

    // 备注（如签证类型等自由记入）/ 備考（ビザ種別等の自由記入欄）/ Free-form notes (e.g. visa type)
    private String note;

    // 员工可工作的设施 ID 列表（多对多关系，从 employee_facility 表读入）
    // 従業員が勤務可能な施設 ID のリスト（多対多関係、employee_facility テーブルから読み込み）
    // List of facility IDs this employee can work at (many-to-many, loaded from employee_facility table)
    private List<Integer> facilityIds = new ArrayList<>();

    /**
     * 默认构造方法（DAO 反射需要）
     * デフォルトコンストラクタ（DAO のリフレクションで必要）
     * Default constructor (required for DAO reflection)
     */
    public Employee() {
    }

    // ===== Getter / Setter =====

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

    /**
     * 显示用：JComboBox 等控件会调用此方法展示员工名
     * 表示用：JComboBox 等のコントロールがこのメソッドで従業員名を表示
     * For display: used by JComboBox etc. to show the employee name
     */
    @Override
    public String toString() {
        return name;
    }
}