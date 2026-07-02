# Run Anywhere

在 IntelliJ IDEA 中，对**任意 Java 方法**右键一键调用，直接作用于**当前正在运行的 JVM**。

插件内置 [Arthas](https://arthas.aliyun.com/) 4.0.5，通过动态 Attach + HTTP API 执行 OGNL / vmtool 命令，无需修改启动参数或 Run Configuration。

| 项目 | 值 |
|------|-----|
| 插件 ID | `com.runanywhere` |
| 版本 | 0.1.0 |
| 兼容 IDE | IntelliJ IDEA **2026.1+**（`sinceBuild = 261`） |
| 快捷键 | `Alt + Shift + A` |

**典型场景**：本地快速触发 Service 方法、验证 Spring Bean 逻辑、在断点下调试时异步触发重试任务——无需写临时 Controller 或单元测试。

---

## 目录

- [功能特性](#功能特性)
- [环境要求](#环境要求)
- [安装](#安装)
- [快速开始](#快速开始)
- [配置](#配置)
- [使用指南](#使用指南)
  - [调用策略](#调用策略)
  - [参数输入](#参数输入)
  - [执行模式](#执行模式)
  - [进程选择](#进程选择)
  - [结果窗口](#结果窗口)
- [常见问题](#常见问题)
- [架构说明](#架构说明)
- [开发与构建](#开发与构建)
- [安全提示](#安全提示)

---

## 功能特性

- **三种调用路径**：静态方法（OGNL）、Spring Bean（`getBean`）、普通实例（`vmtool`）
- **智能进程匹配**：自动发现 IDEA 启动的 Java 进程，多进程时按模块/主类/包名打分匹配
- **丰富参数支持**：基本类型、枚举、POJO / List / Map / 数组（JSON 输入）
- **多 JSON 库兼容**：Jackson、Gson、Fastjson2、Fastjson 1.x，支持自动检测
- **同步 / 异步执行**：异步模式适合断点调试与长耗时任务
- **多进程并行**：每个 PID 独立 Arthas 会话与 HTTP 端口
- **预热 Attach**：应用启动后后台 Attach，缩短首次调用等待
- **多 Tab 结果窗口**：每次调用独立 Tab，支持并行执行与单独关闭

---

## 环境要求

| 项目 | 要求 |
|------|------|
| IDE | IntelliJ IDEA 2026.1+ |
| 插件依赖 | Java 插件（内置） |
| 可选依赖 | Spring 插件（增强 Bean 识别） |
| 构建 JDK | Java 21 |
| 目标应用 | 由 IDEA 启动的本地 Java 进程，且允许 JVM Attach |

---

## 安装

### 从 ZIP 安装（推荐）

```powershell
# Windows
.\gradlew.bat buildPlugin

# macOS / Linux
./gradlew buildPlugin
```

1. **Settings → Plugins → ⚙ → Install Plugin from Disk...**
2. 选择 `build/distributions/run-anywhere-0.1.0.zip`
3. 重启 IDE

### 开发调试

```bash
./gradlew runIde
```

启动带插件的 Sandbox IDE 实例。

---

## 快速开始

```
启动应用 → 光标置于方法内 → 右键「运行方法」→ 填写参数 → 查看结果
```

1. 在 IDEA 中 **Run / Debug** 启动 Java / Spring Boot 应用
2. 将光标置于目标 **Java 方法**内
3. 右键 **「运行方法 (Run Anywhere)」** 或按 **`Alt + Shift + A`**
4. 首次使用确认安全提示；有参数时填写参数对话框（无参数自动跳过）
5. 插件自动选择目标进程（多进程无法确定时弹窗）
6. 在底部 **Run Anywhere** 工具窗口查看结果

---

## 配置

路径：**Settings → Other Settings → Run Anywhere**（应用级，全局生效）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| 异步执行 | 开启 | 使用 `async_exec`，提交后立即返回，适合断点调试 |
| 同步执行超时 | 300 秒 | 范围 30～3600 秒；仅同步模式生效 |
| 对象参数 JSON 库 | 自动检测 | 见下表 |

### 对象参数 JSON 库

| 选项 | 适用场景 |
|------|----------|
| **自动检测** | 在目标 JVM 中依次尝试 Jackson → Gson → Fastjson2 → Fastjson 1.x |
| Jackson | Spring Boot 项目（默认依赖） |
| Gson | 无 Jackson 的纯 Java 项目 |
| Fastjson2 | 阿里系新项目 |
| Fastjson 1.x | 老项目兼容 |

自动模式通过 `Class.forName` 检测 classpath，无需提前知道项目依赖。若目标 JVM **完全没有**上述 JSON 库，对象参数将无法反序列化（结果为 `null`），可添加 Gson 等依赖或手动指定库。

> **Fastjson 1.x 限制**：`Map<K,V>` 泛型会退化为普通 `Map`；复杂 Map 建议使用 Jackson / Gson / Fastjson2。

---

## 使用指南

### 调用策略

插件根据方法类型自动构造 Arthas 命令：

| 方法类型 | 识别条件 | 命令示例 |
|----------|----------|----------|
| 静态方法 | `static` 修饰符 | `ognl '@com.example.Utils@add(1, 2)' -x 3` |
| Spring Bean | `@Component` / `@Service` / `@RestController` 等 | `vmtool ... getBean("userService").findById(1L)` |
| 普通实例 | 非静态、无 Spring 注解 | `vmtool ... getInstances --className com.example.Service --express 'instances[0].run()'` |

**Spring Bean 名称规则**：

- 优先读取注解 `value` / `name`
- 未指定时按 Spring 默认规则（`UserService` → `userService`）
- 支持 `@RocketMQMessageListener`、`@KafkaListener` 等 Listener 注解类

**已知限制**：

- 通过 `@Bean` 方法注册的 Bean 暂不支持自动识别
- 普通实例方法默认取 `instances[0]`；类未加载时可能失败

### 参数输入

有参数时弹出对话框；无参数时自动跳过。

| 类型 | 输入示例 | 说明 |
|------|----------|------|
| `String` | `hello` | 自动加引号转义 |
| `int` / `Integer` | `123` | 原样传递 |
| `long` / `Long` | `123` 或 `123L` | 自动补 `L` 后缀 |
| `boolean` / `Boolean` | `true` | 原样传递 |
| `BigDecimal` / `BigInteger` | `99.99` | 构造器表达式 |
| 枚举 | `ACTIVE` | `@com.example.Status@ACTIVE` |
| POJO / DTO | `{"id":1,"name":"test"}` | JSON 反序列化 |
| `List<T>` / `Set<T>` | `[{"id":1}]` | JSON 数组 |
| `Map<K,V>` | `{"key":{"id":1}}` | JSON 对象 |
| `T[]` | `[{"id":1}]` | JSON 数组 |
| 空值 | 留空或勾选「传 null」 | `null` |

对象/集合类型使用多行 JSON 输入框，提交前校验 JSON 格式。

**无 JSON 库时的最小依赖**（任选其一）：

```xml
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.11.0</version>
</dependency>
```

### 执行模式

#### 异步执行（默认，推荐断点调试）

- 使用 Arthas `async_exec`，**提交后立即返回**
- 目标线程在断点暂停时**不会导致插件超时**
- 工具窗口显示「已提交（异步）」及 Job ID
- 适合：断点调试、上传重试、长时间任务

**断点调试流程**：

1. 在目标方法打断点
2. Settings 中保持「异步执行」开启
3. 右键调用方法 → 立即显示「已提交」
4. 在 Debugger 中继续单步，观察变量

#### 同步执行

- 使用 Arthas `exec`，**等待方法执行完毕**后返回结果
- 超时可在 Settings 中配置（默认 300 秒）
- 适合：需要直接在工具窗口查看返回值

出现 `timeExpired: true` / `state: INTERRUPTED` 时，增大同步超时或改用异步模式。

### 进程选择

通过 IDEA `ExecutionManager` 检测当前项目下由 IDE 启动的 Java 进程：

| 进程数 | 行为 |
|--------|------|
| 0 | 提示先运行应用 |
| 1 | 自动选中 |
| 多个 | 智能匹配；无法唯一确定时弹窗 |

**自动匹配打分规则**（优先级从高到低）：

| 规则 | 分值 | 示例 |
|------|------|------|
| 模块一致 | +100 | 方法所在 Module = Run Configuration 关联 Module |
| 主类包名匹配 | +60 | 方法类位于启动类包或其子包 |
| 包名业务分段匹配 | +45 | `com.maiyawx.admin.*` ↔ 模块名 `maiyawx-admin` |
| 根包名一致 | +30 | `com.maiyawx.*` ↔ `com.maiyawx.app.*` |
| 配置名含模块名 | +25 | Run Configuration 名称包含模块名 |

仅一个进程得分 > 0 时自动选中。查看 `idea.log` 中 `Process match` 可了解打分详情。

### 结果窗口

- 位置：IDE 底部 **Run Anywhere** 面板
- 每次调用新增可关闭 Tab（标题含 PID 与方法名）
- 展示：执行状态、Arthas 命令、原始 JSON、解析后的返回值
- 标题栏 **「全部关闭」** 保留 Welcome Tab

---

## 常见问题

### Attach 失败

**现象**：「Arthas attach 到 PID xxx 失败」

| 原因 | 处理 |
|------|------|
| JVM 禁止 Attach | 确认未设置 `-XX:+DisableAttachMechanism` |
| Arthas 启动超时 | 查看 `idea.log`，搜索 `[Arthas]` |
| HTTP 端口被占用 | 关闭其他 Arthas 实例或重启应用 |
| 首次需下载依赖 | 等待 1～2 分钟 |

### Session not found

**现象**：`state: FAILED`，`message: session not found: <uuid>`

Arthas HTTP Session 因应用重启、长时间未调用等原因失效。插件会在异步调用前重建 Session，失败时自动重试一次。仍失败则重启目标应用。

### 同步执行超时

**现象**：`timeExpired: true`，`state: INTERRUPTED`

1. 改用异步模式（断点 / 长任务）
2. Settings 中增大同步超时
3. 排查方法本身死锁或无限阻塞

### Spring Bean 调用失败

- Bean 名不正确 → 注解中显式指定 `value` / `name`
- 类未被 Spring 管理 → 确认有 `@Component` 等注解
- `@Bean` 注册的 Bean → 当前版本暂不支持

### 对象参数反序列化失败

- 目标 JVM 无 JSON 库 → 添加 Gson 等依赖，或在 Settings 指定正确库
- JSON 字段名与 Java 属性不匹配 → 检查 JSON 格式与字段名
- Fastjson 1.x + 复杂 Map → 改用 Jackson / Gson / Fastjson2

### 未检测到进程

1. 应用须通过 IDEA Run/Debug 启动（非外部终端）
2. 进程仍在运行
3. 当前打开的是启动该应用的同一项目

### 多进程未能自动匹配

弹出「选择目标进程（未能自动匹配）」时：

1. 为各服务使用**独立 Module 的 Run Configuration**（推荐）
2. 手动选择正确 PID
3. 查看 `idea.log` 中 `Process match` 打分

---

## 架构说明

```
RunMethodAction（右键 / Alt+Shift+A）
        │
        ├── MethodMetadataExtractor    提取类名、参数、Spring Bean
        ├── ParameterInputDialog       参数输入 → OGNL 字面量
        └── ProcessAutoSelector        智能进程匹配
                    │
                    ▼
        ArthasSessionService（项目级）
                    │
                    ▼
            ArthasManager
        Map<PID, ArthasSession>        每 PID 独立端口 + HTTP Client
                    │
        ┌───────────┴───────────┐
        ▼                       ▼
  arthas-boot.jar          ArthasHttpClient
  （Attach 子进程）         exec / async_exec
        │                       │
        └───────────┬───────────┘
                    ▼
              目标 JVM
                    ▼
        Run Anywhere 工具窗口
```

| 包 | 核心类 | 职责 |
|----|--------|------|
| `action` | `RunMethodAction` | 入口，串联调用流程 |
| `psi` | `MethodMetadataExtractor` | PSI 元数据提取 |
| `psi` | `ParameterTypeAnalyzer` | 参数类型分类（对象/集合/Map 等） |
| `arthas` | `ArthasManager` | 多 PID 会话、Attach 生命周期 |
| `arthas` | `OgnlExpressionBuilder` | 构造 OGNL / vmtool 命令 |
| `arthas` | `ArthasHttpClient` | HTTP 客户端 |
| `process` | `ProcessAutoSelector` | 进程智能匹配 |
| `process` | `ProcessMatchScorer` | 匹配打分 |
| `ui` | `OgnlLiteralConverter` | 基本类型 → OGNL 字面量 |
| `ui` | `JsonDeserializeOgnlBuilder` | JSON → 多库反序列化 OGNL |
| `settings` | `RunAnywhereSettings` | 持久化配置 |

---

## 开发与构建

### 前置条件

- JDK 21
- Gradle Wrapper（项目自带）

### 常用命令

```bash
./gradlew downloadArthasBoot   # 下载 arthas-boot.jar（构建时自动执行）
./gradlew compileKotlin        # 编译
./gradlew test                 # 单元测试
./gradlew buildPlugin          # 构建可安装 ZIP
./gradlew runIde               # Sandbox IDE 调试
```

### 构建产物

| 文件 | 说明 |
|------|------|
| `build/distributions/run-anywhere-0.1.0.zip` | 可安装插件包 |
| `build/libs/run-anywhere-0.1.0.jar` | 插件 JAR |

### 项目结构

```
run-anywhere/
├── build.gradle.kts
├── gradle.properties
├── src/main/
│   ├── kotlin/com/runanywhere/
│   │   ├── action/           # 菜单 Action
│   │   ├── arthas/           # Arthas 管理与 HTTP 客户端
│   │   ├── concurrent/       # 插件线程池
│   │   ├── lifecycle/        # 应用生命周期
│   │   ├── process/          # 进程检测与智能匹配
│   │   ├── psi/              # 方法元数据与类型分析
│   │   ├── service/          # 项目级 Service
│   │   ├── settings/         # 配置页与持久化
│   │   └── ui/               # 对话框、工具窗口、OGNL 转换
│   └── resources/
│       ├── META-INF/plugin.xml
│       └── arthas/arthas-boot.jar
└── src/test/kotlin/          # 单元测试
```

### 技术栈

| 组件 | 版本 |
|------|------|
| Kotlin | 2.3 |
| IntelliJ Platform Gradle Plugin | 2.17 |
| 目标 IDE | IntelliJ IDEA 2026.1 |
| Arthas | 4.0.5 |
| Gson（插件内部） | 2.11 |

---

## 安全提示

> **此插件可在运行中的 JVM 内执行任意 Java 方法，属于高权限操作。**

- 仅用于**本地开发 / 调试环境**
- **请勿在生产环境使用**
- 调用可能产生副作用（写库、发消息、改状态等）
- 首次调用弹出安全确认，可勾选「不再提示」

---

## License

本项目采用 [Apache License 2.0](LICENSE) 开源协议。

内置的 [Arthas](https://arthas.aliyun.com/) 由阿里巴巴开源，遵循其自身许可证；构建时从 Maven Central 下载 `arthas-boot.jar`。
