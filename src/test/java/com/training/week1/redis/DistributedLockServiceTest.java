package com.training.week1.redis;

import com.training.week1.support.RedisTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 分布式锁服务集成测试
 * 
 * 测试目标：
 * 1. 验证分布式锁的互斥性（同一时刻只有一个线程能执行临界区代码）
 * 2. 验证锁的正确释放（避免死锁）
 * 3. 验证高并发场景下的数据一致性
 * 
 * 测试方法：
 * 使用20个线程同时递增计数器，如果没有锁保护，
 * 由于竞态条件，最终结果会小于20。使用锁后，结果应该正好是20。
 * 
 * @author Ace Chen
 */
@SpringBootTest
class DistributedLockServiceTest extends RedisTestSupport {

    static {
        // 配置已在 RedisTestSupport 基类中设置，无需重复
    }

    /** 注入被测对象：分布式锁服务 */
    @Autowired
    private DistributedLockService lockService;

    /**
     * 测试用例：验证临界区的串行化执行
     * 
     * 测试场景：
     * - 20个线程同时尝试执行临界区代码
     * - 临界区操作：读取计数器值 -> 休眠20ms -> 写入计数器值+1
     * - 如果没有锁保护，会出现“丢失更新”问题
     * 
     * 预期结果：
     * - 计数器最终值为20（说明所有更新都成功了）
     * - 证明锁机制保证了串行化执行
     * 
     * @throws InterruptedException 如果等待过程中被中断
     */
    @Test
    void serializesCriticalSection() throws InterruptedException {
        System.out.println("\n========== 开始测试：分布式锁 ==========");
            
        // 锁的键名（建议使用业务前缀）
        String lockKey = "test:lock:counter";
            
        // 共享计数器（多线程访问）
        AtomicInteger counter = new AtomicInteger();
            
        // 线程数量
        int threads = 20;
            
        System.out.println("[配置] 线程数: " + threads);
        System.out.println("[配置] 锁键名: " + lockKey);
        System.out.println("[配置] 临界区操作: 读取 -> 休眠20ms -> 递增");
            
        // 创建固定大小的线程池
        ExecutorService pool = Executors.newFixedThreadPool(threads);
            
        // 同步辅助工具：
        // start：确保所有线程同时开始（模拟高并发）
        // done：等待所有线程执行完毕
        CountDownLatch
                start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
    
        System.out.println("\n[执行] 提交 " + threads + " 个并发任务...");
        long startTime = System.currentTimeMillis();
            
        // 提交20个并发任务
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    // 等待所有线程就绪后同时开始
                    start.await();
                        
                    // 在分布式锁保护下执行临界区代码
                    lockService.executeWithLock(lockKey, 5, 3, () -> {
                        // 步骤1：读取当前值
                        int current = counter.get();
                            
                        try {
                            // 步骤2：模拟业务处理时间（放大竞态条件的影响）
                            Thread.sleep(20);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                            
                        // 步骤3：基于读取的值递增
                        // 如果没有锁，多个线程可能读到相同的current值，导致更新丢失
                        counter.set(current + 1);
                            
                        return null;
                    });
                        
                } catch (InterruptedException e) {
                    // 恢复中断状态
                    Thread.currentThread().interrupt();
                } finally {
                    // 无论成功或失败，都要通知主线程
                    done.countDown();
                }
            });
        }
    
        // 启动所有线程（模拟瞬间高并发）
        System.out.println("[执行] 启动所有线程（模拟高并发）...");
        start.countDown();
            
        // 等待所有线程执行完毕
        done.await();
            
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
            
        // 关闭线程池
        pool.shutdownNow();
    
        System.out.println("\n[完成] 执行耗时: " + duration + "ms");
        System.out.println("[结果] 计数器最终值: " + counter.get());
        System.out.println("[预期] 期望值: " + threads);
            
        if (counter.get() == threads) {
            System.out.println("[验证] ✓ 锁机制有效：所有更新都成功，无竞态条件");
        } else {
            System.out.println("[验证] ✗ 锁机制失效：发生了竞态条件，丢失了 " + (threads - counter.get()) + " 次更新");
        }
    
        // 验证：计数器值应该等于线程数（20）
        // 如果小于20，说明发生了竞态条件（锁未生效）
        assertEquals(threads, counter.get());
            
        System.out.println("\n========== 测试通过 ==========\n");
    }
}
