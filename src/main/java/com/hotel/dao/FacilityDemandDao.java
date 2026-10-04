package com.hotel.dao;

import com.hotel.db.DatabaseManager;
import com.hotel.model.FacilityDemand;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 设施需求数据库操作
 * 施設需要データベースの操作
 * Facility demand database operations
 */
public class FacilityDemandDao {

    public List<FacilityDemand> findByFacility(int facilityId) {
        List<FacilityDemand> list = new ArrayList<>();
        String sql = """
                SELECT fd.id, fd.facility_id, fd.shift_type_id, fd.occupancy_min, fd.occupancy_max,
                       fd.required_count, fd.metric_type, st.name AS shift_name
                FROM facility_demand fd
                JOIN shift_type st ON fd.shift_type_id = st.id
                WHERE fd.facility_id = ?
                ORDER BY fd.shift_type_id, fd.metric_type, fd.occupancy_min
                """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, facilityId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    FacilityDemand d = new FacilityDemand();
                    d.setId(rs.getInt("id"));
                    d.setFacilityId(rs.getInt("facility_id"));
                    d.setShiftTypeId(rs.getInt("shift_type_id"));
                    d.setOccupancyMin(rs.getInt("occupancy_min"));
                    d.setOccupancyMax(rs.getInt("occupancy_max"));
                    d.setRequiredCount(rs.getInt("required_count"));
                    d.setMetricType(rs.getString("metric_type"));
                    d.setShiftTypeName(rs.getString("shift_name"));
                    list.add(d);
                }
            }
        } catch (SQLException e) {
            System.err.println("施設需要情報の取得に失敗しました：" + e.getMessage());
            e.printStackTrace();
        }

        return list;
    }

    public boolean insert(FacilityDemand d) {
        String sql = "INSERT INTO facility_demand (facility_id, shift_type_id, occupancy_min, occupancy_max, required_count, metric_type) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, d.getFacilityId());
            ps.setInt(2, d.getShiftTypeId());
            ps.setInt(3, d.getOccupancyMin());
            ps.setInt(4, d.getOccupancyMax());
            ps.setInt(5, d.getRequiredCount());
            ps.setString(6, d.getMetricType());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) d.setId(keys.getInt(1));
                }
                return true;
            }
        } catch (SQLException e) {
            System.err.println("施設需要情報の追加に失敗しました：" + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public boolean update(FacilityDemand d) {
        String sql = "UPDATE facility_demand SET shift_type_id = ?, occupancy_min = ?, occupancy_max = ?, required_count = ?, metric_type = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, d.getShiftTypeId());
            ps.setInt(2, d.getOccupancyMin());
            ps.setInt(3, d.getOccupancyMax());
            ps.setInt(4, d.getRequiredCount());
            ps.setString(5, d.getMetricType());
            ps.setInt(6, d.getId());

            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("施設需要情報の更新に失敗しました：" + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    public boolean delete(int id) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM facility_demand WHERE id = ?")) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("施設需要情報の削除に失敗しました：" + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
}