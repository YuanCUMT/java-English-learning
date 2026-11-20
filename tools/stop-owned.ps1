$ErrorActionPreference='Stop'
$Root=Split-Path -Parent $PSScriptRoot
$Jar=[IO.Path]::GetFullPath((Join-Path $Root 'app\daily-reading-0.1.0.jar'))
$LogRoot=Join-Path $Root 'data\launcher'
foreach($Mode in @('demo','mysql')) {
  $State=Join-Path $LogRoot ($Mode+'.json')
  if(-not(Test-Path -LiteralPath $State)) { continue }
  $Saved=Get-Content -Raw -LiteralPath $State | ConvertFrom-Json
  $ProcessInfo=Get-CimInstance Win32_Process -Filter "ProcessId=$($Saved.pid)" -ErrorAction SilentlyContinue
  if($ProcessInfo -and $ProcessInfo.CommandLine -and $ProcessInfo.CommandLine.Contains($Jar)) {
    Stop-Process -Id $Saved.pid -ErrorAction Stop
    Write-Host "已停止本工程 $Mode 进程。"
  } else { Write-Host "没有匹配的 $Mode 工程进程，未终止其他程序。" }
}
