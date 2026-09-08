#!/usr/bin/env bash
#
# BV 本地打包脚本
#
# 用法:
#   ./build.sh                        # 打 default flavor 的 release 正式包
#   ./build.sh release lite           # 打 lite flavor 的 release 包
#   ./build.sh debug                  # 打 debug 包
#   ./build.sh alpha default --clean  # 先 clean 再打 alpha 包
#
# 参数:
#   [buildType]  release(默认) | alpha | debug | r8test
#   [flavor]     default(默认) | lite
#
# 可选开关:
#   --clean          构建前执行 clean
#   --install        构建完成后 adb install -r 到已连接设备
#   --out <目录>     把 apk 额外拷贝一份到指定目录
#   其余未识别参数原样透传给 gradlew(如 --offline、--stacktrace)

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

红() { printf '\033[31m%s\033[0m\n' "$*"; }
绿() { printf '\033[32m%s\033[0m\n' "$*"; }
黄() { printf '\033[33m%s\033[0m\n' "$*"; }

die() { 红 "✗ $*" >&2; exit 1; }

# ---------- 解析参数 ----------
BUILD_TYPE=""
FLAVOR=""
DO_CLEAN=0
DO_INSTALL=0
OUT_DIR=""
GRADLE_ARGS=()

while [ $# -gt 0 ]; do
    case "$1" in
        release|alpha|debug|r8test|r8Test)
            BUILD_TYPE="$1" ;;
        default|lite)
            FLAVOR="$1" ;;
        --clean)   DO_CLEAN=1 ;;
        --install) DO_INSTALL=1 ;;
        --out)
            shift
            [ $# -gt 0 ] || die "--out 需要跟一个目录"
            OUT_DIR="$1" ;;
        -h|--help)
            awk 'NR>1 && /^#/ { sub(/^# ?/, ""); print; next } NR>1 { exit }' "$0"
            exit 0 ;;
        *)
            GRADLE_ARGS+=("$1") ;;
    esac
    shift
done

BUILD_TYPE="${BUILD_TYPE:-release}"
FLAVOR="${FLAVOR:-default}"
# r8test 的实际 build type 名是 r8Test，目录与任务名都按它来
[ "$BUILD_TYPE" = "r8test" ] && BUILD_TYPE="r8Test"

# 首字母大写(兼容 macOS 自带的 bash 3.2，不能用 ${var^})
cap() { printf '%s' "$(printf '%s' "${1:0:1}" | tr '[:lower:]' '[:upper:]')${1:1}"; }
TASK="assemble$(cap "$FLAVOR")$(cap "$BUILD_TYPE")"

