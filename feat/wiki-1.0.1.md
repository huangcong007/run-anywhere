# Run Anywhere

在 IntelliJ IDEA 中，对**任意 Java 方法**右键一键调用，直接作用于**当前正在运行的 JVM**。

插件内置 [Arthas](https://arthas.aliyun.com/) 4.3.5，通过动态 Attach + HTTP API 执行 OGNL / vmtool 命令，无需修改启动参数或 Run Configuration。

| 项目 | 值 |
|---|---|
| 插件 ID | `com.runanywhere` |
| 版本 | 1.0.1 |
| 兼容 IDE | IntelliJ IDEA **2026.1 ～ 2026.3**（`sinceBuild = 261`，`untilBuild = 263.*`） |
| 构建 JDK | Java 25 |
| 内置 Arthas | 4.3.5 |
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
  - [入口与右键菜单](#入口与右键菜单)
  - [进程选择](#进程选择)
  - [参数输入](#参数输入)
  - [执行与进度](#执行与进度)
  - [结果窗口](#结果窗口)
- [从 0.1.0 升级](#从-010-升级)
- [常见问题](#常见问题)
- [架构说明](#架构说明)
- [开发与构建](#开发与构建)
- [安全提示](#安全提示)
- [License](#license)

---

## 功能特性

- **三种调用路径**：静态方法（OGNL）、Spring Bean（`getBean`）、普通实例（`vmtool`）
- **智能进程匹配**：自动发现 IDEA 启动的 Java 进程，多进程时按模块/主类/包名打分匹配
- **丰富参数支持**：基本类型、枚举、POJO / List / Map / 数组（JSON 输入）
- **多 JSON 库兼容**：Jackson、Gson、Fastjson2、Fastjson 1.x，支持自动检测
- **同步 / 异步执行**：异步模式适合断点调试与长耗时任务
- **进度与取消**：连接与执行阶段在状态栏显示进度，可一键取消
- **结果可追溯**：每次调用独立 Tab，解析结果常驻，命令与原始响应按需展开，一键复制诊断信息
- **多进程并行**：每个 PID 独立 Arthas 会话与 HTTP 端口
- **预热 Attach**：可配置在应用启动后后台 Attach，缩短首次调用等待
- **中英双语**：文案跟随 IDE 语言切换

---

## 环境要求

| 项目 | 要求 |
|---|---|
| IDE | IntelliJ IDEA 2026.1 ～ 2026.3 |
| 插件依赖 | Java 插件（内置） |
| 可选依赖 | Spring 插件（增强 Bean 识别） |
| 构建 JDK | Java 25 |
| 目标应用 | 由 IDEA 启动的本地 Java 进程，且允许 JVM Attach |

---

## 安装

### 从 ZIP 安装（推荐）

```bash
# Windows
.\gradlew.bat buildPlugin

# macOS / Linux
./gradlew buildPlugin
```

1. **Settings → Plugins → ⚙ → Install Plugin from Disk...**
2. 选择 `build/distributions/run-anywhere-1.0.1.zip`
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
3. 右键菜单**首项**「运行方法」，或按 `Alt + Shift + A`
4. 首次使用会弹出安全确认；有参数时填写参数对话框（无参数自动跳过）
5. 插件自动选择目标进程（多进程无法确定时弹窗）
6. 状态栏显示进度；结果在底部 **Run Anywhere** 工具窗口展示

---

## 配置

路径：**Settings → Other Settings → Run Anywhere**（应用级，全局生效）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| 异步执行 | 开启 | 使用 `async_exec`，提交后立即返回，适合断点调试 |
| 进程启动后预热 Attach | 关闭 | 应用启动约 2 秒后后台 Attach，缩短首次调用等待 |
| 执行超时（秒） | 300 | 范围 30～3600。同步执行的 Arthas 超时，**也是异步模式等待返回值的时间上限** |
| 对象参数 JSON 库 | 自动检测 | 依次尝试 Jackson → Gson → Fastjson2 → Fastjson 1.x；无 Jackson 时可手动指定 |
| 恢复首次安全提示 | — | 撤销「不再提示」，下次调用重新弹出风险确认。**点击后需「应用」才生效** |

面板底部提供「查看使用说明」链接，指向本 Wiki。

数值项为受控输入：超出范围会就地提示，不会被静默改成边界值。

---

## 使用指南

### 入口与右键菜单

- 右键菜单：**首项**直接是「运行方法」，仅当光标位于方法内时出现
- 快捷键：`Alt + Shift + A`
- 仅当光标位于 Java 方法内、且能解析出方法元数据时可见

### 进程选择

通过 IDEA `ExecutionManager` 检测当前项目下由 IDE 启动的 Java 进程：

| 进程数 | 行为 |
|---|---|
| 0 | 气球通知提示先运行应用 |
| 1 | 直接选中，不再额外通知 |
| 多个 | 智能匹配；唯一最高分自动选中并发通知，否则弹窗选择 |

弹窗支持按 `PID` 或进程名输入过滤。

**自动匹配打分规则**（优先级从高到低）：

| 规则 | 分值 | 示例 |
|---|---|---|
| 模块一致 | +100 | 方法所在 Module = Run Configuration 关联 Module |
| 主类包名匹配 | +60 | 方法类位于启动类包或其子包 |
| 包名业务分段匹配 | +45 | `com.maiyawx.admin.*` ↔ 模块名 `maiyawx-admin` |
| 根包名一致 | +30 | `com.maiyawx.*` ↔ `com.maiyawx.app.*` |
| 配置名含模块名 | +25 | Run Configuration 名称包含模块名 |

查看 `idea.log` 中 `Process match` 可了解打分详情。

### 参数输入

方法有参数时弹出对话框，无参数自动跳过。

| 类型 | 输入示例 | 说明 |
|---|---|---|
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

交互说明：

- 对象 / 集合类型使用多行 JSON 输入框，提交前就地校验格式，错误标在对应字段上，不弹二次对话框
- **不支持的类型**会禁用输入框，并在框内与提示中注明「不支持该类型，将传 null」——请确认这是预期行为
- 参数名、类型、值、传 null 分列展示；读屏软件可朗读每个输入项的用途

### 执行与进度

调用分三个阶段，前两个阶段共用一个可取消的后台任务：

1. **连接**：必要时 attach 到目标 JVM（含预热检查），状态栏显示进度
2. **准备**：普通实例方法会先查询实例数量；存在多个实例时**在后台任务结束后**弹出选择框
3. **执行**：构造并执行命令；异步模式会继续等待返回值直到执行超时

取消：点击状态栏进度条上的「取消」即可中断。限制是**同步 HTTP 请求执行期间**取消需要等请求返回（上限 = 执行超时）才生效；attach 阶段约 200ms 内响应。

### 结果窗口

- 位置：IDE 底部 **Run Anywhere** 面板
- 每次调用新增可关闭 Tab，标题为 `运行配置名 · PID 1234 methodName()`；同名调用追加 `#2`、`#3`
- 状态行：执行时间 / 状态（✅ 成功、❌ 失败、📝 已提交（异步））/ PID
- 正文：默认显示**返回值的 JSON**（等宽字体、不换行）。对象 / 集合 / 数组按层级缩进，已去掉 Arthas 的 `@type`、`@fieldTypes` 元数据；方法本身返回 JSON 字符串时会自动格式化；返回普通字符串或数字则原样显示
- 「显示详情」：展开本次执行的 Arthas 命令与原始响应
- 「复制诊断信息」：把状态、命令、原始响应、解析结果一并复制到剪贴板
- 标题栏「全部关闭」：关闭所有结果 Tab，保留「欢迎使用」
- 首次使用（无任何结果）时，空态提供「打开设置」与「查看使用说明」两个入口

结果到达时，屏幕阅读器会播报一条摘要（非打断式）。

---

## 从 0.1.0 升级

1.0.1 的交互变化：

- 右键入口从菜单末尾的子菜单改为菜单**首项**，一次点击直达
- 新增执行进度与取消、结果详情折叠、复制诊断信息
- 解析结果统一按 JSON 输出：去掉 `@type` 元数据并缩进，正文改等宽字体
- 新增设置项：预热 Attach、恢复首次安全提示
- 错误提示改为气球通知 / 编辑器内提示，不再弹模态框
- 表单校验改为就地标注
- 全量中英文案，跟随 IDE 语言

---

## 常见问题

### Attach 失败

**现象**：气球通知「无法连接 PID xxx，详情见 Run Anywhere 工具窗口」

| 原因 | 处理 |
|---|---|
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
2. 在设置中增大「执行超时」
3. 排查方法本身死锁或无限阻塞

### 点了「运行方法」没有任何反应

按顺序确认：

1. 目标应用是通过 IDEA Run/Debug 启动的（不是外部终端）
2. 应用仍在运行
3. 当前打开的是启动该应用的同一项目
4. 若弹出过参数对话框，确认已点「调用」而不是「取消」

仍无反应时查看 `idea.log` 中是否有 `Run Anywhere` 相关异常，并在 Issues 中反馈。

### Spring Bean 调用失败

- Bean 名不正确 → 注解中显式指定 `value` / `name`
- 类未被 Spring 管理 → 确认有 `@Component` 等注解
- `@Bean` 注册的 Bean → 当前版本暂不支持自动识别

### 对象参数反序列化失败

- 目标 JVM 无 JSON 库 → 添加 Gson 等依赖，或在设置中指定正确的库
- JSON 字段名与 Java 属性不匹配 → 检查字段名
- Fastjson 1.x + 复杂 Map → 改用 Jackson / Gson / Fastjson2

### 多进程未能自动匹配

弹出选择框时：

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
|---|---|---|
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

- JDK 25
- Gradle Wrapper（项目自带）

### 常用命令

```bash
./gradlew downloadArthasBoot   # 下载 arthas-boot.jar（版本不匹配时重新下载，构建时自动执行）
./gradlew compileKotlin        # 编译
./gradlew test                 # 单元测试
./gradlew buildPlugin          # 构建可安装 ZIP
./gradlew runIde               # Sandbox IDE 调试
```

### 构建产物

| 文件 | 说明 |
|---|---|
| `build/distributions/run-anywhere-1.0.1.zip` | 可安装插件包 |
| `build/libs/run-anywhere-1.0.1.jar` | 插件 JAR |

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
│   │   ├── i18n/             # 文案门面
│   │   ├── lifecycle/        # 应用生命周期
│   │   ├── process/          # 进程检测与智能匹配
│   │   ├── psi/              # 方法元数据与类型分析
│   │   ├── service/          # 项目级 Service
│   │   ├── settings/         # 配置页与持久化
│   │   └── ui/               # 对话框、工具窗口、OGNL 转换
│   └── resources/
│       ├── META-INF/plugin.xml
│       ├── icons/            # 16/20 灰阶图标
│       ├── messages/         # 中英文案
│       └── arthas/arthas-boot.jar
└── src/test/kotlin/          # 单元测试
```

### 技术栈

| 组件 | 版本 |
|---|---|
| Kotlin | 2.3 |
| IntelliJ Platform Gradle Plugin | 2.20 |
| 目标 IDE | IntelliJ IDEA 2026.3 |
| Arthas | 4.3.5 |
| Gson（插件内部） | 2.11 |

---

## 安全提示

> **此插件可在运行中的 JVM 内执行任意 Java 方法，属于高权限操作。**

- 仅用于**本地开发 / 调试环境**
- **请勿在生产环境使用**
- 调用可能产生副作用（写库、发消息、改状态等）
- 首次调用弹出安全确认，可勾选「不再提示」；之后可在设置页「恢复首次安全提示」撤销

---

## License

本项目采用 [Apache License 2.0](https://github.com/huangcong007/run-anywhere/blob/main/LICENSE) 开源协议。

内置的 [Arthas](https://arthas.aliyun.com/) 由阿里巴巴开源，遵循其自身许可证；构建时从 GitHub Releases 的 `arthas-bin.zip` 中提取 `arthas-boot.jar`。
