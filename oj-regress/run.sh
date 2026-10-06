#!/usr/bin/env bash
#
# 第 8 站回归集 —— 7 份提交，判题机 7 条判定分支各一根探针。
#
#   bash run.sh gen     跑一遍，逐条核对 7 个数字，全对才写 baseline.txt
#   bash run.sh check   跑一遍，和 baseline.txt 比：一致 exit 0，不一致 exit 1
#
# 目录约定：
#   oj-regress/
#     run.sh
#     baseline.txt                            <- gen 生成，别手改
#     cases/AC/Main.java                      <- 7 份探针，文件名和类名都必须是 Main
#     cases/WA/Main.java                         (SandboxCompiler 写死 resolve("Main.java"))
#     cases/{TLE,MLE,RE,CE,OLE}/Main.java
#
# 警告：本文件行尾必须是 LF。仓库 core.autocrlf=true，clone/checkout 会把 .sh 转成 CRLF，
#       bash 就会报 "$'\r': command not found"。治本靠 .gitattributes 里加一行：*.sh text eol=lf
#
set -u

# ── 要改的就这几行 ────────────────────────────────────────────────────
HOST=http://192.168.169.128:8080   # 在 VM 上跑就改成 http://127.0.0.1:8080
USERNAME=chenru1chao
PASSWORD=123456                    # 注册时输的明文密码；库里存的是 BCrypt 哈希，服务端 checkpw 比对
USER_ID=1                          # 提交落库的 userId
PROBLEM_ID=1                       # VM 上唯一的题：Hello World!（1.in 空 / 1.out = Hello World!）
MAX_WAIT=30                        # 等一份提交判完的总预算（秒），超了就当判题机卡住
# ─────────────────────────────────────────────────────────────────────

HERE="$(cd "$(dirname "$0")" && pwd)"
CASES="$HERE/cases"
BASELINE="$HERE/baseline.txt"

# 探针名 -> 期望的 judgeStatus（JudgeStatus 枚举里的 code）。
# 这张表代表"判定语义"，只在 gen 时当裁判；check 只跟 baseline.txt 比，不重新判对错。
EXPECTED="AC:2 WA:4 TLE:5 MLE:6 RE:7 CE:8 OLE:9"

# 找可用的 python。
# 坑：Windows 上 PATH 里的 python3/python 可能是微软商店的占位程序，它能"跑"、不报错、
#     却什么都不输出。所以不能只看 command -v，得真让它算出一个 1 来。
PY="${PY:-}"
for cand in "$PY" python3 python /d/develop/Miniconda3/python.exe; do
    [ -n "$cand" ] || continue
    if [ "$("$cand" -X utf8 -c 'print(1)' 2>/dev/null)" = "1" ]; then
        PY="$cand"
        break
    fi
done
if [ -z "$PY" ]; then
    echo "找不到可用的 python。手动指定再来：PY=/path/to/python bash run.sh $*" >&2
    exit 1
fi

TOKEN=""

login() {
    local resp
    resp=$(curl -s -m 10 -X POST "$HOST/user/login" \
        -H 'Content-Type: application/json' \
        --data-binary "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")

    if [ -z "$resp" ]; then
        echo "登录没有响应 —— $HOST 通吗？应用起着吗？" >&2
        exit 1
    fi

    TOKEN=$("$PY" -X utf8 -c 'import json,sys;print(json.load(sys.stdin)["data"]["token"])' <<<"$resp" 2>/dev/null)

    if [ -z "$TOKEN" ]; then
        echo "登录失败，服务端说：$resp" >&2
        exit 1
    fi
}

# 查一次判题状态，成功把响应体原样吐到 stdout；网络或鉴权失败打错误信息并 return 1
poll() {
    local resp

    resp=$(curl -s -m 10 "$HOST/submit/$1" -H "Authorization: $TOKEN")

    # 和提交那处同理：空响应体（401）不能漏过去，否则会被当成"没结果"白等一轮
    if [ -z "$resp" ]; then
        echo "查询没有响应（401？token 过期了？）: submitId=$1" >&2
        return 1
    fi

    printf '%s' "$resp"
}

