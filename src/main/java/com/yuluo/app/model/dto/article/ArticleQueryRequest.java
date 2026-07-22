package com.yuluo.app.model.dto.article;

import com.yuluo.app.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 查询文章请求
 *
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class ArticleQueryRequest extends PageRequest implements Serializable {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 状态
     */
    private String status;

    @Serial
    private static final long serialVersionUID = 1L;
}
