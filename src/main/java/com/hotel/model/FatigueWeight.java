package com.hotel.model;

/**
 * 疲劳权重：某设施 × 某班次 的疲劳系数
 * 疲労度ウェイト：施設 × シフト種別ごとの疲労係数
 * Fatigue weight: fatigue coefficient per facility × shift-type combination
 *
 * 示例：東京ホテル × 夜番 → 2.0（夜班更累，权重高）
 *       京都ホテル × 朝番 → 1.0（普通班次，基准权重）
 * 例：東京ホテル × 夜番 → 2.0（夜勤はより疲労度が高いため重み大）
 *     京都ホテル × 朝番 → 1.0（通常シフト、基準重み）
 * Example: Tokyo Hotel × night shift → 2.0 (night shifts are more tiring, higher weight)
 *          Kyoto Hotel × morning shift → 1.0 (standard shift, baseline weight)
 *
 * 排班算法会累加每位员工的疲劳分，疲劳分低的员工优先被排班。
 * シフトアルゴリズムが従業員ごとに疲労度を累積し、疲労度の低い従業員が優先的に配置される。
 * The scheduling algorithm accumulates fatigue per employee; those with lower fatigue are prioritized.
 */
public class FatigueWeight {

    // 主键 ID / 主キー ID / Primary key ID
    private int id;

    // 设施 ID / 施設 ID / Facility ID
    private int facilityId;

    // 班次 ID / シフト種別 ID / Shift type ID
    private int shiftTypeId;

    // 疲劳系数（通常 1.0 为基准，越大表示越累）
    // 疲労係数（通常 1.0 を基準、大きいほど疲労度が高い）
    // Fatigue coefficient (1.0 as baseline; higher = more tiring)
    private double weight;

    /**
     * 默认构造方法（DAO 反射需要）
     * デフォルトコンストラクタ（DAO のリフレクションで必要）
     * Default constructor (required for DAO reflection)
     */
    public FatigueWeight() {
    }

    /**
     * 便捷构造方法（省略 ID，适合新增时使用）
     * 簡易コンストラクタ（ID 省略、新規登録時に便利）
     * Convenience constructor (omits ID, handy for inserts)
     */
    public FatigueWeight(int facilityId, int shiftTypeId, double weight) {
        this.facilityId = facilityId;
        this.shiftTypeId = shiftTypeId;
        this.weight = weight;
    }

    // ===== Getter / Setter =====

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getFacilityId() { return facilityId; }
    public void setFacilityId(int facilityId) { this.facilityId = facilityId; }

    public int getShiftTypeId() { return shiftTypeId; }
    public void setShiftTypeId(int shiftTypeId) { this.shiftTypeId = shiftTypeId; }

    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }
}