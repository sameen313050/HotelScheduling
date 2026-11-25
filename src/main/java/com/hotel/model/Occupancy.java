package com.hotel.model;

/**
 * 某设施某日的稼働率
 * 施設の日別稼働率データ
 * Daily occupancy data for a facility
 *
 * 排班算法根据稼働率 / CI / CO 决定该设施当日每个班次需要几人。
 * シフトアルゴリズムは稼働率 / CI / CO に基づき、当該施設のシフト毎の必要人数を決定する。
 * The scheduling algorithm uses occupancy / CI / CO to decide required headcount per shift.
 */
public class Occupancy {

    // 主键 ID / 主キー ID / Primary key ID
    private int id;

    // 设施 ID / 施設 ID / Facility ID
    private int facilityId;

    // 工作日期（格式 "2026-06-15"，用字符串便于 SQLite 存储和前缀查询）
    // 勤務日（"2026-06-15" 形式、SQLite 保存と前方一致検索のため文字列型）
    // Work date (format "2026-06-15"; stored as string for SQLite + prefix queries)
    private String workDate;

    // 稼働率 0-100 / 稼働率 0-100 / Occupancy rate 0-100
    private int occupancyRate;

    // 当日 CI（チェックイン）人数，可空（旧数据没有此字段）
    // 当日 CI（チェックイン）人数、null 可（旧データには無い）
    // Check-in count for the day; nullable (missing in legacy data)
    private Integer ciCount;

    // 当日 CO（チェックアウト）人数，可空（旧数据没有此字段）
    // 当日 CO（チェックアウト）人数、null 可（旧データには無い）
    // Check-out count for the day; nullable (missing in legacy data)
    private Integer coCount;

    /**
     * 默认构造方法（DAO 反射需要）
     * デフォルトコンストラクタ（DAO のリフレクションで必要）
     * Default constructor (required for DAO reflection)
     */
    public Occupancy() {
    }

    /**
     * 便捷构造方法（仅稼働率、不含 CI/CO）
     * 簡易コンストラクタ（稼働率のみ、CI/CO は含まない）
     * Convenience constructor (occupancy only, without CI/CO)
     */
    public Occupancy(int facilityId, String workDate, int occupancyRate) {
        this.facilityId = facilityId;
        this.workDate = workDate;
        this.occupancyRate = occupancyRate;
    }

    // ===== Getter / Setter =====

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