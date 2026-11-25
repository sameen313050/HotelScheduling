package com.hotel.dao;

import com.hotel.db.DatabaseManager;
import com.hotel.model.PaidLeave;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 有休数据库操作
 * 有給休暇のデータベース操作
 * Paid leave database operations
 */
public class PaidLeaveDao {

    /**
     * 查询某员工某月的所有有休记录
     * 指定した従業員の指定月の有給休暇記録をすべて取得
     * Retrieves all paid leave records for a given employee in a given month
     *
     * 返回 Map<日期字符串, 小时数>
     * 戻り値 Map<日付文字列, 時間数>
     * Returns Map<date string, hours>
     */
    public Map<String, Integer> findByEmployeeAndMonth(int employeeId, String yearMonth) {
        Map<String, Integer> map = new HashMap<>();
        String sql = "SELECT leave_date, hours FROM paid_leave WHERE employee_id = ? AND leave_date LIKE ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            // 日期前缀匹配（例：2026-08-%）
            // 日付の前方一致（例：2026-08-%）
            // Date prefix match (e.g. 2026-08-%)
            ps.setString(2, yearMonth + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("leave_date"), rs.getInt("hours"));
                }
            }
        } catch (SQLException e) {
            System.err.println("有給休暇の取得に失敗しました：" + e.getMessage());
        }
        return map;
    }

    /**
     * 保存某天某员工的有休小时数（已存在则更新）
     * 指定した日付の有給休暇時間を保存（既存の場合は更新）
     * Saves the paid leave hours for a specific date (updates if already exists)
     */
    public boolean save(int employeeId, String leaveDate, int hours) {
        // UPSERT 语法：INSERT 时若主键冲突则 UPDATE
        // UPSERT 構文：INSERT 時に主キー衝突があれば UPDATE
        // UPSERT syntax: performs UPDATE on primary-key conflict during INSERT
        String sql = """
                INSERT INTO paid_leave (employee_id, leave_date, hours)
                VALUES (?, ?, ?)
                ON CONFLICT(employee_id, leave_date) DO UPDATE SET hours = excluded.hours
                """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            ps.setString(2, leaveDate);
            ps.setInt(3, hours);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("有給休暇の保存に失敗しました：" + e.getMessage());
            return false;
        }
    }

    /**
     * 删除某员工某天的有休记录
     * 指定した従業員・日付の有給休暇記録を削除
     * Deletes the paid leave record for a specific employee and date
     */
    public boolean delete(int employeeId, String leaveDate) {
        String sql = "DELETE FROM paid_leave WHERE employee_id = ? AND leave_date = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            ps.setString(2, leaveDate);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("有給休暇の削除に失敗しました：" + e.getMessage());
            return false;
        }
    }
}