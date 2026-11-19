# Run Anywhere 交互设计规范复核结论

| 项目 | 值 |
|---|---|
| 复核对象 | Run Anywhere（`com.runanywhere`）1.0.1 |
| 复核分支 / 提交 | `feature/ux-fixes` / `4538af5` |
| 复核日期 | 2026-11-19 |
| 复核依据 | `feat/ux-review.md`（前次评审 + 自评整改表） |
| 复核方法 | 41 个源文件静态走查 + 平台规范逐条比对 + 构建验证（`compileKotlin` 通过，`test` 87 项 / 0 失败） |
| 复核结论 | **B（有条件通过）** —— 平台硬性禁令清零，但整改引入 6 处闭环缺陷，需一轮收尾 |
| 与自评差异 | 自评 28 ✅ / 2 ⚠️；独立判定 **18 ✅ / 12 ⚠️ / 0 ❌**，另新增 15 项发现 |
| 整改状态 | 2026-11-19 已落地 9 项（工作区未提交）；`compileKotlin` 通过，`test` 87 项 0 失败；**待实机回归** |

---

## 一、结论

**一句话：交互层的"违规"确实改掉了，但"整改"本身制造了新的断点。当前状态不适合直接分发，收尾一轮后可到 A。**

三条判断依据：

1. **平台明文禁令已全部消除，且是可验证的消除。** 全仓库 `Messages.show*Dialog` 归零、`JBColor.BLACK/RED` 归零、`doValidate()` 已接入、`Task.Backgroundable` + 可中断等待真实生效（`attach` 等待循环每 200ms 查取消）。这部分前次评审的核心指控成立，整改有效。

2. **有 4 处"整改动作"产生了新的用户可见故障**，且都属于"沿着界面走就一定撞上"的类型：
   - 空态与设置页的**两个帮助入口指向不存在的页面**（404）；
   - 设置页"恢复首次安全提示"**绕过 Apply/Cancel 语义**，点完按"取消"仍生效；
   - 不支持的参数类型**静默传 null** 且无可见提示，可能让目标 JVM 收到意料之外的参数。

3. **有 5 项自评偏高**（判 ✅ 但只完成了一半），集中在"机制接入了、效果没闭环"：图标双尺寸无引用、Help topic 无文档、accessible name 只覆盖复选框、设置校验分支不可达、读屏播报策略过度。

**等级口径**（与 `ux-review.md` 的 A/B/C 对齐，补充定义）：

| 等级 | 定义 |
|---|---|
| A | 30 项达标且无新增缺陷，可直接分发 |
| **B** | 平台硬性规范全部达标，但存在需收尾的闭环/语义缺陷（≤ 1 人日） |
| C | 仍存在违反平台明文禁令的项 |
| D | 功能闭环不成立 |

本插件落在 B：**规范符合度高，产品完成度不足**。

---

## 二、复核方法与规范取舍

### 2.1 为什么以平台规范为最高权重

桌面 IDE 插件是"平台内嵌产品"，不是独立站点。用户的心智模型来自 IDE 本身：右键菜单的位置、设置页的 Apply/Cancel、通知的形态、空态的结构。插件的任何偏离都会被读成"这个 IDE 坏了"，而不是"这个插件有个性"。

因此权重顺序为：**① 平台规范 > ② 通用可用性启发式 > ③ 可访问性基线 > ④ 分发规范**。若平台规范与通用规范冲突，以平台为准（例如平台要求模态确认框用于不可逆操作，而通用规范倾向非模态——此处平台优先）。

### 2.2 各规范体系的适用性

