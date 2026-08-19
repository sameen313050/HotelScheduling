package com.hotel.dao;

import com.hotel.db.DatabaseManager;
import com.hotel.model.Employee;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 员工数据库操作
 * 包括员工本身的增删改查，以及员工-设施关联的维护
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
            System.err.println("查询员工失败：" + ex.getMessage());
            ex.printStackTrace();
        }

        return list;
    }

    /** 查询某员工关联的所有设施 ID */
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
            System.err.println("新增员工失败：" + ex.getMessage());
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
            System.err.println("更新员工失败：" + ex.getMessage());
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
            System.err.println("删除员工失败：" + ex.getMessage());
            ex.printStackTrace();
        }

        return false;
    }

    /** 保存员工-设施关联（批量插入） */
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
 */

