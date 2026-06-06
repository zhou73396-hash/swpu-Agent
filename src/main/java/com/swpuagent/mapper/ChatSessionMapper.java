package com.swpuagent.mapper;

import com.swpuagent.entity.ChatSession;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ChatSessionMapper {

    @Select("SELECT * FROM chat_sessions WHERE user_id = #{userId} AND status != 'DELETED' ORDER BY updated_at DESC")
    List<ChatSession> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM chat_sessions WHERE id = #{id} AND status != 'DELETED'")
    ChatSession findById(@Param("id") Long id);

    @Insert("INSERT INTO chat_sessions (user_id, db_connection_id, title, status) " +
            "VALUES (#{userId}, #{dbConnectionId}, #{title}, 'ACTIVE')")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatSession session);

    @Update("UPDATE chat_sessions SET status = 'DELETED' WHERE id = #{id}")
    int softDelete(@Param("id") Long id);

    @Update("UPDATE chat_sessions SET message_count = message_count + 1, updated_at = NOW() WHERE id = #{id}")
    int incrementMessageCount(@Param("id") Long id);

    @Update("UPDATE chat_sessions SET title = #{title} WHERE id = #{id}")
    int updateTitle(@Param("id") Long id, @Param("title") String title);
}
