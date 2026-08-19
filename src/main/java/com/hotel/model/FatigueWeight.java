package com.hotel.model;

/**
 * 疲劳权重：某设施 × 某班次 的疲劳系数
 */
public class FatigueWeight {

    private int id;
    private int facilityId;
    private int shiftTypeId;
    private double weight;

    public FatigueWeight() {
    }

    public FatigueWeight(int facilityId, int shiftTypeId, double weight) {
        this.facilityId = facilityId;
        this.shiftTypeId = shiftTypeId;
        this.weight = weight;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getFacilityId() { return facilityId; }
    public void setFacilityId(int facilityId) { this.facilityId = facilityId; }

    public int getShiftTypeId() { return shiftTypeId; }
    public void setShiftTypeId(int shiftTypeId) { this.shiftTypeId = shiftTypeId; }

    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }
}