| 规范体系 | 适用性 | 本插件应采纳的核心约束 |
|---|---|---|
| JetBrains UI / Plugin UX Guidelines | **最高**（平台内嵌） | 组件与布局、主题色 key、就地校验、通知选型、空态结构、图标规格、文案写法 |
| Nielsen 10 启发式 | 高（跨平台） | 状态可见、一致性、错误预防优于错误提示、用户控制与自由、识别优于回忆 |
| ISO 9241-110 | 中（对话原则） | 自描述性、可预期性、容错性、可学习性 |
| WCAG 2.2 AA | 高（无障碍基线） | 1.3.1 信息与关系、1.4.1 不单靠颜色、1.4.3 对比度、2.1.1 键盘、4.1.2 名称角色值、4.1.3 状态消息 |
| Microsoft Windows UX Guidelines | 参考 | "被禁用的控件必须说明为何不可用"——直接支撑第五节 F6 |
| **Material Design / Apple HIG** | **不适用** | 触控优先、大间距、手势导航，与桌面 IDE 的高信息密度、键盘优先、鼠标悬停反馈互相冲突。套用会降低而非提升体验 |
| Marketplace Listing Best Practices | 收尾 | 元数据真实、描述完整、截图、changelog 可追溯 |

### 2.3 本次新增的评审维度：闭环有效性

前次评审的检查方式是"规范条目 → 代码里有没有对应实现"，这在整改阶段是对的，但会漏掉一类问题：**控件、链接、按钮都存在，规范也对，但走到底走不通。**

本次对每个可交互元素追加一问：*用户按这个控件走下去，终点有效吗？* 由此发现 F1、F2、F3、F4 四项——它们的共同特征是"自评 ✅，实际是死路"。

---

## 三、8 个用户触点的结论

| # | 触点 | 规范符合性 | 设计合理性 | 主要遗留 |
|---|---|---|---|---|
| 1 | 入口动作（右键子菜单 / `Alt+Shift+A`） | 符合 | 合理：收进自建子菜单、`anchor="last"`，不再抢占首位 | F11（后台线程读 PSI，需实机确认） |
| 2 | 进程检测与选择 | 符合 | 合理：自动匹配 + 结果通知 + 列表可过滤 | F13（单进程也发"已选择"通知，噪音） |
| 3 | 首次安全确认 | 符合 | 合理：默认按钮为"取消"，Enter 不会误触注入 | — |
| 4 | 参数输入 | **部分符合** | 基本合理（表格式布局、就地校验、JSON 占位提示） | **F6 不支持类型静默传 null；F7 输入框无 accessible name** |
| 5 | 多实例选择 | 符合 | 一般：模态框出现在后台任务执行期间，用户会以为 IDE 卡住 | F12 |
| 6 | 执行反馈（工具窗口 / Tab / 渲染） | 符合 | 合理：进度→完成状态行、解析结果常驻、原始响应折叠、可复制诊断 | F8（播报过强）、F15（状态区不滚动） |
| 7 | 设置页 | **部分符合** | 有两处语义错误：Cancel 不能撤销、校验分支永不触发 | **F4、F5、F2** |
| 8 | 插件元数据 | **部分符合** | 描述已成型，但 vendor / URL / 截图仍为占位 | **F1（文档地址死链）、D4** |

**结论：核心执行链路（2→3→4→6）设计合理、规范到位；问题全部集中在"边缘触点"——帮助入口、设置页语义、无障碍细节。** 这也是为什么定级 B 而不是 C：主干没有问题。

---

## 四、原 30 项独立复核

**统计：18 ✅ / 12 ⚠️ / 0 ❌**（自评为 28 / 2 / 0）

18 项确认达标：A1 A2 A6 B1 B2 B3 B4 B5 B6 B7 B8 B9 C6 C7 C9 D1 D3 D5。其中 B1/B3 的整改可通过 grep 直接证伪式验证——`Messages.` 与 `JBColor.` 硬编码在全仓库已归零。

### 判定下调的 10 项

