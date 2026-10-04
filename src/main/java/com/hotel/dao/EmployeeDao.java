package com.hotel.dao;

import com.hotel.db.DatabaseManager;
import com.hotel.model.Employee;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 员工数据库操作
 * 包括员工本身的增删改查，以及员工-设施关联的维护
 * 従業員データベースの操作
 * Employee database operations
 * 従業員情報の登録・取得・更新・削除、および従業員と施設の関連付けを管理する
 * Manage employee CRUD operations and employee-facility associations
 */
public class EmployeeDao {

    public List<Employee> findAll() {
        List<Employee> list = new ArrayList<>();
        String sql = "SELECT id, name, employee_type, monthly_hour_limit, weekly_hour_limit, is_active, note FROM employee ORDER BY id";

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                Employee e = new Employee();
                e.setId(rs.getInt("id"));
                e.setName(rs.getString("name"));
                e.setEmployeeType(rs.getString("employee_type"));

                int mhl = rs.getInt("monthly_hour_limit");
                e.setMonthlyHourLimit(rs.wasNull() ? null : mhl);

                int whl = rs.getInt("weekly_hour_limit");
                e.setWeeklyHourLimit(rs.wasNull() ? null : whl);

                e.setActive(rs.getInt("is_active") == 1);
                e.setNote(rs.getString("note"));

                // 加载这个员工的可工作设施列表
                e.setFacilityIds(findFacilityIds(conn, e.getId()));

                list.add(e);
            }

        } catch (SQLException ex) {
            System.err.println("従業員情報の取得に失敗しました：" + ex.getMessage());
            ex.printStackTrace();
        }

        return list;
    }

    /** 查询某员工关联的所有设施 ID
     * 指定した従業員に関連付けられているすべての施設IDを取得する
     * Retrieve all facility IDs associated with the specified employee
     */
    private List<Integer> findFacilityIds(Connection conn, int employeeId) throws SQLException {
        List<Integer> ids = new ArrayList<>();
        String sql = "SELECT facility_id FROM employee_facility WHERE employee_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, employeeId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getInt(1));
                }
            }
        }
        return ids;
    }

    public boolean insert(Employee e) {
        String sql = "INSERT INTO employee (name, employee_type, monthly_hour_limit, weekly_hour_limit, is_active, note) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);  // 开启事务，保证员工+关联同时成功或失败

            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, e.getName());
                ps.setString(2, e.getEmployeeType());

                if (e.getMonthlyHourLimit() == null) ps.setNull(3, Types.INTEGER);
                else ps.setInt(3, e.getMonthlyHourLimit());

                if (e.getWeeklyHourLimit() == null) ps.setNull(4, Types.INTEGER);
                else ps.setInt(4, e.getWeeklyHourLimit());

                ps.setInt(5, e.isActive() ? 1 : 0);
                ps.setString(6, e.getNote());

                int affected = ps.executeUpdate();
                if (affected > 0) {
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (keys.next()) {
                            e.setId(keys.getInt(1));
                        }
                    }
                    // 保存关联设施
                    saveFacilityLinks(conn, e.getId(), e.getFacilityIds());
                    conn.commit();
                    return true;
                }
                conn.rollback();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }

        } catch (SQLException ex) {
            System.err.println("従業員の追加に失敗しました：" + ex.getMessage());
            ex.printStackTrace();
        }

        return false;
    }

    public boolean update(Employee e) {
        String sql = "UPDATE employee SET name = ?, employee_type = ?, monthly_hour_limit = ?, weekly_hour_limit = ?, is_active = ?, note = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, e.getName());
                ps.setString(2, e.getEmployeeType());

                if (e.getMonthlyHourLimit() == null) ps.setNull(3, Types.INTEGER);
                else ps.setInt(3, e.getMonthlyHourLimit());

                if (e.getWeeklyHourLimit() == null) ps.setNull(4, Types.INTEGER);
                else ps.setInt(4, e.getWeeklyHourLimit());

                ps.setInt(5, e.isActive() ? 1 : 0);
                ps.setString(6, e.getNote());
                ps.setInt(7, e.getId());

                if (ps.executeUpdate() > 0) {
                    // 更新关联设施：先删旧的，再插新的
                    deleteFacilityLinks(conn, e.getId());
                    saveFacilityLinks(conn, e.getId(), e.getFacilityIds());
                    conn.commit();
                    return true;
                }
                conn.rollback();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }

        } catch (SQLException ex) {
            System.err.println("従業員情報の更新に失敗しました：" + ex.getMessage());
            ex.printStackTrace();
        }

        return false;
    }

    public boolean delete(int id) {
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // 先删关联
                deleteFacilityLinks(conn, id);

                // 再删员工
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM employee WHERE id = ?")) {
                    ps.setInt(1, id);
                    if (ps.executeUpdate() > 0) {
                        conn.commit();
                        return true;
                    }
                }
                conn.rollback();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }

        } catch (SQLException ex) {
            System.err.println("従業員の削除に失敗しました：" + ex.getMessage());
            ex.printStackTrace();
        }

        return false;
    }

    /** 保存员工-设施关联（批量插入）
     * 従業員と施設の関連付けを保存する（一括挿入）
     * Save employee-facility associations (batch insert)
     */
    private void saveFacilityLinks(Connection conn, int employeeId, List<Integer> facilityIds) throws SQLException {
        if (facilityIds == null || facilityIds.isEmpty()) return;

        String sql = "INSERT INTO employee_facility (employee_id, facility_id) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Integer fid : facilityIds) {
                ps.setInt(1, employeeId);
                ps.setInt(2, fid);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** 删除某员工的所有关联设施 */
    private void deleteFacilityLinks(Connection conn, int employeeId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM employee_facility WHERE employee_id = ?")) {
            ps.setInt(1, employeeId);
            ps.executeUpdate();
        }
    }
}

/*
 * 	•	事务（transaction）：conn.setAutoCommit(false) + commit() / rollback()。意思是”要么都成功，要么都失败”。新增员工时如果保存关联设施失败，员工记录也会回滚，避免出现”员工建好了但关联没建”的脏数据
 * 	•	rs.wasNull()：判断刚读出来的 int 在数据库里是不是 NULL（因为 int 本身不能是 null）
 * 	•	批量插入：addBatch() + executeBatch() 比循环一条条 insert 快很多
 *  • トランザクション（transaction）：conn.setAutoCommit(false) + commit() / rollback()。
 *   「すべて成功するか、すべて失敗するか」を保証する。
 *   従業員の追加時に関連施設の保存が失敗した場合、従業員レコードもロールバックされ、
 *   従業員だけが登録されて関連付けがない不整合なデータを防止する。
 *
 *  • Transaction: conn.setAutoCommit(false) + commit() / rollback().
 *   This ensures that all operations either succeed together or fail together.
 *   If saving facility associations fails while adding an employee, the employee record
 *   is also rolled back, preventing inconsistent data without the required associations.
 *
 *  • rs.wasNull()：直前に読み込んだ int 値がデータベース上で NULL だったかを確認する
 *   （プリミティブ型の int 自体は null にできないため）。
 *
 *  • rs.wasNull(): Check whether the int value just read was NULL in the database,
 *   because the primitive int type itself cannot be null.
 *
 *  • 一括挿入：addBatch() + executeBatch() は、insert を一件ずつループ実行するより高速である。
 *
 *  • Batch insert: addBatch() + executeBatch() is much faster than executing
 *   individual insert statements in a loop.
 */

