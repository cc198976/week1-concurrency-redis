# 快速开始指南

## 🚀 5分钟快速上手

### 步骤1：验证环境（1分钟）

```powershell
# 检查Java版本（应该是17或更高）
java -version

# 检查Maven版本
mvn -version
```

### 步骤2：编译项目（2分钟）

```powershell
cd C:\workspace\week1-concurrency-redis

# 清理并编译（跳过测试）
mvn clean compile -DskipTests
```

如果看到 `BUILD SUCCESS`，说明编译成功！✅

### 步骤3：运行单元测试（2分钟）

```powershell
# 只运行不需要Docker的单元测试
mvn test -Dtest=OrderProcessorSimulationTest
```

应该看到类似输出：
```
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 📦 启动应用

### 方式1：使用Maven（推荐）

```powershell
mvn spring-boot:run
```

应用将在 http://localhost:8080 启动

### 方式2：打包后运行

```powershell
# 打包
mvn clean package -DskipTests

# 运行JAR文件
java -jar target/week1-concurrency-redis-1.0.0-SNAPSHOT.jar
```

---

## 🧪 测试API接口

### 1. 初始化库存

```powershell
curl -X POST "http://localhost:8080/api/flash-sale/init?stock=100"
```

### 2. 查询库存

```powershell
curl "http://localhost:8080/api/flash-sale/stock"
```

### 3. 购买商品

```powershell
curl -X POST "http://localhost:8080/api/flash-sale/buy?quantity=1"
```

### 4. 批量处理订单

```powershell
curl -X POST "http://localhost:8080/api/orders/process?poolSize=4" ^
  -H "Content-Type: application/json" ^
  -d "[{\"id\":1,\"sku\":\"SKU-001\",\"quantity\":2},{\"id\":2,\"sku\":\"SKU-002\",\"quantity\":1}]"
```

---

## 🔧 常见问题速查

### Q1: 编译错误 "package does not exist"

**解决方案**：
```powershell
# 重新下载Maven依赖
mvn clean install -U
```

### Q2: 测试错误 "Could not find a valid Docker environment"

**原因**：集成测试需要Docker

**解决方案A**：安装Docker Desktop
- 下载：https://www.docker.com/products/docker-desktop
- 安装后启动Docker Desktop
- 重新运行测试

**解决方案B**：跳过集成测试
```powershell
# 只运行单元测试
mvn test -Dtest=OrderProcessorSimulationTest
```

### Q3: 端口8080已被占用

**解决方案**：
```powershell
# 查找占用端口的进程
netstat -ano | findstr :8080

# 终止进程（替换PID为实际进程ID）
taskkill /F /PID <PID>

# 或者修改application.yml中的端口号
```

### Q4: Redis连接失败

**如果使用Docker**：
```powershell
# 启动Redis容器
docker-compose up -d

# 检查容器状态
docker ps
```

**如果不使用Docker**：
- 应用可以正常启动（Redis只在特定接口中使用）
- 调用秒杀接口前需要先启动Redis

---

## 📚 学习路径建议

### Day 1-2: 理解代码
1. 阅读 [Week1Application.java](file:///C:/workspace/week1-concurrency-redis/src/main/java/com/training/week1/Week1Application.java)
2. 理解 [OrderProcessorSimulation.java](file:///C:/workspace/week1-concurrency-redis/src/main/java/com/training/week1/concurrency/OrderProcessorSimulation.java) 的线程池配置
3. 查看模型类 [Order.java](file:///C:/workspace/week1-concurrency-redis/src/main/java/com/training/week1/concurrency/model/Order.java) 和 [ProcessResult.java](file:///C:/workspace/week1-concurrency-redis/src/main/java/com/training/week1/concurrency/model/ProcessResult.java)

### Day 3-4: 学习Redis
1. 阅读 [FlashSaleCounterService.java](file:///C:/workspace/week1-concurrency-redis/src/main/java/com/training/week1/redis/FlashSaleCounterService.java) - Lua脚本
2. 阅读 [DistributedLockService.java](file:///C:/workspace/week1-concurrency-redis/src/main/java/com/training/week1/redis/DistributedLockService.java) - 分布式锁
3. 查看控制器 [FlashSaleController.java](file:///C:/workspace/week1-concurrency-redis/src/main/java/com/training/week1/web/FlashSaleController.java)

### Day 5: 运行测试
1. 运行单元测试
2. 安装Docker（如果需要）
3. 运行集成测试
4. 运行压力测试脚本

### Day 6-7: 深入学习
1. 阅读 [week1-summary.md](file:///C:/workspace/week1-concurrency-redis/week1-summary.md) 学习笔记
2. 尝试修改代码，调整参数
3. 观察不同配置下的性能表现

---

## 🎯 核心知识点

### Java并发
- ✅ ThreadPoolExecutor 线程池配置
- ✅ CompletableFuture 异步编程
- ✅ BlockingQueue 有界队列
- ✅ ThreadFactory 自定义线程工厂

### Redis分布式
- ✅ Lua脚本原子操作
- ✅ Redisson分布式锁
- ✅ 秒杀防超卖设计
- ✅ 高并发数据一致性

### Spring Boot
- ✅ REST API开发
- ✅ 依赖注入
- ✅ 配置管理
- ✅ 测试框架集成

---

## 📖 更多资源

- [详细README](README.md) - 完整的项目文档
- [测试说明](TESTING.md) - 如何运行测试
- [学习笔记](week1-summary.md) - 详细的理论知识
- [压力测试脚本](scripts/) - 性能测试工具

---

## 💡 小贴士

1. **代码都有详细注释**：每个类和方法都有中文注释，仔细阅读
2. **先理解再运行**：不要急于运行，先理解代码逻辑
3. **动手实验**：修改参数，观察效果变化
4. **记录问题**：遇到问题记录下来，查阅笔记或搜索解决

祝您学习愉快！🎉
