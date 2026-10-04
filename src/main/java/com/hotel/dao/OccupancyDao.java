package com.hotel.dao;

import com.hotel.db.DatabaseManager;
import com.hotel.model.Occupancy;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 稼働率 / CI / CO 数据库操作
 * 稼働率・CI・COデータベースの操作
 * Occupancy rate, CI, and CO database operations
 */
public class OccupancyDao {

    /**
     * 查询某设施某月所有日期的稼働率
     * 保留：向后兼容，只返回 occupancy_rate
     * 指定した施設・月の全日付の稼働率を取得する
     * Retrieve occupancy rates for all dates in the specified month and facility
     * 後方互換性のために保持し、occupancy_rate のみを返す
     * Retained for backward compatibility; returns only occupancy_rate
     */
    public Map<String, Integer> findByFacilityAndMonth(int facilityId, String yearMonth) {
        Map<String, Integer> map = new HashMap<>();
        String sql = "SELECT work_date, occupancy_rate FROM occupancy WHERE facility_id = ? AND work_date LIKE ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, facilityId);
            ps.setString(2, yearMonth + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("work_date"), rs.getInt("occupancy_rate"));
                }
            }
        } catch (SQLException e) {
            System.err.println("稼働率の取得に失敗しました：" + e.getMessage());
        }
        return map;
    }

    /**
     * 查询某设施某月完整数据（含 CI/CO），返回 Map<日期, Occupancy 对象>
     * 指定した施設・月の完全なデータ（CI／COを含む）を取得し、
     * Map<日付, Occupancy オブジェクト> を返す
     * Retrieve complete data (including CI/CO) for the specified facility and month,
     * returning Map<date, Occupancy object>
     */
    public Map<String, Occupancy> findFullByFacilityAndMonth(int facilityId, String yearMonth) {
        Map<String, Occupancy> map = new HashMap<>();
        String sql = "SELECT work_date, occupancy_rate, ci_count, co_count FROM occupancy WHERE facility_id = ? AND work_date LIKE ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, facilityId);
            ps.setString(2, yearMonth + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Occupancy o = new Occupancy();
                    o.setFacilityId(facilityId);
                    o.setWorkDate(rs.getString("work_date"));
                    o.setOccupancyRate(rs.getInt("occupancy_rate"));

                    int ci = rs.getInt("ci_count");
                    o.setCiCount(rs.wasNull() ? null : ci);
                    int co = rs.getInt("co_count");
                    o.setCoCount(rs.wasNull() ? null : co);

                    map.put(o.getWorkDate(), o);
                }
            }
        } catch (SQLException e) {
            System.err.println("稼働率の完全なデータの取得に失敗しました：" + e.getMessage());
        }
        return map;
    }

    /**
     * 保存某天某设施完整数据（稼働率 + CI + CO）
     * 指定した日付・施設の完全なデータ（稼働率・CI・CO）を保存する
     * Save complete data (occupancy rate, CI, and CO) for the specified date and facility
     */
    public boolean saveFull(int facilityId, String workDate, int rate, Integer ciCount, Integer coCount) {
        String sql = """
                INSERT INTO occupancy (facility_id, work_date, occupancy_rate, ci_count, co_count)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT(facility_id, work_date) DO UPDATE SET
                    occupancy_rate = excluded.occupancy_rate,
                    ci_count = excluded.ci_count,
                    co_count = excluded.co_count
                """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, facilityId);
            ps.setString(2, workDate);
            ps.setInt(3, rate);
            if (ciCount == null) ps.setNull(4, Types.INTEGER);
            else ps.setInt(4, ciCount);
            if (coCount == null) ps.setNull(5, Types.INTEGER);
            else ps.setInt(5, coCount);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("稼働率の保存に失敗しました：" + e.getMessage());
            return false;
        }
    }

    /**
     * 老方法：只保存稼働率（保留以避免破坏现有 OccupancyPanel）
     * 従来のメソッド：稼働率のみを保存する
     * （既存の OccupancyPanel との互換性を維持するために残す）
     * Legacy method: Save only the occupancy rate
     * (retained to preserve compatibility with the existing OccupancyPanel)
     */
    public boolean save(int facilityId, String workDate, int rate) {
        return saveFull(facilityId, workDate, rate, null, null);
    }

    /**
     * 删除某天某设施的数据
     * 指定した日付・施設のデータを削除する
     * Delete data for the specified date and facility
     */
    public boolean deleteOne(int facilityId, String workDate) {
        String sql = "DELETE FROM occupancy WHERE facility_id = ? AND work_date = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, facilityId);
            ps.setString(2, workDate);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("稼働率の削除に失敗しました：" + e.getMessage());
            return false;
        }
    }
}