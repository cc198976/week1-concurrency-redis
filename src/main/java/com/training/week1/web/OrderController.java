package com.training.week1.web;

import com.training.week1.concurrency.OrderProcessorSimulation;
import com.training.week1.concurrency.model.Order;
import com.training.week1.concurrency.model.ProcessResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单处理REST API控制器
 * 
 * 提供批量订单处理的HTTP接口，演示多线程并发处理能力。
 * 
 * 核心功能：
 * - 接收订单列表（JSON格式）
 * - 使用线程池并行处理订单
 * - 返回每个订单的处理结果（包含耗时、执行线程等信息）
 * 
 * API调用示例：
 * <pre>{@code
 * # 批量处理订单
 * curl -X POST "http://localhost:8080/api/orders/process?poolSize=4" \
 *   -H "Content-Type: application/json" \
 *   -d '[
 *     {"id": 1, "sku": "SKU-001", "quantity": 2},
 *     {"id": 2, "sku": "SKU-002", "quantity": 1},
 *     {"id": 3, "sku": "SKU-003", "quantity": 3}
 *   ]'
 * }</pre>
 * 
 * @author Ace Chen
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    /** 订单处理器模拟服务（注入Spring容器管理的Bean） */
    private final OrderProcessorSimulation simulation;

    /**
     * 构造函数：注入订单处理服务
     * 
     * @param simulation 订单处理器模拟服务实例
     */
    public OrderController(OrderProcessorSimulation simulation) {
        this.simulation = simulation;
    }

    /**
     * 批量处理订单
     * 
     * 接收订单列表，使用多线程并行处理，并返回每个订单的处理结果。
     * 
     * 处理流程：
     * 1. 接收HTTP请求中的订单列表（JSON自动反序列化为List<Order>）
     * 2. 调用OrderProcessorSimulation进行并发处理
     * 3. 等待所有订单处理完成
     * 4. 返回处理结果列表（JSON格式）
     * 
     * 性能特点：
     * - 并行处理：多个订单同时处理，提升吞吐量
     * - 线程复用：使用线程池避免频繁创建/销毁线程
     * - 结果聚合：保持订单提交顺序，便于前端展示
     * 
     * @param orders   待处理的订单列表（从HTTP请求体中读取JSON）
     * @param poolSize 线程池大小（控制并发度，默认4个线程）
     * @return 订单处理结果列表，每个结果包含：
     *         - orderId: 订单ID
     *         - workerName: 处理该订单的线程名称
     *         - elapsedMs: 处理耗时（毫秒）
     *         - success: 是否成功
     *         - message: 处理结果描述
     * @throws InterruptedException 如果处理过程中被中断
     */
    @PostMapping("/process")
    public List<ProcessResult> process(
            @RequestBody List<Order> orders,
            @RequestParam(defaultValue = "4") int poolSize
    ) throws InterruptedException {
        // 调用服务层进行并发处理
        // Spring MVC会自动将返回值序列化为JSON响应
        return simulation.processOrders(orders, poolSize);
    }
}
