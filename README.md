# 局域网唤醒 (WOL Web)

一个零依赖的 Java 网页工具：监听 `9999` 端口，首次访问设置管理员账号，登录后可管理局域网电脑的 MAC 地址并一键远程唤醒。

- 纯 JDK 实现（`com.sun.net.httpserver`），**无任何第三方依赖**
- 单文件 jar 约 19KB，兼容 **JDK 17+**（Linux / Windows / macOS 均可运行）
- 密码 PBKDF2 加盐加密存储，不落明文
- 唤醒包自动广播到本机所有网段（UDP 9 端口）+ `255.255.255.255`

## 快速开始

```bash
# 方式一: 直接运行预编译 jar (见 GitHub Releases)
java -jar wolweb.jar

# 方式二: 从源码构建
./build.sh
java -jar build/wolweb.jar
```

启动后浏览器访问 `http://服务器IP:9999`：

1. 首次访问自动进入「设置管理员账号」页面
2. 登录后点击「添加电脑」，填写名称和 MAC 地址
3. 需要唤醒时点击对应电脑的「唤醒」按钮

## 功能

| 功能 | 说明 |
| --- | --- |
| 管理员账号 | 首次运行设置，用户名 + 密码（至少 6 位） |
| 登录会话 | Cookie 会话，12 小时有效，可退出登录 |
| MAC 管理 | 添加 / 删除电脑（名称 + MAC 地址） |
| 远程唤醒 | 一键发送 Wake-on-LAN 魔术包 |
| MAC 格式 | 支持 `AA:BB:CC:DD:EE:FF`、`AA-BB-CC-DD-EE-FF`、无分隔符格式 |
| IPv6 支持 | 网页/API 双栈监听，IPv6 用方括号访问: `http://[IPv6地址]:9999` |
| IPv6 排查 | 先确认服务在运行且是最新 jar；本机测试用「本机测试」地址；局域网设备优先用推荐的 IPv4 地址（2409 公网 IPv6 能否访问取决于路由器） |

## 配置

- 配置文件 `wolweb.properties` 生成在 jar 所在目录（源码运行则为当前工作目录）
- 修改端口：`java -jar wolweb.jar --port 8080` 或环境变量 `PORT=8080`
- 忘记密码：删除 `wolweb.properties` 后重启，重新设置管理员
- 停止服务：终端里输入 `stop` 回车（前台运行时），或另开终端执行 `java -jar wolweb.jar --stop`（无需管理员权限）
- 启动后终端会自动打印本机真实访问地址（局域网/IPv6/本机），直接复制即可
- 查看帮助：`java -jar wolweb.jar --help`


## API 接口

除 `/api/health` 和 `/api/login` 外, 所有接口都需要登录凭证 (二选一):

- 请求头 `Authorization: Bearer <token>` (token 由 `/api/login` 返回)
- 浏览器 Cookie (网页登录后自动携带)

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/health` | 服务状态 (无需登录) |
| POST | `/api/login` | 登录, 返回 token |
| GET | `/api/macs` | 电脑列表 |
| POST | `/api/macs` | 添加电脑 `{name, addr}` |
| DELETE | `/api/macs/{id}` | 删除电脑 |
| POST | `/api/wake` | 唤醒 `{id}` 或 `{mac}` |

### 示例

```bash
# 1. 登录, 返回 {"ok":true,"token":"...","user":"admin"}
curl -X POST http://IP:9999/api/login \
  -H "Content-Type: application/json" \
  -d '{"user":"admin","password":"admin123"}'

# 2. 添加电脑
curl -X POST http://IP:9999/api/macs \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"客厅电脑","addr":"AA:BB:CC:DD:EE:FF"}'

# 3. 唤醒 (按 id 或直接按 MAC)
curl -X POST http://IP:9999/api/wake \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"id":"1"}'
curl -X POST http://IP:9999/api/wake \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"mac":"AA:BB:CC:DD:EE:FF"}'

