package com.hotel.model;

/**
 * 班次类型实体类
 * シフト種別エンティティクラス
 * Shift type entity class
 *
 * 一个班次 = 一段固定的工作时间段，比如 "早番 8:00-17:00"。
 * 1 シフト = 固定の勤務時間帯、例：「早番 8:00-17:00」。
 * One shift = a fixed working time period, e.g. "Morning shift 8:00-17:00".
 */
public class ShiftType {

    // 主键 ID / 主キー ID / Primary key ID
    private int id;

    // 班次名称，比如 "早番 8-17"
    // シフト名、例：「早番 8-17」
    // Shift name, e.g. "Morning 8-17"
    private String name;

    // 开始时间 "HH:mm"（字符串便于 SQLite 存储和 UI 直接显示）
    // 開始時刻 "HH:mm"（SQLite 保存と UI 表示に便利な文字列型）
    // Start time "HH:mm" (string for SQLite storage and direct UI display)
    private String startTime;

    // 结束时间 "HH:mm"
    // 終了時刻 "HH:mm"
    // End time "HH:mm"
    private String endTime;

    // 是否跨日（例：夜番 21:00-09:00 跨到次日）
    // 日跨ぎフラグ（例：夜番 21:00-09:00 は翌日に跨る）
    // Whether the shift crosses midnight (e.g. night shift 21:00-09:00 spans next day)
    private boolean crossDay;

    // 时段分类（数据库里存日文：朝 / 昼 / 夕 / 夜）
    // 排班算法用 period 判断"夜勤専属只能上夜班"等硬性约束
    // 時間帯区分（DB には日本語で保存：朝 / 昼 / 夕 / 夜）
    // シフトアルゴリズムは period を使って「夜勤専属は夜番のみ」などのハード制約を判定
    // Time slot category (stored in Japanese in DB: 朝 / 昼 / 夕 / 夜)
    // The scheduling algorithm uses period for hard constraints like "night-only staff can only take night shifts"
    private String period;

    /**
     * 默认构造方法（DAO 反射需要）
     * デフォルトコンストラクタ（DAO のリフレクションで必要）
     * Default constructor (required for DAO reflection)
     */
    public ShiftType() {
    }

    // ===== Getter / Setter =====

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

    /**
     * 显示用：JComboBox 等控件会调用此方法展示班次名
     * 表示用：JComboBox 等のコントロールがこのメソッドでシフト名を表示
     * For display: used by JComboBox etc. to show the shift name
     */
    @Override
    public String toString() {
        return name;
    }
}