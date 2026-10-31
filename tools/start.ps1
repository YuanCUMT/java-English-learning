# 一键启动：MySQL -> 应用 -> 浏览器
$ErrorActionPreference = 'Continue'
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

function Test-Port($p) {
  [bool](Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue)
}
function Wait-Port($p, $seconds) {
  for ($i = 0; $i -lt $seconds; $i++) {
    if (Test-Port $p) { return $true }
    Start-Sleep -Seconds 1
  }
  return (Test-Port $p)
}

Write-Host ""
Write-Host "  每日阅读 · Java 版" -ForegroundColor Yellow
Write-Host "  ==================" -ForegroundColor DarkGray

# 1. MySQL
if (Test-Port 3306) {
  Write-Host "  [1/3] MySQL 已在运行" -ForegroundColor Green
} else {
  Write-Host "  [1/3] 正在启动 MySQL ..." -ForegroundColor Cyan
  & powershell -NoProfile -ExecutionPolicy Bypass -File "D:\MySQLServer\mysql-start.ps1" -NoPause
  if (-not (Wait-Port 3306 30)) {
    Write-Host "  [x] MySQL 30 秒内没起来。手动跑：D:\MySQLServer\mysql-start.ps1" -ForegroundColor Red
    Read-Host "按回车关闭"
    exit 1
  }
  Write-Host "       MySQL 已就绪" -ForegroundColor Green
}

# 2. 应用
if (Test-Port 8080) {
  Write-Host "  [2/3] 应用已在运行，直接开浏览器" -ForegroundColor Green
} else {
  $jar = Join-Path $Root 'target\daily-reading-0.1.0.jar'
  if (-not (Test-Path $jar)) {
    Write-Host "  [x] 没找到 $jar" -ForegroundColor Red
    Write-Host "      先构建：mvn package -DskipTests" -ForegroundColor DarkGray
    Read-Host "按回车关闭"
    exit 1
  }
  Write-Host "  [2/3] 正在启动应用 ..." -ForegroundColor Cyan
  $java = 'C:\Program Files\Java\jdk-21.0.12.1\bin\java.exe'
  if (-not (Test-Path $java)) { $java = 'java' }
  Start-Process -FilePath $java -ArgumentList '-jar', $jar `
    -WorkingDirectory $Root -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $Root 'run.log') `
    -RedirectStandardError (Join-Path $Root 'run.err.log')
  if (-not (Wait-Port 8080 60)) {
    Write-Host "  [x] 应用 60 秒内没起来，看 run.err.log" -ForegroundColor Red
    Read-Host "按回车关闭"
    exit 1
  }
  Write-Host "       应用已就绪" -ForegroundColor Green
}

# 3. 浏览器
Write-Host "  [3/3] http://127.0.0.1:8080" -ForegroundColor Yellow
Start-Process 'cmd.exe' -ArgumentList '/c','start','','http://127.0.0.1:8080' -WindowStyle Hidden
Start-Sleep -Seconds 2
