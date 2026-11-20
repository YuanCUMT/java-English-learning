param([switch]$NoPause)
& (Join-Path $PSScriptRoot 'start-easy.ps1') -Mode demo
exit $LASTEXITCODE
