@echo off
chcp 65001 >nul
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
    echo 未检测到 Java, 请先安装 JDK 17+  https://adoptium.net/
    pause
    exit /b 1
)
where javaw >nul 2>nul
if not errorlevel 1 (
    start "" javaw -jar wolweb.jar
) else (
    start "WOL Web" /min java -jar wolweb.jar
)
echo WOL Web 已在后台启动, 请访问 http://本机IP:9999
echo 后台停止: 任务管理器结束 javaw/java 进程, 或用部署脚本 -Uninstall
timeout /t 2 >nul
