package com.hotel.model;

/**
 * 设施实体类（酒店、分店等工作地点）
 * 施設エンティティクラス（ホテル・支店などの勤務場所）
 * Facility entity class (hotel, branch, or any work location)
 */
public class Facility {

    // 主键 ID / 主キー ID / Primary key ID
    private int id;

    // 设施名（例：東京ホテル、大阪ホテル）
    // 施設名（例：東京ホテル、大阪ホテル）
    // Facility name (e.g. Tokyo Hotel, Osaka Hotel)
    private String name;

    // 备注（如地址、联系人等自由记入）
    // 備考（住所・担当者等の自由記入欄）
    // Free-form notes (e.g. address, contact person)
    private String note;

    /**
     * 默认构造方法（DAO 反射需要）
     * デフォルトコンストラクタ（DAO のリフレクションで必要）
     * Default constructor (required for DAO reflection)
     */
    public Facility() {
    }

    /**
     * 全参构造方法（便于初始化数据）
     * 全引数コンストラクタ（初期化データ用）
     * All-arg constructor (convenient for initialization)
     */
    public Facility(int id, String name, String note) {
        this.id = id;
        this.name = name;
        this.note = note;
    }

    // ===== Getter / Setter =====

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /**
     * 显示用：JComboBox 等控件会调用此方法展示设施名
     * 表示用：JComboBox 等のコントロールがこのメソッドで施設名を表示
     * For display: used by JComboBox etc. to show the facility name
     */
    @Override
    public String toString() {
        return name;
    }
}