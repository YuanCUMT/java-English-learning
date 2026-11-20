@echo off
setlocal
chcp 65001 >nul
title Daily Reading Launcher
cd /d "%~dp0"
if errorlevel 1 goto failed
"%SystemRoot%\System32\WindowsPowerShell\v1.0\powershell.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\start-easy.ps1" -Mode demo
set "LAUNCH_EXIT=%ERRORLEVEL%"
if not "%LAUNCH_EXIT%"=="0" goto failed
echo.
echo Startup finished. If the browser is not visible, open the URL printed above.
echo Closing this window does not stop the app.
echo Press any key to close this launcher.
pause >nul
exit /b 0
:failed
echo.
echo Startup failed. Keep the error message above for troubleshooting.
echo Logs: data\launcher
echo Press any key to close this window.
pause >nul
exit /b 1
