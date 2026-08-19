package com.hotel.model;

/**
 * 某设施某日的稼働率
 */
public class Occupancy {

    private int id;
    private int facilityId;
    private String workDate;     // 格式 "2026-06-15"
    private int occupancyRate;   // 0-100
    private Integer ciCount;   // 当日 CI（入住）人数，可空
    private Integer coCount;   // 当日 CO（退房）人数，可空

    public Occupancy() {
    }

    public Occupancy(int facilityId, String workDate, int occupancyRate) {
        this.facilityId = facilityId;
        this.workDate = workDate;
        this.occupancyRate = occupancyRate;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getFacilityId() { return facilityId; }
    public void setFacilityId(int facilityId) { this.facilityId = facilityId; }

    public String getWorkDate() { return workDate; }
    public void setWorkDate(String workDate) { this.workDate = workDate; }

    public int getOccupancyRate() { return occupancyRate; }
    public void setOccupancyRate(int occupancyRate) { this.occupancyRate = occupancyRate; }

    public Integer getCiCount() { return ciCount; }
    public void setCiCount(Integer ciCount) { this.ciCount = ciCount; }

    public Integer getCoCount() { return coCount; }
    public void setCoCount(Integer coCount) { this.coCount = coCount; }
}
