# Week 1 学习笔记：Java并发与Redis分布式技术

**学习时间：** 2026年5月22日 - 5月28日  
**学习目标：** 掌握Java多线程进阶、分布式思维入门、Redis核心应用、AI Copilot配置

---

## 📚 目录

1. [Java并发编程最佳实践](#1-java并发编程最佳实践)
2. [Redis使用场景与常见问题](#2-redis使用场景与常见问题)
3. [分布式系统理论基础](#3-分布式系统理论基础)
4. [AI Copilot使用体验](#4-ai-copilot使用体验)
5. [实战经验总结](#5-实战经验总结)

---

## 1. Java并发编程最佳实践

### 1.1 线程池的正确使用

#### ❌ 错误示例：使用Executors快捷方法

```java
// 危险！队列无界，可能导致OOM
ExecutorService executor = Executors.newFixedThreadPool(10);
```

**问题分析：**
- `LinkedBlockingQueue` 默认容量为 `Integer.MAX_VALUE`
- 任务堆积时无法及时拒绝，导致内存溢出
- 线程名称默认为 `pool-N-thread-M`，不便于调试

#### ✅ 正确示例：手动创建ThreadPoolExecutor

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    corePoolSize,           // 核心线程数（根据CPU核数设置）
    maximumPoolSize,        // 最大线程数
    60L,                    // 空闲线程存活时间
    TimeUnit.SECONDS,       // 时间单位
    new ArrayBlockingQueue<>(100),  // 有界队列（防止OOM）
    new CustomThreadFactory(),      // 自定义线程工厂
    new CallerRunsPolicy()          // 拒绝策略（背压机制）
);
```

**关键参数调优：**

| 参数 | CPU密集型 | IO密集型 | 混合型 |
|------|-----------|----------|--------|
| 核心线程数 | CPU核数+1 | CPU核数*2 | CPU核数*1.5 |
| 队列容量 | 较小（100-500） | 较大（1000-5000） | 中等（500-1000） |
| 拒绝策略 | CallerRunsPolicy | AbortPolicy | CallerRunsPolicy |

#### 线程池监控指标

```java
// 获取线程池运行时数据
executor.getActiveCount();      // 活跃线程数
executor.getQueue().size();     // 队列中等待的任务数
executor.getCompletedTaskCount(); // 已完成任务数
executor.getRejectedExecutionCount(); // 被拒绝的任务数
```

**告警阈值建议：**
- 队列使用率 > 80%：预警
- 活跃线程数 = 最大线程数：扩容信号
- 拒绝任务数 > 0：立即告警

---

### 1.2 CompletableFuture异步编排

#### 基本用法

```java
// 创建异步任务
CompletableFuture<String> future = CompletableFuture.supplyAsync(
    () -> fetchDataFromAPI(),  // 业务逻辑
    executor                    // 自定义线程池
);

// 链式调用
future.thenApply(result -> process(result))
      .thenAccept(processed -> save(processed))
      .exceptionally(ex -> handleError(ex));
```

#### 多任务组合

```java
// 并行执行多个任务
CompletableFuture<Order> orderFuture = getOrderAsync(orderId);
CompletableFuture<User> userFuture = getUserAsync(userId);
CompletableFuture<Product> productFuture = getProductAsync(productId);

// 等待所有任务完成
CompletableFuture.allOf(orderFuture, userFuture, productFuture)
    .thenRun(() -> {
        Order order = orderFuture.join();
        User user = userFuture.join();
        Product product = productFuture.join();
        // 处理业务逻辑
    });
```

#### 常见陷阱

**陷阱1：忘记指定线程池**

```java
// ❌ 使用默认的ForkJoinPool.commonPool()
CompletableFuture.supplyAsync(() -> doSomething());

// ✅ 显式指定线程池
CompletableFuture.supplyAsync(() -> doSomething(), executor);
```

**陷阱2：阻塞主线程**

```java
// ❌ join()会阻塞当前线程
result = future.join();

// ✅ 使用回调方式
future.thenAccept(result -> process(result));
```

---

### 1.3 锁机制选择指南

| 场景 | 推荐方案 | 说明 |
|------|---------|------|
| 单JVM内同步 | `synchronized` / `ReentrantLock` | 性能最好 |
| 分布式互斥 | Redisson RLock | 支持自动续期 |
| 读写分离 | `ReadWriteLock` | 读多写少场景 |
| 限流控制 | `Semaphore` | 控制并发度 |
| 一次性同步 | `CountDownLatch` | 等待多个任务完成 |

---

## 2. Redis使用场景与常见问题

### 2.1 核心数据结构及应用

#### String（字符串）

**应用场景：**
- 缓存热点数据
- 计数器（如秒杀库存）
- 分布式锁（SETNX）

```java
// 原子递增
redisTemplate.opsForValue().increment("counter", 1);

// 设置过期时间（防止缓存雪崩）
redisTemplate.opsForValue().set("key", "value", 30, TimeUnit.MINUTES);
```

#### Hash（哈希）

**应用场景：**
- 对象存储（用户信息、商品详情）
- 购物车

```java
// 存储用户信息
redisTemplate.opsForHash().put("user:1001", "name", "张三");
redisTemplate.opsForHash().put("user:1001", "age", "25");

// 批量获取
Map<Object, Object> userInfo = redisTemplate.opsForHash().entries("user:1001");
```

#### List（列表）

**应用场景：**
- 消息队列
- 最新N条记录

```java
// 左侧推送（生产者）
redisTemplate.opsForList().leftPush("queue:orders", orderId);

// 右侧弹出（消费者）
String orderId = redisTemplate.opsForList().rightPop("queue:orders", 10, TimeUnit.SECONDS);
```

#### Set（集合）

**应用场景：**
- 共同好友
- 唯一性约束（如抽奖名单）

```java
// 添加元素
redisTemplate.opsForSet().add("lottery:users", userId);

// 随机抽取
Set<String> winners = redisTemplate.opsForSet().randomMembers("lottery:users", 10);
```

#### ZSet（有序集合）

**应用场景：**
- 排行榜
- 延迟队列

```java
// 添加分数
redisTemplate.opsForZSet().add("leaderboard", userId, score);

// 获取Top 10
Set<String> top10 = redisTemplate.opsForZSet().reverseRange("leaderboard", 0, 9);
```

---

### 2.2 秒杀系统设计要点

#### 核心问题：超卖

**原因分析：**
```java
// ❌ 非原子操作，存在竞态条件
int stock = getStock();  // 步骤1：查询库存
if (stock > 0) {         // 步骤2：判断是否充足
    setStock(stock - 1); // 步骤3：扣减库存
}
```

**解决方案：Lua脚本保证原子性**

```lua
local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
local qty = tonumber(ARGV[1])
if stock >= qty then
  redis.call('DECRBY', KEYS[1], qty)
  return 1
end
return 0
```

**为什么Lua脚本能解决问题？**
1. Redis单线程执行，不会被其他命令打断
2. 检查、扣减在同一事务中完成
3. 减少网络往返次数（从3次降到1次）

---

### 2.3 缓存三大问题及解决方案

#### 1. 缓存穿透

**问题：** 查询不存在的数据，请求直接打到数据库

**解决方案：**
- 布隆过滤器拦截无效请求
- 缓存空值（设置较短过期时间）

```java
String value = redisTemplate.opsForValue().get(key);
if (value == null) {
    // 查询数据库
    value = db.query(key);
    if (value == null) {
        // 缓存空值，5分钟过期
        redisTemplate.opsForValue().set(key, "", 5, TimeUnit.MINUTES);
    } else {
        redisTemplate.opsForValue().set(key, value, 30, TimeUnit.MINUTES);
    }
}
```

#### 2. 缓存击穿

**问题：** 热点key过期瞬间，大量请求打到数据库

**解决方案：**
- 互斥锁（只让一个线程重建缓存）
- 逻辑过期（不设置物理过期时间）

```java
// 使用分布式锁防止缓存击穿
public String getData(String key) {
    String value = redisTemplate.opsForValue().get(key);
    if (value == null) {
        lockService.executeWithLock("lock:" + key, 5, 10, () -> {
            // 双重检查
            value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                value = db.query(key);
                redisTemplate.opsForValue().set(key, value, 30, TimeUnit.MINUTES);
            }
            return null;
        });
    }
    return value;
}
```

#### 3. 缓存雪崩

**问题：** 大量key同时过期，数据库压力骤增

**解决方案：**
- 过期时间加随机值
- 多级缓存（本地缓存 + Redis）

```java
// 过期时间增加随机偏移（5-10分钟）
long expireTime = 300 + new Random().nextInt(300);
redisTemplate.opsForValue().set(key, value, expireTime, TimeUnit.SECONDS);
```

---

### 2.4 Redis持久化策略

| 策略 | 优点 | 缺点 | 适用场景 |
|------|------|------|---------|
| RDB | 性能好，文件小 | 可能丢失数据 | 备份、灾难恢复 |
| AOF | 数据安全性高 | 文件大，恢复慢 | 对数据一致性要求高 |
| RDB+AOF | 兼顾性能和安全性 | 配置复杂 | 生产环境推荐 |

**配置建议：**
```yaml
# docker-compose.yml
command: redis-server --appendonly yes  # 启用AOF
```

---

## 3. 分布式系统理论基础

### 3.1 CAP定理

**CAP三要素：**
- **C**onsistency（一致性）：所有节点看到的数据相同
- **A**vailability（可用性）：每个请求都能得到响应
- **P**artition tolerance（分区容错性）：网络分区时系统仍能运行

**权衡取舍：**
- **CP系统**（如ZooKeeper）：保证一致性和分区容错性，牺牲可用性
- **AP系统**（如Cassandra）：保证可用性和分区容错性，牺牲强一致性
- **Redis**：通常是AP系统（主从复制是最终一致性）

**实际案例：**
```
电商秒杀场景：
- 库存扣减：CP（不能超卖，必须强一致）
- 商品展示：AP（可以短暂不一致，保证可用性）
```

---

### 3.2 为何需要消息队列？

**解耦：**
```
传统架构：Order Service → Inventory Service → Notification Service
问题：调用链路长，任何一个环节失败都会影响整体

MQ架构：Order Service → [MQ] → Inventory Service / Notification Service
优势：订单服务只需发送消息，无需关心后续处理
```

**削峰填谷：**
```
秒杀场景：10000 QPS瞬时流量
→ MQ缓冲 → 后端服务以2000 QPS平稳处理
```

**异步处理：**
```
同步：下单 → 扣库存 → 发通知 → 积分累加（耗时500ms）
异步：下单 → 扣库存（耗时50ms），其他操作异步执行
```

---

### 3.3 为何需要缓存？

**性能提升：**
- Redis内存操作：~100,000 OPS
- MySQL磁盘操作：~1,000 OPS
- 性能差距：100倍

**典型架构：**
```
用户请求 → CDN → Nginx → 应用层缓存 → Redis → MySQL
         （静态资源） （负载均衡） （本地缓存） （分布式缓存） （持久化）
```

**缓存更新策略：**
1. **Cache Aside**（旁路缓存）：先更新数据库，再删除缓存（推荐）
2. **Read/Write Through**：应用只操作缓存，缓存负责同步数据库
3. **Write Behind**：先更新缓存，异步批量写入数据库

---

## 4. AI Copilot使用体验

### 4.1 工具选择

本次实训使用了以下AI编程助手：
- **GitHub Copilot**：代码补全和生成
- **通义灵码**：中文注释和文档生成
- **Qoder**：项目理解和重构建议

---

### 4.2 使用场景

#### 场景1：生成单元测试

**提示词：**
```
为FlashSaleCounterService编写JUnit测试，包含：
1. 正常扣减库存
2. 库存不足时扣减失败
3. 高并发场景下不会超卖（50个线程）
```

**效果评估：** ⭐⭐⭐⭐⭐
- 生成了完整的测试用例
- 包含了边界条件验证
- 使用了CountDownLatch模拟并发

**需要人工调整：**
- 修改断言消息使其更清晰
- 添加中文注释

---

#### 场景2：生成压力测试脚本

**提示词：**
```
用PowerShell编写秒杀接口压测脚本：
- 初始化100件库存
- 发送200个并发请求
- 统计成功率和QPS
```

**效果评估：** ⭐⭐⭐⭐
- 基本功能完整
- 并发控制合理
- 结果统计清晰

**需要人工调整：**
- 优化输出格式（添加颜色）
- 增加异常处理

---

#### 场景3：代码注释生成

**提示词：**
```
为DistributedLockService类添加详细的中文注释，包括：
- 类的作用
- 方法参数说明
- 使用示例
```

**效果评估：** ⭐⭐⭐⭐⭐
- 注释结构清晰
- 包含使用示例
- 解释了设计原理

---

### 4.3 优缺点总结

**优点：**
✅ 大幅提升编码效率（估计提升50%+）  
✅ 减少样板代码编写时间  
✅ 提供多种实现思路  
✅ 自动生成测试用例  

**缺点：**
❌ 生成的代码需要仔细审查（可能有bug）  
❌ 对复杂业务逻辑理解有限  
❌ 有时生成过度设计的代码  
❌ 依赖网络连接  

**最佳实践：**
1. **明确提示词**：详细描述需求和约束条件
2. **分步生成**：先生成框架，再填充细节
3. **人工审查**：必须理解每一行代码的含义
4. **结合使用**：多个AI工具互补

---

## 5. 实战经验总结

### 5.1 遇到的问题和解决方案

#### 问题1：线程池任务堆积

**现象：** 压测时发现大量请求超时

**排查过程：**
1. 监控线程池指标：`executor.getQueue().size()` 达到上限
2. 检查日志：发现大量"Task rejected"异常

**根本原因：**
- 队列容量设置过小（64）
- 拒绝策略不合理

**解决方案：**
```java
// 调整前
new ThreadPoolExecutor(4, 4, 30, SECONDS, 
    new ArrayBlockingQueue<>(64), ...);

// 调整后
new ThreadPoolExecutor(8, 16, 60, SECONDS, 
    new ArrayBlockingQueue<>(512), 
    new ThreadPoolExecutor.CallerRunsPolicy());
```

---

#### 问题2：Redis连接超时

**现象：** 高并发时出现 `JedisConnectionException`

**根本原因：**
- Redis客户端连接池太小
- 默认超时时间过短

**解决方案：**
```yaml
# application.yml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 50    # 最大连接数
          max-idle: 20      # 最大空闲连接
          min-idle: 5       # 最小空闲连接
          max-wait: 3000    # 最大等待时间（毫秒）
```

---

#### 问题3：分布式锁死锁

**现象：** 某些请求一直等待锁释放

**根本原因：**
- 业务逻辑执行时间超过锁的租约时间
- 未正确释放锁

**解决方案：**
```java
// 确保在finally块中释放锁
try {
    acquired = lock.tryLock(waitTime, leaseTime, SECONDS);
    if (acquired) {
        // 业务逻辑
    }
} finally {
    if (acquired && lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

---

### 5.2 性能优化心得

#### 1. 减少网络往返

**优化前：**
```java
// 3次网络请求
Long stock = redisTemplate.opsForValue().get(key);  // GET
if (stock > 0) {
    redisTemplate.opsForValue().decrement(key);      // DECRBY
}
```

**优化后：**
```java
// 1次网络请求（Lua脚本）
redisTemplate.execute(luaScript, List.of(key), args);
```

**性能提升：** ~60%

---

#### 2. 批量操作

**优化前：**
```java
// 100次单独查询
for (String key : keys) {
    values.add(redisTemplate.opsForValue().get(key));
}
```

**优化后：**
```java
// 1次批量查询
values = redisTemplate.opsForValue().multiGet(keys);
```

**性能提升：** ~90%

---

#### 3. 连接池调优

**监控指标：**
- 活跃连接数 / 最大连接数 < 80%
- 平均等待时间 < 10ms
- 连接超时次数 = 0

---

### 5.3 学习收获

#### 技术层面
1. **深入理解并发模型**：从理论到实践的完整闭环
2. **掌握Redis高级用法**：Lua脚本、分布式锁、持久化
3. **学会性能调优方法**：监控、分析、优化
4. **理解分布式系统挑战**：一致性、可用性、分区容错性

#### 工程层面
1. **测试驱动开发**：先写测试，再实现功能
2. **防御性编程**：考虑各种异常情况
3. **代码可读性**：详细注释、清晰命名
4. **文档重要性**：README、API文档、学习笔记

#### AI协作层面
1. **提示词工程**：如何向AI清晰表达需求
2. **代码审查**：AI生成的代码仍需人工审核
3. **效率提升**：合理使用可提升50%+效率
4. **局限性认知**：AI不能完全替代人类思考

---

## 📖 参考资料

1. **Java并发编程**
   - 《Java并发编程实战》- Brian Goetz
   - [Oracle官方教程](https://docs.oracle.com/javase/tutorial/essential/concurrency/)

2. **Redis**
   - 《Redis设计与实现》- 黄健宏
   - [Redis官方文档](https://redis.io/documentation)

3. **分布式系统**
   - 《数据密集型应用系统设计》- Martin Kleppmann
   - [CAP定理维基百科](https://en.wikipedia.org/wiki/CAP_theorem)

4. **Spring Boot**
   - [Spring Boot官方指南](https://spring.io/guides)
   - 《Spring Boot实战》- Craig Walls

---

## 🎯 下一步学习计划

### Week 2 预习
- [ ] 消息队列（RabbitMQ / Kafka）
- [ ] 微服务架构（Spring Cloud）
- [ ] 分布式事务（Seata）

### 深入方向
- [ ] JVM内存模型和GC调优
- [ ] Redis集群和高可用
- [ ] 分布式一致性算法（Raft、Paxos）

---

**总结：** 本周通过实战项目深入理解了Java并发编程和Redis分布式技术，掌握了线程池、CompletableFuture、分布式锁等核心概念，并学会了使用AI Copilot提升开发效率。最大的收获是从理论到实践的完整闭环，以及遇到问题时的排查和解决能力。
