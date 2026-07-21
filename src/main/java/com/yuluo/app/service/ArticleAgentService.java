package com.yuluo.app.service;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.yuluo.app.constant.PromptConstant;
import com.yuluo.app.model.dto.article.ArticleState;
import com.yuluo.app.model.enums.ImageMethodEnum;
import com.yuluo.app.model.enums.SseMessageTypeEnum;
import com.yuluo.app.util.GsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.function.Consumer;

@Slf4j
public class ArticleAgentService {

    @Resource
    private DashScopeChatModel chatModel;

    /**
     * 生成文章流程
     * @param state 执行状态
     * @param streamHandler 流式输出器
     */
    public void executeArticleGeneration(ArticleState state, Consumer<String> streamHandler) {
        // 线性流程（后期升级为并行流）
        try {
            // 1. 生成标题
            log.info("智能体1：开始生成标题，taskId = {}", state.getTaskId());
            generateTitle(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT1_COMPLETE.getValue());
            // 2. 生成大纲（流式输出）
            log.info("智能体2：开始生成大纲，taskId = {}", state.getTaskId());
            generateOutline(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.TITLES_GENERATED.getValue());
            // 3. 生成正文（流式输出）
            log.info("智能体3：开始生成正文，taskId = {}", state.getTaskId());
            generateContent(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.OUTLINE_GENERATED.getValue());
            // 4. 配图分析
            log.info("智能体4：开始分析配图需求，taskId = {}", state.getTaskId());
            analyzeImageRequirements(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT4_COMPLETE.getValue());
            // 5. 配图生成（流式输出）
            log.info("智能体5：开始生成配图，taskId = {}", state.getTaskId());
            generateImages(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT5_COMPLETE.getValue());
            // 6. 图文合并：将图片插入正文
            log.info("智能体6：开始合并图文，taskId = {}", state.getTaskId());
            mergeImagesIntoContent(state);
            streamHandler.accept(SseMessageTypeEnum.MERGE_COMPLETE.getValue());
            log.info("文章生成完成，taskId = {}", state.getTaskId());
        } catch (Exception e) {
            log.error("生成文章失败，taskId = {}", state.getTaskId(), e);
            throw new RuntimeException("生成文章失败" + e.getMessage(), e);
        }
    }

    /**
     * 生成标题
     */
    private void generateTitle(ArticleState state) {
        // 将标题嵌入到提示词中
        String prompt = PromptConstant.AGENT1_TITLE_PROMPT.replace("{topic}", state.getTopic());
        // 同步调用LLM
        String content = callLlm(prompt);
        // 将结果转换为JSON，保存到state中
        ArticleState.TitleResult titleResult =
                parseJsonResponse(content, ArticleState.TitleResult.class, "标题");
        state.setTitle(titleResult);
        log.info("智能体1：标题生成完成，mainTitle = {}", titleResult.getMainTitle());
    }

