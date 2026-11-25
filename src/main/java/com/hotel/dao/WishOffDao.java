package com.hotel.dao;

import com.hotel.db.DatabaseManager;

import java.sql.*;
import java.util.HashSet;
import java.util.Set;

/**
 * 希望休数据库操作
 * 希望休のデータベース操作
 * Preferred day-off database operations
 */
public class WishOffDao {

    /**
     * 查询某员工某月所有希望休日期
     * 指定した従業員の指定月の希望休日付をすべて取得
     * Retrieves all preferred day-off dates for a given employee in a given month
     *
     * 返回 Set<日期字符串>，比如 {"2026-06-03", "2026-06-15"}
     * 戻り値 Set<日付文字列>、例：{"2026-06-03", "2026-06-15"}
     * Returns Set<date string>, e.g. {"2026-06-03", "2026-06-15"}
     */
    public Set<String> findByEmployeeAndMonth(int employeeId, String yearMonth) {
        Set<String> set = new HashSet<>();
        String sql = "SELECT off_date FROM wish_off WHERE employee_id = ? AND off_date LIKE ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            // 日期前缀匹配（例：2026-08-%）
            // 日付の前方一致（例：2026-08-%）
            // Date prefix match (e.g. 2026-08-%)
            ps.setString(2, yearMonth + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    set.add(rs.getString("off_date"));
                }
            }
        } catch (SQLException e) {
            System.err.println("希望休の取得に失敗しました：" + e.getMessage());
        }
        return set;
    }

    /**
     * 替换某员工某月的希望休：先删除当月旧数据，再插入新数据
     * 指定従業員の指定月の希望休を置き換え：当月の旧データを削除してから新データを登録
     * Replaces the preferred days off for a given employee and month: deletes old data, then inserts new data
     */
    public boolean replaceMonth(int employeeId, String yearMonth, Set<String> newDates) {
        try (Connection conn = DatabaseManager.getConnection()) {
            // 开启事务：保证"删旧+插新"的原子性
            // トランザクション開始：「旧削除＋新挿入」の原子性を保証
            // Begin transaction: ensures atomicity of "delete old + insert new"
            conn.setAutoCommit(false);

            try {
                // 1. 删除该员工当月所有旧记录
                // 1. 当該従業員の当月の旧データをすべて削除
                // 1. Delete all old records for this employee in the target month
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM wish_off WHERE employee_id = ? AND off_date LIKE ?")) {
                    ps.setInt(1, employeeId);
                    ps.setString(2, yearMonth + "%");
                    ps.executeUpdate();
                }

                // 2. 批量插入新数据
                // 2. 新データを一括登録
                // 2. Batch-insert new records
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

                // 提交事务 / コミット / Commit
                conn.commit();
                return true;
            } catch (SQLException ex) {
                // 任意一步失败则回滚
                // いずれかのステップで失敗すればロールバック
                // Roll back if any step fails
                conn.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            System.err.println("希望休の保存に失敗しました：" + e.getMessage());
            return false;
        }
    }
}