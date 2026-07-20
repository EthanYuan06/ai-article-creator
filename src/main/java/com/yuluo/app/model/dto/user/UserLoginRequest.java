package com.yuluo.app.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户登录请求类
 */
@Data
public class UserLoginRequest implements Serializable {
    private String userAccount;
    private String userPassword;
}
