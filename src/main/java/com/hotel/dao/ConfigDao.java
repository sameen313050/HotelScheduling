package com.hotel.dao;

import com.hotel.db.DatabaseManager;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局配置读写（app_config 表）
 * 用 key-value 形式存储各种参数
 */
public class ConfigDao {

    /**
     * 读取一个配置的值。如果不存在返回默认值。
     */
    public String get(String key, String defaultValue) {
        String sql = "SELECT config_value FROM app_config WHERE config_key = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException e) {
            System.err.println("读取配置失败：" + e.getMessage());
        }
        return defaultValue;
    }

    /**
     * 读取浮点数配置
     */
    public double getDouble(String key, double defaultValue) {
        String value = get(key, null);
        if (value == null) return defaultValue;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 读取整数配置
     */
    public int getInt(String key, int defaultValue) {
        String value = get(key, null);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 写入一个配置（如果 key 已存在则更新，不存在则插入）
     */
    public boolean set(String key, String value) {
        String sql = "INSERT OR REPLACE INTO app_config (config_key, config_value) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("写入配置失败：" + e.getMessage());
            return false;
        }
    }

    /**
     * 批量读取所有配置
     */
    public Map<String, String> getAll() {
        Map<String, String> map = new HashMap<>();
        String sql = "SELECT config_key, config_value FROM app_config";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getString(2));
            }
        } catch (SQLException e) {
            System.err.println("读取所有配置失败：" + e.getMessage());
        }
        return map;
    }
}
