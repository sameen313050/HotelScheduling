package com.hotel.model;

/**
 * 班次类型实体类
 */
public class ShiftType {

    private int id;
    private String name;          // 班次名称，比如 "早番 8-17"
    private String startTime;     // 开始时间 "08:00"
    private String endTime;       // 结束时间 "17:00"
    private boolean crossDay;     // 是否跨日（夜班）
    private String period;        // 时段：早/中/晚/夜（数据库里存日文 朝/昼/夕/夜）

    public ShiftType() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public boolean isCrossDay() { return crossDay; }
    public void setCrossDay(boolean crossDay) { this.crossDay = crossDay; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    @Override
    public String toString() {
        return name;
    }
}
