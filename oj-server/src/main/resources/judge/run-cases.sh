#!/bin/sh
#
# 容器内驱动脚本 —— 在 oj-judge:17 里跑，/work 是宿主上这次判题的临时目录（workDir）。
#
# 参数：
#   $1  用例个数
#   $2  每个用例的时限（秒，可以是 1.5 这种小数）
#   $3  单文件大小上限（KB），用来兜住"死循环无限打印"
#   $4  每个用例的内存上限（MB），从 problem.memory_limit 来
#   $5  编译阶段 JVM 堆上限（MB）
#   $6  编译超时（秒）
#
# 先编译、再跑用例。每个用例产出四个文件，交给容器外的 Java 读：
#   out/$i.exit  退出码 —— 0 正常 / 124 timeout 掐的 / 3 JVM OOM 自杀 / 153 写文件超限被杀
#   out/$i.out   标准输出（就是判 WA/AC 要比对的那个）
#   out/$i.err   标准错误（RE 的栈在这）
#   out/$i.time  /usr/bin/time -v 的报告（峰值内存、CPU 时间都在里面）
#
# 编译结果单独产出三个文件：
#   out/compile.exit   javac 的退出码（0 成功；124 被 timeout 掐；其余为编译错误）
#   out/compile.out    编译的标准输出
#   out/compile.err    编译报错 —— 前端显示给用户的就是这个
#
# 编译失败时脚本以非 0 退出，用例一个都不跑。容器外面先读 compile.exit 判 CE，
# 所以这个退出码不能吞。
#
# 顺序有讲究：time 在外、timeout 在内（第 6 站踩出来的）
#    timeout 超时会打死整个进程组，连它跑的那个 time 一起打，报告就写不出来了；
#    倒过来只打死 java，time 好端端把报告写完，124 也照样透出来。
#
# JVM 那几个参数一个都不能少：
#    -Xmx                    内存限制。少了它 JVM 默认用宿主内存的 1/4，MLE 会被判成 AC
#    -XX:+ExitOnOutOfMemoryError  OOM 时让 JVM 用退出码 3 直接死 —— 判 MLE 全靠它
#    其余几个（SerialGC / TieredStopAtLevel）是为了快速启动，和 baseline 保持一致
#
# ulimit -f 的单位实测过：bash 5.2.21（oj-judge:17）按 1024 字节块算，
#    所以 $FILE_KB 就是 KiB，10240 块 → 10 MiB，正好等于 OUTPUT_LIMIT_BYTES。
#    注意本脚本是被 `bash /work/run-cases.sh` 显式调用的，shebang 不生效；
#    哪天改成用 sh 调（Ubuntu 的 sh 是 dash，按 POSIX 的 512 字节块），这里要重新换算。

# 少参数报错
set -u

CASES=$1
LIMIT_S=$2
FILE_KB=$3
MEM_MB=$4
COMPILE_MB=$5
COMPILE_S=$6

RUN_UID=2000 #与Dockerfile的useradd -u 2000 保持一致
RUN_GID=2000

# 兜底：任何单个文件写超上限就吃 SIGXFSZ（退出码 153），别把宿主磁盘写爆
ulimit -f "$FILE_KB"

mkdir -p /work/out
chmod 755 /work/out

# 编译
# 用 root 编：javac 不会执行用户代码（-proc:none 已关掉注解处理器），
# 编出来的 class 是 root:root 644，后面 chmod 644 /work/*.class 再兜一层。
# 编译放在拷贝测试用例之前 —— 编不过就别把用例往容器里搬。
cd /work || exit 1
timeout "$COMPILE_S" javac \
    -encoding UTF-8 \
    -Xmaxerrs 20 \
    -proc:none \
    --release 17 \
    -J-Dfile.encoding=UTF-8 \
    -J-Xmx"${COMPILE_MB}"m \
    -J-XX:MaxMetaspaceSize=64m \
    -d /work \
    Main.java > /work/out/compile.out 2> /work/out/compile.err

COMPILE_EXIT=$?
echo "$COMPILE_EXIT" > /work/out/compile.exit
chmod 644 /work/out/compile.*

if [ "$COMPILE_EXIT" -ne 0 ]; then
    # 编译失败：用例一个都不跑，容器外面读 compile.exit 判 CE
    exit 1
fi

# 测试用例 /dev/shm 只留root能进
mkdir -p /dev/shm/in
# 拷贝测试用例 到/dev/shm
cp /work/in/*.in /dev/shm/in/
# 降权当前文件只有root才能rwx 其他人什么都不行
chmod 700 /dev/shm/in
# 删除原来的测试用例 防止测试用例泄漏
rm -rf /work/in
# 对/work文件也上权限 705
chmod 705 /work
# 把用户程序的字节码文件权限放开
chmod 644 /work/*.class

i=1
while [ "$i" -le "$CASES" ]; do
    # in=/work/in/$i.in

    # 程序降权
    # setpriv 在运行程序前修改进程权限/身份
    # --reuid="$RUN_UID" 把真实 UID 和有效 UID 设为 RUN_UID，通常是 2000
    # --regid="$RUN_GID" 把真实 GID 和有效 GID 设为 RUN_GID，通常是 2000
    # --clear-groups 清空附加组
    # --no-new-privs 设置no_new_privs 防止后续通过 setuid/setgid 程序提权

    /usr/bin/time -v -o /work/out/$i.time \
        timeout -k 1 "$LIMIT_S" \
            setpriv --reuid="$RUN_UID" --regid="$RUN_GID" --clear-groups --no-new-privs \
            java -Dfile.encoding=UTF-8 \
                 -Xmx"$MEM_MB"m \
                 -XX:MaxMetaspaceSize=64m \
                 -Xss1m \
                 -XX:+UseSerialGC \
                 -XX:TieredStopAtLevel=1 \
                 -XX:+ExitOnOutOfMemoryError \
                 Main < /dev/shm/in/$i.in > /work/out/$i.out 2> /work/out/$i.err

    echo "$?" > /work/out/$i.exit
    chmod 644 /work/out/$i.*
    i=$((i + 1))
done
