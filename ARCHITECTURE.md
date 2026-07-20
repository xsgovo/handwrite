# 架构基线

本文仅记录长期、不可违反的架构约束。实现细节以代码为准；架构发生变化时，先更新本文，再修改实现。

## 1. 产品边界

本项目是永久本地运行的 Android 图片笔记与手写文档应用。

- 无账号、服务端、网络同步、遥测、广告和远程配置。
- 核心模型：`Library → Document → Page → PageElement`。
- 当前编辑器可只展示单页，多页、文件夹等领域模型可暂未开放 UI。
- 文档唯一事实来源是 Room；UI/ViewModel 状态仅是会话状态。
- 启动进入新的临时编辑会话，不恢复上次撤销历史。
- 临时文档首次发生实质内容修改后才物化到 Room；空白临时文档结束时丢弃。
- 文档删除立即永久删除，无回收站。
- 不引入 A4、DPI、打印尺寸等物理纸张语义。
- 不实现云同步、账号、服务端、遥测、应用层加密、用户图层、跨会话撤销和页面操作撤销。

## 2. 领域模型

核心层级：

```
Library → Document → Page → PageElement
```

约束：

- 文档至少包含一页。
- Document/Page/Element/Folder/Resource 使用稳定本地 `Long ID`。
- 页面和元素使用 `orderKey` 排序，不以数组位置作为身份。
- `OperationId` 使用 UUID，作为命令幂等键。
- 原生导入必须重新生成全部本地 ID。
- PageElement 当前仅包含笔迹；背景独立于 PageElement。

坐标：

- 页面使用无单位逻辑坐标。
- 最长边为 `65_535`，另一边按比例计算。
- 显示和导出分别映射到像素空间。
- 历史 `1000:1414` 仅作为兼容比例模板，不代表 A4。

笔迹：

- 持久化使用自有领域模型，不依赖 AndroidX Ink 类型。
- `StrokeSample` 保存逻辑坐标、0～65535 压力、相对时间和可选倾斜。
- `BrushStyle` 保存 brush ID、颜色、逻辑笔宽、混合模式和压力敏感度。
- AndroidX Ink 仅用于输入和显示，完成笔迹必须转换为领域模型后持久化。

背景：

- 背景属于 `PageBackground`，不是 PageElement。
- 支持纯色、透明、横线/方格、图片/PDF。
- 外部资源必须复制到应用私有存储，不长期依赖来源 URI。

## 3. 模块边界

- `:core:model`：纯 Kotlin 领域模型和契约，不依赖 Android。
- `:core:document`：文档命令、Repository 契约、会话和可靠写入契约。
- `:core:data`：Room、DataStore、资源和持久化实现。
- `:core:rendering`：笔迹、背景和资源渲染。
- `:core:designsystem`：共享 UI 基础。
- `:feature:*`：各功能 UI 和业务流程。
- `:app`：应用入口、导航和依赖组合。

约束：

- feature 之间禁止直接依赖，由 `:app` 负责组合。
- core 不得依赖 feature。
- Room Entity、Proto Message、Compose 状态和 AndroidX Ink 类型不得泄漏为领域 API。
- 新领域能力先进入 core 契约，再由 data/feature 实现。

## 4. 持久化

Room 是文档、页面、元素和资源关系的唯一事实来源。

- 所有文档内容变更必须通过 Room transaction。
- 内容、修改时间、资源引用计数和幂等键必须保持事务一致。
- v1 后 schema 变化必须提供 migration 和测试。
- 当前不迁移重构前旧数据。

Proto DataStore 独立管理设置。

新增设置必须同步：

```
Proto schema → mapper → 默认值 → UI
```

资源：

- 导入资源必须复制到私有存储并计算 SHA-256。
- 相同哈希复用资源。
- 通过 Room 引用计数管理资源生命周期。
- 引用为 0 的资源可清理。
- 资源文件和数据库记录必须保持一致。

## 5. 可靠写入与撤销

可撤销内容变更统一使用 `DocumentCommand`。

可靠写入顺序：

```
journal → Room transaction → OperationId → 删除 journal → 会话历史
```

规则：

- journal 必须先于 Room 写入。
- `OperationId` 保证恢复和重复执行幂等。
- 启动时恢复未完成命令。
- 正常写入与恢复必须串行。
- journal 位于 `noBackupFilesDir`，不参与设备迁移。

