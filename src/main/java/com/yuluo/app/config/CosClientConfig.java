package com.yuluo.app.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.region.Region;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 腾讯云COS客户端配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "cos")
public class CosClientConfig {

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

    /**
     * 创建COS客户端
     */
    @Bean
    public COSClient cosClient() {
        // 初始化用户身份信息
        COSCredentials cred = new BasicCOSCredentials(secretId, secretKey);
        // 设置bucket的区域
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        // 生成cos客户端
        return new COSClient(cred, clientConfig);
    }
}
