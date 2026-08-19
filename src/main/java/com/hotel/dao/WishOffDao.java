package com.hotel.dao;

import com.hotel.db.DatabaseManager;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;

/**
 * 希望休数据库操作
 */
public class WishOffDao {

    /**
     * 查询某员工某月所有希望休日期
     * 返回 Set<日期字符串>，比如 {"2026-06-03", "2026-06-15"}
     */
    public Set<String> findByEmployeeAndMonth(int employeeId, String yearMonth) {
        Set<String> set = new HashSet<>();
        String sql = "SELECT off_date FROM wish_off WHERE employee_id = ? AND off_date LIKE ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            ps.setString(2, yearMonth + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    set.add(rs.getString("off_date"));
                }
            }
        } catch (SQLException e) {
            System.err.println("查询希望休失败：" + e.getMessage());
        }
        return set;
    }

    /**
     * 替换某员工某月的希望休：先删除当月旧数据，再插入新数据
     */
    public boolean replaceMonth(int employeeId, String yearMonth, Set<String> newDates) {
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // 1. 删除该员工当月所有旧记录
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM wish_off WHERE employee_id = ? AND off_date LIKE ?")) {
                    ps.setInt(1, employeeId);
                    ps.setString(2, yearMonth + "%");
                    ps.executeUpdate();
                }

                // 2. 插入新数据
                if (!newDates.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO wish_off (employee_id, off_date) VALUES (?, ?)")) {
                        for (String date : newDates) {
                            ps.setInt(1, employeeId);
                            ps.setString(2, date);
                            ps.addBatch();
                        }
                        ps.executeBatch();
                    }
                }

                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            System.err.println("保存希望休失败：" + e.getMessage());
            return false;
        }
    }
}
