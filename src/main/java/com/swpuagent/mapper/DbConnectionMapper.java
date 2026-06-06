package com.swpuagent.mapper;

import com.swpuagent.entity.DbConnection;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface DbConnectionMapper {

    @Select("SELECT id, user_id, name, db_type, host, port, database_name, username, " +
            "is_active, last_tested_at, test_status, created_at, updated_at " +
            "FROM db_connections WHERE user_id = #{userId} AND is_active = 1")
    List<DbConnection> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM db_connections WHERE id = #{id} AND is_active = 1")
    DbConnection findById(@Param("id") Long id);

    @Insert("INSERT INTO db_connections (user_id, name, db_type, host, port, database_name, " +
            "username, encrypted_password) " +
            "VALUES (#{userId}, #{name}, #{dbType}, #{host}, #{port}, #{databaseName}, " +
            "#{username}, #{encryptedPassword})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(DbConnection conn);

    @Update("UPDATE db_connections SET name=#{name}, host=#{host}, port=#{port}, " +
            "database_name=#{databaseName}, username=#{username}, " +
            "encrypted_password=#{encryptedPassword}, updated_at=NOW() WHERE id=#{id}")
    int update(DbConnection conn);

    @Update("UPDATE db_connections SET is_active = 0 WHERE id = #{id}")
    int softDelete(@Param("id") Long id);

    @Update("UPDATE db_connections SET test_status=#{status}, last_tested_at=NOW() WHERE id=#{id}")
    int updateTestStatus(@Param("id") Long id, @Param("status") String status);
}
