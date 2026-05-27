package com.training.week1.redis;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 分布式锁服务（基于Redisson实现）
 * 
 * 分布式锁解决的核心问题：
 * 在分布式系统中，多个应用实例需要互斥访问共享资源时，
 * 传统的Java锁（synchronized、ReentrantLock）无法跨JVM工作，
 * 因此需要借助外部存储（如Redis）实现分布式协调。
 * 
 * Redisson的优势：
 * 1. 自动续期：看门狗机制防止业务执行时间过长导致锁释放
 * 2. 可重入：同一线程可多次获取同一把锁
 * 3. 原子性：基于Lua脚本实现，避免竞态条件
 * 4. 高可用：支持Redis哨兵和集群模式
 * 
 * 典型应用场景：
 * - 秒杀库存扣减（防止超卖）
 * - 分布式任务调度（防止重复执行）
 * - 缓存重建（防止缓存击穿）
 * - 订单幂等性控制（防止重复提交）
 * 
 * @author Ace Chen
 */
@Service
public class DistributedLockService {

    /** Redisson客户端，由Spring Boot自动配置注入 */
    private final RedissonClient redissonClient;

    /**
     * 构造函数注入Redisson客户端
     * 
     * @param redissonClient Redisson客户端实例（由Spring容器管理）
     */
    public DistributedLockService(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 在分布式锁保护下执行业务逻辑
     * 
     * 执行流程：
     * 1. 尝试获取锁（带超时时间）
     * 2. 获取成功则执行用户提供的业务逻辑
     * 3. 无论成功或失败，最终都释放锁
     * 4. 如果获取锁失败或发生中断，抛出异常
     * 
     * 使用示例：
     * <pre>{@code
     * String result = lockService.executeWithLock(
     *     "lock:order:123",  // 锁的key
     *     5,                  // 等待5秒
     *     10,                 // 锁持有10秒
     *     () -> {
     *         // 需要同步执行的业务代码
     *         return orderService.createOrder(order);
     *     }
     * );
     * }</pre>
     * 
     * @param lockKey      锁的唯一标识（建议使用业务前缀，如"lock:order:xxx"）
     * @param waitSeconds  最大等待时间（秒），超过此时间仍未获取到锁则放弃
     * @param leaseSeconds 锁的租约时间（秒），到期后自动释放（防止死锁）
     * @param action       需要在锁保护下执行的业务逻辑（Supplier函数式接口）
     * @return 业务逻辑的返回值
     * @param <T>          返回值的类型
     * @throws IllegalStateException 如果获取锁失败或被中断
     */
    public <T> T executeWithLock(String lockKey, long waitSeconds, long leaseSeconds, Supplier<T> action) {
        // 步骤1：获取锁对象（RLock是java.util.concurrent.locks.Lock的分布式版本）
        RLock lock = redissonClient.getLock(lockKey);
        boolean acquired = false;
        
        try {
            // 步骤2：尝试获取锁
            // tryLock参数说明：
            // - waitTime：最多等待多久（避免无限等待）
            // - leaseTime：锁自动释放时间（防止持有锁的节点宕机导致死锁）
            // - TimeUnit：时间单位
            // 返回值：true表示获取成功，false表示超时未获取到
            acquired = lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
            
            if (!acquired) {
                // 获取锁失败，可能是其他节点正在执行
                throw new IllegalStateException("Failed to acquire lock: " + lockKey);
            }
            
            // 步骤3：在锁保护下执行业务逻辑
            // 此时只有一个线程能执行到这里（分布式环境下的互斥性）
            return action.get();
            
        } catch (InterruptedException interrupted) {
            // 等待锁的过程中被中断（如线程池关闭）
            // 重要：恢复中断状态，让调用者知道发生了中断
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for lock: " + lockKey, interrupted);
            
        } finally {
            // 步骤4：释放锁（必须在finally中执行，确保一定会释放）
            // 检查条件：
            // 1. acquired == true：确实获取到了锁
            // 2. isHeldByCurrentThread：当前线程仍持有该锁（防止误解锁）
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock(); // 释放锁，其他节点可以获取
            }
        }
    }
}
