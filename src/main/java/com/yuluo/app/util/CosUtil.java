package com.yuluo.app.util;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.model.PutObjectResult;
import com.yuluo.app.config.CosClientConfig;
import com.yuluo.app.exception.BusinessException;
import com.yuluo.app.exception.ErrorCode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 腾讯云COS工具类
 */
@Slf4j
@Component
public class CosUtil {

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private COSClient cosClient;

    /**
     * 上传图片到COS
     *
     * @param file             上传的文件
     * @param uploadPathPrefix 上传路径前缀
     * @return 图片URL
     */
    public String uploadPicture(MultipartFile file, String uploadPathPrefix) {
        // 1. 校验文件
        validatePicture(file);

        // 2. 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename);
        String uuid = RandomUtil.randomString(16);
        String fileName = String.format("%s_%s.%s",
                DateUtil.formatDate(new Date()),
                uuid,
                suffix);
        String uploadPath = uploadPathPrefix + "/" + fileName;

        File tempFile = null;
        try {
            // 3. 创建临时文件
            tempFile = File.createTempFile("upload_", "." + suffix);
            file.transferTo(tempFile);

            // 4. 上传到COS
            PutObjectRequest putObjectRequest = new PutObjectRequest(
                    cosClientConfig.getBucket(),
                    uploadPath,
                    tempFile
            );
            PutObjectResult putObjectResult = cosClient.putObject(putObjectRequest);
            log.info("上传成功，requestId: {}", putObjectResult.getRequestId());

            // 5. 构建返回URL
            return cosClientConfig.getHost() + "/" + uploadPath;

        } catch (Exception e) {
            log.error("上传失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        } finally {
            // 6. 删除临时文件
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * 校验图片文件
     *
     * @param file 上传的文件
     */
    private void validatePicture(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件不能为空");
        }

        // 校验文件大小（5MB）
        long fileSize = file.getSize();
        if (fileSize > 5 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过5MB");
        }

        // 校验文件格式
        String originalFilename = file.getOriginalFilename();
        String suffix = FileUtil.getSuffix(originalFilename).toLowerCase();
        List<String> allowedFormats = Arrays.asList("jpg", "jpeg", "png", "webp");
        if (!allowedFormats.contains(suffix)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不支持的图片格式");
        }
    }
}
