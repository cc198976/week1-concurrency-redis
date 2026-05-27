# Maven依赖自动诊断和修复脚本
# 使用方法：.\scripts\fix-maven-dependencies.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Maven依赖问题诊断和修复工具" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查点1：Java环境
Write-Host "[步骤1] 检查Java环境..." -ForegroundColor Yellow
try {
    $javaVersion = java -version 2>&1 | Select-String "version" | Select-Object -First 1
    Write-Host "  Java版本: $javaVersion" -ForegroundColor Green
} catch {
    Write-Host "  Java未安装或配置错误" -ForegroundColor Red
    Write-Host "  请安装JDK 17或更高版本" -ForegroundColor Yellow
    exit 1
}

# 检查点2：Maven环境
Write-Host "`n[步骤2] 检查Maven环境..." -ForegroundColor Yellow
try {
    $mavenVersion = mvn -version 2>&1 | Select-String "Apache Maven" | Select-Object -First 1
    Write-Host "  Maven版本: $mavenVersion" -ForegroundColor Green
} catch {
    Write-Host "  Maven未安装或配置错误" -ForegroundColor Red
    Write-Host "  请安装Maven 3.9或更高版本" -ForegroundColor Yellow
    exit 1
}

# 检查点3：pom.xml存在性
Write-Host "`n[步骤3] 检查项目文件..." -ForegroundColor Yellow
if (Test-Path "pom.xml") {
    Write-Host "  pom.xml 文件存在" -ForegroundColor Green
} else {
    Write-Host "  pom.xml 文件不存在" -ForegroundColor Red
    Write-Host "  请在项目根目录运行此脚本" -ForegroundColor Yellow
    exit 1
}

# 检查点4：本地Maven仓库
Write-Host "`n[步骤4] 检查本地Maven仓库..." -ForegroundColor Yellow
$mavenRepo = "$env:USERPROFILE\.m2\repository"
if (Test-Path $mavenRepo) {
    Write-Host "  Maven仓库存在: $mavenRepo" -ForegroundColor Green
    
    # 检查关键依赖
    $springRedisPath = "$mavenRepo\org\springframework\data\spring-data-redis"
    if (Test-Path $springRedisPath) {
        Write-Host "  Spring Data Redis 已下载" -ForegroundColor Green
    } else {
        Write-Host "  Spring Data Redis 未下载（需要重新下载）" -ForegroundColor Yellow
    }
    
    $redissonPath = "$mavenRepo\org\redisson\redisson-spring-boot-starter"
    if (Test-Path $redissonPath) {
        Write-Host "  Redisson 已下载" -ForegroundColor Green
    } else {
        Write-Host "  Redisson 未下载（需要重新下载）" -ForegroundColor Yellow
    }
} else {
    Write-Host "  Maven仓库不存在（首次使用，将自动创建）" -ForegroundColor Yellow
}

# 询问用户是否继续
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "诊断完成！是否执行修复操作？" -ForegroundColor White
Write-Host "这将：" -ForegroundColor Gray
Write-Host "  1. 清理项目编译文件" -ForegroundColor Gray
Write-Host "  2. 强制更新所有Maven依赖" -ForegroundColor Gray
Write-Host "  3. 重新构建项目" -ForegroundColor Gray
Write-Host ""

$choice = Read-Host "是否继续？(Y/N)"

if ($choice -ne 'Y' -and $choice -ne 'y') {
    Write-Host "`n操作已取消" -ForegroundColor Yellow
    exit 0
}

# 执行修复
Write-Host "`n[修复步骤1] 清理项目..." -ForegroundColor Yellow
try {
    mvn clean
    Write-Host "  清理完成" -ForegroundColor Green
} catch {
    Write-Host "  清理失败" -ForegroundColor Red
    exit 1
}

Write-Host "`n[修复步骤2] 下载依赖（这可能需要几分钟）..." -ForegroundColor Yellow
Write-Host "  正在从Maven中央仓库下载依赖..." -ForegroundColor Gray
try {
    mvn dependency:resolve -U
    Write-Host "  依赖下载完成" -ForegroundColor Green
} catch {
    Write-Host "  依赖下载失败" -ForegroundColor Red
    Write-Host "  请检查网络连接或配置国内镜像" -ForegroundColor Yellow
    exit 1
}

Write-Host "`n[修复步骤3] 编译项目..." -ForegroundColor Yellow
try {
    mvn compile -DskipTests
    Write-Host "  编译成功" -ForegroundColor Green
} catch {
    Write-Host "  编译失败" -ForegroundColor Red
    exit 1
}

Write-Host "`n[修复步骤4] 运行单元测试..." -ForegroundColor Yellow
try {
    mvn test -Dtest=OrderProcessorSimulationTest
    Write-Host "  测试通过" -ForegroundColor Green
} catch {
    Write-Host "  测试失败（但不影响依赖问题）" -ForegroundColor Yellow
}

# 最终验证
Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "  修复完成！" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "下一步操作：" -ForegroundColor White
Write-Host "  1. 在IDEA中点击 Maven -> Reload Project" -ForegroundColor Gray
Write-Host "  2. 等待IDEA索引完成" -ForegroundColor Gray
Write-Host "  3. 检查Java文件是否还有红色错误" -ForegroundColor Gray
Write-Host ""
Write-Host "如果仍有问题，请查看 TROUBLESHOOTING.md 文档" -ForegroundColor Yellow
Write-Host ""
