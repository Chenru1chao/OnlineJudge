#!/bin/sh
#
# 容器内驱动脚本 —— 在 oj-judge:17 里跑，/work 是宿主上这次判题的临时目录（workDir）。
#
# 参数：
#   $1  用例个数
#   $2  每个用例的时限（秒，可以是 1.5 这种小数）
#   $3  单文件大小上限（KB），用来兜住"死循环无限打印"
#   $4  每个用例的内存上限（MB），从 problem.memory_limit 来
#
# 每个用例产出四个文件，交给容器外的 Java 读：
#   out/$i.exit  退出码 —— 0 正常 / 124 timeout 掐的 / 3 JVM OOM 自杀 / 153 写文件超限被杀
#   out/$i.out   标准输出（就是判 WA/AC 要比对的那个）
#   out/$i.err   标准错误（RE 的栈、CE 的 javac 报错都在这）
#   out/$i.time  /usr/bin/time -v 的报告（峰值内存、CPU 时间都在里面）
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
# ulimit -f 的单位有坑（512 还是 1024 字节块，POSIX 和 bash 说法不一），
#    实测不对就改这里的换算。

# 少参数报错
set -u

CASES=$1
LIMIT_S=$2
FILE_KB=$3
MEM_MB=$4

RUN_UID=2000 #与Dockerfile的useradd -u 2000 保持一致
RUN_GID=2000

# 兜底：任何单个文件写超上限就吃 SIGXFSZ（退出码 153），别把宿主磁盘写爆
ulimit -f "$FILE_KB"

mkdir -p /work/out
chmod 755 /work/out
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
    # --clear-groups 清空附加组，防止继承 root 的附加组权限
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