    /**
     * 生成大纲
     */
    private void generateOutline(ArticleState state, Consumer<String> streamHandler) {
        // 将主标题、副标题嵌入提示词
        String prompt = PromptConstant.AGENT2_OUTLINE_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle());
        // 流式调用LLM
        String content = callLlmWithStreaming(prompt, streamHandler, SseMessageTypeEnum.OUTLINE_GENERATED);
        // 将结果转换为JSON，保存到state中
        ArticleState.OutlineResult outlineResult =
                parseJsonResponse(content, ArticleState.OutlineResult.class, "大纲");
        state.setOutline(outlineResult);
        log.info("智能体2：大纲生成完成，总章节数 = {}", outlineResult.getSections().size());
    }

    /**
     * 生成正文
     */
    private void generateContent(ArticleState state, Consumer<String> streamHandler) {
        // 将大纲转为JSON
        String outlineJson = GsonUtils.toJson(state.getOutline().getSections());
        // 嵌入主标题、副标题、大纲内容
        String prompt = PromptConstant.AGENT3_CONTENT_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle())
                .replace("{outline}", outlineJson);
        // 流式调用LLM
        String content = callLlmWithStreaming(prompt, streamHandler, SseMessageTypeEnum.AGENT3_STREAMING);
        state.setContent(content);
        log.info("智能体3：正文生成完成，总字数 = {}", content.length());
    }

    /**
     * 分析配图需求
     */
    private void analyzeImageRequirements(ArticleState state) {
        // 将主标题、正文嵌入提示词
        String prompt = PromptConstant.AGENT4_IMAGE_REQUIREMENTS_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{content}", state.getContent());
        // 调用LLM
        String content = callLlm(prompt);
        // 大模型返回JSON数组，构造成配图需求列表
        List<ArticleState.ImageRequirement> imageRequirements =
                parseJsonListResponse(
                        content,
                        // 绕过Java泛型擦除，确保智能体5方法设计上更简洁易读，并保证类型安全
                        new TypeToken<List<ArticleState.ImageRequirement>>() {},
                        "配图需求");
        state.setImageRequirements(imageRequirements);
        log.info("智能体4：配图需求生成完成，总需求配图数 = {}", imageRequirements.size());
    }

    /**
     * 生成配图
     */
    private void generateImages(ArticleState state, Consumer<String> streamHandler) {

    }

    /**
     * 合并配图和正文
     */
    private void mergeImagesIntoContent(ArticleState state) {
    }

    // region 辅助方法

    /**
     * 调用 LLM（非流式）
     */
    private String callLlm(String prompt) {
        ChatResponse response = chatModel.call(new Prompt(new UserMessage(prompt)));
        return response.getResult().getOutput().getText();
    }

    /**
     * 调用 LLM（流式输出）
     */
    private String callLlmWithStreaming(String prompt, Consumer<String> streamHandler, SseMessageTypeEnum messageType) {
        StringBuilder contentBuilder = new StringBuilder();

        Flux<ChatResponse> streamResponse = chatModel.stream(new Prompt(new UserMessage(prompt)));

        streamResponse
                .doOnNext(response -> {
                    String chunk = response.getResult().getOutput().getText();
                    if (chunk != null && !chunk.isEmpty()) {
                        contentBuilder.append(chunk);
                        streamHandler.accept(messageType.getStreamingPrefix() + chunk);
                    }
                })
                .doOnError(error -> log.error("LLM 流式调用失败, messageType={}", messageType, error))
                .blockLast();

        return contentBuilder.toString();
    }

    /**
     * 解析 JSON 响应
     */
    private <T> T parseJsonResponse(String content, Class<T> clazz, String name) {
        try {
            return GsonUtils.fromJson(content, clazz);
        } catch (JsonSyntaxException e) {
            log.error("{}解析失败, content={}", name, content, e);
            throw new RuntimeException(name + "解析失败");
        }
    }

    /**
     * 解析 JSON 列表响应
     */
    private <T> T parseJsonListResponse(String content, TypeToken<T> typeToken, String name) {
        try {
            return GsonUtils.fromJson(content, typeToken);
        } catch (JsonSyntaxException e) {
            log.error("{}解析失败, content={}", name, content, e);
            throw new RuntimeException(name + "解析失败");
        }
    }

    /**
     * 构建配图结果
     */
    private ArticleState.ImageResult buildImageResult(ArticleState.ImageRequirement requirement,
                                                      String imageUrl,
                                                      ImageMethodEnum method) {
        ArticleState.ImageResult imageResult = new ArticleState.ImageResult();
        imageResult.setPosition(requirement.getPosition());
        imageResult.setUrl(imageUrl);
        imageResult.setMethod(method.getValue());
        imageResult.setKeywords(requirement.getKeywords());
        imageResult.setSectionTitle(requirement.getSectionTitle());
        imageResult.setDescription(requirement.getType());
        return imageResult;
    }

    /**
     * 在章节标题后插入对应图片
     */
    private void insertImageAfterSection(StringBuilder fullContent,
                                         List<ArticleState.ImageResult> images,
                                         String sectionTitle) {
        for (ArticleState.ImageResult image : images) {
            if (image.getPosition() > 1 &&
                    image.getSectionTitle() != null &&
                    sectionTitle.contains(image.getSectionTitle().trim())) {
                fullContent.append("\n![").append(image.getDescription())
                        .append("](").append(image.getUrl()).append(")\n");
                break;
            }
        }
    }

    // endregion

}