| # | 自评 | 独立判定 | 下调理由（证据） |
|---|---|---|---|
| A3 图标 | ✅ | **⚠️** | `_16`/`_20` 两个文件已建，但 `RunAnywhereIcons.ToolWindow20`（`RunAnywhereIcons.kt:21`）**全仓库无任何引用**；`plugin.xml:73` 与 `:102` 都指向 16px 版本。且未提供 `_dark` 变体。属"文件层面完成、接入层面未完成" |
| A4 工具窗口按需出现 | ✅ | **⚠️** | 实现存在，但与 A5 冲突（见 F3）；`invokeLater` 直接改可用性，未用 `ToolWindowManager.invokeLater` 保证时序 |
| A5 空态 | ✅ | **⚠️** | 结构合规（标题+说明+主操作+次操作），但"查看使用说明"指向不存在的 GitHub 仓库（F1）；且无图标，与平台 Empty State 示例的结构有差距 |
| B10 读屏播报 | ✅ | **⚠️** | `AccessibleAnnouncerUtil.announce(panel, msg, true)`（`RunResultToolWindowFactory.kt:151`）第三参为 assertive，会**打断**用户当前朗读；且"开始执行"也播报，属过度播报 |
| C1 受控输入 | ✅ | **⚠️** | 已改 `JSpinner`，但 `textField.isEditable = true`（`:163`）：用户键入越界值或非法文本时 JSpinner 内部吞掉，文本框显示与实际值不一致——正是 C1 要消除的"静默修正" |
| C2 配置校验抛异常 | ✅ | **⚠️** | `SpinnerNumberModel` 自带 clamp，`value` 恒在界内 → `validate()`（`:111-129`）**永不返回错误** → `ConfigurationException` 分支不可达。形式合规、实质无效 |
| C3 Help topic | ✅ | **⚠️** | `getHelpTopic() = "run-anywhere-settings"`（`RunAnywhereSettingsConfigurable.kt:40`）但未提供 help 资源包，平台会打开不存在的页面（F2）。**比不设 topic 更差** |
| C5 accessible name | ✅ | **⚠️** | 只给复选框加了 `accessibleName`（`ParameterInputDialog.kt:88`）。输入框（`:79-81`）仍无名称，表头 `JLabel`（`:54-57`）与字段无 `labelFor` 关联。前次评审原文就写了"输入框与参数名列未 labelFor 关联"，整改状态却标了 ✅ |
| C8 可撤销"不再提示" | ✅ | **⚠️** | 入口已加，但点完**立即写盘**（`RunAnywhereSettingsComponent.kt:52-54`），不等 Apply。用户点完再按"取消"，改动仍然生效，违反设置页 Cancel 契约；且无任何生效反馈 |
| D2 语言一致 | ✅ | **⚠️** | i18n 机制已全量接入（key 中英对齐，`I18nBundleTest` 覆盖），但 `action.runMethod.text=运行方法 (Run Anywhere)`（`RunAnywhereBundle.properties:4`）仍是前次评审点名的那行"中文夹英文"；`plugin.xml` 的 `description` / `change-notes` 仍只有中文 |

> C4（`parentId="other"`）维持 ⚠️，与前次一致，非阻塞；D4 维持 ⚠️，阻塞于外部信息。

---

## 五、新发现的问题

### P1 —— 发布前必须修（6 项）

| 编号 | 问题 | 证据 | 影响 |
|---|---|---|---|
| **F1** | 帮助链接指向不存在的地址 | `RunAnywhereLinks.kt:10` → `https://github.com/run-anywhere#readme`；`RunResultToolWindowFactory.kt:186` 绑定到"查看使用说明" | 用户点空态里的引导链接，浏览器打开 404。**比不给链接更糟**：给出路径却断了 |
| **F2** | 设置页 Help topic 无对应文档 | `RunAnywhereSettingsConfigurable.kt:40` | 设置页 Help 按钮跳到不存在的帮助页 |
| **F3** | A4 与 A5 互相抵消 + 主入口存在静默失败路径 | `HideUnusedToolWindowActivity.kt:22-27` 隐藏窗口 → `createToolWindowContent` 不执行 → 空态永不可见；`RunResultToolWindowFactory.kt:90-97` 先 `setAvailable` 再 `show` 再 `addContent`，依赖未验证的时序；`RunMethodAction.kt:128-129` 若返回 null 则 `?: return` | ① 精心做的空态用户看不到，`View → Tool Windows` 也无入口；② 极端情况下用户点"运行方法"后**界面毫无反应** |
| **F4** | "恢复首次安全提示"绕过 Apply/Cancel | `RunAnywhereSettingsComponent.kt:48-56` 监听器内直接 `PropertiesComponent.setValue` | 用户点按钮后按"取消"关闭设置页，期望放弃更改，实际已生效。违反 ISO 9241-110 一致性 与 平台 Configurable 契约 |
| **F6** | 不支持的参数类型静默传 null | `OgnlLiteralConverter.kt:68` `else -> "null"`；`ParameterInputDialog.kt:66-73` 仅给类型标签挂 `toolTipText`；`:118-121` 输入框 `isEnabled=false` | 鼠标悬停才知道"不支持，将传 null"；键盘/读屏用户完全无感知，点"调用"也无任何警告 → 目标 JVM 收到 null，可能 NPE 或产生副作用。命中原评审 C 类"不静默替用户做选择"原则，但未列入 30 条 |
| **F11** | `update()` 在后台线程访问 PSI | `RunMethodAction.kt:349-357`：`getActionUpdateThread() = BGT`，但 `findPsiMethod()` 读 `CommonDataKeys.PSI_ELEMENT/PSI_FILE` | 平台要求 PSI 访问持读锁，BGT 无锁可能抛 read-access 断言 → 右键菜单项不出现或日志刷异常。**静态判断，需实机确认**（若菜单缺失即为此因） |

