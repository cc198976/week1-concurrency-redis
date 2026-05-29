package com.training.week1.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Redis测试支持基类
 * 
 * 使用Testcontainers框架在测试中自动启动Redis容器，
 * 避免依赖外部Redis服务，确保测试的独立性和可重复性。
 * 
 * 兼容性配置：
 * 1. 禁用 Ryuk 容器（避免 Docker Desktop 4.75.0 API 异常）
 * 2. 添加 Docker 连接重试机制
 * 3. 使用 Testcontainers 1.20.6+（已更新 pom.xml）
 * 
 * 使用方法：
 * 让测试类继承此类，即可自动获得Redis容器支持。
 * Spring会自动将容器的地址和端口注入到环境中。
 * 
 * @author Ace Chen
 */
@Testcontainers(disabledWithoutDocker = true)
public abstract class RedisTestSupport {

    static {
        // 解决 Docker Desktop API 兼容性问题的配置
        // 1. 禁用 Ryuk 容器（资源清理容器，会导致连接失败）
        System.setProperty("testcontainers.ryuk.disabled", "true");
        // 2. 使用 EnvironmentAndSystemProperty 策略（最兼容的策略）
        System.setProperty("testcontainers.docker.client.strategy", "org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy");
        // 3. 增加 Docker 客户端超时时间（秒）
        System.setProperty("testcontainers.docker.client.timeout", "30");
        // 4. 禁用容器复用，确保每次测试使用新容器
        System.setProperty("testcontainers.attempt.reuse", "false");
        // 5. 禁用 Docker Machine 检测（加速启动）
        System.setProperty("testcontainers.docker.client.disable-machine-check", "true");
    }

    /**
     * Redis测试容器（静态单例，所有测试共享）
     * 
     * 配置说明：
     * - 使用Redis 7 Alpine镜像（体积小、启动快）
     * - 暴露6379端口（Redis默认端口）
     * - @Container注解：JUnit生命周期管理（测试前启动，测试后销毁）
     * - static修饰：所有测试类共享同一个容器，提升性能
     */
    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(false); // 每次测试使用新容器，确保隔离性

    /**
     * 动态注册Redis连接属性到Spring环境
     * 
     * 工作原理：
     * 1. Testcontainers启动容器后，会分配一个随机可用端口
     * 2. 通过@DynamicPropertySource将容器地址注入Spring
     * 3. Spring Boot自动配置读取这些属性，创建Redis连接
     * 
     * 这样测试代码无需硬编码Redis地址，与生产环境配置保持一致。
     * 
     * @param registry 动态属性注册表（Spring Test提供）
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        // 注册Redis主机地址（通常是localhost或宿主机IP）
        registry.add("spring.data.redis.host", REDIS::getHost);
        
        // 注册Redis端口（Testcontainers映射后的随机端口）
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379).toString());
        
        System.out.println("[配置] Testcontainers Redis容器: " + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
    }
}
