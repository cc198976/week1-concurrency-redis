package com.training.week1.concurrency;

import com.training.week1.concurrency.model.Order;
import com.training.week1.concurrency.model.ProcessResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 订单处理器模拟服务（并发编程演示）
 * 
 * 本服务演示Java多线程并发处理的核心技术：
 * 1. ThreadPoolExecutor - 自定义线程池配置
 * 2. BlockingQueue - 有界队列控制并发度
 * 3. CompletableFuture - 异步任务编排和结果聚合
 * 4. ThreadFactory - 自定义线程命名和管理
 * 
 * 典型应用场景：
 * - 批量订单处理
 * - 并行数据导入
 * - 异步任务调度
 * 
 * @author Ace Chen
 */
@Service
public class OrderProcessorSimulation {

    /** 任务队列容量：限制最大等待任务数，防止内存溢出 */
    private static final int QUEUE_CAPACITY = 64;

    /**
     * 批量处理订单并聚合结果
     * 
     * 处理流程：
     * 1. 创建固定大小的线程池，使用有界队列
     * 2. 为每个订单创建异步任务（CompletableFuture）
     * 3. 等待所有任务完成（allOf + join）
     * 4. 收集并返回所有处理结果
     * 5. 优雅关闭线程池
     * 
     * @param orders   待处理的订单列表
     * @param poolSize 线程池大小（并发度）
     * @return 所有订单的处理结果列表（保持提交顺序）
     * @throws InterruptedException 如果等待过程中被中断
     */
    public List<ProcessResult> processOrders(List<Order> orders, int poolSize) throws InterruptedException {
        // 步骤1：创建有界阻塞队列，限制内存使用
        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        
        // 步骤2：原子计数器用于生成唯一的线程名称
        AtomicInteger threadIndex = new AtomicInteger(1);
        
        // 步骤3：自定义线程工厂，便于调试和监控
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("order-worker-" + threadIndex.getAndIncrement());
            thread.setDaemon(false); // 非守护线程，确保任务执行完毕
            return thread;
        };

        // 步骤4：创建固定大小线程池
        // 参数说明：
        // - corePoolSize = maxPoolSize：固定线程数
        // - keepAliveTime：多余线程的空闲存活时间（这里不会有多余线程）
        // - workQueue：有界队列，防止任务无限堆积
        // - handler：CallerRunsPolicy - 队列满时由调用线程执行，起到背压作用
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                poolSize,                    // 核心线程数
                poolSize,                    // 最大线程数
                30L,                         // 空闲线程存活时间
                TimeUnit.SECONDS,            // 时间单位
                workQueue,                   // 工作队列
                threadFactory,               // 线程工厂
                new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
        );

        try {
            // 步骤5：为每个订单创建异步处理任务
            List<CompletableFuture<ProcessResult>> futures = new ArrayList<>(orders.size());
            for (Order order : orders) {
                // supplyAsync：创建有返回值的异步任务
                // 使用自定义线程池而非默认的ForkJoinPool
                CompletableFuture<ProcessResult> future = CompletableFuture.supplyAsync(
                        () -> processOne(order),  // 任务逻辑：处理单个订单
                        executor                  // 指定线程池
                );
                futures.add(future);
            }

            // 步骤6：等待所有任务完成
            // allOf：组合多个CompletableFuture，返回一个新的CompletableFuture
            // join：阻塞等待所有任务完成（类似Thread.join）
            CompletableFuture<Void> allDone = CompletableFuture.allOf(
                    futures.toArray(CompletableFuture[]::new)
            );
            allDone.join(); // 阻塞直到所有订单处理完成

            // 步骤7：收集所有处理结果（按提交顺序）
            List<ProcessResult> results = new ArrayList<>(futures.size());
            for (CompletableFuture<ProcessResult> future : futures) {
                results.add(future.join()); // 获取每个任务的结果
            }
            return results;
            
        } finally {
            // 步骤8：优雅关闭线程池，释放资源
            executor.shutdown(); // 不再接受新任务，等待已提交任务完成
            
            // 等待30秒，如果还有任务未执行完则强制关闭
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow(); // 强制中断所有正在执行的任务
            }
        }
    }

    /**
     * 处理单个订单（模拟业务逻辑）
     * 
     * 模拟真实的订单处理流程：
     * 1. 支付验证（模拟网络IO）
     * 2. 库存检查（模拟数据库查询）
     * 3. 订单创建（模拟写操作）
     * 
     * @param order 待处理的订单
     * @return 处理结果（包含成功/失败状态和耗时）
     */
    private ProcessResult processOne(Order order) {
        // 记录当前工作线程名称（用于追踪并发执行情况）
        String worker = Thread.currentThread().getName();
        
        // 记录开始时间（纳秒级精度）
        long start = System.nanoTime();
        
        try {
            // 模拟业务处理耗时（50-120ms的随机延迟）
            // 真实场景中可能是：RPC调用、数据库查询、第三方API请求等
            Thread.sleep(50 + (order.id() % 7) * 10L);
            
            // 业务规则验证：订单数量必须大于0
            if (order.quantity() <= 0) {
                return new ProcessResult(
                        order.id(),
                        worker,
                        elapsedMs(start),
                        false,
                        "invalid quantity"  // 失败原因：无效数量
                );
            }
            
            // 成功处理订单
            return new ProcessResult(
                    order.id(),
                    worker,
                    elapsedMs(start),
                    true,
                    "processed sku=" + order.sku()  // 成功消息
            );
            
        } catch (InterruptedException interrupted) {
            // 处理中断异常（线程被强制终止）
            // 重要：恢复中断状态，让上层代码知道发生了中断
            Thread.currentThread().interrupt();
            
            return new ProcessResult(
                    order.id(),
                    worker,
                    elapsedMs(start),
                    false,
                    "interrupted"  // 失败原因：被中断
            );
        }
    }

    /**
     * 计算执行耗时（毫秒）
     * 
     * @param startNano 开始时间戳（纳秒）
     * @return 耗时（毫秒）
     */
    private static long elapsedMs(long startNano) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNano);
    }
}
