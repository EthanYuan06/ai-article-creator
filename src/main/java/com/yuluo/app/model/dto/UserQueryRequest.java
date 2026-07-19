package com.yuluo.app.model.dto;

import com.yuluo.app.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户查询请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UserQueryRequest extends PageRequest {

    /**
     * 用户昵称（模糊查询）
     */
    private String userName;

    /**
     * 用户角色
     */
    private String userRole;
}
