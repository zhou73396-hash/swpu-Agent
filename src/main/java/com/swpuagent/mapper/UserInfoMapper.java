package com.swpuagent.mapper;

import com.swpuagent.entity.UserInfo;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserInfoMapper {

    @Select("SELECT id, user_name, email, role, age, country, salary, created_at, updated_at " +
            "FROM user_info WHERE email = #{email}")
    UserInfo findByEmail(@Param("email") String email);

    @Insert("INSERT INTO user_info (user_name, email, role, age, country, salary) " +
            "VALUES (#{userName}, #{email}, #{role}, #{age}, #{country}, #{salary})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserInfo userInfo);

    @Select("SELECT COUNT(*) FROM user_info WHERE email = #{email}")
    int countByEmail(@Param("email") String email);
}
