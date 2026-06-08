package com.swpuagent.service;

import com.swpuagent.common.exception.NotFoundException;
import com.swpuagent.entity.DbConnection;
import com.swpuagent.mapper.DbConnectionMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.*;

@Slf4j
@Service
public class DatabaseConnectionService {

    private final DbConnectionMapper mapper;
    private final String encryptionKey;

    public DatabaseConnectionService(DbConnectionMapper mapper,
                                      @Value("${db.encryption-key}") String encryptionKey) {
        this.mapper = mapper;
        this.encryptionKey = encryptionKey;
    }

    @PostConstruct
    void validate() {
        if (encryptionKey.length() < 16) {
            log.warn("⚠ DB_ENCRYPTION_KEY is weak ({} chars). Set a strong key (≥16) via environment variable for production.", encryptionKey.length());
        } else {
            log.info("DB encryption key validated ({} chars)", encryptionKey.length());
        }
    }

    public List<DbConnection> listConnections(Long userId) {
        return mapper.findByUserId(userId);
    }

    public DbConnection getConnection(Long id) {
        DbConnection conn = mapper.findById(id);
        if (conn == null) throw new NotFoundException("数据库连接不存在");
        return conn;
    }

    public DbConnection create(Long userId, String name, String dbType, String host,
                                int port, String dbName, String username, String password) {
        DbConnection conn = new DbConnection();
        conn.setUserId(userId);
        conn.setName(name);
        conn.setDbType(dbType);
        conn.setHost(host);
        conn.setPort(port);
        conn.setDatabaseName(dbName);
        conn.setUsername(username);
        conn.setEncryptedPassword(encrypt(password));
        mapper.insert(conn);
        log.info("DB connection created: id={}, name={}", conn.getId(), name);
        return conn;
    }

    public DbConnection update(Long id, String name, String host, Integer port,
                                String dbName, String username, String password) {
        DbConnection conn = getConnection(id);
        if (name != null) conn.setName(name);
        if (host != null) conn.setHost(host);
        if (port != null) conn.setPort(port);
        if (dbName != null) conn.setDatabaseName(dbName);
        if (username != null) conn.setUsername(username);
        if (password != null) conn.setEncryptedPassword(encrypt(password));
        mapper.update(conn);
        return conn;
    }

    public void delete(Long id) {
        getConnection(id);
        mapper.softDelete(id);
    }

    /** Test connectivity and return detailed result */
    public Map<String, Object> testConnection(Long id) {
        DbConnection conn = getConnection(id);
        Map<String, Object> result = new HashMap<>();
        result.put("connectionId", id);
        long start = System.currentTimeMillis();

        try (Connection jdbcConn = openJdbcConnection(conn)) {
            long latency = System.currentTimeMillis() - start;
            DatabaseMetaData meta = jdbcConn.getMetaData();
            result.put("status", "SUCCESS");
            result.put("dbVersion", meta.getDatabaseProductVersion());
            result.put("latencyMs", latency);
            mapper.updateTestStatus(id, "SUCCESS");
        } catch (Exception e) {
            result.put("status", "FAILED");
            result.put("error", e.getMessage());
            mapper.updateTestStatus(id, "FAILED");
        }
        return result;
    }

    /** Retrieve schema metadata (tables + columns) */
    public Map<String, Object> getSchema(Long id, String tableName) {
        DbConnection conn = getConnection(id);
        Map<String, Object> result = new HashMap<>();
        result.put("databaseName", conn.getDatabaseName());
        List<Map<String, Object>> tables = new ArrayList<>();

        try (Connection jdbcConn = openJdbcConnection(conn)) {
            DatabaseMetaData meta = jdbcConn.getMetaData();
            String catalog = conn.getDatabaseName();
            try (ResultSet rs = meta.getTables(catalog, null,
                    tableName != null ? tableName : "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    Map<String, Object> tbl = new HashMap<>();
                    String tName = rs.getString("TABLE_NAME");
                    tbl.put("name", tName);
                    tbl.put("columns", getColumns(meta, catalog, tName));
                    tables.add(tbl);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to retrieve schema: " + e.getMessage(), e);
        }
        result.put("tables", tables);
        return result;
    }

    private List<Map<String, String>> getColumns(DatabaseMetaData meta, String catalog, String table) throws Exception {
        List<Map<String, String>> cols = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(catalog, null, table, null)) {
            while (rs.next()) {
                cols.add(Map.of(
                        "name", rs.getString("COLUMN_NAME"),
                        "type", rs.getString("TYPE_NAME"),
                        "nullable", String.valueOf(rs.getInt("NULLABLE") == 1),
                        "key", rs.getString("IS_AUTOINCREMENT") != null &&
                                rs.getString("IS_AUTOINCREMENT").equals("YES") ? "PRI" : ""
                ));
            }
        }
        return cols;
    }

    private Connection openJdbcConnection(DbConnection conn) throws Exception {
        String url = String.format("jdbc:%s://%s:%d/%s?useUnicode=true&characterEncoding=UTF-8",
                conn.getDbType().toLowerCase(), conn.getHost(), conn.getPort(), conn.getDatabaseName());
        return DriverManager.getConnection(url, conn.getUsername(), decrypt(conn.getEncryptedPassword()));
    }

    private String encrypt(String plain) {
        try {
            SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    private String decrypt(String encrypted) {
        try {
            SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes(StandardCharsets.UTF_8), "AES");
            Cipher cipher = Cipher.getInstance("AES");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)));
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
