# 订单处理接口压力测试脚本（PowerShell）
# 
# 用途：模拟批量订单的并发处理
# 使用方法：
#   .\scripts\stress-orders.ps1
#
# 测试场景：
# - 生成100个测试订单
# - 使用8个线程并行处理
# - 统计处理耗时和成功率
#
# 前置条件：
# - 应用已启动（http://localhost:8080）

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  订单处理接口压力测试" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 配置参数
$baseUrl = "http://localhost:8080/api/orders"
$orderCount = 100
$poolSize = 8

Write-Host "[步骤1] 生成 $orderCount 个测试订单" -ForegroundColor Yellow

# 生成订单JSON数组
$orders = @()
for ($i = 1; $i -le $orderCount; $i++) {
    $orders += @{
        id = $i
        sku = "SKU-$([string]::Format('{0:D4}', $i))"
        quantity = (Get-Random -Minimum 1 -Maximum 5)
    }
}

# 转换为JSON格式
$jsonBody = $orders | ConvertTo-Json -Depth 3

Write-Host "  ✓ 订单生成完成" -ForegroundColor Green
Write-Host ""
Write-Host "[步骤2] 发送批量处理请求（线程池大小: $poolSize）" -ForegroundColor Yellow
Write-Host "  请稍候..." -ForegroundColor Gray
Write-Host ""

# 记录开始时间
$startTime = Get-Date

try {
    # 发送POST请求
    $response = Invoke-RestMethod -Uri "$baseUrl/process?poolSize=$poolSize" `
                                  -Method Post `
                                  -Body $jsonBody `
                                  -ContentType "application/json"
    
    # 记录结束时间
    $endTime = Get-Date
    $duration = ($endTime - $startTime).TotalSeconds
    
    Write-Host "[步骤3] 处理结果统计" -ForegroundColor Yellow
    Write-Host "  总订单数: $($response.Count)" -ForegroundColor White
    Write-Host "  处理耗时: $([math]::Round($duration, 2)) 秒" -ForegroundColor White
    Write-Host "  平均耗时: $([math]::Round($duration / $orderCount * 1000, 2)) 毫秒/订单" -ForegroundColor White
    
    # 统计成功和失败数量
    $successCount = ($response | Where-Object { $_.success -eq $true }).Count
    $failCount = ($response | Where-Object { $_.success -eq $false }).Count
    
    Write-Host "  成功数量: $successCount" -ForegroundColor Green
    Write-Host "  失败数量: $failCount" -ForegroundColor $(if ($failCount -gt 0) { "Red" } else { "Gray" })
    
    # 统计使用的线程数
    $workerThreads = ($response | Select-Object -ExpandProperty workerName | Sort-Object -Unique).Count
    Write-Host "  工作线程数: $workerThreads" -ForegroundColor White
    
    Write-Host ""
    Write-Host "[步骤4] 样本结果展示（前5条）" -ForegroundColor Yellow
    $response | Select-Object -First 5 | ForEach-Object {
        $status = if ($_.success) { "✓ 成功" } else { "✗ 失败" }
        $color = if ($_.success) { "Green" } else { "Red" }
        Write-Host "  订单$($_.orderId): $status | 线程: $($_.workerName) | 耗时: $($_.elapsedMs)ms | $($_.message)" -ForegroundColor $color
    }
    
    Write-Host ""
    Write-Host "========================================" -ForegroundColor Cyan
    Write-Host "  测试完成" -ForegroundColor Cyan
    Write-Host "========================================" -ForegroundColor Cyan
    
} catch {
    Write-Host "  ✗ 请求失败: $_" -ForegroundColor Red
    Write-Host "  请确认应用已启动并运行在 http://localhost:8080" -ForegroundColor Yellow
}
