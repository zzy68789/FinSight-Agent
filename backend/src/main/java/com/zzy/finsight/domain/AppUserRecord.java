package com.zzy.finsight.domain;

import java.time.LocalDateTime;

/**
 * 表示持久化的应用用户信息。
 * @param id 主键标识。
 * @param username 用户名。
 * @param email 用户邮箱。
 * @param passwordHash 密码摘要。
 * @param role 用户角色。
 * @param status 当前状态。
 * @param createdAt 创建时间。
 * @param updatedAt 更新时间。
 * @param lastLoginAt 最近登录时间。
 */
public record AppUserRecord(
        long id,
        String username,
        String email,
        String passwordHash,
        String role,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastLoginAt
) {
}
