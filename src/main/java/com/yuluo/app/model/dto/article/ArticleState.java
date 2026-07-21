package com.yuluo.app.model.dto.article;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 文章执行状态类
 */
@Data
public class ArticleState implements Serializable {

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 选题
     */
    private String topic;

    /**
     * 标题结果（智能体 1输出）
     */
    private TitleResult title;

    /**
     * 大纲结果（智能体 2输出）
     */
    private OutlineResult outline;

    /**
     * 正文内容（智能体 3输出）
     */
    private String content;

    /**
     * 配图需求列表（智能体 4输出）
     */
    private List<ImageRequirement> imageRequirements;

    /**
     * 封面图 URL（单独存储，同时 images 列表中的 position=1 也是封面图）
     */
    private String coverImage;

    /**
     * 配图结果列表（智能体5输出）
     */
    private List<ImageResult> images;

    /**
     * 完整图文内容（合成后）
     */
    private String fullContent;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 阶段 1：标题结果
     */
    @Data
    public static class TitleResult implements Serializable {
        private String mainTitle;
        private String subTitle;
    }

    /**
     * 阶段 2：大纲结果
     */
    @Data
    public static class OutlineResult implements Serializable {
        private List<OutlineSection> sections;
    }

    /**
     * 阶段 3：大纲章节
     */
    @Data
    public static class OutlineSection implements Serializable {
        private Integer section;
        private String title;
        private List<String> points;
    }

    /**
     * 阶段 4：配图需求分析
     */
    @Data
    public static class ImageRequirement implements Serializable {
        private Integer position;
        private String type;
        private String sectionTitle;
        private String keywords;
    }

    /**
     * 阶段 5：配图结果
     */
    @Data
    public static class ImageResult implements Serializable {
        private Integer position;
        private String url;
        private String method;
        private String keywords;
        private String sectionTitle;
        private String description;
    }
}
