package com.hotel.dao;

import com.hotel.db.DatabaseManager;
import com.hotel.model.FatigueWeight;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 疲労度重みデータベースの操作
 * Fatigue weight database operations
 */

public class FatigueWeightDao {

    /**
    * 指定した施設の全シフトの疲労度重みを取得し、Map<shiftTypeId, weight> を返す
    * Retrieve the fatigue weights for all shifts at the specified facility and return Map<shiftTypeId, weight>
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
            System.err.println("疲労度重みの取得に失敗しました：" + e.getMessage());
        }
        return map;
    }

    /**
     * 疲労度重みを1件保存する（同じ施設とシフトの組み合わせが存在する場合は更新する）
     * Save a fatigue weight entry (update it if the same facility-shift combination already exists)
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
            System.err.println("疲労度重みの保存に失敗しました：" + e.getMessage());
            return false;
        }
    }

    /**
     * 指定した施設のすべての疲労度重みを削除する（リセット用）
     * Delete all fatigue weights for the specified facility (used for resetting)
     */
    
    public boolean deleteByFacility(int facilityId) {
        String sql = "DELETE FROM fatigue_weight WHERE facility_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, facilityId);
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("疲労度重みの削除に失敗しました：" + e.getMessage());
            return false;
        }
    }
}
