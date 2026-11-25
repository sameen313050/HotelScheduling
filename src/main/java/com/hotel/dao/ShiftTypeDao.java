package com.hotel.dao;

import com.hotel.db.DatabaseManager;
import com.hotel.model.ShiftType;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 班次类型数据库操作
 * シフト種別のデータベース操作
 * Shift type database operations
 */
public class ShiftTypeDao {

    /**
     * 查询所有班次类型（按 ID 升序）
     * 全シフト種別を取得（ID 昇順）
     * Retrieves all shift types (ordered by ID ascending)
     */
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
                // SQLite 没有 boolean 类型，用 0/1 存储
                // SQLite には boolean 型がないので 0/1 で保存
                // SQLite has no boolean type, so stored as 0/1
                s.setCrossDay(rs.getInt("cross_day") == 1);
                s.setPeriod(rs.getString("period"));
                list.add(s);
            }

        } catch (SQLException e) {
            System.err.println("シフト種別の取得に失敗しました：" + e.getMessage());
            e.printStackTrace();
        }

        return list;
    }

    /**
     * 新增班次类型，成功后将自增 ID 回填到对象
     * シフト種別を新規登録、成功時は自動採番 ID をオブジェクトに反映
     * Inserts a new shift type and writes the auto-generated ID back into the object
     */
    public boolean insert(ShiftType shift) {
        String sql = "INSERT INTO shift_type (name, start_time, end_time, cross_day, period) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             // RETURN_GENERATED_KEYS：INSERT 后能取到自增 ID
             // RETURN_GENERATED_KEYS：INSERT 後に自動採番 ID を取得可能
             // RETURN_GENERATED_KEYS: allows retrieval of the auto-generated ID after INSERT
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, shift.getName());
            ps.setString(2, shift.getStartTime());
            ps.setString(3, shift.getEndTime());
            ps.setInt(4, shift.isCrossDay() ? 1 : 0);
            ps.setString(5, shift.getPeriod());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                // 取回自增 ID 并回填
                // 自動採番 ID を取得して反映
                // Retrieve the auto-generated ID and apply it
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        shift.setId(keys.getInt(1));
                    }
                }
                return true;
            }

        } catch (SQLException e) {
            System.err.println("シフト種別の新規登録に失敗しました：" + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * 更新班次类型
     * シフト種別を更新
     * Updates a shift type
     */
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
            System.err.println("シフト種別の更新に失敗しました：" + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * 删除班次类型
     * シフト種別を削除
     * Deletes a shift type
     */
    public boolean delete(int id) {
        String sql = "DELETE FROM shift_type WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("シフト種別の削除に失敗しました：" + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }
}