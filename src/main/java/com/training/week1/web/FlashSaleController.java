package com.training.week1.web;

import com.training.week1.redis.DistributedLockService;
import com.training.week1.redis.FlashSaleCounterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 秒杀活动REST API控制器
 * 
 * 提供秒杀系统的核心接口：
 * 1. 初始化库存 - POST /api/flash-sale/init
 * 2. 查询库存 - GET /api/flash-sale/stock
 * 3. 购买商品 - POST /api/flash-sale/buy
 * 
 * 并发安全设计：
 * - 使用分布式锁（Redisson）保护库存扣减操作
 * - 使用Lua脚本（Redis）保证检查和扣减的原子性
 * - 双重保障防止超卖问题
 * 
 * API调用示例：
 * <pre>{@code
 * # 1. 初始化库存（设置100件商品）
 * curl -X POST "http://localhost:8080/api/flash-sale/init?stock=100"
 * 
 * # 2. 查询当前库存
 * curl "http://localhost:8080/api/flash-sale/stock"
 * 
 * # 3. 购买1件商品
 * curl -X POST "http://localhost:8080/api/flash-sale/buy?quantity=1"
 * }</pre>
 * 
 * @author Ace Chen
 */
@RestController
@RequestMapping("/api/flash-sale")
public class FlashSaleController {

    /** 库存计数器服务（Redis实现） */
    private final FlashSaleCounterService counterService;
    
    /** 分布式锁服务（Redisson实现） */
    private final DistributedLockService lockService;
    
    /** 默认库存键名（从配置文件读取） */
    private final String defaultStockKey;

    /**
     * 构造函数：注入依赖服务
     * 
     * @param counterService   库存计数服务
     * @param lockService      分布式锁服务
     * @param defaultStockKey  默认库存键名（通过@Value注解从application.yml注入）
     */
    public FlashSaleController(
            FlashSaleCounterService counterService,
            DistributedLockService lockService,
            @Value("${week1.flash-sale.default-stock-key}") String defaultStockKey
    ) {
        this.counterService = counterService;
        this.lockService = lockService;
        this.defaultStockKey = defaultStockKey;
    }

    /**
     * 初始化商品库存
     * 
     * 在秒杀活动开始前，运营人员调用此接口设置商品的初始库存。
     * 
     * 请求示例：
     * - POST /api/flash-sale/init?stock=100
     * - POST /api/flash-sale/init?stock=50&key=flash:sale:sku-2002
     * 
     * @param stock 初始库存数量（默认100）
     * @param key   库存键名（可选，不传则使用配置文件的默认值）
     * @return 初始化结果，包含库存键名和设置的库存数量
     */
    @PostMapping("/init")
    public Map<String, Object> init(
            @RequestParam(defaultValue = "100") long stock,
            @RequestParam(required = false) String key
    ) {
        // 解析实际使用的库存键名
        String stockKey = resolveKey(key);
        
        // 在Redis中设置初始库存
        counterService.initStock(stockKey, stock);
        
        // 返回JSON响应
        return Map.of("stockKey", stockKey, "stock", stock);
    }

    /**
     * 查询当前剩余库存
     * 
     * 前端页面可定时调用此接口展示实时库存，
     * 或在用户点击购买前检查库存是否充足。
     * 
     * 请求示例：
     * - GET /api/flash-sale/stock
     * - GET /api/flash-sale/stock?key=flash:sale:sku-2002
     * 
     * @param key 库存键名（可选，不传则使用默认值）
     * @return 当前库存信息，包含键名和剩余数量
     */
    @GetMapping("/stock")
    public Map<String, Object> stock(@RequestParam(required = false) String key) {
        // 解析实际使用的库存键名
        String stockKey = resolveKey(key);
        
        // 从Redis查询当前库存
        return Map.of("stockKey", stockKey, "stock", counterService.currentStock(stockKey));
    }

    /**
     * 购买商品（扣减库存）
     * 
     * 这是秒杀系统的核心接口，采用双重保护机制：
     * 1. 分布式锁：确保同一时刻只有一个请求能执行库存扣减逻辑
     * 2. Lua脚本：在Redis中原子性地检查和扣减库存
     * 
     * 为什么需要分布式锁？
     * 虽然Lua脚本已经保证了原子性，但在实际业务中，扣减库存后
     * 还需要创建订单、发送消息等操作，这些需要在同一个事务中完成。
     * 分布式锁可以保护整个业务流程的完整性。
     * 
     * 请求示例：
     * - POST /api/flash-sale/buy?quantity=1
     * - POST /api/flash-sale/buy?quantity=2&key=flash:sale:sku-2002
     * 
     * @param quantity 购买数量（默认1件）
     * @param key      库存键名（可选，不传则使用默认值）
     * @return 购买结果，包含：
     *         - stockKey: 库存键名
     *         - success: 是否购买成功
     *         - remaining: 剩余库存数量
     */
    @PostMapping("/buy")
    public Map<String, Object> buy(
            @RequestParam(defaultValue = "1") int quantity,
            @RequestParam(required = false) String key
    ) {
        // 步骤1：解析库存键名
        String stockKey = resolveKey(key);
        
        // 步骤2：构造分布式锁的键名（建议添加前缀区分）
        String lockKey = "lock:" + stockKey;
        
        // 步骤3：在分布式锁保护下执行库存扣减
        // executeWithLock参数说明：
        // - lockKey: 锁的唯一标识
        // - 2: 最多等待2秒获取锁（超时则放弃，快速失败）
        // - 5: 锁持有5秒后自动释放（防止死锁）
        // - Lambda表达式：实际要执行的业务逻辑
        boolean success = lockService.executeWithLock(lockKey, 2, 5, () ->
                counterService.tryDeduct(stockKey, quantity)
        );
        
        // 步骤4：返回购买结果
        return Map.of(
                "stockKey", stockKey,
                "success", success,
                "remaining", counterService.currentStock(stockKey)
        );
    }

    /**
     * 解析库存键名
     * 
     * 如果客户端传入了自定义键名则使用传入的值，
     * 否则使用配置文件中的默认键名。
     * 
     * @param key 客户端传入的键名（可能为null或空字符串）
     * @return 实际使用的Redis键名
     */
    private String resolveKey(String key) {
        // 判断是否为null或空白字符串
        return key == null || key.isBlank() ? defaultStockKey : key;
    }
}
