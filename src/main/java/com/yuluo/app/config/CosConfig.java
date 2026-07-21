package com.yuluo.app.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云COS配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "cos")
public class CosConfig {

    /**
     * 密钥ID
     */
    private String secretId;

    /**
     * 密钥Key
     */
    private String secretKey;

    /**
     * 存储区域
     */
    private String region;

    /**
     * 存储桶名称
     */
    private String bucket;

    /**
     * CDN域名
     */
    private String host;
}