### P2 —— 本轮建议修（6 项）

| 编号 | 问题 | 证据 | 建议 |
|---|---|---|---|
| **F5** | 设置校验为死代码 | 见 C2 说明 | 改 `JBIntSpinner` + `ComponentValidator` 实时校验，或显式 clamp 后回写文本框 |
| **F7** | 输入框缺 accessible name | `ParameterInputDialog.kt:79-81` | `input.accessibleContext.accessibleName = "${param.name}（${类型}）"` |
| **F8** | 播报过强 | `RunResultToolWindowFactory.kt:151` | 结果用 polite（`false`）；"开始执行"的播报可去掉 |
| **F9** | 图标接入不完整 | `RunAnywhereIcons.kt:21` 无引用；无 `_dark` 变体 | 删除未使用常量；按平台惯例补 `_dark` 或用可跟随主题的描边色 |
| **F10** | 文案与布局残留 | `RunAnywhereBundle.properties:4`（中英夹注）；`tab.running.pid` 用空格对齐（比例字体下无效）；`I18n.kt:34` `TOOL_WINDOW_TITLE()` 无引用（窗口标题取自 `id`）；`param.cancel` 未使用 | 逐项清理；状态区改用 GridBag 或固定宽度标签，别用空格对齐 |
| **F12** | 模态对话框嵌在后台任务中 | `RunMethodAction.kt:331-335` 在 `Task.Backgroundable.run()` 内 `invokeAndWait` 弹实例选择框 | 把"查实例数 + 选择"前移到任务开始前（EDT），或在对话框上提供"取消执行" |

### P3 —— 可延后（3 项）

| 编号 | 问题 | 证据 |
|---|---|---|
| F13 | 只有 1 个进程时也弹"已选择进程"气球通知，属无选择余地的噪音 | `ProcessSelectionPopup.kt:39-48` |
| F14 | `run-anywhere.toolwindow.revealed` 为应用级全局标志，一个项目用过，所有项目都常驻 | `RunResultToolWindowFactory.kt:94` + `RunAnywhereSettings.kt:80-85` |
| F15 | 状态区固定 `JTextArea(3,0)` 且不可滚动，进程名过长换行后被裁切 | `RunResultToolWindowFactory.kt:255` |

---

## 六、发布前行动清单

