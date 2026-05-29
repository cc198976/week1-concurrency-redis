package com.training.week1.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Redis测试支持基类（使用docker-compose固定Redis）
 * 
 * 使用场景：
 * 当Testcontainers与Docker Desktop存在兼容性问题时，
 * 使用此配置通过docker-compose启动Redis并连接到固定端口。
 * 
 * 前置条件：
 * 1. Docker Desktop已安装并运行
 * 2. 已通过docker-compose up -d启动Redis服务
 * 3. Redis服务监听在localhost:6379
 * 
 * 优势：
 * - 避免Testcontainers兼容性问题
 * - 配置简单可靠
 * - 与生产环境配置一致
 * 
 * 使用方法：
 * 让测试类继承此类，即可自动获得Redis连接支持。
 * 
 * @author Ace Chen
 */
public abstract class RedisDockerComposeSupport {

    /**
     * 动态注册Redis连接属性到Spring环境
     * 
     * 连接到docker-compose启动的Redis实例：
     * - 主机: localhost
     * - 端口: 6379（docker-compose映射的固定端口）
     * 
     * @param registry 动态属性注册表（Spring Test提供）
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        // 注册Redis主机地址
        registry.add("spring.data.redis.host", () -> "localhost");
        
        // 注册Redis端口
        registry.add("spring.data.redis.port", () -> "6379");
        
        System.out.println("[配置] 使用docker-compose Redis: localhost:6379");
        System.out.println("[提示] 请确保已执行: docker-compose up -d");
    }
}
