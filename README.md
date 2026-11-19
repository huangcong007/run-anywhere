# Run Anywhere

在 IntelliJ IDEA 中，对**任意 Java 方法**右键一键调用，直接作用于**当前正在运行的 JVM**。

插件内置 [Arthas](https://arthas.aliyun.com/) 4.3.5 引导包（`arthas-boot.jar`），通过动态 Attach + HTTP API 执行 OGNL / vmtool 命令，无需修改启动参数或 Run Configuration。首次 attach 时由 Arthas 自行下载运行时并缓存到 `~/.arthas`。

| 项目 | 值 |
|------|-----|
| 插件 ID | `com.runanywhere` |
| 版本 | 1.0.1 |
| 兼容 IDE | IntelliJ IDEA **2026.1 ～ 2026.3**（`sinceBuild = 261`，`untilBuild = 263.*`） |
| 快捷键 | `Alt + Shift + A` |
| 内置 Arthas | 4.3.5 |

**典型场景**：本地快速触发 Service 方法、验证 Spring Bean 逻辑、在断点下调试时异步触发重试任务——无需写临时 Controller 或单元测试。

使用文档（含图文说明）：<https://github.com/huangcong007/run-anywhere/wiki>

---

## 目录

- [功能特性](#功能特性)
- [环境要求](#环境要求)
- [安装](#安装)
- [快速开始](#快速开始)
- [配置](#配置)
- [使用指南](#使用指南)
  - [入口](#入口)
  - [调用策略](#调用策略)
  - [参数输入](#参数输入)
  - [执行模式](#执行模式)
  - [进程选择](#进程选择)
  - [实例选择](#实例选择)
  - [结果窗口](#结果窗口)
- [常见问题](#常见问题)
- [架构说明](#架构说明)
- [开发与构建](#开发与构建)
- [安全提示](#安全提示)

---

## 功能特性

- **四种调用路径**：静态方法、构造函数、Spring Bean、普通实例；重载方法自动改用反射调用
- **智能进程匹配**：自动发现 IDEA 启动的 Java 进程，多进程时按模块 / 主类 / 包名打分
- **参数输入**：基本类型、枚举、`java.time`、BigDecimal、POJO / List / Set / Map / 数组（JSON 输入）
- **多 JSON 库兼容**：Jackson、Gson、Fastjson2、Fastjson 1.x，支持自动检测
- **JSON 化结果**：解析结果自动清洗为可读 JSON（去掉 Arthas `@type` 元数据、缩进排版）
- **同步 / 异步执行**：异步模式在断点暂停时不会超时
- **进度与取消**：Attach 与执行阶段在状态栏显示进度，可一键中断
- **多实例选择**：同一类存在多个实例时，执行前弹窗选择目标实例
- **多进程并行**：每个 PID 独立 Arthas 会话与 HTTP 端口
- **预热 Attach**：进程启动后可选自动 Attach，缩短首次调用等待
- **多 Tab 结果窗口**：每次调用独立 Tab，支持并行执行、折叠详情、复制诊断信息
- **中英文案**：跟随 IDE 语言自动切换

---

## 环境要求

| 项目 | 要求 |
|------|------|
| IDE | IntelliJ IDEA 2026.1 ～ 2026.3（本版本已验证 2026.3） |
| 插件依赖 | Java 插件（内置） |
| 可选依赖 | Spring 插件（增强 Bean 识别） |
| 构建 JDK | Java 25 |
| 目标应用 | 由 IDEA 启动的本地 Java 进程，且允许 JVM Attach（已验证 JDK 17 / 21 / 25） |
| 网络 | 首次 attach 需可访问 Maven Central：运行时约数十 MB，下载后缓存在 `~/.arthas`，后续调用不再联网 |

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
2. 选择 `build/distributions/run-anywhere-1.0.1.zip`
3. 重启 IDE

> 本地打包若在 `buildSearchableOptions` 一步失败（该任务会临时启动一个无界面 IDE 生成设置项检索索引，读取 IDE 目录锁时可能报错），可跳过它：`./gradlew buildPlugin -x buildSearchableOptions`。这一步只影响 Marketplace 的设置项搜索，不影响本地安装。

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
2. 将光标置于目标 **Java 方法**（含构造方法）内
3. 右键，点击编辑器菜单**首项**「**运行方法 (Run Anywhere)**」，或按 **`Alt + Shift + A`**
4. 首次使用会弹出风险确认，可勾选「不再提示」
5. 有参数时弹出参数对话框（无参数自动跳过）
6. 插件自动匹配目标进程；多进程无法确定时弹窗选择
7. 目标类存在多个实例时，Attach 完成后弹窗选择实例
8. 底部 **Run Anywhere** 工具窗口查看结果；执行期间状态栏可见进度并可取消

> 菜单项只在光标位于方法内时出现；不在方法上时整项隐藏，不会在菜单里留下灰行。

---

## 配置

路径：**Settings → Other Settings → Run Anywhere**（应用级，全局生效）

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| 异步执行 | 开启 | 通过 `async_exec` 提交后再取回返回值；目标线程停在断点时不会超时 |
| 进程启动后预热 Attach | 关闭 | 开启后应用启动约 2 秒自动 Attach；关闭则只在首次调用时注入 Arthas |
| 执行超时（秒） | 300 | 范围 30～3600。既是同步执行的 Arthas 超时，也是**异步模式等待返回值的上限** |
| 对象参数 JSON 库 | 自动检测 | 见下表 |
| 恢复首次安全提示 | — | 重新开启首次调用前的风险确认。点击后需**应用**才生效，取消则保持原状 |

设置面板底部提供「查看使用说明」链接，直达项目 Wiki。

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

### 入口

| 方式 | 说明 |
|------|------|
| 右键菜单首项 | 「运行方法 (Run Anywhere)」，位于编辑器右键菜单第一位，一次右键 + 一次点击 |
| 快捷键 | `Alt + Shift + A` |
| 可见性 | 仅当光标位于方法（含构造方法）内时出现 |

### 调用策略

插件根据方法类型自动构造 Arthas 命令，`-x` 展开层级固定为 3（返回 `void` 时为 0）：

| 方法类型 | 识别条件 | 命令示例 |
|----------|----------|----------|
| 静态方法 | `static` 修饰符 | `ognl '@com.example.Utils@add(1, 2)' -x 3` |
| 构造函数 | 光标位于构造方法内 | `ognl 'new com.example.User(1L)' -x 3` |
| Spring Bean | `@Component` / `@Service` / `@RestController` 等 | `vmtool --action getInstances --className org.springframework.context.ApplicationContext --express 'instances.{? #this.containsBean("userService")}[0].getBean("userService").findById(1L)' -x 3` |
| 普通实例 | 非静态、无 Spring 注解 | `vmtool --action getInstances --className com.example.Service --express 'instances[0].run()' -x 3` |

**重载方法**会自动改用反射调用，以消除参数类型歧义：方法走 `getDeclaredMethod(...).invoke(...)`，构造方法走 `getDeclaredConstructor(...).newInstance(...)`，并 `setAccessible(true)`。重载的 `private` 方法同样可以被调用。

**Spring Bean 名称规则**：

- 优先读取注解 `value` / `name`
- 未指定时按 Spring 默认规则（`UserService` → `userService`）
- 支持 `@RocketMQMessageListener`、`@KafkaListener` 等 Listener 注解类

**已知限制**：

- 通过 `@Bean` 方法注册的 Bean 暂不支持自动识别
- 普通实例方法默认取 `instances[0]`；存在多个实例时会先弹窗选择

### 参数输入

有参数时弹出对话框；无参数时自动跳过。

| 类型 | 输入示例 | 说明 |
|------|----------|------|
| `String` | `hello` | 自动加引号转义（内容含单引号也会安全拼接） |
| `int` / `Integer` | `123` | 原样传递 |
| `long` / `Long` | `123` 或 `123L` | 自动补 `L` 后缀 |
| `boolean` / `Boolean` | `true` | 原样传递 |
| `char` / `Character` | `A` | 字符字面量 |
| `BigDecimal` / `BigInteger` | `99.99` | 构造器表达式 |
| 枚举 | `ACTIVE` | `@com.example.Status@ACTIVE` |
| `java.time.*` | `2026-11-19T10:00:00` | `LocalDate` / `LocalDateTime` / `LocalTime` / `Instant` / `OffsetDateTime` / `ZonedDateTime`，按 `parse()` 构造 |
| POJO / DTO | `{"id":1,"name":"test"}` | JSON 反序列化 |
| `List<T>` / `Set<T>` | `[{"id":1}]` | JSON 数组 |
| `Map<K,V>` | `{"key":{"id":1}}` | JSON 对象 |
| `T[]` | `[{"id":1}]` | JSON 数组 |
| 空值 | 留空或勾选「传 null」 | `null` |

对象 / 集合类型使用多行 JSON 输入框，提交前校验 JSON 格式。每行提供「传 null」复选框。

**不支持的类型**：输入框会被禁用并常显提示「不支持该类型，将传 null」，避免静默把 `null` 传给目标方法。

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

- 通过 Arthas `async_exec` 提交，再轮询取回返回值
- **目标线程停在断点时不会触发插件超时**：提交是立即的，等待只发生在插件侧
- 等待上限与「执行超时」相同；超过后 Tab 显示「已提交（异步）」、Job ID 与任务状态
- 适合：断点调试、上传重试、长时间任务

**断点调试流程**：

1. 在目标方法打断点
2. 保持「异步执行」开启
3. 右键调用方法 → 提交成功
4. 在 Debugger 中继续单步；方法返回后插件自动回填结果
5. 若在等待上限内未返回，继续执行后重新调用一次即可

#### 同步执行

- 使用 Arthas `exec`，**等待方法执行完毕**后返回结果
- 超时由「执行超时」控制（默认 300 秒）
- 适合：需要立即在工具窗口查看返回值

出现 `timeExpired: true` / `state: INTERRUPTED` 时，增大执行超时或改用异步模式。

### 进程选择

通过 IDEA `ExecutionManager` 检测当前项目下由 IDE 启动的 Java 进程：

| 进程数 | 行为 |
|--------|------|
| 0 | 提示先运行应用 |
| 1 | 静默自动选中（无选择余地，不再弹通知） |
| 多个 | 智能匹配；匹配成功时提示「已选择进程」，无法唯一确定时弹窗 |

**自动匹配打分规则**（优先级从高到低）：

| 规则 | 分值 | 示例 |
|------|------|------|
| 模块一致 | +100 | 方法所在 Module = Run Configuration 关联 Module |
| 主类包名匹配 | +60 | 方法类位于启动类包或其子包 |
| 包名业务分段匹配 | +45 | `com.maiyawx.admin.*` ↔ 模块名 `maiyawx-admin` |
| 根包名一致 | +30 | `com.maiyawx.*` ↔ `com.maiyawx.app.*` |
| 配置名含模块名 | +25 | Run Configuration 名称包含模块名 |

仅一个进程得分 > 0 时自动选中。查看 `idea.log` 中 `Process match` 可了解打分详情。

### 实例选择

普通实例方法需要确定"调哪一个对象"：

1. Attach 完成后先查询该类在目标 JVM 中的实例数量
2. 只有一个实例 → 直接使用 `instances[0]`
3. 多个实例 → 执行前弹窗，按「类名@identityHashCode」列出可选实例
4. 一个实例都没有 → 提示「类 X 在目标 JVM 中没有已创建的实例」，本次不执行

选择框出现在 Attach 完成之后、命令执行之前，不会覆盖进度条；取消则本次调用终止。

### 结果窗口

- 位置：IDE 底部 **Run Anywhere** 面板
- 每次调用新增可关闭 Tab，标题为 `运行配置名 · PID · 方法名()`
- 状态区：开始 / 完成时间、状态（成功 / 失败 / 已提交（异步））、PID
- **解析结果**常驻显示，按 JSON 输出：去掉 Arthas 的 `@type`、`@fieldTypes` 元数据并缩进；方法返回 JSON 字符串时也会自动格式化
- 正文使用等宽字体、不自动换行（JSON 依赖缩进表达层级，长行可横向滚动）
- **显示详情**可展开「执行的命令」与「原始响应」；**复制诊断信息**一键复制完整上下文
- 标题栏 **「全部关闭」** 关闭所有结果 Tab，保留欢迎页
- 未执行过任何调用时显示欢迎页：说明入口，并提供「打开设置」「查看使用说明」

> 嵌套超过 3 层的对象会被 Arthas 渲染成字符串；这是 `-x` 的展开上限，不是插件截断。

---

## 常见问题

### Attach 失败

**现象**：「Arthas attach 到 PID xxx 失败」

| 原因 | 处理 |
|------|------|
| JVM 禁止 Attach | 确认未设置 `-XX:+DisableAttachMechanism` |
| Arthas 启动超时 | 查看 `idea.log`，搜索 `[Arthas]` |
| HTTP 端口被占用 | 关闭其他 Arthas 实例或重启应用 |
| 首次注入较慢 | 首次 attach 要下载 Arthas 运行时（数十 MB），等待 1～2 分钟；也可开启「预热 Attach」 |
| 无法访问 Maven Central | 首次 attach 会失败或超时。配置代理，或提前把 `~/.arthas/lib/4.3.5/arthas` 准备好（已有同版本安装会被直接复用） |

> 插件包内只有 `arthas-boot.jar` 引导包（147 KB），完整的 core / agent / spy 运行时是在首次 attach 时由 `arthas-boot` 下载的，之后走 `~/.arthas` 本地缓存。

### Session not found

**现象**：`state: FAILED`，`message: session not found: <uuid>`

Arthas HTTP Session 因应用重启、长时间未调用等原因失效。插件会在调用前重建 Session，失败时自动重试一次。仍失败则重启目标应用。

### 执行超时 / 未拿到结果

**现象**：`timeExpired: true`、`state: INTERRUPTED`，或异步模式显示「已提交，但在 N 秒内没有等到返回值」

1. 目标线程可能停在断点 → 继续执行后重新调用
2. Settings 中增大执行超时
3. 排查方法本身死锁或无限阻塞

### 解析结果只有 3 层

Arthas 的对象展开深度由 `-x` 控制，插件固定为 3。更深的嵌套会被渲染成字符串，属于 Arthas 的渲染限制。需要完整结构时，可让目标方法返回 `String`（自行序列化），或用「原始响应」核对。

### 参数显示「不支持该类型」

该参数类型不在支持列表内（如自定义接口、函数式接口、泛型通配符），提交时会传 `null`。可改用包装类型、改为 JSON 可反序列化的具体类，或临时增加一个便于调用的重载方法。

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
        ├── ParameterInputDialog       参数输入 → OGNL 字面量 / JSON 反序列化
        ├── ProcessSelectionPopup      进程选择
        │
        ├── 后台任务①  Attach + 实例查询
        │        └── InstancePickDialog（EDT，多实例时弹窗）
        │
        └── 后台任务②  执行命令
                 │
                 ▼
        ArthasSessionService（项目级）
                 │
                 ▼
             ArthasManager
        Map<PID, ArthasSession>        每 PID 独立端口 + HTTP Client
                 │
        ┌────────┴────────┐
        ▼                 ▼
  arthas-boot.jar    ArthasHttpClient
  （Attach 子进程）    exec / async_exec
        │                 │
        └────────┬────────┘
                 ▼
            目标 JVM
                 ▼
     ArthasResponseParser（清洗为 JSON）
                 ▼
      Run Anywhere 工具窗口
```

| 包 | 核心类 | 职责 |
|----|--------|------|
| `action` | `RunMethodAction` | 入口，串联「准备 → 选实例 → 执行」两阶段流程 |
| `psi` | `MethodMetadataExtractor` | PSI 元数据提取（含重载、构造函数、Spring Bean 名） |
| `psi` | `ParameterTypeAnalyzer` | 参数类型分类（对象 / 集合 / Map / 时间等） |
| `arthas` | `ArthasManager` | 多 PID 会话、Attach 生命周期、异步结果轮询 |
| `arthas` | `OgnlExpressionBuilder` | 构造 OGNL / vmtool / 反射命令 |
| `arthas` | `ArthasResponseParser` | 响应解析、`@type` 元数据清洗、JSON 缩进 |
| `arthas` | `ArthasHttpClient` | HTTP 客户端 |
| `process` | `RunningProcessDetector` | 进程发现 |
| `process` | `ProcessAutoSelector` / `ProcessMatchScorer` | 匹配打分与选择 |
| `ui` | `OgnlLiteralConverter` | 基本类型 → OGNL 字面量 |
| `ui` | `JsonDeserializeOgnlBuilder` | JSON → 多库反序列化 OGNL |
| `ui` | `RunResultToolWindowFactory` | 工具窗口、结果渲染、诊断复制 |
| `service` | `ArthasSessionService` | 项目级会话管理、预热 Attach |
| `settings` | `RunAnywhereSettings` | 持久化配置 |
| `i18n` | `I18n` | 中英文案读取 |

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
./gradlew buildPlugin -x buildSearchableOptions  # 本地环境无法生成检索索引时使用
./gradlew runIde               # Sandbox IDE 调试
```

### 构建产物

| 文件 | 说明 |
|------|------|
| `build/distributions/run-anywhere-1.0.1.zip` | 可安装插件包 |
| `build/libs/run-anywhere-1.0.1.jar` | 插件 JAR |

### 项目结构

```
run-anywhere/
├── build.gradle.kts
├── gradle.properties
├── src/main/
│   ├── kotlin/com/runanywhere/
│   │   ├── action/           # 菜单 Action 与执行编排
│   │   ├── arthas/           # Arthas 管理、命令构造、响应解析
│   │   ├── concurrent/       # 插件线程池
│   │   ├── i18n/             # 中英文案读取
│   │   ├── icons/            # 图标
│   │   ├── lifecycle/        # 应用生命周期
│   │   ├── process/          # 进程检测与智能匹配
│   │   ├── psi/              # 方法元数据与类型分析
│   │   ├── service/          # 项目级 Service、预热 Attach
│   │   ├── settings/         # 配置页与持久化
│   │   └── ui/               # 对话框、工具窗口、OGNL 转换
│   └── resources/
│       ├── META-INF/plugin.xml
│       ├── icons/
│       ├── messages/         # 中文 / 英文文案 Bundle
│       └── arthas/arthas-boot.jar
└── src/test/kotlin/          # 单元测试
```

### 技术栈

| 组件 | 版本 |
|------|------|
| Kotlin | 2.3.0 |
| IntelliJ Platform Gradle Plugin | 2.20.1 |
| 目标 IDE | IntelliJ IDEA 2026.3 |
| Arthas | 4.3.5 |
| Gson（插件内部） | 2.11.0 |

---

## 安全提示

> **此插件可在运行中的 JVM 内执行任意 Java 方法，属于高权限操作。**

- 仅用于**本地开发 / 调试环境**
- **请勿在生产环境使用**
- 调用可能产生副作用（写库、发消息、改状态等）
- 首次调用弹出安全确认，可勾选「不再提示」；需要恢复时到设置页点「恢复首次安全提示」并**应用**

---

## License

本项目采用 [Apache License 2.0](LICENSE) 开源协议。

内置的 [Arthas](https://arthas.aliyun.com/) 由阿里巴巴开源，遵循其自身许可证；构建时从 GitHub Releases 的 `arthas-bin.zip` 中提取 `arthas-boot.jar`。
