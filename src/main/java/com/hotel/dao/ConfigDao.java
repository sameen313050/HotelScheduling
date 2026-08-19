package com.hotel.dao;

import com.hotel.db.DatabaseManager;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * グローバル設定の読み書き（app_config テーブル）
 * Read and write global settings (app_config table)
 *
 * 各種パラメータをキー・バリュー形式で保存する
 * Store various parameters in key-value format
 */

public class ConfigDao {

    /**
 * 設定値を読み込む。存在しない場合はデフォルト値を返す。
 * Read a configuration value. Return the default value if it does not exist.
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
            System.err.println("設定の読み込みに失敗しました：" + e.getMessage());
        }
        return defaultValue;
    }

    /**
 * 浮動小数点数の設定値を読み込む
 * Read a floating-point configuration value
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
 * 整数の設定値を読み込む
 * Read an integer configuration value
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
 * 設定値を書き込む（キーが存在する場合は更新し、存在しない場合は追加する）
 * Write a configuration value (update it if the key exists; otherwise, insert it)
 */
    
    public boolean set(String key, String value) {
        String sql = "INSERT OR REPLACE INTO app_config (config_key, config_value) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, value);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("設定の書き込みに失敗しました：" + e.getMessage());
            return false;
        }
    }

    /**
 * すべての設定を一括で読み込む
 * Read all configuration values in bulk
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