| 序 | 动作 | 涉及文件 | 工作量 | 验收标准 |
|---|---|---|---|---|
| 1 | 帮助入口改为有效目标：内置说明 / README，或暂时移除链接 | `RunAnywhereLinks.kt`、`RunResultToolWindowFactory.kt:185` | 0.3 人日 | 点击后能读到真实内容，无 404 |
| 2 | `getHelpTopic()` 返回 `null`（或补 help 资源包） | `RunAnywhereSettingsConfigurable.kt:40` | 0.1 | 设置页 Help 不跳死链 |
| 3 | `showRunning` 返回 null 时给 error 通知；`invokeLater` 换 `ToolWindowManager.invokeLater` | `RunMethodAction.kt:128`、`HideUnusedToolWindowActivity.kt:22` | 0.3 | 任何路径下点击都有反馈 |
| 4 | "恢复安全提示"改为 apply 时生效 + 计入 `isModified()` + 点击后 toast | `RunAnywhereSettingsComponent.kt:48-56` | 0.2 | 点按钮 + 取消 → 设置未变 |
| 5 | 不支持类型加行内常显提示（灰字/图标+文字） | `ParameterInputDialog.kt:66-73` | 0.2 | 不悬停也能看到"N 个参数将传 null" |
| 6 | `update()` 内 PSI 访问加 `ReadAction`（或改 EDT） | `RunMethodAction.kt:349-357` | 0.2 | 右键菜单稳定出现，日志无 read-access 异常 |
| 7 | 播报改 polite；补输入框 accessible name | `RunResultToolWindowFactory.kt:151`、`ParameterInputDialog.kt:79` | 0.2 | 读屏不被打断，字段可报名称 |
| 8 | 清理文案残留与死代码 | bundle、`I18n.kt`、`RunAnywhereIcons.kt` | 0.2 | 无未引用 key / 常量 |
| 9 | 补 vendor 真实邮箱、仓库 URL、2–4 张截图 | `plugin.xml:4` | 待外部信息 | Marketplace 列表无占位 |

合计约 **1.7 人日**。其中 1–6 为发布阻断项。

---

## 七、已经做对、不要为了"整改"再改回去

1. **平台禁用项清零是可验证的**：`Messages.show*Dialog` 0 处、`JBColor.BLACK/RED` 0 处、硬编码 `Color(` 0 处。这是本次整改最实的成果。
2. **渐进披露（Progressive Disclosure）**：解析结果常驻、原始命令与响应折叠、一键复制诊断。符合"主界面面向用户、实现细节按需展开"。
3. **默认安全**：注入前有风险确认；默认按钮为"取消"；预热 attach 默认关闭。三处都守住了"危险操作不设默认路径"。
4. **结果不丢失**：每次调用独立 Tab + "全部关闭"，符合"用户数据不应意外丢弃"。
5. **可辨识性**：Tab 标题带运行配置名 + PID + 同名序号，解决了"打到了谁、第几次"。
6. **i18n 结构完整**：中英 key 完全对齐（已校验），`I18n` 门面 + `{n}` 手写替换避开 `MessageFormat` 对 JSON 花括号的解析——这个坑踩对了。
7. **取消的粒度是真的**：`attach` 等待循环每 200ms 查取消并回收进程（`ArthasManager.kt:409-419`），不是"编译过的取消"。

---

## 八、复核局限

1. **未做真机交互回归**。本次结论来自静态走查 + 构建验证（`compileKotlin` 通过、`test` 87 项 0 失败），未在真实 IDE 中点击验证。F3（工具窗口时序）、F11（BGT 读 PSI）、F5（spinner 吞输入）为代码推断，需实机确认；确认方式见第六节验收标准。
2. **未做暗色 / 高对比度主题截图**。颜色类结论基于"已改用 `UIUtil` 语义色"，代码层面确定，但对比度 4.5:1 未实测。
3. **未跑读屏软件**（NVDA / JAWS）。可访问性结论定位到 API 调用层（`announce` 参数、`accessibleName` 是否设置），未验证实际朗读效果。
4. **未核验 `Alt+Shift+A` 在 2026.3 默认 keymap 中的冲突情况**。
5. **无用户数据**。Nielsen / ISO 部分为专家走查判断，非实测。
6. **Marketplace 分发表现未评估**（截图、描述转化率），仅检查元数据真实性。

---

## 附：关键证据索引

| 主题 | 位置 |
|---|---|
| 通知组注册 / 子菜单 / 元数据 | `src/main/resources/META-INF/plugin.xml:4,67,73,102,113-120` |
| 执行主链路 / 进度 / 取消 | `src/main/kotlin/com/runanywhere/action/RunMethodAction.kt:107-287,349-357` |
| 结果面板 / 空态 / 播报 | `src/main/kotlin/com/runanywhere/ui/RunResultToolWindowFactory.kt:150-225,249-429` |
| 参数校验与可达性 | `src/main/kotlin/com/runanywhere/ui/ParameterInputDialog.kt:54-121,151-190` |
| 设置页语义 | `src/main/kotlin/com/runanywhere/settings/RunAnywhereSettingsComponent.kt:48-56,94-165` |
| 取消粒度 | `src/main/kotlin/com/runanywhere/arthas/ArthasManager.kt:186-210,409-419` |
| 文案资源 | `src/main/resources/messages/RunAnywhereBundle.properties`（169 行）/ `_en`（170 行，key 全对齐） |

