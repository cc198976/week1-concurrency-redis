package com.training.week1.concurrency.model;

/**
 * 订单处理结果数据模型（Record类）
 * 
 * 封装订单处理的执行结果，包括：
 * - 订单ID和处理线程信息
 * - 执行耗时统计
 * - 成功/失败状态及原因
 * 
 * @param orderId    订单ID
 * @param workerName 处理该订单的工作线程名称
 * @param elapsedMs  处理耗时（毫秒）
 * @param success    是否处理成功
 * @param message    处理结果描述信息
 * 
 * @author Ace Chen
 */
public record ProcessResult(long orderId, String workerName, long elapsedMs, boolean success, String message) {
}
