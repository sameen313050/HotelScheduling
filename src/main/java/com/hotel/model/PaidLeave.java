package com.hotel.model;

/**
 * 有休记录（员工请的带薪休假）
 * 有給休暇記録（従業員が取得する有給休暇）
 * Paid leave record (annual paid leave taken by an employee)
 *
 * 业务规则：
 *   1. 有休日员工不会被排班（硬性约束）
 *   2. 有休小时数计入工时统计（即正社员月目标工时包含有休）
 *   3. 区别于希望休：希望休是"不想上班但未请假"，是软性目标
 *
 * 業務ルール：
 *   1. 有休日は従業員がシフトに入らない（ハード制約）
 *   2. 有休時間は勤務時間統計に計上（正社員の月間目標時間に含む）
 *   3. 希望休との違い：希望休は「出勤したくないが有給未申請」、ソフト目標
 *
 * Business rules:
 *   1. Employees are not scheduled on paid leave days (hard constraint)
 *   2. Paid leave hours count toward working hour totals (included in regular staff monthly target)
 *   3. Different from preferred day-off: preferred day-off is a soft goal without formal leave
 */
public class PaidLeave {

    // 主键 ID / 主キー ID / Primary key ID
    private int id;

    // 员工 ID / 従業員 ID / Employee ID
    private int employeeId;

    // 请假日期（格式 "2026-11-15"，字符串便于 SQLite 前缀查询）
    // 休暇日（"2026-11-15" 形式、SQLite 前方一致検索のため文字列型）
    // Leave date (format "2026-11-15"; stored as string for SQLite prefix queries)
    private String leaveDate;

    // 小时数（一般 8h = 全天，也可输入 4h 半天等任意数值）
    // 時間数（通常 8h = 1 日、4h の半休など任意の値を入力可能）
    // Hours (typically 8h = full day; also accepts 4h half-day or any custom value)
    private int hours;

    // 备注（请假事由等自由记入）
    // 備考（休暇事由等の自由記入欄）
    // Free-form notes (e.g. reason for leave)
    private String note;

    /**
     * 默认构造方法（DAO 反射需要）
     * デフォルトコンストラクタ（DAO のリフレクションで必要）
     * Default constructor (required for DAO reflection)
     */
    public PaidLeave() {}

    /**
     * 便捷构造方法（必填字段）
     * 簡易コンストラクタ（必須項目のみ）
     * Convenience constructor (required fields only)
     */
    public PaidLeave(int employeeId, String leaveDate, int hours) {
        this.employeeId = employeeId;
        this.leaveDate = leaveDate;
        this.hours = hours;
    }

    // ===== Getter / Setter =====

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getEmployeeId() { return employeeId; }
    public void setEmployeeId(int employeeId) { this.employeeId = employeeId; }

    public String getLeaveDate() { return leaveDate; }
    public void setLeaveDate(String leaveDate) { this.leaveDate = leaveDate; }

    public int getHours() { return hours; }
    public void setHours(int hours) { this.hours = hours; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}