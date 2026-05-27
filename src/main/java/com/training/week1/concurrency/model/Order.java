package com.training.week1.concurrency.model;

/**
 * 订单数据模型（Record类）
 * 
 * 使用Java 17的record特性创建不可变的数据载体类，
 * 用于表示订单的基本信息。
 * 
 * @param id       订单唯一标识ID
 * @param sku      商品SKU编码（Stock Keeping Unit）
 * @param quantity 购买数量
 * 
 * @author Ace Chen
 */
public record Order(long id, String sku, int quantity) {
}
