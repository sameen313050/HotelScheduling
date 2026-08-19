package com.hotel.dao;

import com.hotel.db.DatabaseManager;
import com.hotel.model.ShiftType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 班次类型数据库操作
 */
public class ShiftTypeDao {

    public List<ShiftType> findAll() {
        List<ShiftType> list = new ArrayList<>();
        String sql = "SELECT id, name, start_time, end_time, cross_day, period FROM shift_type ORDER BY id";

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                ShiftType s = new ShiftType();
                s.setId(rs.getInt("id"));
                s.setName(rs.getString("name"));
                s.setStartTime(rs.getString("start_time"));
                s.setEndTime(rs.getString("end_time"));
                s.setCrossDay(rs.getInt("cross_day") == 1);
                s.setPeriod(rs.getString("period"));
                list.add(s);
            }

        } catch (SQLException e) {
            System.err.println("查询班次失败：" + e.getMessage());
            e.printStackTrace();
        }

        return list;
    }

    public boolean insert(ShiftType shift) {
        String sql = "INSERT INTO shift_type (name, start_time, end_time, cross_day, period) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, shift.getName());
            ps.setString(2, shift.getStartTime());
            ps.setString(3, shift.getEndTime());
            ps.setInt(4, shift.isCrossDay() ? 1 : 0);
            ps.setString(5, shift.getPeriod());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        shift.setId(keys.getInt(1));
                    }
                }
                return true;
            }

        } catch (SQLException e) {
            System.err.println("新增班次失败：" + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    public boolean update(ShiftType shift) {
        String sql = "UPDATE shift_type SET name = ?, start_time = ?, end_time = ?, cross_day = ?, period = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, shift.getName());
            ps.setString(2, shift.getStartTime());
            ps.setString(3, shift.getEndTime());
            ps.setInt(4, shift.isCrossDay() ? 1 : 0);
            ps.setString(5, shift.getPeriod());
            ps.setInt(6, shift.getId());

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("更新班次失败：" + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    public boolean delete(int id) {
        String sql = "DELETE FROM shift_type WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("删除班次失败：" + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }
}
