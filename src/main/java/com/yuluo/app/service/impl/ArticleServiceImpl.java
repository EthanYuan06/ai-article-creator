package com.yuluo.app.service.impl;

import cn.hutool.core.util.IdUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.yuluo.app.exception.BusinessException;
import com.yuluo.app.exception.ErrorCode;
import com.yuluo.app.exception.ThrowUtils;
import com.yuluo.app.mapper.ArticleMapper;
import com.yuluo.app.model.dto.article.ArticleQueryRequest;
import com.yuluo.app.model.dto.article.ArticleState;
import com.yuluo.app.model.entity.Article;
import com.yuluo.app.model.entity.User;
import com.yuluo.app.model.enums.ArticleStatusEnum;
import com.yuluo.app.model.vo.ArticleVO;
import com.yuluo.app.service.ArticleService;
import com.yuluo.app.utils.GsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.yuluo.app.constant.UserConstant.ADMIN_ROLE;

@Service
@Slf4j
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    /**
     * 创建文章任务
     *
     * @param topic     选题
     * @param loginUser 用户
     * @return 任务ID
     */
    @Override
    public String createArticleTask(String topic, User loginUser) {
        // 生成任务ID
        String taskId = IdUtil.simpleUUID();
        // 创建文章记录
        Article article = new Article();
        article.setTaskId(taskId);
        article.setUserId(loginUser.getId());
        article.setTopic(topic);
        article.setStatus(ArticleStatusEnum.PENDING.name());
        article.setCreateTime(LocalDateTime.now());
        // 记录保存到数据库
        this.save(article);
        log.info("创建文章任务成功，任务ID：{}，userI：{}d", taskId, loginUser.getId());
        return taskId;
    }

    /**
     * 根据任务ID查询文章信息
     *
     * @param taskId 任务ID
     * @return 文章信息
     */
    @Override
    public Article getByTaskId(String taskId) {
        return this.getOne(QueryWrapper.create().eq("taskId", taskId));
    }

    /**
     * 更新文章状态
     *
     * @param taskId       任务ID
     * @param status       文章状态
     * @param errorMessage 错误信息
     */
    @Override
    public void updateArticleStatus(String taskId, ArticleStatusEnum status, String errorMessage) {
        Article article = getByTaskId(taskId);
        if (article == null) {
            log.error("更新文章状态时，文章记录不存在，任务ID：{}", taskId);
            return;
        }
        article.setStatus(status.getValue());
        article.setErrorMessage(errorMessage);
        this.updateById(article);
        log.info("文章状态已更新，任务ID：{}，状态：{}", taskId, status.getValue());
    }

    @Override
    public void saveArticleContent(String taskId, ArticleState state) {
        Article article = getByTaskId(taskId);
        if (article == null) {
            log.error("保存文章内容时，文章记录不存在，任务ID：{}", taskId);
            return;
        }
        article.setMainTitle(state.getTitle().getMainTitle());
        article.setSubTitle(state.getTitle().getSubTitle());
        article.setOutline(GsonUtils.toJson(state.getOutline().getSections()));
        article.setContent(state.getContent());
        article.setFullContent(state.getFullContent());
        // 保存封面图URL
        if (state.getImages() != null && !state.getImages().isEmpty()) {
            // 获取配图列表，流式处理：找出Position为1的图片作为封面图，否则返回null
            ArticleState.ImageResult cover = state.getImages().stream()
                    .filter(img -> img.getPosition() != null && img.getPosition() == 1)
                    .findFirst()
                    .orElse(null);
            if (cover != null && cover.getUrl() != null) {
                // 单独把封面图保存到一个字段，避免多次解析image数组
                article.setCoverImage(cover.getUrl());
            }
        }
        article.setImages(GsonUtils.toJson(state.getImages()));
        article.setCompletedTime(LocalDateTime.now());
        this.updateById(article);
        log.info("文章内容已保存，任务ID：{}", taskId);
    }

    /**
     * 获取文章详情
     *
     * @param taskId       任务ID
     * @param loginUser    当前用户
     * @return 文章详情
     */
    @Override
    public ArticleVO getArticleDetail(String taskId, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");

        // 校验权限：只能查看自己的文章（管理员除外）
        checkArticlePermission(article, loginUser);

        return ArticleVO.objToVo(article);
    }

    /**
     * 分页查询文章列表
     *
     * @param request 查询参数
     * @param loginUser 当前用户
     * @return 文章列表
     */
    @Override
    public Page<ArticleVO> listArticleByPage(ArticleQueryRequest request, User loginUser) {
        long current = request.getCurrent();
        long size = request.getPageSize();

        // 构建查询条件
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("isDelete", 0)
                .orderBy("createTime", false);

        // 非管理员只能查看自己的文章
        if (!ADMIN_ROLE.equals(loginUser.getUserRole())) {
            // 普通用户只能查自己的文章
            queryWrapper.eq("userId", loginUser.getId());
        } else if (request.getUserId() != null) {
            // 管理员查询指定用户文章
            queryWrapper.eq("userId", request.getUserId());
        }

        // 按状态筛选
        if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
            queryWrapper.eq("status", request.getStatus());
        }

        // 分页查询
        Page<Article> articlePage = this.page(new Page<>(current, size), queryWrapper);

        // 转换为 VO
        return convertToVOPage(articlePage);
    }

    /**
     * 删除文章
     *
     * @param id         文章ID
     * @param loginUser  当前用户
     * @return 是否成功
     */
    @Override
    public boolean deleteArticle(Long id, User loginUser) {
        Article article = this.getById(id);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR);

        // 校验权限：只能删除自己的文章（管理员除外）
        checkArticlePermission(article, loginUser);

        // 逻辑删除
        return this.removeById(id);
    }

    /**
     * 校验文章权限
     *
     * @param article   文章
     * @param loginUser 当前用户
     */
    private void checkArticlePermission(Article article, User loginUser) {
        if (!article.getUserId().equals(loginUser.getId()) &&
                !ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

    /**
     * 将文章分页结果转换为 VO 分页
     *
     * @param articlePage 文章分页
     * @return VO 分页
     */
    private Page<ArticleVO> convertToVOPage(Page<Article> articlePage) {
        Page<ArticleVO> articleVOPage = new Page<>();
        articleVOPage.setPageNumber(articlePage.getPageNumber());
        articleVOPage.setPageSize(articlePage.getPageSize());
        articleVOPage.setTotalRow(articlePage.getTotalRow());

        List<ArticleVO> articleVOList = articlePage.getRecords().stream()
                .map(ArticleVO::objToVo)
                .collect(Collectors.toList());
        articleVOPage.setRecords(articleVOList);

        return articleVOPage;
    }
}
