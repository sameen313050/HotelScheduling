package com.hotel.scheduling;

import com.hotel.model.Facility;
import com.hotel.model.ShiftType;

import java.time.LocalDate;

/**
 * 排班任务：某天某设施某班次需要几人
 */
public class Task {

    private final LocalDate date;
    private final Facility facility;
    private final ShiftType shiftType;
    private final int requiredCount;

    /** 已分配到这个任务的人数 */
    private int assignedCount;

    public Task(LocalDate date, Facility facility, ShiftType shiftType, int requiredCount) {
        this.date = date;
        this.facility = facility;
        this.shiftType = shiftType;
        this.requiredCount = requiredCount;
        this.assignedCount = 0;
    }

    public LocalDate getDate() { return date; }
    public Facility getFacility() { return facility; }
    public ShiftType getShiftType() { return shiftType; }
    public int getRequiredCount() { return requiredCount; }
    public int getAssignedCount() { return assignedCount; }

    public void incrementAssigned() { this.assignedCount++; }

    /** 还差几人 */
    public int getShortage() { return requiredCount - assignedCount; }

    /** 是否已排满 */
    public boolean isFull() { return assignedCount >= requiredCount; }

    @Override
    public String toString() {
        return date + " " + facility.getName() + " " + shiftType.getName()
                + " (" + assignedCount + "/" + requiredCount + ")";
    }
}