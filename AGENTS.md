# AGENTS.md

## 项目定位

这是“随手写”的 Android 本地手写图片笔记应用。长期领域模型是“文档库 -> 多页文档 -> 有序页面元素”，当前编辑器界面只展示一个活动页面。产品永久仅本地运行，不提供账号、网络同步、云备份、遥测、远程配置或应用层加密；旧版本数据不迁移，重构前数据可以直接舍弃。

导出的首要目标是尽可能小的图片笔记文件，不以打印为目标。坐标使用无单位逻辑空间，页面最长边固定为 `65,535`；历史的 `1:1.414` 只作为视觉比例模板，不表示 A4、毫米或 DPI。

## 当前目录

```text
app/                         应用壳、Activity、导航、Hilt 入口、备份规则
build-logic/                 本地约定插件（Android application/library、Compose、Kotlin JVM）
core/
  model/                     无 Android 依赖的领域值对象和约束
  document/                  文档契约、命令、会话撤销/重做、可靠写协调
  data/                     Room、Proto DataStore、Protobuf codec、资源仓库、DI
  rendering/                AndroidX Ink 显示适配、背景解码、图片/PDF 渲染
  designsystem/              Compose Material 主题和共享视觉基础
feature/
  editor/                    画布、固定顶部工具栏、输入手势和编辑会话
  library/                   文档库列表、打开、新建、永久删除
  settings/                  输入、主题、导出、返回行为和侧键设置
  export/                    导出 UI、WorkManager、图片/PDF/原生 ZIP 导出
gradle/                      Version Catalog 和 Gradle Wrapper
ARCHITECTURE.md             已确认的产品与技术架构决策基线
AGENTS.md                   本文件；代理和贡献者的工作约束
```

根目录的 `.gradle/`、`.idea/`、`.kotlin/`、`build/`，各模块的 `build/`，以及 `build-logic` 的构建缓存都是本地生成物，不能提交。`local.properties` 是机器专属的 Android SDK 配置，保留在本机但不能提交。真机截图、XML、导出包和检查报告应放在构建输出目录，不要复制到源码目录。

## 架构边界

- `core:model` 必须保持纯 Kotlin，不依赖 Android、Room、Compose、WorkManager 或 AndroidX Ink。
- `core:document` 只定义领域契约和会话行为，不知道 Room、DataStore、WorkManager 或 Compose。
- `core:data` 是持久化实现：Room 是文档、页面、元素和资源引用的唯一事实源；Proto DataStore 只保存设置。
- `core:rendering` 负责把领域笔迹和背景映射到屏幕或导出画布；AndroidX Ink 只能用于采样/显示适配，不能成为持久化格式。
- feature 模块不能相互依赖，只能依赖 `core`；`app` 是组合根，负责 Activity、导航、全局状态和 Hilt 装配。
- 新模块必须在 `settings.gradle.kts` 注册，在本文件目录表补充所有权，并加入根 `verifyLocal` 的测试/lint/build 覆盖范围。

## 数据与行为不变量

- 每次启动创建新的临时编辑会话并直接进入画布；首次实质内容修改时才物化文档。只改变工具、颜色、缩放或设置不算内容修改；未修改的临时文档结束会话时自动丢弃。
- Room 数据写入顺序是：写入 pending command journal -> 执行 Room transaction -> 删除 journal。重放必须以 UUID operation id 幂等；journal 是恢复介质，不是第二份文档事实源。
- 撤销/重做只在当前编辑会话有效，重新打开文档只能看到最终内容。页面创建、删除不进入撤销栈；清空当前页是一个可撤销内容命令。文档删除立即永久删除并需要确认。
- 文档以多页和稳定 `orderKey` 建模；当前 UI 不添加假占位的多页控件，也不提供用户图层。
- 图片和 PDF 是页面的非破坏性背景，笔迹独立保存其上。外部资源必须复制到应用私有存储，以 SHA-256 内容寻址、去重并用引用计数回收。
- 导出从一次一致性 Room 快照读取；支持当前页图片、全部页面长图、混合 PDF 和版本化原生 ZIP。图片编码是设置项（自动、PNG、WebP、JPEG），默认自动策略偏向 WebP 和较小文件。
- 所有设备使用固定顶部工具栏；空间不足时横向滚动。缩放范围为 100%（完整页面适配）到 400%。

## 工程工作流

本项目当前不建立 CI；本地统一验证命令是：

```powershell
.\gradlew.bat --no-daemon verifyLocal
```

提交前至少运行 `git diff --check` 和上述验证。测试应覆盖新增的领域不变量、持久化幂等性和导出快照一致性；不为实现细节添加脆弱测试。保持原子化提交：一个提交只解决一个可独立审阅的结构、行为、测试或文档变更，并使用能说明意图的提交消息。

编辑时遵循现有 Kotlin/Compose/Gradle 风格，优先复用当前模块契约，不跨边界引入快捷全局状态。新增持久化字段必须同步 Room schema；新增设置必须同步 Proto schema、mapper、默认值和设置 UI。任何架构决策改变都先更新 `ARCHITECTURE.md`，再修改实现。
