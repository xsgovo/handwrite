# AGENTS.md

## 工作原则

本项目是 Android 本地手写笔记应用。开发时遵循：

- 优先复用现有模块契约和架构，不跨边界引入快捷全局状态。
- 保持现有 Kotlin / Compose / Gradle 风格。
- 不为无关问题顺手重构、升级依赖、格式化文件或修改测试。
- 发现独立问题时记录并单独处理，不混入当前任务。
- 修改前先阅读相关代码和测试，理解现有行为后再实现。
- 架构约束以 `ARCHITECTURE.md` 为准；架构改变必须先更新该文件。

## 模块与数据

- `core:model` 保持纯 Kotlin。
- `core:document` 只定义领域契约和会话行为。
- `core:data` 负责 Room、Proto DataStore 和资源持久化。
- `core:rendering` 负责领域模型到屏幕/导出画面的渲染。
- feature 模块不得相互依赖；`app` 负责导航和依赖组合。
- Room 是文档数据的事实来源；Proto DataStore 只保存设置。
- 新增持久化字段必须同步 Room schema 和 migration（如适用）。
- 新增设置必须同步 Proto schema、mapper、默认值和 UI。
- 领域层不得暴露 Room Entity、Proto Message、Compose 状态或 AndroidX Ink 类型。

## 测试与验证

本项目当前不建立 CI。统一完整验证命令：

```powershell
.\gradlew.bat --no-daemon verifyLocal
```

根据变更范围选择验证级别：

- 局部低风险修改：优先运行受影响模块的定向测试。
- 跨模块契约、公共 API、构建配置、持久化、导出或架构变更：运行完整 `verifyLocal`。
- 任务明确要求完整验证时，必须运行 `verifyLocal`。
- 提交前至少运行 `git diff --check`。
- 测试优先覆盖领域不变量、持久化幂等性和导出快照一致性。
- 不为实现细节编写脆弱测试。
- 不重复执行结果未发生变化的相同测试或检查。

## Git

保持原子化提交：

- 一个提交只解决一个可独立审阅的结构、行为、测试或文档变更。
- 提交前确认暂存区仅包含当前任务相关文件。
- 使用能说明意图的提交消息。
- 提交后检查一次工作区状态，确认提交成功且无意外修改。

## 新模块

新增模块必须：

1. 在 `settings.gradle.kts` 注册。
2. 遵循 `ARCHITECTURE.md` 的依赖边界。
3. 配置必要的测试、lint 和构建验证。
4. 将验证纳入根 `verifyLocal`。

## 文件与提交

以下内容不得提交：

- `.gradle/`
- `.idea/`
- `.kotlin/`
- `build/`
- 各模块 `build/`
- `local.properties`
- 构建缓存
- 真机截图、XML、导出包和检查报告等临时产物

临时产物放在构建输出目录，不复制到源码目录。

## 完成任务前

确认：

- 修改范围仅包含当前任务相关内容。
- 相关测试已通过。
- 必要时已运行完整 `verifyLocal`。
- `git diff --check` 通过。
- 未引入无关重构或依赖。
- 若改变架构，`ARCHITECTURE.md` 已同步更新。
