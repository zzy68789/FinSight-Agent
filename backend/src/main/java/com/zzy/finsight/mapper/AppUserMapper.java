package com.zzy.finsight.mapper;

import com.zzy.finsight.domain.AppUserRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 定义应用用户的 MyBatis 数据访问操作。
 */
@Mapper
public interface AppUserMapper {
    default AppUserRecord create(String username, String email, String passwordHash, String role) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("username", username);
        command.put("email", email);
        command.put("passwordHash", passwordHash);
        command.put("role", role);
        command.put("createdAt", now);
        command.put("updatedAt", now);
        insertUser(command);
        Number id = (Number) command.get("id");
        if (id == null) {
            throw new IllegalStateException("创建用户后未返回主键");
        }
        return new AppUserRecord(id.longValue(), username, email, passwordHash, role, "ACTIVE", now, now, null);
    }

    int insertUser(Map<String, Object> command);

    Optional<AppUserRecord> findByUsername(@Param("username") String username);

    Optional<AppUserRecord> findById(@Param("userId") long userId);

    default void updateLastLoginAt(long userId) {
        LocalDateTime now = LocalDateTime.now();
        updateLoginTime(userId, now);
    }

    int updateLoginTime(@Param("userId") long userId, @Param("now") LocalDateTime now);
}
