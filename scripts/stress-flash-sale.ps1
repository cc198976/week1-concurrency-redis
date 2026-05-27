# 秒杀接口压力测试脚本（PowerShell）
# 
# 用途：模拟高并发场景下的商品购买请求
# 使用方法：
#   .\scripts\stress-flash-sale.ps1
#
# 测试场景：
# - 初始化100件库存
# - 发送200个并发购买请求
# - 验证最终库存为0（不会超卖）
#
# 前置条件：
# - 应用已启动（http://localhost:8080）
# - Redis服务正常运行

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  秒杀接口压力测试" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 配置参数
$baseUrl = "http://localhost:8080/api/flash-sale"
$initialStock = 100
$concurrentRequests = 200

Write-Host "[步骤1] 初始化库存: $initialStock 件" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/init?stock=$initialStock" -Method Post
    Write-Host "  ✓ 初始化成功，当前库存: $($response.stock)" -ForegroundColor Green
} catch {
    Write-Host "  ✗ 初始化失败: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[步骤2] 发送 $concurrentRequests 个并发购买请求..." -ForegroundColor Yellow
Write-Host "  请稍候，这可能需要几秒钟..." -ForegroundColor Gray
Write-Host ""

# 记录开始时间
$startTime = Get-Date

# 创建任务列表
$tasks = @()
$successCount = 0
$failCount = 0

# 发送并发请求
for ($i = 1; $i -le $concurrentRequests; $i++) {
    $task = Start-ThreadJob -ScriptBlock {
        param($url, $index)
        try {
            $response = Invoke-RestMethod -Uri $url -Method Post
            return @{
                Index = $index
                Success = $response.success
                Remaining = $response.remaining
            }
        } catch {
            return @{
                Index = $index
                Success = $false
                Error = $_.Exception.Message
            }
        }
    } -ArgumentList "$baseUrl/buy?quantity=1", $i
    
    $tasks += $task
}

# 等待所有任务完成
$tasks | Wait-Job | Out-Null

# 收集结果
$results = $tasks | Receive-Job
$tasks | Remove-Job

# 统计结果
$endTime = Get-Date
$duration = ($endTime - $startTime).TotalSeconds
$successCount = ($results | Where-Object { $_.Success -eq $true }).Count
$failCount = ($results | Where-Object { $_.Success -eq $false }).Count

Write-Host "[步骤3] 测试结果统计" -ForegroundColor Yellow
Write-Host "  总请求数: $concurrentRequests" -ForegroundColor White
Write-Host "  成功数量: $successCount" -ForegroundColor Green
Write-Host "  失败数量: $failCount (库存不足)" -ForegroundColor $(if ($failCount -gt 0) { "Red" } else { "Gray" })
Write-Host "  耗时: $([math]::Round($duration, 2)) 秒" -ForegroundColor White
Write-Host "  QPS: $([math]::Round($concurrentRequests / $duration, 2)) 请求/秒" -ForegroundColor White

Write-Host ""
Write-Host "[步骤4] 验证最终库存" -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/stock" -Method Get
    $finalStock = $response.stock
    Write-Host "  最终库存: $finalStock" -ForegroundColor White
    
    if ($finalStock -eq 0) {
        Write-Host "  ✓ 测试通过：库存正确扣减至0，未出现超卖" -ForegroundColor Green
    } elseif ($finalStock -lt 0) {
        Write-Host "  ✗ 测试失败：库存为负数（超卖）！" -ForegroundColor Red
    } else {
        Write-Host "  ⚠ 警告：库存剩余 $finalStock 件（可能部分请求失败）" -ForegroundColor Yellow
    }
} catch {
    Write-Host "  ✗ 查询库存失败: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  测试完成" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
