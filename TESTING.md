# 测试说明文档

## 测试类型

本项目包含两种类型的测试：

### 1. 单元测试（无需Docker）
- `OrderProcessorSimulationTest.java` - 订单处理器单元测试
- **特点**：纯Java代码测试，不需要外部依赖
- **运行方式**：`mvn test`

### 2. 集成测试（需要Docker）
- `DistributedLockServiceTest.java` - 分布式锁集成测试
- `FlashSaleCounterServiceTest.java` - 秒杀计数器集成测试
- **特点**：使用Testcontainers启动Redis容器进行真实环境测试
- **运行方式**：需要先启动Docker Desktop

---

## 如何运行测试

### 方案A：只运行单元测试（推荐初学者）

如果您还没有安装Docker，可以先运行单元测试验证代码逻辑：

```powershell
# 运行所有单元测试
mvn test

# 或者运行特定测试类
mvn test -Dtest=OrderProcessorSimulationTest
```

### 方案B：运行所有测试（包括集成测试）

#### 前置条件：
1. 安装 Docker Desktop
2. 启动 Docker Desktop
3. 验证Docker正常运行：`docker ps`

#### 运行命令：
```powershell
# 运行所有测试（包括集成测试）
mvn clean test

# 或者跳过单元测试，只运行集成测试
mvn test -Dtest=DistributedLockServiceTest,FlashSaleCounterServiceTest
```

---

## 如果没有Docker怎么办？

### 选项1：安装Docker Desktop（推荐）
- 下载地址：https://www.docker.com/products/docker-desktop
- Windows系统直接下载安装即可
- 学习Docker对后续微服务开发很有帮助

### 选项2：暂时跳过集成测试
在pom.xml中已经配置了Surefire插件，默认会跳过标记为集成的测试。

您可以：
1. 先专注于单元测试和代码学习
2. 等安装好Docker后再运行集成测试

### 选项3：使用本地Redis替代Testcontainers

修改测试基类 `RedisTestSupport.java`，不使用Testcontainers：

```java
// 注释掉@Testcontainers和相关代码
// 直接在application.yml中配置本地Redis地址
```

**注意**：这种方式不推荐，因为失去了测试的隔离性。

---

## 常见问题

### Q1: Docker Desktop启动后仍然报错？

**解决方案**：
```powershell
# 1. 检查Docker是否真的在运行
docker ps

# 2. 重启Docker Desktop
# 右键任务栏Docker图标 → Restart

# 3. 检查Docker权限
# 确保当前用户有Docker使用权限
```

### Q2: Testcontainers下载镜像很慢？

**解决方案**：配置国内镜像加速器
1. 打开Docker Desktop设置
2. Docker Engine → 添加镜像加速器：
```json
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com"
  ]
}
```

### Q3: 只想验证代码能否编译通过？

**解决方案**：
```powershell
# 只编译，不运行测试
mvn clean compile

# 或者打包时跳过测试
mvn clean package -DskipTests
```

---

## 测试覆盖的功能

### OrderProcessorSimulationTest
- ✅ 多线程订单处理
- ✅ CompletableFuture结果聚合
- ✅ 异常情况处理（无效数量）
- ✅ 并发执行验证

### DistributedLockServiceTest
- ✅ 分布式锁互斥性
- ✅ 临界区串行化执行
- ✅ 高并发场景数据一致性
- ⚠️ 需要Docker环境

### FlashSaleCounterServiceTest
- ✅ Redis原子性扣减
- ✅ 库存不足处理
- ✅ 高并发防超卖验证
- ⚠️ 需要Docker环境

---

## 建议的学习路径

1. **第一阶段**：理解代码
   - 阅读源代码和注释
   - 运行 `mvn clean compile` 确保编译通过

2. **第二阶段**：单元测试
   - 运行 `mvn test -Dtest=OrderProcessorSimulationTest`
   - 理解测试用例的设计思路

3. **第三阶段**：安装Docker
   - 下载并安装Docker Desktop
   - 学习基本的Docker命令

4. **第四阶段**：集成测试
   - 运行完整的集成测试
   - 观察Testcontainers如何启动Redis
   - 理解真实环境下的并发控制

5. **第五阶段**：压力测试
   - 启动应用：`mvn spring-boot:run`
   - 运行PowerShell压测脚本
   - 分析性能和并发表现

---

## 总结

- **没有Docker**：可以运行单元测试，学习代码逻辑
- **有Docker**：可以运行完整测试，验证分布式功能
- **建议**：尽早安装Docker，对后续学习很有帮助

祝您学习顺利！🎉
