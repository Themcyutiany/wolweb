<#
.SYNOPSIS
  WOL Web Windows 部署/卸载脚本
.DESCRIPTION
  部署 (需管理员, 用于放行防火墙 + 注册开机自启):
    1. 检查 Java (JDK 17+)
    2. 复制 wolweb.jar 到安装目录
    3. 放行防火墙端口 (TCP 入站, 默认 9999)
    4. 注册开机自启计划任务 (SYSTEM 账户)
    5. 立即启动
  卸载 (普通权限即可停止服务, 移除自启/防火墙需管理员):
    .\deploy-windows.ps1 -Uninstall
.EXAMPLE
  .\deploy-windows.ps1
  .\deploy-windows.ps1 -JarPath C:\tmp\wolweb.jar -InstallDir D:\wolweb -Port 9999
  .\deploy-windows.ps1 -Uninstall
#>
param(
    [string]$JarPath = (Join-Path $PSScriptRoot 'wolweb.jar'),
    [string]$InstallDir = 'C:\wolweb',
    [int]$Port = 9999,
    [switch]$Uninstall
)

$ErrorActionPreference = 'Stop'
$TaskName = 'WOLWeb'
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

# 自动定位 jar: 优先脚本同目录, 找不到则用 build 子目录 (仓库源码布局)
if (-not (Test-Path $JarPath)) {
    $alt = Join-Path $PSScriptRoot 'build\wolweb.jar'
    if (Test-Path $alt) { $JarPath = $alt }
}

function Find-Java {
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $bases = @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft")
    foreach ($base in $bases) {
        if (Test-Path $base) {
            $exe = Get-ChildItem $base -Recurse -Filter java.exe -ErrorAction SilentlyContinue |
                   Where-Object { $_.FullName -match 'bin\\java\.exe$' } | Select-Object -First 1
            if ($exe) { return $exe.FullName }
        }
    }
    return $null
}

function Stop-WolProcess {
    # 通过进程命令行找到 wolweb 并停止
    Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" -ErrorAction SilentlyContinue |
        Where-Object { $_.CommandLine -like '*wolweb.jar*' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    # 通过 pid 文件停止 (jar 内置 --stop 也会清理 pid 文件)
    Get-ChildItem -Path $InstallDir, $PSScriptRoot -Filter 'wolweb-*.pid' -ErrorAction SilentlyContinue |
        ForEach-Object {
            $id = (Get-Content $_.FullName -ErrorAction SilentlyContinue | Select-Object -First 1).Trim()
            if ($id -match '^\d+$') { Stop-Process -Id $id -Force -ErrorAction SilentlyContinue }
            Remove-Item $_.FullName -Force -ErrorAction SilentlyContinue
        }
}

if ($Uninstall) {
    Write-Host '== 卸载 WOL Web ==' -ForegroundColor Cyan
    if (-not $isAdmin) {
        Write-Host '当前非管理员: 仍可停止服务进程 (移除自启任务和防火墙规则需要管理员)' -ForegroundColor Yellow
    }
    Stop-WolProcess
    Write-Host '服务进程已停止' -ForegroundColor Green
    if ($isAdmin) {
        Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction SilentlyContinue
        Remove-NetFirewallRule -DisplayName "WOL Web ($Port/tcp)" -ErrorAction SilentlyContinue
        Write-Host '已移除开机自启任务和防火墙规则' -ForegroundColor Green
    } else {
        Write-Host '提示: 若需一并移除开机自启和防火墙规则, 请以管理员身份重跑: .\deploy-windows.ps1 -Uninstall' -ForegroundColor Yellow
    }
    Write-Host '卸载完成 (安装目录文件保留, 可手动删除)' -ForegroundColor Green
    exit 0
}

if (-not $isAdmin) {
    Write-Host '部署需要管理员权限 (放行防火墙 + 注册开机自启)' -ForegroundColor Red
    Write-Host '请右键 PowerShell, 选择"以管理员身份运行"后重试;' -ForegroundColor Yellow
    Write-Host '若只想临时运行: java -jar wolweb.jar (终端里输入 stop 回车可停止)' -ForegroundColor Yellow
    exit 1
}

Write-Host '== WOL Web Windows 部署 ==' -ForegroundColor Cyan

# 1. 检查 Java
$java = Find-Java
if (-not $java) {
    Write-Host '未检测到 Java, 请先安装 JDK 17+ (https://adoptium.net/) 后再运行本脚本' -ForegroundColor Red
    exit 1
}
Write-Host "Java: $java" -ForegroundColor Green

# 2. 复制 jar 到安装目录
if (-not (Test-Path $JarPath)) {
    Write-Host "找不到 jar 文件: $JarPath" -ForegroundColor Red
    Write-Host '请把 wolweb.jar 放在脚本同目录, 或用参数指定: -JarPath C:\path\to\wolweb.jar' -ForegroundColor Yellow
    exit 1
}
New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
Copy-Item $JarPath (Join-Path $InstallDir 'wolweb.jar') -Force
Write-Host "已安装到: $InstallDir" -ForegroundColor Green

# 3. 防火墙放行端口
$ruleName = "WOL Web ($Port/tcp)"
if (-not (Get-NetFirewallRule -DisplayName $ruleName -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -DisplayName $ruleName -Direction Inbound -Protocol TCP -LocalPort $Port -Action Allow | Out-Null
}
Write-Host "防火墙已放行 TCP $Port (入站)" -ForegroundColor Green

# 4. 注册开机自启计划任务
$jarFile = Join-Path $InstallDir 'wolweb.jar'
$action = New-ScheduledTaskAction -Execute $java -Argument "-jar `"$jarFile`"" -WorkingDirectory $InstallDir
$trigger = New-ScheduledTaskTrigger -AtStartup
$principal = New-ScheduledTaskPrincipal -UserId 'SYSTEM' -LogonType ServiceAccount -RunLevel Highest
Register-ScheduledTask -TaskName $TaskName -Action $action -Trigger $trigger -Principal $principal `
    -Description 'WOL Web - 局域网唤醒网页工具' -Force | Out-Null
Write-Host '已注册开机自启任务 (任务名: WOLWeb)' -ForegroundColor Green

# 5. 立即启动
Start-ScheduledTask -TaskName $TaskName
Start-Sleep -Seconds 2

$localIp = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } |
    Select-Object -First 1).IPAddress
if (-not $localIp) { $localIp = 'localhost' }

Write-Host ''
Write-Host '部署完成!' -ForegroundColor Green
Write-Host "访问地址:  http://$localIp`:$Port"
Write-Host "配置文件:  $InstallDir\wolweb.properties"
Write-Host '首次访问会自动进入管理员账号设置页'
Write-Host '停止服务: java -jar wolweb.jar --stop (无需管理员)'
Write-Host '卸载:     .\deploy-windows.ps1 -Uninstall'