# 提交一份源码并等它判完，成功输出一行 "状态 失败用例 耗时"，失败打错误信息并 exit 1。
#
# POST /submit 现在只把记录落库（status=0 排队中）就返回，不等判题 —— 所以必须自己
# 回过头轮询 GET /submit/{id}。前端要抄的原型就是下面这段退避轮询。
submit() {
    local resp id status line waited delay

    # 边造 body 边喂给 curl：--data-binary @- 从标准输入读，
    # 既不落临时文件，也不给 shell 机会去吃掉 Java 源码里的引号
    resp=$("$PY" -X utf8 -c '
import json,sys
print(json.dumps({
    "userId": int(sys.argv[2]),   # 死字段：服务端只认 token 里的 userId
    "problemId": int(sys.argv[3]),
    "submitLanguage": "java",     # 必须叫 submitLanguage，叫 language 会被静默丢掉存 null
    "code": open(sys.argv[1], encoding="utf-8").read(),
}))' "$1" "$USER_ID" "$PROBLEM_ID" \
        | curl -s -m 30 -X POST "$HOST/submit" \
            -H "Authorization: $TOKEN" \
            -H 'Content-Type: application/json' \
            --data-binary @-)

    # token 缺了或过期了 = 401 + 空响应体。空字符串要是漏过去，会被当成
    # "跑完了但没结果"，白等一轮还看不出原因
    if [ -z "$resp" ]; then
        echo "提交没有响应（401？token 过期了？）: $1" >&2
        exit 1
    fi

    # 提交这一下只要拿到 submitId（下面轮询要用），结果等轮询再说
    if ! id=$("$PY" -X utf8 -c '
import json,sys
r = json.load(sys.stdin)
d = r.get("data")
if r.get("code") != 1 or not d:
    sys.exit(1)
print(d["id"])
' <<<"$resp" 2>/dev/null); then
        echo "提交没拿到 submitId：$resp" >&2
        exit 1
    fi

    # 轮询判据：0=排队中 1=正在判题，都还没结果；其余（2~10）都是终态。
    # 退避 1s → 2s → 5s → 5s…，总预算 MAX_WAIT 秒。
    # 为什么要退避：判题要几十毫秒到几秒，1s 一次的固定间隔既浪费请求，
    # 又会在队里排了 50 份时把服务端问爆。
    # 为什么要预算：判题机卡死时必须报错退出，否则这里会一直转圈，
    # 回归就永远给不出红绿 —— 卡住和通过一样麻烦。
    waited=0
    delay=1
    while :; do
        resp=$(poll "$id") || exit 1

        if ! status=$("$PY" -X utf8 -c '
import json,sys
r = json.load(sys.stdin)
d = r.get("data")
if r.get("code") != 1 or not d:
    sys.exit(1)
print(d["status"])
' <<<"$resp" 2>/dev/null); then
            echo "查状态没拿到结果：$resp" >&2
            exit 1
        fi

        case "$status" in
            0|1) ;;                 # 还没判完，接着等
            *)   break ;;           # 终态，resp 里就是最终结果
        esac

        if [ "$waited" -ge "$MAX_WAIT" ]; then
            echo "等了 ${waited}s 还是 status=$status，判题机卡住了？submitId=$id" >&2
            exit 1
        fi

        sleep "$delay"
        waited=$((waited + delay))
        case "$delay" in 1) delay=2 ;; *) delay=5 ;; esac
    done

    # 注意字段名：SubmitVO 里叫 status，不是 judgeStatus；判题 ID 叫 id
    if ! line=$("$PY" -X utf8 -c '
import json,sys
r = json.load(sys.stdin)
d = r["data"]
f, t = d["failedCaseNo"], d["timeUsed"]
print(d["status"], "-" if f is None else f, "-" if t is None else t)
' <<<"$resp" 2>/dev/null); then
        echo "服务端没给成功结果：$resp" >&2
        exit 1
    fi

    printf '%s\n' "$line"
}

# 按 EXPECTED 的顺序跑满 7 份，输出七行 "名称 状态 失败用例 耗时"。
# 任何一份没跑出结果就 return 1，绝不吐半截结果出去 —— 断在半路的结果拿去比对，
# 只会得到一堆假红
collect() {
    local pair name src line
    for pair in $EXPECTED; do
        name=${pair%%:*}
        src="$CASES/$name/Main.java"
        if [ ! -f "$src" ]; then
            echo "缺探针文件: $src" >&2
            return 1
        fi
        line=$(submit "$src") || return 1
        printf '%s %s\n' "$name" "$line"
    done
}

show() {
    echo "名称  状态  失败用例  耗时(ms)"
    printf '%s\n' "$1"
}

do_gen() {
    if [ -f "$BASELINE" ]; then
        echo "baseline.txt 已经存在了。要重建得先删掉它 —— 重建基线等于承认旧尺子作废" >&2
        exit 1
    fi

    local rows
    rows=$(collect) || exit 1
    show "$rows"

    # 校准零点：7 个数字必须和 EXPECTED 表逐个对上。
    # 对不上就一个字都不写 —— 尺子自己不准的时候冻进基线，等于把 bug 冻进去
    local bad=0 pair name want got
    for pair in $EXPECTED; do
        name=${pair%%:*}
        want=${pair##*:}
        got=$(printf '%s\n' "$rows" | awk -v n="$name" '$1 == n { print $2 }')
        if [ "$got" != "$want" ]; then
            echo "校准失败: $name 期望 $want，实际 $got" >&2
            bad=1
        fi
    done

    if [ "$bad" != 0 ]; then
        echo "baseline.txt 未写入" >&2
        exit 1
    fi

    printf '%s\n' "$rows" | cut -d' ' -f1-3 | sort > "$BASELINE"
    echo "校准通过（7/7），已写入 $BASELINE"
}

do_check() {
    if [ ! -f "$BASELINE" ]; then
        echo "还没有 $BASELINE，先跑 bash run.sh gen" >&2
        exit 1
    fi

    local rows now diffout
    rows=$(collect) || exit 1
    show "$rows"

    # 排序后再比：逐行 diff 的前提是两边顺序一致，靠 sort 保证，而不是靠默契
    now=$(printf '%s\n' "$rows" | cut -d' ' -f1-3 | sort)

    if diffout=$(diff "$BASELINE" <(printf '%s\n' "$now")); then
        echo "回归通过：$(printf '%s\n' "$now" | wc -l | tr -d ' ')/7 和基线一致"
        exit 0
    fi

    echo "回归失败（< 基线，> 现在）：" >&2
    printf '%s\n' "$diffout" >&2
    exit 1
}

case "${1:-}" in
    gen)   login; do_gen ;;
    check) login; do_check ;;
    *)     echo "用法: bash run.sh gen | check" >&2; exit 2 ;;
esac
