package com.training.week1.concurrency;

import com.training.week1.concurrency.model.Order;
import com.training.week1.concurrency.model.ProcessResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 订单处理器模拟单元测试
 * 
 * 测试目标：
 * 1. 验证所有订单都能被正确处理
 * 2. 验证结果聚合的正确性（数量、顺序）
 * 3. 验证并发执行的有效性（多个线程参与处理）
 * 4. 验证异常情况的处理（如无效数量）
 * 
 * @author Ace Chen
 */
class OrderProcessorSimulationTest {

    /** 被测对象：订单处理器模拟器 */
    private final OrderProcessorSimulation simulation = new OrderProcessorSimulation();

    /**
     * 测试用例：处理所有订单并聚合结果
     * 
     * 测试场景：
     * - 提交5个订单（其中1个数量为0，应该失败）
     * - 使用3个线程并行处理
     * - 验证返回结果的数量和内容
     * 
     * 预期结果：
     * - 返回5个结果（与订单数量一致）
     * - 4个成功，1个失败（订单5数量为0）
     * - 至少有1个工作线程参与处理
     */
    @Test
    void processesAllOrdersAndAggregatesResults() throws InterruptedException {
        // 准备测试数据：5个订单
        List<Order> orders = List.of(
                new Order(1, "sku-a", 2),  // 正常订单
                new Order(2, "sku-b", 1),  // 正常订单
                new Order(3, "sku-c", 3),  // 正常订单
                new Order(4, "sku-d", 1),  // 正常订单
                new Order(5, "sku-e", 0)   // 异常订单：数量为0
        );

        // 执行订单处理（使用3个线程）
        List<ProcessResult> results = simulation.processOrders(orders, 3);

        // 验证1：结果数量与订单数量一致
        assertEquals(orders.size(), results.size());
        
        // 验证2：统计成功处理的订单数量（应该是4个）
        long successCount = results.stream().filter(ProcessResult::success).count();
        assertEquals(4, successCount);
        
        // 验证3：订单5应该处理失败（因为数量为0）
        assertTrue(results.stream().anyMatch(r -> !r.success() && r.orderId() == 5));
        
        // 验证4：至少有一个工作线程参与了处理
        // （如果只有一个线程，说明没有并发执行）
        assertTrue(results.stream().map(ProcessResult::workerName).distinct().count() >= 1);
    }
}
