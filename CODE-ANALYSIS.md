# 代码分析报告：过度设计与防御性编程评估

- 分析日期：2026-08-30
- 分析范围：全部主代码（约 4,500 行）与测试（约 1,000 行）
- 状态：本报告为当次分析快照，文中文件与行号对应分析时点的代码；精简处置见其后的提交记录。

## 结论

存在过度设计，且是成体系的那种，但不算严重到失控；防御性编程属于中度过量，其中三处的实际效果有害。代码底子良好：风格统一、测试聚焦行为、渲染和画布层是真实复杂度。问题集中在 `core:document`/`core:data` 的"可靠性机器"和一组没有调用方的预留契约上，估计占主代码的 15–20%。

## 一、过度设计（按严重度排序）

### 1. 逐笔笔迹的写前日志（WAL）——最重的一处

每一笔笔迹的提交流程（`EditorViewModel.kt` `commit()` → `DurableCommandExecutor.execute()`）：

1. 编码 protobuf → 写临时文件 → fsync → 原子 rename（`FilePendingCommandJournal.append()`）
2. Room 事务写入（本身又是一次 fsync）
3. 删除日志文件
4. 往 `applied_operations` 表插一行幂等记录

配套机制包括：`PendingCommandJournal` 接口、`DurableCommandExecutor`、`PendingCommandCodec`、`OperationId`、`applied_operations` 表、启动时 `recover()` 回放。这是分布式系统级别的持久化方案，用在单进程、单用户、无网络的本地 Room 应用上，保护的场景只有"进程在 fsync 完成到 Room 提交之间这几毫秒内崩溃，丢失最后一笔笔迹"。

代价不只是复杂度（约 300+ 行 + 一张表）：

- `applied_operations` 每笔笔迹插一行，没有任何清理路径，永久无限增长；
- `HandwriteApplication.onCreate()` 调用 `recover()` 后丢弃返回值——回放失败是静默的，而 UI 却承诺"操作将在下次启动时恢复"；
- 删掉整套机制、直接写 Room，用户可见行为几乎不变。

### 2. 五个没有生产调用方的仓储方法 + 整套文件夹/收藏字段

生产代码中零调用（只有测试 fake 在实现它们）：`renameDocument`、`createPage`、`deletePage`、`setLastActivePage`、`observePages`。同样，`Folder` 领域模型（含 `MAX_FOLDER_DEPTH=10`）、`parentFolderId` 自外键 + 索引、`depth`、`FOLDER` kind、`isFavorite` 列 + 复合索引全部是死字段。这是典型的面向未来功能的投机设计（speculative generality），而且已经产生连锁成本：

- `renameDocument` 没有调用方，但 `normalizedName` 的唯一索引因此存在 → 逼出 `DomainFailure.NameConflict` → 逼出下一条的重试协议；
- `LastPageCannotBeDeleted` 失败类型服务于一个不存在的删除页面 UI。

### 3. `ensureDocument` 的重试协议：为几乎不可能的冲突设计

`EditorViewModel.ensureDocument()` 对一个自带毫秒级时间戳的文档名做 `repeat(100)` 的 NameConflict 重试，紧接着同一函数里出现 `(DisplayName.create(...) as NameResult.Valid)` 的裸强转——强转本身就说明作者知道名字不可能非法。整套重试 + 唯一索引 + 失败类型，防御的是一个实际不可达的碰撞。

### 4. 只有写出、没有读入的"原生包格式"

`NativePackageWriter` 定义了魔数 `0x48575047` + 版本号 + `manifest.json` 的版本化交换格式，全仓库没有任何读取/导入代码。`ARCHITECTURE.md` 甚至写好了导入规则（"原生导入必须重新生成全部本地 ID"）。此外它用 `DataOutputStream` 手写了第二套笔迹序列化——同一笔迹在 `PayloadCodec`（protobuf）已序列化过一次，导出时又用另一套二进制格式重写一遍，两套格式将来要同步维护。

### 5. 次一级的问题

