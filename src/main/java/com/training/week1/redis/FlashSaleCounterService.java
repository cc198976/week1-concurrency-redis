package com.training.week1.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 秒杀库存计数器服务（基于Redis实现）
 * 
 * 核心设计思想：
 * 在高并发场景下（如秒杀活动），传统的"查询-判断-更新"三步操作
 * 会产生竞态条件，导致超卖问题。本服务使用Redis + Lua脚本
 * 将检查和扣减合并为原子操作，确保数据一致性。
 * 
 * 为什么使用Lua脚本？
 * 1. 原子性：Redis单线程执行Lua脚本，不会被其他命令打断
 * 2. 减少网络开销：多次操作合并为一次网络请求
 * 3. 避免竞态条件：检查和扣减在同一事务中完成
 * 
 * 与数据库方案对比：
 * - 性能：Redis内存操作比数据库快10-100倍
 * - 并发：Lua脚本原子执行，无需额外锁机制
 * - 扩展：可轻松支持水平扩展（Redis集群）
 * 
 * 典型应用场景：
 * - 电商秒杀活动库存管理
 * - 优惠券发放数量控制
 * - 限流器（令牌桶/计数器算法）
 * - 分布式信号量
 * 
 * @author Ace Chen
 */
@Service
public class FlashSaleCounterService {

    /**
     * Lua脚本：原子性地检查并扣减库存
     * 
     * 脚本逻辑：
     * 1. 获取当前库存值（如果不存在则视为0）
     * 2. 将库存和请求数量转换为数字类型
     * 3. 判断库存是否充足
     * 4. 如果充足则扣减并返回1（成功）
     * 5. 如果不足则返回0（失败）
     * 
     * KEYS[1]：库存的Redis键名（如"flash:sale:sku-1001"）
     * ARGV[1]：请求扣减的数量
     * 
     * 返回值：1表示扣减成功，0表示库存不足
     */
    private static final String DECR_IF_POSITIVE_LUA = """
            local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
            local qty = tonumber(ARGV[1])
            if stock >= qty then
              redis.call('DECRBY', KEYS[1], qty)
              return 1
            end
            return 0
            """;

    /** Spring Data Redis模板，用于执行Redis操作 */
    private final StringRedisTemplate redisTemplate;
    
    /** 封装后的Lua脚本对象，可重复执行 */
    private final DefaultRedisScript<Long> decrScript;

    /**
     * 构造函数：注入Redis模板并初始化Lua脚本
     * 
     * @param redisTemplate Spring Data Redis字符串模板（由Spring容器管理）
     */
    public FlashSaleCounterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        // 创建Redis脚本对象，指定返回类型为Long
        this.decrScript = new DefaultRedisScript<>(DECR_IF_POSITIVE_LUA, Long.class);
    }

    /**
     * 初始化商品库存
     * 
     * 在秒杀活动开始前，预先设置商品的初始库存数量。
     * 通常由运营人员在后台管理系统中调用此接口。
     * 
     * 使用示例：
     * <pre>{@code
     * // 为SKU-1001商品设置100件库存
     * counterService.initStock("flash:sale:sku-1001", 100);
     * }</pre>
     * 
     * @param stockKey 库存的Redis键名（建议格式："业务前缀:商品ID"）
     * @param quantity 初始库存数量（必须 >= 0）
     */
    public void initStock(String stockKey, long quantity) {
        // 将库存值以字符串形式存储到Redis
        // SET命令是原子的，不用担心并发问题
        redisTemplate.opsForValue().set(stockKey, String.valueOf(quantity));
    }

    /**
     * 查询当前剩余库存
     * 
     * 用于前端展示或后台监控，实时获取商品的剩余库存数量。
     * 
     * @param stockKey 库存的Redis键名
     * @return 当前库存数量（如果键不存在则返回0）
     */
    public long currentStock(String stockKey) {
        // 从Redis获取库存值
        String value = redisTemplate.opsForValue().get(stockKey);
        
        // 处理空值情况（键不存在或已被删除）
        return value == null ? 0L : Long.parseLong(value);
    }

    /**
     * 尝试扣减库存（原子操作）
     * 
     * 这是秒杀系统的核心方法，通过Lua脚本保证：
     * 1. 检查库存是否充足
     * 2. 如果充足则扣减
     * 3. 返回扣减结果
     * 以上三步在Redis服务器端原子执行，不会被其他请求打断。
     * 
     * 高并发场景下的表现：
     * - 100个请求同时扣减库存10的商品
     * - 只有前10个请求会成功（返回true）
     * - 后90个请求会失败（返回false）
     * - 最终库存为0，不会出现负数（超卖）
     * 
     * 使用示例：
     * <pre>{@code
     * // 用户购买商品，扣减1件库存
     * boolean success = counterService.tryDeduct("flash:sale:sku-1001", 1);
     * if (success) {
     *     // 创建订单
     * } else {
     *     // 提示"已售罄"
     * }
     * }</pre>
     * 
     * @param stockKey 库存的Redis键名
     * @param quantity 请求扣减的数量（必须 > 0）
     * @return true表示扣减成功，false表示库存不足
     */
    public boolean tryDeduct(String stockKey, int quantity) {
        // 执行Lua脚本
        // execute方法参数说明：
        // - script：要执行的Lua脚本对象
        // - keys：脚本中的KEYS参数列表
        // - args：脚本中的ARGV参数列表
        Long ok = redisTemplate.execute(
                decrScript,                          // Lua脚本
                List.of(stockKey),                   // KEYS[1] = stockKey
                String.valueOf(quantity)             // ARGV[1] = quantity
        );
        
        // 解析返回值：1表示成功，0表示失败
        return ok != null && ok == 1L;
    }
}
