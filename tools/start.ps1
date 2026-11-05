# 一键启动：MySQL -> 应用 -> 浏览器
# 不写死任何本机路径：MySQL 依次找环境变量 / 常见位置 / 已在运行；JDK 依次找 JAVA_HOME / PATH。
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
# 端口通了不代表能访问：要真发一个请求，拿到非 5xx 才算就绪。
# 否则浏览器会被打开在一个「正在启动」的页面上，看起来就像启动报错。
function Wait-Http($url, $seconds) {
  for ($i = 0; $i -lt ($seconds * 4); $i++) {
    try {
      $r = Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 3 -MaximumRedirection 0 -ErrorAction Stop
      if ($r.StatusCode -lt 500) { return $true }
    } catch {
      $code = $null
      if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
      if ($code -and $code -lt 500) { return $true }
    }
    Start-Sleep -Milliseconds 250
  }
  return $false
}
function Find-MySqlStart {
  if ($env:MYSQL_START_SCRIPT -and (Test-Path $env:MYSQL_START_SCRIPT)) { return $env:MYSQL_START_SCRIPT }
  $candidates = @(
    'C:\MySQLServer\mysql-start.ps1',
    (Join-Path $env:USERPROFILE 'MySQLServer\mysql-start.ps1'),
    'D:\MySQLServer\mysql-start.ps1',
    'C:\Program Files\MySQL\mysql-start.ps1'
  )
  foreach ($c in $candidates) { if (Test-Path $c) { return $c } }
  return $null
}
function Find-Java {
  $home = $env:JAVA_HOME
  if ($home) {
    $j = Join-Path $home 'bin\java.exe'
    if (Test-Path $j) { return $j }
  }
  # JAVA_HOME 没设时别直接用 PATH 上那个：这台机器 PATH 上是 JDK 25，
  # 跑 Spring Boot 3.3 会冒一堆 "restricted method" 警告，看着像报错。
  $roots = @('C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Microsoft')
  foreach ($r in $roots) {
    if (-not (Test-Path $r)) { continue }
    $cand = Get-ChildItem $r -Directory -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -match 'jdk-?21' -or $_.Name -match 'jdk-21' } |
      Sort-Object Name -Descending | Select-Object -First 1
    if ($cand) {
      $j = Join-Path $cand.FullName 'bin\java.exe'
      if (Test-Path $j) { return $j }
    }
  }
  $cmd = Get-Command java -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  return $null
}

Write-Host ""
Write-Host "  每日阅读 · Java 版" -ForegroundColor Yellow
Write-Host "  ==================" -ForegroundColor DarkGray

# 1. MySQL
if (Test-Port 3306) {
  Write-Host "  [1/3] MySQL 已在运行" -ForegroundColor Green
} else {
  $starter = Find-MySqlStart
  if (-not $starter) {
    Write-Host "  [x] 3306 没在监听，也没找到启动脚本" -ForegroundColor Red
    Write-Host "      要么先手动启动 MySQL，要么把启动脚本路径放进环境变量 MYSQL_START_SCRIPT" -ForegroundColor DarkGray
    Read-Host "按回车关闭"
    exit 1
  }
  Write-Host "  [1/3] 正在启动 MySQL（$starter）..." -ForegroundColor Cyan
  & powershell -NoProfile -ExecutionPolicy Bypass -File $starter -NoPause
  if (-not (Wait-Port 3306 30)) {
    Write-Host "  [x] MySQL 30 秒内没起来，手动跑：$starter" -ForegroundColor Red
    Read-Host "按回车关闭"
    exit 1
  }
  Write-Host "       MySQL 已就绪" -ForegroundColor Green
}

# 2. 应用
if (Test-Port 8080) {
  Write-Host "  [2/3] 应用已在运行，直接开浏览器" -ForegroundColor Green
  if (-not (Wait-Http 'http://127.0.0.1:8080/login' 10)) {
    Write-Host "       端口在监听但还没响应，等一下再刷新页面" -ForegroundColor Yellow
  }
} else {
  $jar = Get-ChildItem -Path (Join-Path $Root 'target') -Filter 'daily-reading-*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -notlike '*sources*' -and $_.Name -notlike '*javadoc*' } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
  if (-not $jar) {
    Write-Host "  [x] target 下没有可运行的 jar" -ForegroundColor Red
    Write-Host "      先构建：mvn package -DskipTests" -ForegroundColor DarkGray
    Read-Host "按回车关闭"
    exit 1
  }
  $java = Find-Java
  if (-not $java) {
    Write-Host "  [x] 找不到 java，装个 JDK 21 或设置 JAVA_HOME" -ForegroundColor Red
    Read-Host "按回车关闭"
    exit 1
  }
  Write-Host "  [2/3] 正在启动应用（$java）..." -ForegroundColor Cyan
  Start-Process -FilePath $java -ArgumentList '-jar', $jar.FullName `
    -WorkingDirectory $Root -WindowStyle Hidden `
    -RedirectStandardOutput (Join-Path $Root 'run.log') `
    -RedirectStandardError (Join-Path $Root 'run.err.log')
  if (-not (Wait-Http 'http://127.0.0.1:8080/login' 60)) {
    Write-Host "  [x] 应用 60 秒内没响应，看 run.err.log / run.log" -ForegroundColor Red
    Read-Host "按回车关闭"
    exit 1
  }
  Write-Host "       应用已就绪" -ForegroundColor Green
}

# 3. 浏览器
Write-Host "  [3/3] http://127.0.0.1:8080" -ForegroundColor Yellow
Start-Process 'explorer.exe' -ArgumentList 'http://127.0.0.1:8080'
Start-Sleep -Seconds 2