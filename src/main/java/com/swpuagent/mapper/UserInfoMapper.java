package com.swpuagent.mapper;

import com.swpuagent.entity.UserInfo;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserInfoMapper {

    @Select("SELECT id, user_name, email, role, password " +
            "FROM user_info WHERE email = #{email}")
    UserInfo findByEmail(@Param("email") String email);

    @Insert("INSERT INTO user_info (user_name, email, role) " +
            "VALUES (#{userName}, #{email}, #{role})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserInfo userInfo);

    @Select("SELECT COUNT(*) FROM user_info WHERE email = #{email}")
    int countByEmail(@Param("email") String email);

    @Select("SELECT id, user_name, email, role, password " +
            "FROM user_info WHERE id = #{id}")
    UserInfo findById(@Param("id") Long id);

    @Update("UPDATE user_info SET user_name=#{userName}, email=#{email} WHERE id=#{id}")
    int update(UserInfo userInfo);
}
