package com.hotel.dao;

import com.hotel.db.DatabaseManager;
import com.hotel.model.FatigueWeight;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 疲劳权重数据库操作
 */
public class FatigueWeightDao {

    /**
     * 查询某个设施所有班次的疲劳权重，返回 Map<shiftTypeId, weight>
     */
    public Map<Integer, Double> findByFacility(int facilityId) {
        Map<Integer, Double> map = new HashMap<>();
        String sql = "SELECT shift_type_id, weight FROM fatigue_weight WHERE facility_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, facilityId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getInt("shift_type_id"), rs.getDouble("weight"));
                }
            }
        } catch (SQLException e) {
            System.err.println("查询疲劳权重失败：" + e.getMessage());
        }
        return map;
    }

    /**
     * 写入一条疲劳权重（如果该 facility+shift 已存在则更新）
     */
    public boolean save(int facilityId, int shiftTypeId, double weight) {
        String sql = """
                INSERT INTO fatigue_weight (facility_id, shift_type_id, weight)
                VALUES (?, ?, ?)
                ON CONFLICT(facility_id, shift_type_id) DO UPDATE SET weight = excluded.weight
                """;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, facilityId);
            ps.setInt(2, shiftTypeId);
            ps.setDouble(3, weight);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("保存疲劳权重失败：" + e.getMessage());
            return false;
        }
    }

    /**
     * 删除某设施的所有疲劳权重（用于重置）
     */
    public boolean deleteByFacility(int facilityId) {
        String sql = "DELETE FROM fatigue_weight WHERE facility_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, facilityId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("删除疲劳权重失败：" + e.getMessage());
            return false;
        }
    }
}
