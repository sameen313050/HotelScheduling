package com.hotel.model;

/**
 * 设施需求：某设施在某班次某指标区间下需要多少人
 * 施設需要：施設・シフト種別・指標区間ごとの必要人数
 * Facility demand: required headcount per facility/shift-type/metric-range tier
 *
 * 例：東京ホテル 中番 CI 10-25人 → 2 人
 * Example: Tokyo Hotel middle shift, CI 10-25 guests → 2 staff
 */
public class FacilityDemand {

    // 主键 ID / 主キー ID / Primary key ID
    private int id;

    // 设施 ID / 施設 ID / Facility ID
    private int facilityId;

    // 班次 ID / シフト種別 ID / Shift type ID
    private int shiftTypeId;

    // 指标区间下限（含）0-100 或人数
    // 指標区間の下限（含む）0-100 または人数
    // Metric range lower bound (inclusive) 0-100 or person count
    private int occupancyMin;

    // 指标区间上限（含）0-100 或人数
    // 指標区間の上限（含む）0-100 または人数
    // Metric range upper bound (inclusive) 0-100 or person count
    private int occupancyMax;

    // 所需人数 / 必要人数 / Required headcount
    private int requiredCount;

    // 判断依据：
    //   "occupancy" = 稼働率（%）
    //   "ci_count"  = CI（チェックイン）人数
    //   "co_count"  = CO（チェックアウト）人数
    // 判断基準：
    //   "occupancy" = 稼働率（％）
    //   "ci_count"  = チェックイン人数
    //   "co_count"  = チェックアウト人数
    // Decision metric:
    //   "occupancy" = occupancy rate (%)
    //   "ci_count"  = check-in count
    //   "co_count"  = check-out count
    private String metricType = "occupancy";

    // 显示用（非数据库字段）：班次名称，用于在表格里展示
    // 表示用（DB 非永続）：シフト種別名、テーブル表示用
    // Display-only (not persisted): shift type name, used for table rendering
    private String shiftTypeName;

    /**
     * 默认构造方法（DAO 反射需要）
     * デフォルトコンストラクタ（DAO のリフレクションで必要）
     * Default constructor (required for DAO reflection)
     */
    public FacilityDemand() {
    }

    // ===== Getter / Setter =====

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