package com.yuluo.app.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池配置
 */
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "articleExecutor")
    public Executor articleExecutor(){
        // 创建线程池：ThreadPoolTaskExecutor是Spring对JDK提供ThreadPoolExecutor的高级封装
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：空闲不回收，常态下最多能同时处理5个文章生成任务
        // 假设日均1000篇文章，每分钟处理10-30篇文章，峰值QPS≈0.5，5个线程绰绰有余
        // 每个线程占用内存+CPU上下文切换成本，开太多会资源浪费
        executor.setCorePoolSize(5);
        // 最大线程数：队列满时，会新增线程到最大值，高峰期最多能同时处理10个文章生成任务
        // 线程过多会导致CPU频繁切换上下文，性能受影响；其次是一个线程1MB，内存消耗也大
        executor.setMaxPoolSize(10);
        // 队列长度：最多缓存100个任务；队列长度短容易触发拒绝策略，长度长积压任务太多影响体验
        executor.setQueueCapacity(100);
        // 拒绝策略：由Tomcat线程阻塞同步处理，避免任务丢失（默认是AbortPolicy，直接抛异常）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 线程名称前缀：每一条日志前面都会有，默认是ThreadPoolTaskExecutor-1（为了好识别）
        executor.setThreadNamePrefix("article-async-");
        // 等待所有任务完成后，关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 线程池关闭后，允许再继续执行任务60s，没执行完会强制中断，执行完自行关闭
        executor.setAwaitTerminationSeconds(60);
        // 初始化线程池
        executor.initialize();
        return executor;
    }
}
