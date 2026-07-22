package com.yuluo.app.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import com.yuluo.app.model.dto.article.ArticleQueryRequest;
import com.yuluo.app.model.dto.article.ArticleState;
import com.yuluo.app.model.entity.Article;
import com.yuluo.app.model.entity.User;
import com.yuluo.app.model.enums.ArticleStatusEnum;
import com.yuluo.app.model.vo.ArticleVO;

/**
 * 文章服务接口
 * 负责文章内容增删改查
 * 异步任务的状态更新和内容持久化
 */
public interface ArticleService extends IService<Article> {
    String createArticleTask(String topic, User loginUser);

    Article getByTaskId(String taskId);

    void updateArticleStatus(String taskId, ArticleStatusEnum status, String errorMessage);

    void saveArticleContent(String taskId, ArticleState state);

    ArticleVO getArticleDetail(String taskId, User loginUser);

    Page<ArticleVO> listArticleByPage(ArticleQueryRequest request, User loginUser);

    boolean deleteArticle(Long id, User loginUser);
}