- WorkManager 做交互式导出 + 250ms 轮询（`ExportViewModel.export()`）：用户正在等待的导出用了为"可延迟、跨进程死亡"设计的 WorkManager，然后还用 `while(true)` + `getWorkInfoById(...).get()` 轮询，而不是现成的 `getWorkInfoByIdFlow`。一个普通协程更简单且行为更正确（进程死后 WorkManager 会静默重跑导出，SAF URI 权限可能已失效）。
- 背景图的内容寻址去重（`ContentAddressedResourceRepository`）：SHA-256 去重 + 插入冲突后回查的并发竞态处理，服务的是"单用户并发导入两张字节相同的图"这种场景。引用计数部分是必要的（图片背景功能需要），去重部分是多余的。

## 二、防御性编程：中度过量，三处有害

有害的：

1. `EntityMappers.toDomain()` 中一行坏数据会让整个功能瘫痪——持久化的名字过不了 `DisplayName` 校验就 `error()`，而这个映射在 `observeDocuments` 的 Flow 链上，异常会让整个列表流中断，库界面直接不可用。防御校验把"极不可能的脏数据"升级成了"全量功能失效"，方向反了。`PageElementEntity.toDomain()` 的 `require(payloadVersion == 1)` 同理：字段是为迁移预留的，但策略只有 `error()`。
2. 错误分类基本靠猜：`ContentAddressedResourceRepository` 把所有 `IOException` 映射为 `StorageFull`（权限错误会显示"存储空间不足"）；`DomainFailure` 有 11 个类型，但 `StorageFull` 没有任何一处真正检测过磁盘余量，多数映射是装饰性的。
3. UUID 再做 SHA-256 当文件名（`FilePendingCommandJournal`）：UUID 本来就是文件名安全的，每笔笔迹多算一次摘要、文件名变成 64 字符，纯仪式性代码。

无害但重复的：5 处几乎相同的 `try / catch(CancellationException) rethrow / catch(...)` → `DomainResult` 样板（repository、journal、resource、settings、sharer 各一份）。

不算问题的：模型层 `init { require(...) }` 不变量（`Document.kt`、`Stroke.kt` 等）是好的"非法状态不可表示"实践；画布的 `runCatching { prepare(...) }` 保活策略可以接受。

## 三、不属于过度设计的部分（避免误伤）

- `HandwriteCanvas.kt`（约 660 行）：Liang-Barsky 线段裁剪、双指缩放、笔侧键、防手掌误触——这是画板应用真正的难点，拆分得也干净，纯函数都提出来可测。
- `CommandHistory`、渲染层（`PageRenderEngine`/`InkDocumentRenderer`）、protobuf 存储笔迹、DataStore 设置栈：都克制且贴需求。
- 测试约 1,000 行、无 mock 框架、聚焦领域行为（临时文档生命周期、撤销/重做、空文档丢弃），质量高。
- 10 个 Gradle 模块 + convention plugin 对这个体量偏重，但每个模块的 build 文件只有几行，边界靠编译期强制，`verifyLocal` 聚合任务也实用——可商榷，不构成问题。且 `ARCHITECTURE.md`/`AGENTS.md` 本身在约束范围蔓延。

## 四、处置建议（按优先级）

1. 拆掉 WAL 一套（`DurableCommandExecutor`/journal/codec/`applied_operations`/`OperationId`），命令直接进 Room 事务——收益最大、风险最低；至少要给 `applied_operations` 加清理、让 `recover()` 失败可见。
2. 删掉五个无调用方的仓储方法 + 文件夹/收藏字段（连带 `NameConflict`/`LastPageCannotBeDeleted` 和 `normalizedName` 唯一索引、`repeat(100)` 重试），等 UI 真要做时再加。
3. `EntityMappers` 的 `error()` 改为容错跳过 + 打日志，别让一行脏数据炸掉整个列表流。
4. 原生包格式要么补上导入器，要么先降级为"ZIP + manifest"不搞魔数和双序列化。

> 处置原则：每一项独立提交；架构变化先更新 `ARCHITECTURE.md`；按变更范围运行定向测试并在最后执行完整 `verifyLocal`。
