package com.hotel.dao;

import com.hotel.db.DatabaseManager;
import com.hotel.model.Facility;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 设施数据库操作
 */
public class FacilityDao {

    /**
     * 查询所有设施
     */
    public List<Facility> findAll() {
        List<Facility> list = new ArrayList<>();
        String sql = "SELECT id, name, note FROM facility ORDER BY id";

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Facility f = new Facility();
                f.setId(rs.getInt("id"));
                f.setName(rs.getString("name"));
                f.setNote(rs.getString("note"));
                list.add(f);
            }

        } catch (SQLException e) {
            System.err.println("查询设施失败：" + e.getMessage());
            e.printStackTrace();
        }

        return list;
    }

    /**
     * 新增设施
     */
    public boolean insert(Facility facility) {
        String sql = "INSERT INTO facility (name, note) VALUES (?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, facility.getName());
            ps.setString(2, facility.getNote());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        facility.setId(keys.getInt(1));
                    }
                }
                return true;
            }

        } catch (SQLException e) {
            System.err.println("新增设施失败：" + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * 更新设施
     */
    public boolean update(Facility facility) {
        String sql = "UPDATE facility SET name = ?, note = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, facility.getName());
            ps.setString(2, facility.getNote());
            ps.setInt(3, facility.getId());

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("更新设施失败：" + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * 删除设施
     */
    public boolean delete(int id) {
        String sql = "DELETE FROM facility WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("删除设施失败：" + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }
}