**验证命令与结果**

```text
gradlew.bat compileKotlin --offline   → BUILD SUCCESSFUL
gradlew.bat test --offline            → BUILD SUCCESSFUL
build/test-results/test/*.xml         → tests=87, failures=0, errors=0
grep "Messages\.|JBColor\.BLACK|JBColor\.RED" src/main → 0 命中
```

---

## 九、本轮整改记录（2026-11-19）

> 第四、五节是 `4538af5` 时点的审计快照，保留不改；本节记录据此落地的改动。

| 编号 | 问题 | 处理 | 涉及文件 |
|---|---|---|---|
| F1 | 帮助链接 404 | `DOCS_URL` 指向项目 Wiki（已核实有正文）；新增 `REPO_URL` / `ISSUES_URL` | `RunAnywhereLinks.kt` |
| F2 | Help topic 无文档 | `getHelpTopic()` 返回 `null`（隐藏平台 Help 按钮）；面板内新增「查看使用说明」链接指向 Wiki | `RunAnywhereSettingsConfigurable.kt`、`RunAnywhereSettingsComponent.kt` |
| F3 | A4 抵消 A5 + 静默失败 | 删除 `HideUnusedToolWindowActivity` 与 `run-anywhere.toolwindow.revealed` 设置，工具窗口常驻；`showRunning` 返回 null 时发 error 通知并继续走新建 Tab 路径 | `plugin.xml`、`RunAnywhereSettings.kt`、`RunMethodAction.kt`、`RunResultToolWindowFactory.kt` |
| F4 | 恢复安全提示绕过 Apply | 改为 `securityResetRequested` 标记 + `apply()` 落地 + `isModified()` 计入 + 行内提示「点击应用后生效」 | `RunAnywhereSettingsComponent.kt` |
| F5 | 校验分支不可达 | `validate()` 改为解析编辑框文本（去分隔符、非数字返回错误），越界报具体文案而非静默 clamp；`apply()` 取校验后的文本值 | `RunAnywhereSettingsComponent.kt` |
| F6 | 不支持类型静默传 null | 禁用输入框内常显「不支持该类型，将传 null」 | `ParameterInputDialog.kt`、两份 Bundle、`I18n.kt` |
| F7 | 输入框缺 accessible name | 每个输入控件设 `参数名 + 类型`（不支持时追加"将传 null"） | `ParameterInputDialog.kt` |
| F8 | 播报过强 | `announce(..., assertive = false)` | `RunResultToolWindowFactory.kt` |
| F11 | BGT 访问 PSI | EDT 直取，后台线程走 `ReadAction.nonBlocking{}.executeSynchronously()`（`ReadAction.compute` 在 2026.3 已废弃） | `RunMethodAction.kt` |
| F12 | 模态框嵌在后台任务中 | 执行链路拆成两阶段：阶段一 attach + 实例查询（可取消）；阶段二在 EDT 弹实例选择后再执行 | `RunMethodAction.kt` |
| F13 | 单进程通知噪音 | 只在多进程自动匹配时通知 | `ProcessSelectionPopup.kt` |
| D4 | `untilBuild` 未设 | 加 `untilBuild = "263.*"` | `build.gradle.kts` |
| D4 | vendor url 占位 | 改为 GitHub 仓库地址，移除占位邮箱（邮箱待补） | `plugin.xml` |

**仍需实机验证**：F3 工具窗口常驻后的空态与显示链路、F11 右键菜单稳定性、F12 两阶段任务的进度条观感、F5 输入非法值时的提示。

**未做的**：Marketplace 截图、vendor 邮箱、插件名与 ID 占用核查（均需外部信息）。
