package com.swpuagent.mapper;

import com.swpuagent.entity.ChatMessage;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ChatMessageMapper {

    @Select("SELECT * FROM chat_messages WHERE session_id = #{sessionId} ORDER BY created_at ASC")
    List<ChatMessage> findBySessionId(@Param("sessionId") Long sessionId);

    @Select("SELECT * FROM chat_messages WHERE session_id = #{sessionId} ORDER BY created_at DESC LIMIT #{limit}")
    List<ChatMessage> findRecentBySessionId(@Param("sessionId") Long sessionId, @Param("limit") int limit);

    @Insert("INSERT INTO chat_messages (session_id, role, content, message_type, metadata, token_count) " +
            "VALUES (#{sessionId}, #{role}, #{content}, #{messageType}, #{metadata}, #{tokenCount})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatMessage message);
}
