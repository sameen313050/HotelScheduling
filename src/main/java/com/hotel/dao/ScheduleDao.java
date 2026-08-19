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
 */
public class ScheduleDao {

    /**
     * 保存一整月的排班（先删掉当月旧数据，再批量插入新数据）
     */
    public boolean saveMonth(String yearMonth, List<Assignment> assignments) {
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // 1. 删除该月旧数据
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM schedule WHERE work_date LIKE ?")) {
                    ps.setString(1, yearMonth + "%");
                    ps.executeUpdate();
                }

                // 2. 批量插入新数据
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

                conn.commit();
                return true;
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            System.err.println("保存排班失败：" + e.getMessage());
            return false;
        }
    }

    /**
     * 从数据库读取一整月的排班
     */
    public List<Assignment> loadMonth(String yearMonth,
                                      EmployeeDao empDao,
                                      FacilityDao facDao,
                                      ShiftTypeDao shiftDao) {
        List<Assignment> list = new ArrayList<>();

        // 先加载引用数据
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
                    if (e != null && f != null && s != null) {
                        list.add(new Assignment(date, e, f, s));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("读取排班失败：" + e.getMessage());
        }
        return list;
    }

    /** 清空某月的排班 */
    public boolean clearMonth(String yearMonth) {
        String sql = "DELETE FROM schedule WHERE work_date LIKE ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, yearMonth + "%");
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("清空排班失败：" + e.getMessage());
            return false;
        }
    }
}