# 4. 列表 / 删除
curl http://IP:9999/api/macs -H "Authorization: Bearer <token>"
curl -X DELETE http://IP:9999/api/macs/1 -H "Authorization: Bearer <token>"
```

错误时返回 `{"error":"..."}` + 对应状态码 (400/401/403/404/405)。
## 构建

```bash
./build.sh          # 输出到 build/wolweb.jar
# 等价命令:
# javac --release 17 -encoding UTF-8 -d build/classes src/com/wolweb/*.java
# jar cfe build/wolweb.jar com.wolweb.Main -C build/classes .
```


## Docker 部署

> WOL 依赖 UDP 广播包, Linux 下请务必使用 `--network host`, 否则容器内的广播无法到达局域网。

### 方式一: docker run

```bash
docker build -t wolweb .
docker run -d --name wolweb --restart=always \
  --network host \
  -v $PWD/wolweb-data:/data \
  wolweb
```

- `--network host`: 容器共享宿主机网络, 唤醒广播才能发到局域网
- `-v $PWD/wolweb-data:/data`: 持久化配置文件 `wolweb.properties`
- 常用命令: 查看日志 `docker logs -f wolweb`, 停止 `docker stop wolweb`, 删除 `docker rm wolweb`

### 方式二: docker compose

```bash
docker compose up -d --build
docker compose down    # 停止
```

### Windows / macOS (Docker Desktop)

Docker Desktop 不支持 host 网络, 请改用端口映射 (广播唤醒可能因 NAT 无法到达局域网):

```bash
docker run -d --name wolweb -p 9999:9999 -v $PWD/wolweb-data:/data wolweb
```

### 说明

- 多阶段构建: 第一阶段用 JDK 编译打包, 第二阶段仅用 JRE 运行, 镜像更小
- 容器内 jar 位于 `/opt/wolweb/wolweb.jar`, 配置文件生成在 `/data`
- 构建镜像不需要本机安装 Java
## 部署 (Linux)

后台运行：

```bash
nohup java -jar wolweb.jar > wolweb.log 2>&1 &
```

放行防火墙端口：

```bash
# firewalld (CentOS/RHEL)
firewall-cmd --permanent --add-port=9999/tcp && firewall-cmd --reload

# ufw (Ubuntu/Debian)
ufw allow 9999/tcp
```

systemd 开机自启（`/etc/systemd/system/wolweb.service`）：

```ini
[Unit]
Description=WOL Web
After=network.target

[Service]
WorkingDirectory=/opt/wolweb
ExecStart=/usr/bin/java -jar /opt/wolweb/wolweb.jar
Restart=always

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable --now wolweb
```

## 唤醒失败的排查

- 目标电脑需在 BIOS 中开启 Wake-on-LAN
- 操作系统内开启「魔术包唤醒」（Windows 设备管理器网卡属性 → 电源管理）
- WOL 一般仅**有线网卡**支持，无线网卡通常无效
- 路由器可能隔离广播包，可尝试开启目标机的静态 ARP 或换用支持 WOL 的路由器

## 源码结构

```
src/com/wolweb/
├── Main.java     # 入口: 启动 HTTP 服务 (端口 9999)
├── Handler.java  # 路由 / 会话 / 表单处理
├── Pages.java    # 页面 HTML 渲染
├── Store.java    # 配置存储 (管理员账号 + MAC 列表)
└── Wol.java      # Wake-on-LAN 魔术包发送
```

## Windows 部署

**方式一: 一键部署 (推荐, 需管理员)**

以管理员身份打开 PowerShell, 在 `wolweb.jar` 同目录执行:

```powershell
# 解除脚本执行限制 (仅需一次)
Set-ExecutionPolicy -Scope Process Bypass

.\deploy-windows.ps1
```

脚本会自动: 检查 Java → 复制 jar 到 `C:\wolweb` → 放行防火墙 TCP 9999 → 注册开机自启计划任务 (WOLWeb) → 立即启动。

自定义参数:

```powershell
.\deploy-windows.ps1 -JarPath C:\tmp\wolweb.jar -InstallDir D:\wolweb -Port 9999
# 卸载 (停止服务, 移除自启和防火墙规则)
.\deploy-windows.ps1 -Uninstall（卸载时普通权限即可停止服务；移除自启任务和防火墙规则需管理员）
```

**方式二: 手动运行 (无需管理员)**

命令行执行 `java -jar wolweb.jar` 即可, 终端里输入 `stop` 回车可停止服务。

Windows 防火墙手动放行: 控制面板 → Windows Defender 防火墙 → 高级设置 → 入站规则 → 新建规则 → 端口 → TCP 9999 → 允许连接。






