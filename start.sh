#!/bin/sh
# 运行 WOL Web (监听 9999 端口, 可用 --port 修改)
cd "$(dirname "$0")"
exec java -jar build/wolweb.jar "$@"