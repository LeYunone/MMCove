# 构建两个前端并输出到后端 static 资源目录，随后可 mvn package 打成单 fat jar。
# 用法（PowerShell）：./build.ps1
param([string]$Pm = "pnpm")

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path

function Build-App([string]$name) {
    Write-Host "==> 构建 $name" -ForegroundColor Cyan
    Push-Location (Join-Path $root "web/$name")
    try {
        & $Pm install
        if ($LASTEXITCODE -ne 0) { throw "$name install 失败" }
        & $Pm run build
        if ($LASTEXITCODE -ne 0) { throw "$name build 失败" }
    } finally { Pop-Location }
}

Build-App "chat"
Build-App "admin"

Write-Host "==> 前端构建完成，产物已写入 mmcove-app/src/main/resources/static/{chat,admin}" -ForegroundColor Green
Write-Host "==> 接下来：mvn clean package -DskipTests" -ForegroundColor Green
