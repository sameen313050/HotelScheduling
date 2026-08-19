package com.hotel.model;

/**
 * 设施需求：某设施在某班次某稼働率区间下需要多少人
 */
public class FacilityDemand {

    private int id;
    private int facilityId;       // 设施 ID
    private int shiftTypeId;      // 班次 ID
    private int occupancyMin;     // 稼働率下限 0-100
    private int occupancyMax;     // 稼働率上限 0-100
    private int requiredCount;    // 所需人数
    private String metricType = "occupancy";  // "occupancy" / "ci_count" / "co_count"
    // 显示用（非数据库字段）
    private String shiftTypeName; // 班次名称，用于在表格里显示

    public FacilityDemand() {
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getFacilityId() { return facilityId; }
    public void setFacilityId(int facilityId) { this.facilityId = facilityId; }

    public int getShiftTypeId() { return shiftTypeId; }
    public void setShiftTypeId(int shiftTypeId) { this.shiftTypeId = shiftTypeId; }

    public int getOccupancyMin() { return occupancyMin; }
    public void setOccupancyMin(int occupancyMin) { this.occupancyMin = occupancyMin; }

    public int getOccupancyMax() { return occupancyMax; }
    public void setOccupancyMax(int occupancyMax) { this.occupancyMax = occupancyMax; }

    public int getRequiredCount() { return requiredCount; }
    public void setRequiredCount(int requiredCount) { this.requiredCount = requiredCount; }

    public String getShiftTypeName() { return shiftTypeName; }
    public void setShiftTypeName(String shiftTypeName) { this.shiftTypeName = shiftTypeName; }

    public String getMetricType() { return metricType; }
    public void setMetricType(String metricType) { this.metricType = metricType; }
}
