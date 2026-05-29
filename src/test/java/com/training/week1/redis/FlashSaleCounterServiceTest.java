package com.training.week1.redis;

import com.training.week1.support.RedisDockerComposeSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 秒杀库存计数器服务集成测试
 * 
 * 测试目标：
 * 1. 验证库存扣减的原子性（不会出现超卖）
 * 2. 验证库存不足时的正确处理
 * 3. 验证高并发场景下的数据一致性
 * 
 * 核心测试点：
 * - Lua脚本保证检查和扣减的原子性
 * - 50个线程同时扣减100件商品，最终库存应为0
 * - 成功扣减次数应该正好等于初始库存数量
 * 
 * @author Ace Chen
 */
@SpringBootTest
class FlashSaleCounterServiceTest extends RedisDockerComposeSupport {

    /** 注入被测对象：库存计数器服务 */
    @Autowired
    private FlashSaleCounterService counterService;

    /** 测试使用的Redis键名 */
    private static final String KEY = "test:flash:stock";

    /**
     * 测试前准备：初始化库存为10
     * 
     * @BeforeEach注解：每个测试方法执行前都会调用此方法
     */
    @BeforeEach
    void setUp() {
        counterService.initStock(KEY, 10);
    }

    /**
     * 测试用例1：验证原子性扣减和库存不足处理
     * 
     * 测试场景：
     * - 初始库存10件
     * - 第一次扣减3件（应该成功）
     * - 第二次尝试扣减20件（应该失败，库存不足）
     * 
     * 预期结果：
     * - 第一次扣减后剩余7件
     * - 第二次扣减失败，库存仍为7件
     */
    @Test
    void deductsAtomicallyUntilOutOfStock() {
        System.out.println("\n========== 开始测试：秒杀计数器（基础功能） ==========");
        
        // 初始化库存
        counterService.initStock(KEY, 10);
        System.out.println("[初始] 库存: 10");
        
        // 步骤1：扣减3件库存（应该成功）
        System.out.println("\n[操作1] 扣减3件...");
        boolean result1 = counterService.tryDeduct(KEY, 3);
        System.out.println("  结果: " + (result1 ? "成功" : "失败"));
        assertTrue(result1);
        
        long stock1 = counterService.currentStock(KEY);
        System.out.println("  当前库存: " + stock1);
        
        // 验证：剩余库存应该是 10 - 3 = 7
        assertEquals(7, stock1);
        System.out.println("  ✓ 验证通过: 10 - 3 = 7");
        
        // 步骤2：尝试扣减20件（超过剩余库存，应该失败）
        System.out.println("\n[操作2] 尝试扣减20件（超过库存）...");
        boolean result2 = counterService.tryDeduct(KEY, 20);
        System.out.println("  结果: " + (result2 ? "成功" : "失败"));
        assertFalse(result2);
        
        long stock2 = counterService.currentStock(KEY);
        System.out.println("  当前库存: " + stock2);
        
        // 验证：库存仍然是7（没有发生变化）
        assertEquals(7, stock2);
        System.out.println("  ✓ 验证通过: 库存未变化，仍为7");
        
        System.out.println("\n========== 测试通过 ==========\n");
    }

    /**
     * 测试用例2：验证高并发场景下库存不会变为负数
     * 
     * 测试场景：
     * - 初始库存100件
     * - 50个线程，每个线程尝试扣减4次（每次1件）
     * - 总共200次扣减请求，但只有前100次会成功
     * 
     * 预期结果：
     * - 成功扣减次数 = 100（等于初始库存）
     * - 最终库存 = 0（不会超卖）
     * 
     * 这是秒杀系统最重要的测试，验证了：
     * 1. Lua脚本的原子性
     * 2. 竞态条件的避免
     * 3. 库存数据的最终一致性
     * 
     * @throws InterruptedException 如果等待过程中被中断
     */
    @Test
    void concurrentDeductNeverGoesNegative() throws InterruptedException {
        System.out.println("\n========== 开始测试：秒杀计数器（高并发） ==========");
        
        // 步骤1：初始化库存为100
        counterService.initStock(KEY, 100);
        System.out.println("[初始] 库存: 100");
        
        // 并发参数配置
        int threads = 50;              // 线程数
        int attemptsPerThread = 4;     // 每个线程尝试扣减的次数
        int totalAttempts = threads * attemptsPerThread;
        
        System.out.println("[配置] 线程数: " + threads);
        System.out.println("[配置] 每线程尝试次数: " + attemptsPerThread);
        System.out.println("[配置] 总请求数: " + totalAttempts);
        
        // 创建线程池
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        
        // 同步辅助工具
        CountDownLatch start = new CountDownLatch(1);  // 控制同时启动
        CountDownLatch done = new CountDownLatch(threads);  // 等待所有线程完成
        
        // 成功计数器（线程安全）
        AtomicInteger success = new AtomicInteger();

        System.out.println("\n[执行] 提交 " + threads + " 个并发任务...");
        long startTime = System.currentTimeMillis();
        
        // 步骤2：提交50个并发任务
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    // 等待所有线程就绪后同时开始（模拟秒杀瞬间）
                    start.await();
                    
                    // 每个线程尝试扣减4次
                    for (int j = 0; j < attemptsPerThread; j++) {
                        if (counterService.tryDeduct(KEY, 1)) {
                            // 扣减成功，计数器+1
                            success.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    // 恢复中断状态
                    Thread.currentThread().interrupt();
                } finally {
                    // 通知主线程当前线程已完成
                    done.countDown();
                }
            });
        }

        // 步骤3：启动所有线程（模拟瞬间高并发）
        System.out.println("[执行] 启动所有线程（模拟秒杀瞬间）...");
        start.countDown();
        
        // 等待所有线程执行完毕
        done.await();
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        // 关闭线程池
        pool.shutdownNow();

        System.out.println("\n[完成] 执行耗时: " + duration + "ms");
        System.out.println("[结果] 成功扣减次数: " + success.get());
        System.out.println("[结果] 失败次数: " + (totalAttempts - success.get()));
        System.out.println("[结果] 最终库存: " + counterService.currentStock(KEY));

        // 步骤4：验证结果
        System.out.println("\n[验证1] 成功扣减次数是否等于初始库存？");
        assertEquals(100, success.get());
        System.out.println("  ✓ 通过: " + success.get() + " == 100");
        
        System.out.println("\n[验证2] 最终库存是否为0（无超卖）？");
        assertEquals(0, counterService.currentStock(KEY));
        System.out.println("  ✓ 通过: 库存 = 0，未出现超卖");
        
        System.out.println("\n========== 测试通过 ==========\n");
    }
}
