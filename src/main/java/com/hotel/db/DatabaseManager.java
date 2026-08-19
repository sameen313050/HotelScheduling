package com.hotel.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 数据库管理器：负责 SQLite 连接和初始化建表
 */
public class DatabaseManager {

    private static final String DB_FILE = "hotel_schedule.db";
    private static final String DB_URL = "jdbc:sqlite:" + DB_FILE;

    /**
     * 获取数据库连接（每次调用返回新连接，使用完记得 close）
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    /**
     * 初始化数据库：首次启动时创建所有表
     */
    public static void initialize() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. 员工表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS employee (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    employee_type TEXT NOT NULL,
                    monthly_hour_limit INTEGER,
                    weekly_hour_limit INTEGER,
                    is_active INTEGER DEFAULT 1,
                    note TEXT
                )
            """);

            // 2. 设施表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS facility (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    note TEXT
                )
            """);

            // 3. 员工-设施关联表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS employee_facility (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    employee_id INTEGER NOT NULL,
                    facility_id INTEGER NOT NULL,
                    FOREIGN KEY (employee_id) REFERENCES employee(id),
                    FOREIGN KEY (facility_id) REFERENCES facility(id),
                    UNIQUE(employee_id, facility_id)
                )
            """);

            // 4. 班次类型表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS shift_type (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    name TEXT NOT NULL,
                    start_time TEXT NOT NULL,
                    end_time TEXT NOT NULL,
                    cross_day INTEGER DEFAULT 0,
                    period TEXT NOT NULL
                )
            """);

            // 5. 设施-班次配置表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS facility_shift (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    facility_id INTEGER NOT NULL,
                    shift_type_id INTEGER NOT NULL,
                    FOREIGN KEY (facility_id) REFERENCES facility(id),
                    FOREIGN KEY (shift_type_id) REFERENCES shift_type(id),
                    UNIQUE(facility_id, shift_type_id)
                )
            """);

            // 6. 设施需求表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS facility_demand (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    facility_id INTEGER NOT NULL,
                    shift_type_id INTEGER NOT NULL,
                    occupancy_min INTEGER NOT NULL,
                    occupancy_max INTEGER NOT NULL,
                    required_count INTEGER NOT NULL,
                    FOREIGN KEY (facility_id) REFERENCES facility(id),
                    FOREIGN KEY (shift_type_id) REFERENCES shift_type(id)
                )
            """);

            // 7. 每日稼働率表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS occupancy (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    facility_id INTEGER NOT NULL,
                    work_date TEXT NOT NULL,
                    occupancy_rate INTEGER NOT NULL,
                    FOREIGN KEY (facility_id) REFERENCES facility(id),
                    UNIQUE(facility_id, work_date)
                )
            """);

            // 8. 希望休表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS wish_off (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    employee_id INTEGER NOT NULL,
                    off_date TEXT NOT NULL,
                    FOREIGN KEY (employee_id) REFERENCES employee(id),
                    UNIQUE(employee_id, off_date)
                )
            """);

            // 9. 排班结果表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS schedule (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    work_date TEXT NOT NULL,
                    employee_id INTEGER NOT NULL,
                    facility_id INTEGER NOT NULL,
                    shift_type_id INTEGER NOT NULL,
                    FOREIGN KEY (employee_id) REFERENCES employee(id),
                    FOREIGN KEY (facility_id) REFERENCES facility(id),
                    FOREIGN KEY (shift_type_id) REFERENCES shift_type(id),
                    UNIQUE(work_date, employee_id)
                )
            """);

            // 10. 疲劳权重配置表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS fatigue_weight (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    facility_id INTEGER NOT NULL,
                    shift_type_id INTEGER NOT NULL,
                    weight REAL NOT NULL DEFAULT 1.0,
                    FOREIGN KEY (facility_id) REFERENCES facility(id),
                    FOREIGN KEY (shift_type_id) REFERENCES shift_type(id),
                    UNIQUE(facility_id, shift_type_id)
                )
            """);

            // 11. 全局配置表
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS app_config (
                    config_key TEXT PRIMARY KEY,
                    config_value TEXT NOT NULL
                )
            """);
            // ===== 数据库版本升级：为旧数据库补字段 =====
            tryAlterTable(stmt, "ALTER TABLE facility_demand ADD COLUMN metric_type TEXT NOT NULL DEFAULT 'occupancy'");
            tryAlterTable(stmt, "ALTER TABLE occupancy ADD COLUMN ci_count INTEGER");
            tryAlterTable(stmt, "ALTER TABLE occupancy ADD COLUMN co_count INTEGER");
            // 初始化默认配置
            stmt.execute("""
                INSERT OR IGNORE INTO app_config (config_key, config_value) VALUES
                ('fatigue_continuous_3day_multiplier', '1.1'),
                ('fatigue_continuous_5day_multiplier', '1.3'),
                ('fatigue_night_next_day_bonus', '0.5'),
                ('fatigue_short_rest_bonus', '0.5'),
                ('max_continuous_work_days', '5'),
                ('priority_wish_off', '4'),
                ('priority_fatigue_balance', '3'),
                ('priority_shift_balance', '2'),
                ('priority_weekday_holiday_balance', '1')
            """);

            System.out.println("数据库初始化完成");

        } catch (SQLException e) {
            System.err.println("数据库初始化失败：" + e.getMessage());
            e.printStackTrace();
        }
    }
    /**
     * 尝试执行 ALTER TABLE。如果字段已存在会报错，但吞掉不影响主流程。
     */
    private static void tryAlterTable(Statement stmt, String sql) {
        try {
            stmt.execute(sql);
        } catch (SQLException e) {
            // 字段已存在等预期错误，忽略
        }
    }
}
