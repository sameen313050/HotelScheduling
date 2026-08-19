package com.hotel.scheduling;

import com.hotel.model.Employee;
import com.hotel.model.Facility;
import com.hotel.model.ShiftType;

import java.time.LocalDate;

/**
 * 一条排班分配：某员工在某天被安排到某设施某班次
 */
public class Assignment {

    private final LocalDate date;
    private final Employee employee;
    private final Facility facility;
    private final ShiftType shiftType;

    public Assignment(LocalDate date, Employee employee, Facility facility, ShiftType shiftType) {
        this.date = date;
        this.employee = employee;
        this.facility = facility;
        this.shiftType = shiftType;
    }

    public LocalDate getDate() { return date; }
    public Employee getEmployee() { return employee; }
    public Facility getFacility() { return facility; }
    public ShiftType getShiftType() { return shiftType; }

    @Override
    public String toString() {
        return date + " " + employee.getName() + " → " + facility.getName() + " " + shiftType.getName();
    }
}