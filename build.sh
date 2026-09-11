#!/usr/bin/env bash
# 构建两个前端并输出到后端 static 资源目录，随后可 mvn package 打成单 fat jar。
# 用法：./build.sh
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PM="${PM:-pnpm}"

build_app() {
    local name="$1"
    echo "==> 构建 $name"
    cd "$root/web/$name"
    "$PM" install
    "$PM" run build
    cd "$root"
}

build_app chat
build_app admin

echo "==> 前端构建完成，产物已写入 mmcove-app/src/main/resources/static/{chat,admin}"
echo "==> 接下来：mvn clean package -DskipTests"