# ---------- 找 JDK 17 ----------
# 项目 toolchain 要求 Java 17，而 Android Studio 自带的 JBR 是 21，
# 直接用系统 JAVA_HOME 会报 "No matching toolchains found"。
find_jdk17() {
    if [ -n "${BV_JAVA_HOME:-}" ]; then
        printf '%s' "$BV_JAVA_HOME"; return
    fi
    if [ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
        /usr/libexec/java_home -v 17; return
    fi
    for candidate in \
        /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
        /usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
        /Library/Java/JavaVirtualMachines/*17*/Contents/Home \
        "$HOME/Library/Java/JavaVirtualMachines"/*17*/Contents/Home
    do
        [ -x "$candidate/bin/java" ] && { printf '%s' "$candidate"; return; }
    done
    # 最后看看当前 JAVA_HOME 本身是不是 17
    if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"17\.'; then
        printf '%s' "$JAVA_HOME"
    fi
}

JAVA_HOME_17="$(find_jdk17)"
[ -n "$JAVA_HOME_17" ] || die "找不到 JDK 17。安装: brew install openjdk@17，或手动设置 BV_JAVA_HOME 指向 JDK 17"
export JAVA_HOME="$JAVA_HOME_17"

# ---------- 环境自检 ----------
# Android SDK 路径
if [ ! -f local.properties ]; then
    SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
    [ -d "$SDK_DIR" ] || die "找不到 Android SDK，请设置 ANDROID_HOME 或手动创建 local.properties"
    echo "sdk.dir=$SDK_DIR" > local.properties
    黄 "已生成 local.properties -> $SDK_DIR"
fi
SDK_DIR="$(sed -n 's/^sdk\.dir=//p' local.properties | head -1)"

# libs 子模块
[ -f libs/libVLC/build.gradle.kts ] || die "libs 子模块未检出，先执行: git submodule update --init --recursive"

# 签名配置(release / alpha / r8Test 都用 release 签名)
if [ "$BUILD_TYPE" != "debug" ]; then
    [ -f signing.properties ] || die "缺少 signing.properties，无法产出正式签名包"
    STORE="$(sed -n 's/^releaseStoreFile=//p' signing.properties | head -1)"
    [ -n "$STORE" ] && [ -f "$STORE" ] || die "signing.properties 里的 releaseStoreFile 不存在: ${STORE:-<未配置>}"
fi

VERSION_CODE="$(git rev-list --count HEAD)"
COMMIT="$(git rev-list HEAD --abbrev-commit --max-count=1)"

echo
绿 "==> BV 打包"
echo "    任务      : $TASK"
echo "    flavor    : $FLAVOR / buildType: $BUILD_TYPE"
echo "    JDK       : $JAVA_HOME"
echo "    SDK       : $SDK_DIR"
echo "    版本      : r$VERSION_CODE ($COMMIT)"
if [ -n "$(git status --porcelain)" ]; then
    黄 "    注意      : 工作区有未提交改动，产物版本号仍按 HEAD 计算"
fi
echo

# ---------- 构建 ----------
[ "$DO_CLEAN" = 1 ] && ./gradlew clean
./gradlew "$TASK" ${GRADLE_ARGS[@]+"${GRADLE_ARGS[@]}"}

# ---------- 校验产物 ----------
OUT_APK_DIR="app/build/outputs/apk/$FLAVOR/$BUILD_TYPE"
APK="$(ls -t "$OUT_APK_DIR"/*.apk 2>/dev/null | head -1)" || true
[ -n "$APK" ] || die "构建结束但没找到 apk，检查 $OUT_APK_DIR"

echo
绿 "==> 构建成功"
echo "    产物  : $APK"
echo "    大小  : $(du -h "$APK" | cut -f1)"

# 用最新版 build-tools 里的 apksigner 校验签名
APKSIGNER="$(ls -d "$SDK_DIR"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)" || true
if [ -n "${APKSIGNER:-}" ] && [ -x "$APKSIGNER" ]; then
    CERT="$("$APKSIGNER" verify --print-certs "$APK" 2>/dev/null | sed -n 's/^Signer #1 certificate DN: //p')"
    SHA1="$("$APKSIGNER" verify --print-certs "$APK" 2>/dev/null | sed -n 's/^Signer #1 certificate SHA-1 digest: //p')"
    if [ -n "$CERT" ]; then
        echo "    签名  : $CERT"
        echo "    SHA-1 : $SHA1"
    else
        红 "    签名  : 校验失败(未签名?)"
    fi
fi

if [ "$BUILD_TYPE" != "debug" ]; then
    MAPPING="app/build/outputs/mapping/$FLAVOR$(cap "$BUILD_TYPE")/mapping.txt"
    [ -f "$MAPPING" ] && echo "    混淆表: $MAPPING"
fi

# ---------- 可选: 拷贝 / 安装 ----------
if [ -n "$OUT_DIR" ]; then
    mkdir -p "$OUT_DIR"
    cp "$APK" "$OUT_DIR/"
    echo "    已拷贝: $OUT_DIR/$(basename "$APK")"
fi

if [ "$DO_INSTALL" = 1 ]; then
    echo
    绿 "==> 安装到设备"
    "$SDK_DIR/platform-tools/adb" install -r "$APK"
fi
