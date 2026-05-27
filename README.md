# Week 1 实训项目：Java并发编程 + Redis分布式技术

[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-green.svg)](https://spring.io/projects/spring-boot)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)

## 📋 项目概述

本项目是Week 1（5.22 - 5.28）的实训产出物，演示了Java多线程并发编程和Redis分布式技术的核心应用场景。

### 学习目标

✅ **掌握线程池、CompletableFuture、锁机制，理解并发问题**  
✅ **理解分布式系统的基本思想（CAP、为何需要消息队列与缓存）**  
✅ **熟悉 Redis 核心数据结构及 Java 客户端（Jedis / Lettuce / Redisson）**  
✅ **安装配置 AI Copilot，学会用 AI 生成代码、单元测试、注释**

### 核心功能

1. **多线程订单处理模拟**
   - 使用 `ThreadPoolExecutor` + `BlockingQueue` 实现线程池
   - 使用 `CompletableFuture` 聚合异步任务结果
   - 自定义线程工厂和拒绝策略

2. **Redis分布式计数器（秒杀库存）**
   - 使用 Lua 脚本保证原子性操作
   - 防止超卖问题
   - 高并发场景测试

3. **Redis分布式锁（Redisson实现）**
   - 基于Redisson的分布式锁服务
   - 自动续期和超时控制
   - 临界区串行化执行

4. **REST API接口**
   - 秒杀商品管理（初始化、查询、购买）
   - 批量订单处理
   - 完整的Swagger文档支持

---

## 🚀 快速开始

### 前置条件

**必需：**
- JDK 17 或更高版本
- Maven 3.9+

**可选（用于运行集成测试）：**
- Docker & Docker Compose（用于Testcontainers自动启动Redis）

> **提示**：如果您还没有安装Docker，可以先跳过集成测试，只运行单元测试。详见 [TESTING.md](TESTING.md)

### 启动步骤

#### 1. 克隆项目

```bash
git clone <repository-url>
cd week1-concurrency-redis
```

#### 2. 启动Redis服务

```bash
docker-compose up -d
```

#### 3. 编译并运行应用

```bash
# 编译项目
mvn clean package

# 运行应用
mvn spring-boot:run
```

应用将在 `http://localhost:8080` 启动。

#### 4. 运行测试

**选项A：只运行单元测试（无需Docker）**
```bash
# 运行纯Java单元测试
mvn test -Dtest=OrderProcessorSimulationTest
```

**选项B：运行所有测试（需要Docker）**
```bash
# 先确保Docker Desktop正在运行
docker ps

# 运行所有测试（包括集成测试）
mvn clean test
```

> 📖 详细的测试说明请查看 [TESTING.md](TESTING.md)

---

## 📖 API文档

### 1. 秒杀商品接口

#### 初始化库存

```bash
POST /api/flash-sale/init?stock=100
```

**响应示例：**
```json
{
  "stockKey": "flash:sale:sku-1001",
  "stock": 100
}
```

#### 查询库存

```bash
GET /api/flash-sale/stock
```

**响应示例：**
```json
{
  "stockKey": "flash:sale:sku-1001",
  "stock": 95
}
```

#### 购买商品

```bash
POST /api/flash-sale/buy?quantity=1
```

**响应示例：**
```json
{
  "stockKey": "flash:sale:sku-1001",
  "success": true,
  "remaining": 94
}
```

### 2. 订单处理接口

#### 批量处理订单

```bash
POST /api/orders/process?poolSize=4
Content-Type: application/json

[
  {"id": 1, "sku": "SKU-001", "quantity": 2},
  {"id": 2, "sku": "SKU-002", "quantity": 1},
  {"id": 3, "sku": "SKU-003", "quantity": 3}
]
```

**响应示例：**
```json
[
  {
    "orderId": 1,
    "workerName": "order-worker-1",
    "elapsedMs": 60,
    "success": true,
    "message": "processed sku=SKU-001"
  },
  ...
]
```

---

## 🧪 压力测试

### 秒杀接口压测

```powershell
.\scripts\stress-flash-sale.ps1
```

**测试场景：**
- 初始化100件库存
- 发送200个并发购买请求
- 验证最终库存为0（不会超卖）

### 订单处理接口压测

```powershell
.\scripts\stress-orders.ps1
```

**测试场景：**
- 生成100个测试订单
- 使用8个线程并行处理
- 统计处理耗时和成功率

---

## 🏗️ 项目结构

```
week1-concurrency-redis/
├── src/
│   ├── main/
│   │   ├── java/com/training/week1/
│   │   │   ├── Week1Application.java          # 主应用入口
│   │   │   ├── concurrency/                    # 并发编程模块
│   │   │   │   ├── OrderProcessorSimulation.java
│   │   │   │   └── model/
│   │   │   │       ├── Order.java
│   │   │   │       └── ProcessResult.java
│   │   │   ├── redis/                          # Redis服务模块
│   │   │   │   ├── DistributedLockService.java
│   │   │   │   └── FlashSaleCounterService.java
│   │   │   └── web/                            # Web控制器模块
│   │   │       ├── FlashSaleController.java
│   │   │       └── OrderController.java
│   │   └── resources/
│   │       └── application.yml                 # 应用配置
│   └── test/
│       └── java/com/training/week1/
│           ├── concurrency/
│           │   └── OrderProcessorSimulationTest.java
│           ├── redis/
│           │   ├── DistributedLockServiceTest.java
│           │   └── FlashSaleCounterServiceTest.java
│           └── support/
│               └── RedisTestSupport.java       # 测试支持类
├── scripts/
│   ├── stress-flash-sale.ps1                   # 秒杀压测脚本
│   └── stress-orders.ps1                       # 订单压测脚本
├── docker-compose.yml                          # Docker配置
├── pom.xml                                     # Maven配置
├── README.md                                   # 项目说明
└── week1-summary.md                            # 学习笔记
```

---

## 💡 技术要点

### 1. 线程池最佳实践

```java
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    poolSize,                    // 核心线程数
    poolSize,                    // 最大线程数
    30L,                         // 空闲线程存活时间
    TimeUnit.SECONDS,            // 时间单位
    workQueue,                   // 有界队列（防止内存溢出）
    threadFactory,               // 自定义线程工厂
    new CallerRunsPolicy()       // 拒绝策略（背压机制）
);
```

**关键原则：**
- ✅ 始终使用有界队列
- ✅ 自定义线程名称（便于调试）
- ✅ 优雅关闭线程池
- ❌ 避免使用 `Executors.newFixedThreadPool()`（无界队列风险）

### 2. Redis Lua脚本原子性

```lua
local stock = tonumber(redis.call('GET', KEYS[1]) or '0')
local qty = tonumber(ARGV[1])
if stock >= qty then
  redis.call('DECRBY', KEYS[1], qty)
  return 1
end
return 0
```

**优势：**
- 原子执行，不会被其他命令打断
- 减少网络往返次数
- 避免竞态条件

### 3. 分布式锁正确使用

```java
lockService.executeWithLock(lockKey, 2, 5, () -> {
    // 临界区代码
    return counterService.tryDeduct(stockKey, quantity);
});
```

**注意事项：**
- 设置合理的超时时间（避免死锁）
- 在finally块中释放锁
- 检查当前线程是否持有锁

---

## 📝 学习笔记

详细的学习笔记请查看 [week1-summary.md](week1-summary.md)，包含：

- Java并发设计要点
- Redis常见问题及解决方案
- 分布式系统理论基础（CAP定理）
- AI Copilot使用体验

---

## 🧰 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 编程语言 |
| Spring Boot | 3.2.5 | 应用框架 |
| Redis | 7 | 分布式缓存/锁 |
| Redisson | 3.27.2 | Redis客户端（分布式锁） |
| Testcontainers | 1.19.8 | 集成测试（Docker容器） |
| JUnit 5 | - | 单元测试框架 |
| Maven | 3.9+ | 构建工具 |

---

## 👥 作者

Training Team

---

## 📄 许可证

本项目仅供学习使用。

---

## 🔗 相关资源

- [Java并发编程实战](https://docs.oracle.com/javase/tutorial/essential/concurrency/)
- [Redis官方文档](https://redis.io/documentation)
- [Spring Boot指南](https://spring.io/guides)
- [Redisson Wiki](https://github.com/redisson/redisson/wiki)
