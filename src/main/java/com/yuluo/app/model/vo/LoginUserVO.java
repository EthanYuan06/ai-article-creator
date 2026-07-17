package com.yuluo.app.model.vo;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录用户视图
 *
 */
public record LoginUserVO(
        Long id,
        String userAccount,
        String userName,
        String userAvatar,
        String userProfile,
        String userRole,
        LocalDateTime createTime,
        LocalDateTime updateTime
) implements Serializable {}