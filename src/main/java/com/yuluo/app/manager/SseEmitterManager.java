package com.yuluo.app.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.yuluo.app.constant.ArticleConstant.*;

/**
 * SSE连接管理器
 * 全局共享一个连接
 */
@Component
@Slf4j
public class SseEmitterManager {

    /**
     * 存储所有 SseEmitter
     * 需要用 taskId 找到连接，适合 Map 结构
     * 考虑到高并发场景，使用 ConcurrentHashMap 保证并发安全
     */
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    /**
     * 创建 SseEmitter
     *
     * @param taskId 任务ID
     * @return 连接
     */
    public SseEmitter createEmitter(String taskId) {
        // 创建连接实例，设置超时时间
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        // 超时回调
        emitter.onTimeout(() -> {
            log.info("SSE 连接超时，taskId = {}", taskId);
            emitterMap.remove(taskId);
        });
        // 完成回调
        emitter.onCompletion(() -> {
            log.info("SSE 连接完成，taskId = {}", taskId);
            emitterMap.remove(taskId);
        });
        // 错误回调
        emitter.onError(e -> {
            log.error("SSE 错误，taskId = {}", taskId, e);
            emitterMap.remove(taskId);
        });

        emitterMap.put(taskId, emitter);
        log.info("SSE 连接创建，taskId = {}", taskId);
        return emitter;
    }

    /**
     * 发送消息
     *
     * @param taskId  任务ID
     * @param message 消息
     */
    public void send(String taskId, String message) {
        // 根据taskId获取当前连接
        SseEmitter emitter = emitterMap.get(taskId);
        if (emitter == null) {
            log.warn("发送消息时，SSE 连接不存在，taskId = {}", taskId);
            return;
        }
        try {
            // 发送消息
            emitter.send(SseEmitter.event()
                    .data(message)
                    .reconnectTime(SSE_RECONNECT_TIME_MS));
            log.debug("SSE 发送消息成功，taskId = {}, message = {}", taskId, message);
        } catch (Exception e) {
            log.error("SSE 发送消息失败，taskId = {}", taskId, e);
            emitterMap.remove(taskId);
        }
    }

    /**
     * 完成连接并释放
     *
     * @param taskId 任务ID
     */
    public void complete(String taskId) {
        // 根据taskId获取当前连接
        SseEmitter emitter = emitterMap.get(taskId);
        if (emitter == null) {
            log.warn("连接释放时，SSE 连接不存在，taskId = {}", taskId);
            return;
        }
        try {
            // 完成连接
            emitter.complete();
            log.debug("SSE 连接释放成功，taskId = {}", taskId);
        } catch (Exception e) {
            log.error("SSE 连接释放失败，taskId = {}", taskId, e);
        }finally {
            emitterMap.remove(taskId);
        }
    }

    /**
     * 判断连接是否存在
     *
     * @param taskId 任务ID
     * @return 是否存在
     */
    public boolean exists(String taskId) {
        return emitterMap.containsKey(taskId);
    }
}
