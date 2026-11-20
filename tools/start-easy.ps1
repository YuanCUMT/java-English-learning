param([ValidateSet('demo','mysql')][string]$Mode='demo',[switch]$NoBrowser)
$ErrorActionPreference='Stop'
$Root=Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $Root
$Logs=Join-Path $Root 'data\launcher'
New-Item -ItemType Directory -Force $Logs | Out-Null
$State=Join-Path $Logs ($Mode+'.json')
function Ready($Port) {
  try {
    $r=Invoke-RestMethod "http://127.0.0.1:$Port/api/health" -TimeoutSec 2
    return ($r.data.app -eq 'daily-reading' -and $r.data.ready -eq $true -and $r.data.mode -eq $Mode)
  } catch { return $false }
}
function Open-ReadingPage($Port) {
  $Url="http://127.0.0.1:$Port"
  Write-Host "如果网页没有显示，请手动打开：$Url" -ForegroundColor Cyan
  foreach($InstallRoot in @($env:ProgramFiles,${env:ProgramFiles(x86)},$env:LOCALAPPDATA)) {
    if([string]::IsNullOrWhiteSpace($InstallRoot)) { continue }
    foreach($RelativePath in @('Microsoft\Edge\Application\msedge.exe','Google\Chrome\Application\chrome.exe')) {
      $Browser=Join-Path $InstallRoot $RelativePath
      if(Test-Path -LiteralPath $Browser) {
        try {
          Start-Process -FilePath $Browser -ArgumentList @('--new-window',$Url) -ErrorAction Stop
          return
        } catch { }
      }
    }
  }
  try { Start-Process $Url -ErrorAction Stop }
  catch { Write-Host '未能自动打开浏览器，应用仍在运行，请复制上面的地址访问。' -ForegroundColor Yellow }
}
try {
  if(Test-Path -LiteralPath $State) {
    $last=Get-Content -Raw -LiteralPath $State | ConvertFrom-Json
    if(Ready $last.port) {
      Write-Host "应用已就绪：http://127.0.0.1:$($last.port)" -ForegroundColor Green
      if(-not $NoBrowser) { Open-ReadingPage $last.port }
      exit 0
    }
  }
  $Java=Join-Path $Root 'runtime\bin\java.exe'
  if(-not(Test-Path -LiteralPath $Java)) {
    if($env:JAVA_HOME) { $Java=Join-Path $env:JAVA_HOME 'bin\java.exe' }
    if(-not(Test-Path -LiteralPath $Java)) { $Java=(Get-Command java -ErrorAction Stop).Source }
  }
  $Jar=Join-Path $Root 'app\daily-reading-0.1.0.jar'
  if(-not(Test-Path -LiteralPath $Jar)) { throw '缺少 app\daily-reading-0.1.0.jar，请重新取得完整工程。' }
  $Port=8080
  # Query the .NET network API rather than CIM/WMI, which can fail under restricted accounts.
  $ListeningPorts=@([System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | ForEach-Object { $_.Port })
  while($Port -in $ListeningPorts) {
    $Port++
    if($Port -gt 8099) { throw '8080–8099 端口均被占用，请稍后重试。' }
  }
  $Temp=Join-Path $Root 'data\launcher\tmp'
  New-Item -ItemType Directory -Force -Path $Temp | Out-Null
  $Arguments=@(('"-Djava.io.tmpdir={0}"' -f $Temp),'-jar',('"{0}"' -f $Jar),"--server.port=$Port",'--server.address=127.0.0.1')
  if($Mode -eq 'demo') {
    Write-Host '免安装体验：无需 MySQL、Maven 或 API Key。首次导入词典，请等待。' -ForegroundColor Cyan
    $Arguments+=@('--spring.profiles.active=demo','--spring.config.import=optional:classpath:/demo-disabled.yml','--app.deepseek.api-key=','--app.ocr.baidu.api-key=','--app.ocr.baidu.secret-key=')
  } else {
    Write-Host 'MySQL 模式：请先按 README 建库并配置账号，不会自动删除或重建数据。' -ForegroundColor Cyan
  }
  $Process=Start-Process -FilePath $Java -ArgumentList $Arguments -WorkingDirectory $Root -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $Logs ($Mode+'.log')) -RedirectStandardError (Join-Path $Logs ($Mode+'.err.log'))
  @{pid=$Process.Id;port=$Port;mode=$Mode;jar=$Jar} | ConvertTo-Json | Set-Content -LiteralPath $State -Encoding UTF8
  $OK=$false
  for($i=0;$i -lt 120;$i++) {
    if(Ready $Port) { $OK=$true; break }
    $Process.Refresh()
    if($Process.HasExited) { throw "程序退出。请查看 data\launcher\$Mode.err.log 和 $Mode.log。" }
    Start-Sleep -Seconds 1
  }
  if(-not $OK) { throw "等待启动超时。请查看 data\launcher\$Mode.log；不要重复删除数据库。" }
  Write-Host "已就绪：http://127.0.0.1:$Port" -ForegroundColor Green
  if($Mode -eq 'demo') { Write-Host '点击「直接体验示例阅读」，或注册自己的账号。固定示例不是实时 AI 生成。' }
  if(-not $NoBrowser) { Open-ReadingPage $Port }
} catch {
  Write-Host $_.Exception.Message -ForegroundColor Red
  exit 1
}
