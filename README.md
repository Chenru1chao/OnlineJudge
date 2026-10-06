# Online Judge

> 给算法初学者的练习网站：提交代码，数秒内返回结果，并指出是哪组用例挂了。

## 关键设计

判题系统真正的难点不在 CRUD，而在下面这三件事上。它们决定了这个服务**能不能被陌生人安全地使用**。

### 1. 用户代码为什么不能直接跑

用户提交的是一段完全不认识的程序，必须真跑起来才知道对不对。而最自然的做法——`ProcessBuilder` 起一个子进程——有个致命问题：**用户代码和判题服务拥有完全相同的权限**。它可以删文件、占满磁盘、把机器搞垮；而在 Windows 上要同时约束住文件、网络、资源三样，代价极高。

所以判题执行层整体搬到了 Linux：用户代码在容器里运行，容器外再套一层降权。容器**以 root 身份启动**——因为要先把测试用例安置到 `/dev/shm` 并给它上锁，这一步非 root 不可；安置完成后，再用 `setpriv` 把进程降到 2000 号用户。降权也不是改小 uid 就完事：`--clear-groups` 清掉继承来的 root 附加组，`--no-new-privs` 堵死通过 setuid 程序重新提权的路——四个参数是一个整体，少一个就能爬回 root。

**代价**：开发环境和运行环境从此分裂。本地是 Windows，代码却必须在虚拟机里的 Linux 上才能完整跑起来，改一行试一次的调试成本明显上升。

### 2. 测试用例为什么不能落盘

用户程序必须拿到输入才能跑起来。容器里是一个极简的 Ubuntu 环境，输入得先落成文件，再用重定向喂进程序的标准输入。

但**只要输入落在文件里，用户程序就能读它**。而判题又必须把运行时的错误信息返回给用户——这两件事凑在一起，路就通了： 

```java
public class Main {
    public static void main(String[] args) throws Exception {
        File dir = new File("/work/in");
        System.err.println("files=" + Arrays.toString(dir.list()));
        for (File f : dir.listFiles()) {
            String s = Files.readString(f.toPath());
            System.err.println(f.getName() + " len=" + s.length() + " [" + s + "]");
        }
        throw new RuntimeException("boom");
    }
}
```

```text
== 5.in
-1000000000 -1000000000
== 3.in
-1 -2
== 10.in
999999999 1
……（其余用例略）
Exception in thread "main" java.lang.RuntimeException: boom
```

测试用例原封不动地跟着 RE 返回到了提交记录里。**拿到全部输入，离线暴力跑一遍，再把答案硬编码提交，就 AC 了——判题机亲手把答案递给了作弊的人。**

所以解法不是"把文件藏起来"（藏不住），而是一套**顺序**，让输入从头到尾不落在用户够得到的地方：

1. 容器以 root 启动（后面几步非 root 不可）
2. 用例拷进 `/dev/shm/in`，`chmod 700` 锁死
3. 把原始的 `/work/in` 整个 `rm -rf` 删掉
4. `/work` 设成 `705` —— **不能是 `701`**，因为降权后的进程还要读自己的 `.class`
5. 最后才 `setpriv` 把进程降到 2000 号用户，用 stdin 重定向把用例喂进去

走完这五步，用户进程在整个生命周期里，那个文件既不在它能访问的路径上，也没有任何重新获得权限的机会。

### 3. 判题线程为什么会被耗光

判题每次都要起一个容器，但容器是**外部进程**——它启不启得来、什么时候返回，JVM 说了不算。而判题的线程池只有 4 个线程。

最自然的写法是 `start()` 之后等它返回。可 docker 一旦卡在启动阶段，它**既不返回成功也不返回失败，就那么挂着**——`waitFor()` 会无限期等下去。一个线程被无声吃掉，4 个挂满 4 次，判题服务就再没有线程可用，所有提交堆积在队列里。最要命的是日志里一个字都没有，因为自始至终没有抛出任何异常。

所以给容器创建加了硬时限，而且这个时限不是拍脑袋定的——**按这次判题的用例条数算出来**，用例越多，合法的启动时间就该越久。超时则 kill 子进程、销毁容器、抛异常，把提交状态置为失败并记日志。容器名用这次判题的临时目录名，泄漏了还能按名字找回来删掉。

**代价**：时限是估算的，用例特别多的题存在被误杀的可能。

## 一次提交的流程

提交接口是**异步**的：落库之后立刻返回，判题在后台线程池里跑，前端靠轮询取结果。

![幕截图 2026-10-06 23222](C:\Users\陈睿超\Pictures\Screenshots\屏幕截图 2026-10-06 232221.png)

## 技术栈

| 层 | 选型 |
| --- | --- |
| 语言 | Java 17 |
| 框架 | Spring Boot 3.5.16 |
| 持久层 | MyBatis-Plus 3.5.17 |
| 存储 | MySQL 8 |
| 判题执行 | Docker + Linux（setpriv / ulimit / cgroup） |
| 鉴权 | JWT（jjwt 0.12.6） |
| 前端 | 原生 HTML/JS（`oj-front/`） |

## 快速开始

### 前置条件

- JDK 17
- Maven
- MySQL 8
- Docker（判题执行需要；开发环境下判题机跑在 Linux 虚拟机里）

### 建库

```bash
mysql -uroot -p < oj-server/src/main/resources/db.sql
```

### 配置

配置文件在 `oj-server/src/main/resources/`，通过 `spring.profiles.active` 切换：

| 文件 | 用途 | 说明 |
| --- | --- | --- |
| `application.yml` | 公共配置 | 服务端口 8080、数据源、MyBatis-Plus |
| `application-dev.yml` | Linux / 虚拟机 | 测试用例路径指向 `/home/.../oj-data/` |
| `application-local.yml` | Windows 本机 | 测试用例路径指向本地盘 |

需要自行确认的两项：

- `db.host` / `db.password` —— 数据库连接
- `test-case.file-path.prefix` —— 测试用例在磁盘上的存放目录

### 启动

```bash
mvn -pl oj-server spring-boot:run
```

### 验证

```bash
curl http://localhost:8080/problem/1
```

## 项目结构

```text
OnlineJudge/
├── oj-common/          # 常量（判题上限 SandboxLimits）、自定义异常
├── oj-pojo/            # 实体 / DTO / VO，以及判题状态枚举 JudgeStatus
├── oj-server/          # 主服务
│   └── com.chenru1chao
│       ├── controller/     # Web 层
│       ├── service/        # 业务层
│       ├── mapper/         # MyBatis-Plus Mapper 接口 + XML
│       ├── judge/          # 判题执行层，整个项目的核心
│       ├── task/           # 定时任务（异常提交清扫）
│       ├── listener/       # 启动时的清理逻辑
│       └── config/         # 线程池、拦截器等配置
├── oj-front/           # 前端静态页面
└── oj-regress/         # 判题回归集（baseline.txt + cases/ + run.sh）
```

`oj-server/.../judge/` 内部分工：

| 类 | 职责 |
| --- | --- |
| `SandboxCompiler` | 编译用户代码，禁用注解处理器 |
| `SandboxProcessLauncher` | 起容器、算超时、销毁容器 |
| `SandboxRunner` | 容器外读取退出码与测量结果 |
| `SandboxMultiTestValidator` | 逐用例比对，产出最终判定 |
| `SandboxCleaner` | 清理泄漏的容器与临时目录 |

## 已知边界

只支持 Java 单语言
单机判题，没有多节点调度
编译阶段还没进容器
