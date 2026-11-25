package com.hotel.scheduling;

import com.hotel.model.Employee;
import com.hotel.model.Facility;
import com.hotel.model.ShiftType;

import java.time.LocalDate;

/**
 * 一条排班分配：某员工在某天被安排到某设施某班次
 * 1 件のシフト割当：ある従業員が特定日に特定施設の特定シフトに配置される
 * One schedule assignment: an employee assigned to a specific facility + shift on a specific date
 *
 * 不可变对象（immutable）：所有字段 final，一旦创建不能修改。
 * イミュータブルオブジェクト：全フィールド final、作成後変更不可。
 * Immutable object: all fields are final, cannot be modified once constructed.
 *
 * 这样设计是因为排班算法会同时处理数千条分配，不可变对象线程更安全、Bug 更少。
 * この設計は、シフトアルゴリズムが数千件の割当を同時処理するため、イミュータブルの方がスレッドセーフでバグが少ない。
 * This design: the scheduling algorithm processes thousands of assignments at once; immutable objects are thread-safer and less bug-prone.
 */
public class Assignment {

    // 工作日期 / 勤務日 / Work date
    private final LocalDate date;

    // 分配到的员工 / 割当従業員 / Assigned employee
    private final Employee employee;

    // 分配到的设施 / 割当施設 / Assigned facility
    private final Facility facility;

    // 分配到的班次 / 割当シフト種別 / Assigned shift type
    private final ShiftType shiftType;

    /**
     * 全参构造方法（唯一构造方法，保证不可变性）
     * 全引数コンストラクタ（唯一のコンストラクタ、イミュータブル性を保証）
     * All-arg constructor (the only constructor, ensures immutability)
     */
    public Assignment(LocalDate date, Employee employee, Facility facility, ShiftType shiftType) {
        this.date = date;
        this.employee = employee;
        this.facility = facility;
        this.shiftType = shiftType;
    }

    // ===== Getter（无 Setter，不可变）/ ゲッターのみ（セッター無し、不変）/ Getters only (immutable) =====

    public LocalDate getDate() { return date; }
    public Employee getEmployee() { return employee; }
    public Facility getFacility() { return facility; }
    public ShiftType getShiftType() { return shiftType; }

    /**
     * 显示用：便于调试和日志输出
     * 表示用：デバッグとログ出力に便利
     * For display: convenient for debugging and log output
     *
     * 例：2026-08-15 田中 → 東京ホテル 早番
     * Example: 2026-08-15 Tanaka → Tokyo Hotel Morning
     */
    @Override
    public String toString() {
        return date + " " + employee.getName() + " → " + facility.getName() + " " + shiftType.getName();
    }
}