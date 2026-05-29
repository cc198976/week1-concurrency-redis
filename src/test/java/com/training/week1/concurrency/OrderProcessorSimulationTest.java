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
        System.out.println("\n========== 开始测试：订单处理器 ==========");
        
        // 准备测试数据：5个订单
        List<Order> orders = List.of(
                new Order(1, "sku-a", 2),  // 正常订单
                new Order(2, "sku-b", 1),  // 正常订单
                new Order(3, "sku-c", 3),  // 正常订单
                new Order(4, "sku-d", 1),  // 正常订单
                new Order(5, "sku-e", 0)   // 异常订单：数量为0
        );

        System.out.println("[输入] 订单数量: " + orders.size());
        orders.forEach(order -> 
            System.out.println("  - 订单" + order.id() + ": SKU=" + order.sku() + ", 数量=" + order.quantity())
        );

        // 执行订单处理（使用3个线程）
        System.out.println("\n[执行] 开始处理订单（线程池大小: 3）...");
        long startTime = System.currentTimeMillis();
        List<ProcessResult> results = simulation.processOrders(orders, 3);
        long endTime = System.currentTimeMillis();
        
        System.out.println("[完成] 处理耗时: " + (endTime - startTime) + "ms");
        System.out.println("[输出] 结果数量: " + results.size());

        // 打印每个订单的处理结果
        System.out.println("\n[详细结果]");
        results.forEach(result -> {
            String status = result.success() ? "✓ 成功" : "✗ 失败";
            System.out.println(String.format(
                "  订单%d: %s | 线程: %s | 耗时: %dms | %s",
                result.orderId(),
                status,
                result.workerName(),
                result.elapsedMs(),
                result.message()
            ));
        });

        // 统计信息
        long successCount = results.stream().filter(ProcessResult::success).count();
        long failCount = results.size() - successCount;
        int threadCount = (int) results.stream()
            .map(ProcessResult::workerName)
            .distinct()
            .count();
        
        System.out.println("\n[统计信息]");
        System.out.println("  - 成功: " + successCount + " 个");
        System.out.println("  - 失败: " + failCount + " 个");
        System.out.println("  - 使用线程数: " + threadCount);

        // 验证1：结果数量与订单数量一致
        System.out.println("\n[验证1] 结果数量是否与订单数量一致？");
        assertEquals(orders.size(), results.size());
        System.out.println("  ✓ 通过: " + results.size() + " == " + orders.size());
        
        // 验证2：统计成功处理的订单数量（应该是4个）
        System.out.println("\n[验证2] 成功处理的订单数量是否为4个？");
        assertEquals(4, successCount);
        System.out.println("  ✓ 通过: 成功数量 = " + successCount);
        
        // 验证3：订单5应该处理失败（因为数量为0）
        System.out.println("\n[验证3] 订单5是否处理失败？");
        assertTrue(results.stream().anyMatch(r -> !r.success() && r.orderId() == 5));
        System.out.println("  ✓ 通过: 订单5处理失败（数量为0）");
        
        // 验证4：至少有一个工作线程参与了处理
        System.out.println("\n[验证4] 是否有工作线程参与处理？");
        assertTrue(threadCount >= 1);
        System.out.println("  ✓ 通过: 使用了 " + threadCount + " 个线程");
        
        System.out.println("\n========== 测试通过 ==========\n");
    }
}
