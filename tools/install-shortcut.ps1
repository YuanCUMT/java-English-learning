# 在桌面创建「每日阅读」快捷方式
# 用法：powershell -ExecutionPolicy Bypass -File tools\install-shortcut.ps1
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot

# 图标先复制到 LocalAppData：放在项目目录里 Explorer 有时读不到（权限/路径问题），会显示成白纸
$iconDir = Join-Path $env:LOCALAPPDATA 'DailyReading'
New-Item -ItemType Directory -Force -Path $iconDir | Out-Null
$icon = Join-Path $iconDir 'app.ico'
Copy-Item (Join-Path $Root 'icon.ico') $icon -Force

$desktop = [Environment]::GetFolderPath('Desktop')
$lnk = Join-Path $desktop '每日阅读.lnk'
$sh = New-Object -ComObject WScript.Shell
$s = $sh.CreateShortcut($lnk)
$s.TargetPath = Join-Path $Root '启动.cmd'
$s.WorkingDirectory = $Root
$s.IconLocation = "$icon,0"
$s.WindowStyle = 1
$s.Description = '每日阅读 · 考研英语（Java 版）'
$s.Save()

& "$env:SystemRoot\System32\ie4uinit.exe" -show 2>$null
Write-Host "已创建：$lnk" -ForegroundColor Green