撤销：

- 仅存在当前 EditorViewModel 会话。
- 新会话清空历史。
- 新命令清空 redo。
- Room 写入成功后才进入历史。
- 文档、页面结构操作不进入撤销历史。
- 撤销不是持久版本历史。

## 6. 临时文档

启动创建：

```
EditorDestination(documentId = null, sessionId = random)
```

实质内容修改包括：

- 新增或删除笔迹
- 清空非空页面
- 修改页面背景
- 导入资源

以下操作不物化文档：

- 缩放、平移
- 工具切换
- 颜色、笔宽变化
- 应用设置修改

未物化临时文档不可导出。

打开已有文档必须创建新的 `sessionId`，不恢复旧撤销栈。

## 7. 输入与编辑器

支持 Finger、Stylus-only、双指手势、整笔橡皮擦和手写笔侧键。

规则：

- 两指优先执行缩放/平移。
- Finger 模式单指绘制。
- Stylus-only 模式下 stylus 绘制，手指平移。
- 页面外起笔不创建笔迹。
- 橡皮按逻辑半径命中采样点后删除整笔。
- 侧键支持临时橡皮、切换橡皮和撤销。
- 侧键悬停或未接触屏幕时不得触发橡皮擦命中。
- 完成笔迹必须立即转换为领域模型。

视口：

- 100% 表示完整页面适配，不表示 1:1 像素映射。
- 缩放范围 100%～400%。
- 100% 时 pan 归零。
- pan 不得将整个页面移出视口。

## 8. 导出

导出必须从单次 Room transaction 获取 `DocumentSnapshot`，之后只使用该快照，不观察实时 Flow。

支持当前页图片、全部页面长图、混合 PDF 和版本化原生 ZIP。

约束：

- 导出使用 WorkManager。
- 导出不修改文档。
- 图片支持 `AUTO / PNG / WebP / JPEG`。
- 分辨率支持 `SMALL / STANDARD / HIGH`。
- 默认优先控制文件体积。
- PDF 不引入打印尺寸语义。
- 原生格式必须带版本号。
- 未知版本必须拒绝解析，禁止猜测。
- 导入必须校验格式、版本、路径、哈希、大小和引用。
- 导入重新生成全部本地 ID，并在事务中完成。
- 导入失败不得留下半成品。

## 9. 并发与错误

- Room 和文件 I/O 使用 suspend API / IO dispatcher。
- 同一编辑会话的写操作串行化。
- 可靠命令与恢复串行化。
- DataStore 使用单一实例。
- 导出不依赖 Composable 生命周期。

领域失败必须显式表示，包括存储空间不足、对象不存在、名称冲突、数据库不可用、格式不支持和导出目标不可用。

取消异常必须继续传播，不转换为普通失败。

文档写入失败时保留 journal，后续启动恢复。

## 10. 隐私与备份

- 不声明 INTERNET。
- 无网络、账号、同步、广告、遥测和崩溃上报。
- 不做应用层加密。
- 文档和资源存储于应用私有目录。
- 导出仅写用户通过 SAF 选择的 URI。
- 允许 Android 设备迁移复制完整持久数据。
- 禁止云备份。
- `noBackupFilesDir` 中的 journal 不参与迁移。

## 11. 架构演进

以下变更必须先更新本文：

- 新增或改变领域模型。
- 改变模块依赖边界。
- 改变持久化策略或数据事实来源。
- 新增 PageElement 类型。
- 改变可靠写入、幂等或撤销模型。
- 改变导出/导入格式或版本策略。
- 引入同步、加密、CI、用户图层或持久撤销。

新增 PageElement 必须定义独立 type、payloadVersion、codec、导出策略和未知版本处理。

新增资源类型必须遵循私有复制、校验、哈希、引用计数和清理流程。

原生格式变更必须升级版本，禁止无版本修改二进制布局。

## 12. 事实来源

架构约束以本文为准；具体实现以代码为准：

- 依赖和版本：Gradle Version Catalog / 构建脚本
- 模块边界：Gradle 配置
- 数据库结构：Room Entity / Migration
- 设置结构：Proto schema
- API 契约：core 模块代码
- 当前功能状态：实际实现和测试

不要在本文重复维护上述信息。
