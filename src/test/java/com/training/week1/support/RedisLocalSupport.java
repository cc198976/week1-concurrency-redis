package com.training.week1.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Redis测试支持基类（使用本地Redis）
 * 
 * 使用场景：
 * 当Docker环境不可用或网络问题导致无法下载镜像时，
 * 使用此配置连接到本地安装的Redis服务。
 * 
 * 前置条件：
 * 1. 本地已安装Redis
 * 2. Redis服务正在运行（redis-server）
 * 3. Redis监听在localhost:6379（默认配置）
 * 
 * 使用方法：
 * 让测试类继承此类，即可自动获得Redis连接支持。
 * 
 * 注意：
 * - 此方式适合快速开发和本地测试
 * - 生产环境或CI/CD建议使用Docker容器化方案
 * 
 * @author Ace Chen
 */
public abstract class RedisLocalSupport {

    /**
     * 动态注册Redis连接属性到Spring环境
     * 
     * 连接到本地Redis实例：
     * - 主机: localhost
     * - 端口: 6379（Redis默认端口）
     * 
     * @param registry 动态属性注册表（Spring Test提供）
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        // 注册Redis主机地址
        registry.add("spring.data.redis.host", () -> "localhost");
        
        // 注册Redis端口
        registry.add("spring.data.redis.port", () -> "6379");
        
        System.out.println("[配置] 使用本地Redis: localhost:6379");
        System.out.println("[提示] 请确保本地Redis服务已启动（redis-server）");
    }
}
