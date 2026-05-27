package com.training.week1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Week1实训项目主应用程序入口
 * 
 * 本项目演示Java并发编程和Redis分布式技术：
 * - 多线程订单处理（ThreadPoolExecutor + CompletableFuture）
 * - Redis分布式锁（Redisson实现）
 * - Redis秒杀库存扣减（Lua脚本保证原子性）
 * 
 * @author Ace Chen
 * @version 1.0.0
 */
@SpringBootApplication
public class Week1Application {

    /**
     * 应用程序主入口方法
     * 
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(Week1Application.class, args);
    }
}
