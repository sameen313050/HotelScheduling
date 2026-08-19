package com.hotel.dao;

import com.hotel.db.DatabaseManager;
import com.hotel.model.Occupancy;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 稼働率 / CI / CO 数据库操作
 */
public class OccupancyDao {

    /**
     * 查询某设施某月所有日期的稼働率
     * 保留：向后兼容，只返回 occupancy_rate
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
            System.err.println("查询稼働率失败：" + e.getMessage());
        }
        return map;
    }

    /**
     * 查询某设施某月完整数据（含 CI/CO），返回 Map<日期, Occupancy 对象>
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
            System.err.println("查询完整稼働率数据失败：" + e.getMessage());
        }
        return map;
    }

    /**
     * 保存某天某设施完整数据（稼働率 + CI + CO）
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
            System.err.println("保存稼働率失败：" + e.getMessage());
            return false;
        }
    }

    /**
     * 老方法：只保存稼働率（保留以避免破坏现有 OccupancyPanel）
     */
    public boolean save(int facilityId, String workDate, int rate) {
        return saveFull(facilityId, workDate, rate, null, null);
    }

    /**
     * 删除某天某设施的数据
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
            System.err.println("删除稼働率失败：" + e.getMessage());
            return false;
        }
    }
}