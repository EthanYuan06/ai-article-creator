package com.yuluo.app.service;

import com.yuluo.app.manager.SseEmitterManager;
import com.yuluo.app.model.dto.article.ArticleState;
import com.yuluo.app.model.enums.ArticleStatusEnum;
import com.yuluo.app.model.enums.SseMessageTypeEnum;
import com.yuluo.app.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class ArticleAsyncService {
    @Resource
    private ArticleAgentService articleAgentService;
    @Resource
    private SseEmitterManager sseEmitterManager;
    @Resource
    private ArticleService articleService;

    /**
     * 异步执行文章生成任务
     *
     * @param taskId 任务ID
     * @param topic  选题
     */
    @Async("articleExecutor")
    public void executeArticleGeneration(String taskId, String topic) {
        log.info("开始执行文章生成任务：选题：{}，任务：{}", topic, taskId);
        try {
            // 更新状态为处理中
            articleService.updateArticleStatus(taskId, ArticleStatusEnum.PROCESSING, null);
            // 创建状态对象
            ArticleState state = new ArticleState();
            state.setTaskId(taskId);
            state.setTopic(topic);
            // 执行智能体编排，通过SSE推送进度
            articleAgentService.executeArticleGeneration(state, message -> {
                handleAgentMessage(taskId, message, state);
            });
            // 保存完整文章到数据库
            articleService.saveArticleContent(taskId, state);
            // 更新状态为已完成
            articleService.updateArticleStatus(taskId, ArticleStatusEnum.COMPLETED, null);
            // 推送完成消息
            sendSseMessage(taskId, SseMessageTypeEnum.ALL_COMPLETE, Map.of("taskId", taskId));
            // 释放连接
            sseEmitterManager.complete(taskId);
            log.info("文章生成任务执行成功：{}", taskId);
        } catch (Exception e) {
            log.error("文章生成任务执行失败：{}", taskId, e);
            articleService.updateArticleStatus(taskId, ArticleStatusEnum.FAILED, e.getMessage());
            // 推送错误消息
            sendSseMessage(taskId, SseMessageTypeEnum.ERROR,
                    Map.of("message", e.getMessage()));
            // 释放连接
            sseEmitterManager.complete(taskId);
        }
    }

    /**
     * 处理智能体消息并推送
     * 确保所有来自智能体的消息都能被正确转换并推送到前端
     */
    private void handleAgentMessage(String taskId, String message, ArticleState state) {
        Map<String, Object> data = buildMessageData(message, state);
        if (data != null) {
            sseEmitterManager.send(taskId, GsonUtils.toJson(data));
        }
    }

    /**
     * 构建消息数据
     * 实现消息格式的透明转换，让上层调用者无需关心消息的具体格式，只需传入原始字符串即可
     */
    private Map<String, Object> buildMessageData(String message, ArticleState state) {
        // 处理流式消息（带冒号分隔符）
        String streamingPrefix2 = SseMessageTypeEnum.AGENT2_STREAMING.getStreamingPrefix();
        String streamingPrefix3 = SseMessageTypeEnum.AGENT3_STREAMING.getStreamingPrefix();
        String imageCompletePrefix = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix();

        if (message.startsWith(streamingPrefix2)) {
            return buildStreamingData(SseMessageTypeEnum.AGENT2_STREAMING,
                    message.substring(streamingPrefix2.length()));
        }

        if (message.startsWith(streamingPrefix3)) {
            return buildStreamingData(SseMessageTypeEnum.AGENT3_STREAMING,
                    message.substring(streamingPrefix3.length()));
        }

        if (message.startsWith(imageCompletePrefix)) {
            String imageJson = message.substring(imageCompletePrefix.length());
            return buildImageCompleteData(imageJson);
        }

        // 处理完成消息（枚举值）
        return buildCompleteMessageData(message, state);
    }

    /**
     * 构建流式输出数据
     * 为前端提供统一的流式消息格式，前端可根据 type 字段决定如何渲染（如打字机效果显示）
     */
    private Map<String, Object> buildStreamingData(SseMessageTypeEnum type, String content) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", type.getValue());
        data.put("content", content);
        return data;
    }

    /**
     * 构建图片完成数据
     * 将序列化的图片数据还原为结构化对象，前端可直接使用图片 URL 和元数据，无需二次解析
     */
    private Map<String, Object> buildImageCompleteData(String imageJson) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", SseMessageTypeEnum.IMAGE_COMPLETE.getValue());
        data.put("image", GsonUtils.fromJson(imageJson, ArticleState.ImageResult.class));
        return data;
    }

    /**
     * 构建完成消息数据
     * 实现阶段性成果的即时反馈，让用户在长耗时任务中能看到明确的进度节点，提升用户体验。
     * 同时，每个阶段的数据都可供前端缓存或展示，支持断点续传或局部刷新。
     */
    private Map<String, Object> buildCompleteMessageData(String message, ArticleState state) {
        Map<String, Object> data = new HashMap<>();

        if (SseMessageTypeEnum.AGENT1_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT1_COMPLETE.getValue());
            data.put("title", state.getTitle());
        } else if (SseMessageTypeEnum.AGENT2_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT2_COMPLETE.getValue());
            data.put("outline", state.getOutline().getSections());
        } else if (SseMessageTypeEnum.AGENT3_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT3_COMPLETE.getValue());
        } else if (SseMessageTypeEnum.AGENT4_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT4_COMPLETE.getValue());
            data.put("imageRequirements", state.getImageRequirements());
        } else if (SseMessageTypeEnum.AGENT5_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.AGENT5_COMPLETE.getValue());
            data.put("images", state.getImages());
        } else if (SseMessageTypeEnum.MERGE_COMPLETE.getValue().equals(message)) {
            data.put("type", SseMessageTypeEnum.MERGE_COMPLETE.getValue());
            data.put("fullContent", state.getFullContent());
        } else {
            return null;
        }

        return data;
    }

    /**
     * 发送 SSE 消息
     * 提供标准化的消息发送接口，避免在多处重复编写 SSE 发送逻辑，降低维护成本。
     * 同时，通过统一的 JSON 序列化确保所有消息格式一致，便于前端统一处理。
     */
    private void sendSseMessage(String taskId, SseMessageTypeEnum type, Map<String, Object> additionalData) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", type.getValue());
        data.putAll(additionalData);
        sseEmitterManager.send(taskId, GsonUtils.toJson(data));
    }

}
























