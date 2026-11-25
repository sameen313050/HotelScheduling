package com.hotel.dao;

import com.hotel.db.DatabaseManager;
import com.hotel.model.Employee;
import com.hotel.model.Facility;
import com.hotel.model.ShiftType;
import com.hotel.scheduling.Assignment;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 排班结果数据库操作
 * シフト表のデータベース操作
 * Schedule result database operations
 */
public class ScheduleDao {

    /**
     * 保存一整月的排班（先删掉当月旧数据，再批量插入新数据）
     * 月単位でシフト表を保存（当月の旧データを削除してから新データを一括登録）
     * Saves a whole month of schedule (deletes old data first, then batch-inserts new data)
     */
    public boolean saveMonth(String yearMonth, List<Assignment> assignments) {
        try (Connection conn = DatabaseManager.getConnection()) {
            // 开启事务：保证"删旧+插新"的原子性
            // トランザクション開始：「旧削除＋新挿入」の原子性を保証
            // Begin transaction: ensures atomicity of "delete old + insert new"
            conn.setAutoCommit(false);

            try {
                // 1. 删除该月旧数据
                // 1. 当月の旧データを削除
                // 1. Delete old records of the target month
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM schedule WHERE work_date LIKE ?")) {
                    ps.setString(1, yearMonth + "%");
                    ps.executeUpdate();
                }

                // 2. 批量插入新数据
                // 2. 新データを一括登録
                // 2. Batch-insert new records
                if (!assignments.isEmpty()) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO schedule (work_date, employee_id, facility_id, shift_type_id) VALUES (?, ?, ?, ?)")) {
                        for (Assignment a : assignments) {
                            ps.setString(1, a.getDate().toString());
                            ps.setInt(2, a.getEmployee().getId());
                            ps.setInt(3, a.getFacility().getId());
                            ps.setInt(4, a.getShiftType().getId());
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
            System.err.println("シフト表の保存に失敗しました：" + e.getMessage());
            return false;
        }
    }

    /**
     * 从数据库读取一整月的排班
     * データベースから月単位のシフト表を読み込み
     * Loads a whole month of schedule from the database
     */
    public List<Assignment> loadMonth(String yearMonth,
                                      EmployeeDao empDao,
                                      FacilityDao facDao,
                                      ShiftTypeDao shiftDao) {
        List<Assignment> list = new ArrayList<>();

        // 先加载引用数据（员工/设施/班次）构建 ID 到对象的映射
        // 先に参照データ（従業員・施設・シフト種別）を読み込み、ID → オブジェクトのマップを構築
        // Load reference data first (employees/facilities/shift types) and build ID-to-object maps
        List<Employee> emps = empDao.findAll();
        List<Facility> facs = facDao.findAll();
        List<ShiftType> shifts = shiftDao.findAll();

        java.util.Map<Integer, Employee> empMap = new java.util.HashMap<>();
        java.util.Map<Integer, Facility> facMap = new java.util.HashMap<>();
        java.util.Map<Integer, ShiftType> shiftMap = new java.util.HashMap<>();
        for (Employee e : emps) empMap.put(e.getId(), e);
        for (Facility f : facs) facMap.put(f.getId(), f);
        for (ShiftType s : shifts) shiftMap.put(s.getId(), s);

        String sql = "SELECT work_date, employee_id, facility_id, shift_type_id FROM schedule WHERE work_date LIKE ? ORDER BY work_date";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, yearMonth + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LocalDate date = LocalDate.parse(rs.getString("work_date"));
                    Employee e = empMap.get(rs.getInt("employee_id"));
                    Facility f = facMap.get(rs.getInt("facility_id"));
                    ShiftType s = shiftMap.get(rs.getInt("shift_type_id"));
                    // 外键引用的对象可能不存在（如员工被删除），跳过这条记录
                    // 外部キー参照先が存在しない場合（例：従業員が削除済み）はスキップ
                    // Skip the record if any referenced object no longer exists (e.g. deleted employee)
                    if (e != null && f != null && s != null) {
                        list.add(new Assignment(date, e, f, s));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("シフト表の読み込みに失敗しました：" + e.getMessage());
        }
        return list;
    }

    /**
     * 清空某月的排班
     * 指定月のシフト表を全削除
     * Clears all schedule records of a given month
     */
    public boolean clearMonth(String yearMonth) {
        String sql = "DELETE FROM schedule WHERE work_date LIKE ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, yearMonth + "%");
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("シフト表のクリアに失敗しました：" + e.getMessage());
            return false;
        }
    }
}