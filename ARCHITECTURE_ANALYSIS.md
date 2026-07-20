# handwrite 项目架构分析报告

> 本报告基于对项目源码、构建配置、测试代码的全面探索，独立于现有 `AGENTS.md` 与 `ARCHITECTURE.md` 编写。
> 探索范围：根配置、`build-logic`、全部 10 个模块的源码与测试、AndroidManifest、Proto schema、Room schema。
> 报告日期：2026-07-21

---

## 目录

1. [项目概览](#1-项目概览)
2. [构建系统](#2-构建系统)
3. [模块清单与依赖图](#3-模块清单与依赖图)
4. [核心领域模型（core:model）](#4-核心领域模型coremodel)
5. [文档契约与会话（core:document）](#5-文档契约与会话coredocument)
6. [持久化实现（core:data）](#6-持久化实现coredata)
7. [渲染（core:rendering）](#7-渲染corerendering)
8. [设计系统（core:designsystem）](#8-设计系统coredesignsystem)
9. [编辑器（feature:editor）](#9-编辑器featureeditor)
10. [文档库（feature:library）](#10-文档库featurelibrary)
11. [设置（feature:settings）](#11-设置featuresettings)
12. [导出（feature:export）](#12-导出featureexport)
13. [应用组合层（app）](#13-应用组合层app)
14. [测试与验证体系](#14-测试与验证体系)
15. [隐私与备份](#15-隐私与备份)
16. [与现有约束文件的对比](#16-与现有约束文件的对比)
17. [重写建议](#17-重写建议)

---

## 1. 项目概览

| 项 | 值 |
|---|---|
| 项目名 | `handwrite`（根 `rootProject.name`） |
| 应用名 | 随手写（`strings.xml` `app_name`） |
| applicationId | `com.xsgovo.handwrite` |
| versionCode / versionName | 1 / `1.0.0` |
| AGP / Kotlin / KSP | 9.2.1 / 2.2.21 / 2.3.10 |
| Compose BOM | 2025.12.01 |
| compileSdk / minSdk / targetSdk | 36 / 31 / 36 |
| Java（Android 模块） | 17 |
| JVM Toolchain（core JVM 模块） | 21 |
| 包名前缀 | `com.xsgovo.handwrite.*` |

**产品定位**：永久本地运行的 Android 图片笔记与手写文档应用。无账号、服务端、网络同步、遥测、广告。核心模型为 `Folder → Document → Page → PageElement`，当前编辑器只展示单页。

---

## 2. 构建系统

### 2.1 仓库结构

`settings.gradle.kts` 通过 `includeBuild("build-logic")` 引入 composite build，注册 10 个模块：

```kotlin
rootProject.name = "handwrite"
include(":app")
include(":core:model")
include(":core:document")
include(":core:data")
include(":core:rendering")
include(":core:designsystem")
include(":feature:editor")
include(":feature:library")
include(":feature:settings")
include(":feature:export")
```

`dependencyResolutionManagement` 设置 `FAIL_ON_PROJECT_REPOS`，仓库仅 `google()` + `mavenCentral()`。

### 2.2 build-logic 约定插件

`build-logic/` 是独立的 `kotlin-dsl` composite build，定义 **4 个预编译脚本插件**（Precompiled Script Plugin），位于 `build-logic/src/main/kotlin/`：

| 插件 | 文件 | 作用 |
|---|---|---|
| `handwrite.android.application` | `handwrite.android.application.gradle.kts` | Application 模块：compileSdk=36, minSdk=31, targetSdk=36, Java 17, release 启用 minify+shrink, 默认 proguard-android-optimize |
| `handwrite.android.library` | `handwrite.android.library.gradle.kts` | Library 模块：compileSdk=36, minSdk=31, Java 17, 无 buildTypes（不 minify） |
| `handwrite.android.compose` | `handwrite.android.compose.gradle.kts` | 应用 kotlin compose 插件，对 application/library 都启用 `buildFeatures.compose = true` |
| `handwrite.kotlin.library` | `handwrite.kotlin.library.gradle.kts` | 纯 Kotlin JVM：`kotlin("jvm")` + `jvmToolchain(21)` |

**关键特征：瘦插件**。这 4 个约定插件**只配置插件应用和通用 DSL，没有共享 `dependencies {}` 块**。所有依赖（JUnit、Compose BOM、Hilt、Room 等）都在各模块自己的 `build.gradle.kts` 中显式声明。

**版本双份维护问题**：`build-logic/build.gradle.kts` 硬编码了 AGP/Kotlin 版本（`com.android.tools.build:gradle:9.2.1`、`kotlin-gradle-plugin:2.2.21` 等），与 `gradle/libs.versions.toml` 中的 `agp`/`kotlin` 版本重复。升级时需手动同步两处（build-logic 作为 composite build 默认不共享主项目 version catalog）。

### 2.3 各模块的插件应用

| 模块 | 约定插件 | 额外插件 |
|---|---|---|
| `:app` | application + compose | kotlin.serialization, ksp, **hilt** |
| `:core:model` | kotlin.library | — |
| `:core:document` | kotlin.library | — |
| `:core:data` | android.library | ksp, protobuf, room |
| `:core:rendering` | android.library + compose | — |
| `:core:designsystem` | android.library + compose | — |
| `:feature:editor` | android.library + compose | ksp |
| `:feature:library` | android.library + compose | ksp |
| `:feature:settings` | android.library + compose | ksp |
| `:feature:export` | android.library + compose | ksp |

**Hilt 应用方式**：根 `build.gradle.kts` 声明 `alias(libs.plugins.hilt) apply false`，但**只有 `:app` 实际应用了 hilt 插件**。`:core:data`、所有 feature 模块都只用 `implementation(libs.hilt.android)` + `ksp(libs.hilt.compiler)`，不应用 hilt Gradle 插件（KSP 路径下可行）。

### 2.4 gradle.properties

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
org.gradle.configuration-cache=true
# org.gradle.parallel=true  (被注释)
```

启用 Configuration Cache，未启用 parallel，未启用 build cache。

### 2.5 verifyLocal 任务

定义在根 `build.gradle.kts`，是项目唯一的统一验证入口：

```kotlin
tasks.register("verifyLocal") {
    dependsOn(jvmModules.map { "$it:test" })                    // :core:model, :core:document
    dependsOn(androidModules.map { "$it:testDebugUnitTest" })   // 8 个 Android 模块
    dependsOn(androidModules.map { "$it:lint" })                 // 8 个 Android 模块的 lint
    dependsOn(":app:assembleDebug", ":app:assembleRelease")      // app 双构建
}
```

**实际涵盖**：JVM 模块单测 + Android 模块 debug 单测 + Android 模块 lint + app debug/release 组装。**不包含** instrumented test（项目无 androidTest 源码）、release 单测。release 组装会触发 R8 minify + 资源压缩验证。运行命令：`.\gradlew.bat --no-daemon verifyLocal`。

---

## 3. 模块清单与依赖图

### 3.1 模块职责

| 模块 | 类型 | 职责 |
|---|---|---|
| `:core:model` | JVM | 纯 Kotlin 领域模型与契约（ID、坐标、笔迹、背景、文档、设置、错误） |
| `:core:document` | JVM | 文档命令、Repository 契约、会话历史、可靠写入执行器 |
| `:core:data` | Android | Room + Proto DataStore + 资源持久化实现 |
| `:core:rendering` | Android | 笔迹/背景渲染、导出图片编码 |
| `:core:designsystem` | Android | Material3 主题（HandwriteTheme） |
| `:feature:editor` | Android | 编辑器：Ink 输入、手势、视口、撤销、临时文档 |
| `:feature:library` | Android | 文档库列表 |
| `:feature:settings` | Android | 设置页 |
| `:feature:export` | Android | 导出（WorkManager + SAF） |
| `:app` | Android | 入口、导航、依赖组合 |

### 3.2 实际依赖图（从各 build.gradle.kts 推导）

```
:app
├── :core:data       → :core:model, :core:document
├── :core:document   → :core:model (api), kotlinx-coroutines (api)
├── :core:model      (无项目依赖)
├── :core:rendering  → :core:model, :core:document
├── :core:designsystem → :core:model (implementation)
├── :feature:editor    → :core:model, :core:document, :core:rendering, :core:designsystem
├── :feature:library   → :core:model, :core:document, :core:designsystem
├── :feature:settings  → :core:model, :core:document, :core:designsystem
└── :feature:export    → :core:model, :core:document, :core:rendering, :core:designsystem
```

**核实结论**：
- **feature 模块之间零相互依赖**（四个 feature 的 build.gradle.kts 均无 `project(":feature:...")`），完全由 `:app` 组合。
- **core 不依赖 feature**，依赖方向单向，无循环。
- `:core:document` 对 `:core:model` 用 `api`（公开签名暴露 model 类型），`:core:designsystem` 对 `:core:model` 用 `implementation`。

### 3.3 包名约定

所有模块统一 `com.xsgovo.handwrite.<module>.*`，如 `com.xsgovo.handwrite.core.model`、`com.xsgovo.handwrite.feature.editor`。

---

## 4. 核心领域模型（core:model）

### 4.1 模块特征

- **纯 Kotlin JVM 模块**（`handwrite.kotlin.library` → `kotlin("jvm")` + `jvmToolchain(21)`）。
- 唯一非 Kotlin stdlib 的依赖：JDK 的 `java.text.Normalizer`、`java.util.Locale`。
- 无任何 `android.*` import，无 Android 依赖。
- 仅依赖 JUnit4（测试）。

### 4.2 源码文件（8 个）

| 文件 | 内容 |
|---|---|
| `Identifiers.kt` | 6 个 `@JvmInline value class` ID + `BrushId` 预置笔刷 |
| `Geometry.kt` | `LogicalCanvas`、`LogicalPoint/Size/Rect`、`PageTemplate`、`PagePattern` |
| `Stroke.kt` | `StrokeSample`、`BrushStyle`、`PageElement` 接口、`StrokeElement` |
| `Background.kt` | `PageBackground` 密封接口、`BackgroundTransform`、背景枚举 |
| `Document.kt` | `Folder`、`Document`、`Page`、`PageContent`、`DocumentSnapshot` |
| `Naming.kt` | `DisplayName`（私有构造）、`NameResult`、`NameProblem` |
| `Failure.kt` | `DomainFailure`（11 子类型）、`DomainResult<T>` |
| `AppSettings.kt` | `AppSettings` + 大量枚举 |

### 4.3 关键模型与约束

**ID 类型**：
- `DocumentId`/`PageId`/`ElementId`/`FolderId`/`ResourceId` 全是 `value class(val value: Long)`。
- `OperationId` 是 `value class(val value: String)`，仅 `require(isNotBlank())`。
- `BrushId` 预置 `MONOLINE`、`PRESSURE_PEN`、`HIGHLIGHTER`。

> **注意**：`OperationId` 类型层面**未强制 UUID 格式**，仅校验非空。测试中用 `OperationId("one")` 完全合法。UUID 是生产约定（`EditorViewModel` 中用 `UUID.randomUUID().toString()` 生成），但 model 层不约束。现有 ARCHITECTURE.md 称"OperationId 使用 UUID"是约定层面的描述，非类型约束。

**坐标系**：
- `LogicalCanvas.LONG_EDGE = 65_535`，页面最长边恒为 65535 逻辑单位。
- `LogicalSize` 在 init 中 `require(maxOf(width, height) == LONG_EDGE)`。
- `PagePattern.LOGICAL_SPACING = 65535 / 25 = 2621`，用于横线/方格背景间距。
- `PageTemplate` 枚举：`LEGACY_PORTRAIT`(1000:1414)、`THREE_BY_FOUR`、`FOUR_BY_THREE`、`SQUARE`、`NINE_BY_SIXTEEN`。`LEGACY_PORTRAIT` 注释明确"兼容比例，非 A4"。

**笔迹模型**：
- `StrokeSample`：`point: LogicalPoint`、`pressure: Int`（0..65_535，`MAX_PRESSURE = 65_535`）、`elapsedMillis: Int`、可选 `tiltX/tiltY: Int?`（-32767..32767）。
- `BrushStyle`：`id: BrushId`、`argb: Int`、`width: Int`（逻辑笔宽）、`blendMode: BrushBlendMode`、`pressureSensitivity: PressureSensitivity`。
- `PageElement` 是**非密封接口**（当前唯一实现 `StrokeElement`），`orderKey: Long` 排序。

**背景模型**：`PageBackground` 是**密封接口**，4 子类：
- `Solid(argb)`、`Transparent`、`Pattern(type, baseArgb)`（LINED/GRID）、`Asset(resourceId, kind, pdfPageIndex?, transform)`（IMAGE/PDF）。
- `Asset` 强制 PDF 必须带 `pdfPageIndex`，非 PDF 不允许带。
- `BackgroundTransform` 用 `scalePermille`（千分比）和 `rotationMilliDegrees`（毫度），避免浮点。

**文档层级**：
- `Folder(id, name, parentId?, depth)`，`MAX_FOLDER_DEPTH = 10`。
- `Document(id, name, folderId?, createdAt, modifiedAt, isFavorite, lastActivePageId)`，`init` 要求 `modifiedAt >= createdAt`。
- `Page(id, documentId, orderKey, size, background)`。
- `PageContent(page, elements)`，init 强制元素属于该页且按 orderKey 排序。
- `DocumentSnapshot(document, pages)`，init 强制非空、所有页属同一文档、页面按 orderKey 排序。

> **注意**：代码中**没有 `Library` 类**。`Folder`（有 `parentId`/`depth`）是实际的组织单元，`Library` 是概念性根。现有 ARCHITECTURE.md 反复提到的 "Library → Document → Page → PageElement" 中，"Library" 是概念而非实体。

**命名规范化**：`DisplayName` 私有构造 + `@ConsistentCopyVisibility`，强制走 `create()` 工厂。显示值 NFC 归一化，比较键 NFKC + 小写。禁止 `/`、`\`、控制字符，最多 100 code point。返回 `NameResult` 密封接口。

**错误类型**：`DomainFailure` 11 子类型：`StorageFull`、`DocumentNotFound`、`PageNotFound`、`LastPageCannotBeDeleted`、`NameConflict`、`DatabaseUnavailable`、`UnsupportedPackageVersion(version)`、`InvalidPackage(reason)`、`ExportTargetUnavailable`、`ResourceNotFound`、`InvalidResource`。`DomainResult<T>` 是 Success/Failure 二分。

**设置模型**：`AppSettings` 含 `inputMode`、`themeMode`、`imageFormat`、`exportResolution`、`compressionQuality`、`backBehavior`、`sideButtonAction`、`pressureSensitivity`、`activeBrushId`、`colorSlots`、`activeColorSlot`、`widthSteps`、`activeWidthSlot`、`defaultPageTemplate`、`defaultBackground`。init 校验槽位非空、索引合法、默认背景禁止为 `Asset`。

### 4.4 架构约束体现

- 所有 model 数据类在 `init` 块校验不变量，构造即合法。
- 密封接口表达穷尽领域（`PageBackground`、`DocumentCommand`、`DomainFailure`、`DomainResult`、`NameResult`）。
- 结果类型替代异常，所有可能失败的契约返回 `DomainResult`。
- 时间戳用 `Long` epoch millis，不引入 `java.time`（保持纯 JVM）。

---

## 5. 文档契约与会话（core:document）

### 5.1 模块特征

- **纯 Kotlin JVM 模块**，`api(project(":core:model"))` + `api(libs.kotlinx.coroutines.core)`。
- 无 Android 依赖，唯一 JDK import 是 `java.io.InputStream`（在 `BackgroundResourceRepository`）。

### 5.2 源码文件（9 个）

| 文件 | 内容 |
|---|---|
| `DocumentCommand.kt` | 命令密封接口 + 2 子类 + 工具函数 |
| `DocumentCommandStore.kt` | 命令存储契约（`apply`） |
| `CommandHistory.kt` | 撤销/重做历史（会话状态，具体类） |
| `DurableCommandExecutor.kt` | 可靠写入执行器（具体类） |
| `PendingCommandJournal.kt` | 挂起命令日志契约 + `PendingCommand` |
| `DocumentRepository.kt` | 文档 Repository 契约（继承 Store） |
| `BackgroundResourceRepository.kt` | 背景资源契约 + `StoredResource` + `ResourceInput` |
| `SettingsRepository.kt` | 设置 Repository 契约 |
| `EpochClock.kt` | 时间源（`fun interface`） |

### 5.3 命令模型

`DocumentCommand` 是**密封接口**，仅 **2 个子类**：
- `ReplaceElements(documentId, pageId, removed: List<PageElement>, added: List<PageElement>)`：原子替换，逆操作 = 互换 removed/added。
- `UpdateBackground(documentId, pageId, before, after)`：改背景，逆操作 = 互换 before/after。

每个命令有 `inverse(): DocumentCommand`（撤销基础）和 `estimatedBytes(): Long`（历史淘汰用）。

> **重要**：只有内容级变更（元素增删 + 背景修改）可撤销。文档/页面结构操作（CRUD）不进入命令系统，不可撤销。这与现有 ARCHITECTURE.md 一致。

### 5.4 命令存储与幂等

```kotlin
interface DocumentCommandStore {
    suspend fun apply(command: DocumentCommand, operationId: OperationId): DomainResult<Unit>
}
```

`OperationId` 作为幂等键，保证恢复和重复执行幂等。

### 5.5 DocumentRepository 契约

继承 `DocumentCommandStore`，扩展：
- **响应式观察**（Flow）：`observeDocuments`、`observeDocument`、`observePages`、`observePage`。
- **一次性快照**（suspend）：`loadSnapshot(documentId): DomainResult<DocumentSnapshot>`（导出用）。
- **文档 CRUD**：`createDocument`、`renameDocument`、`deleteDocument`。
- **页面 CRUD**：`createPage`、`deletePage`、`setLastActivePage`。
- `createDocument` 接收 `nowEpochMillis: Long`（时间由调用方注入，便于测试）。

### 5.6 CommandHistory（会话撤销/重做）

具体类（非接口），因为撤销/重做逻辑通用且无平台依赖：
- 双栈（undo/redo）`ArrayDeque<DocumentCommand>`。
- **两阶段确认**：`commandToUndo()` 返回待执行的反命令（预览），`confirmUndo()` 才真正移动栈。避免执行失败却已弹栈。
- `recordCommitted(command)`：**只有 Room 写入成功后才调用**，新命令清空 redo。
- 双重淘汰：`maxCommands` + `maxEstimatedBytes`，优先淘汰最旧 undo。
- `clear()`：新会话清空历史。

### 5.7 DurableCommandExecutor（可靠写入）

具体类，依赖 `DocumentCommandStore` + `PendingCommandJournal`：
- `Mutex` 保证 `execute` 与 `recover` 串行。
- **`execute` 严格三阶段**：`journal.append → store.apply → journal.remove`。store 失败则保留 journal，等启动恢复。
- **`recover`**：启动时遍历 `journal.readAll()`，按 sequence 排序后逐条幂等重放，依赖 `OperationId` 保证重复 apply 安全。返回恢复条数。

### 5.8 其他契约

- `PendingCommandJournal`：`append`/`readAll`/`remove(operationId)` 三方法。
- `BackgroundResourceRepository`：`import(mimeType, ResourceInput)`、`find(resourceId)`、`pruneUnreferenced()`。`StoredResource` 含 `sha256`、`absolutePath`、`byteSize`。
- `SettingsRepository`：`settings: Flow<AppSettings>` + `update(transform: (AppSettings) -> AppSettings)`（函数式原子更新）。
- `EpochClock`：`fun interface`，`nowMillis(): Long`。

### 5.9 缺口与注意点

> **缺口**：core:document 中**没有文档级导出/导入契约**（原生 ZIP 包的版本、codec、校验等）。`DomainFailure` 有 `UnsupportedPackageVersion`/`InvalidPackage` 错误类型，但无对应 Repository 接口。现有 ARCHITECTURE.md §8 详述导出/导入，但契约实际未在 core 层定义——导出实现直接在 `:feature:export` 中调用 `DocumentRepository.loadSnapshot`，导入侧（解析原生包）的实现位置需进一步确认。按"新领域能力先进入 core 契约"的原则，这是一个待补的缺口。

> **注意**：core:document 中**无 `Session` 接口/类**。"会话"概念由 `CommandHistory`（撤销栈）和 `EpochClock` 间接承担。`sessionId` 在 `:app` 导航层（`EditorDestination(sessionId, documentId)`），不在 core。

> **注意**：`DurableCommandExecutor` 是具体类而非接口。其逻辑完全通用，在 core 层直接实现合理。

---

## 6. 持久化实现（core:data）

### 6.1 模块特征

- Android library，依赖 `:core:model`、`:core:document`，无 `:core:rendering` 反向依赖。
- 应用 KSP + Protobuf + Room 插件。
- `room { schemaDirectory("$projectDir/schemas") }` 导出 schema JSON（v1 已生成）。

### 6.2 Room Schema（v1，6 张表）

`@Database(entities = [...], version = 1, exportSchema = true)`，单一 DAO `HandwriteDao`，无 TypeConverter（复杂类型用 protobuf BLOB），无 Migration 类。

| 表 | PK | 关键字段 | 约束 |
|---|---|---|---|
| `library_items` | id (autoGen) | kind(DOCUMENT/FOLDER), name, normalizedName(UNIQUE), parentFolderId(FK 自引用 NO_ACTION), depth, createdAt, modifiedAt, isFavorite | normalizedName 唯一索引 + 多个查询索引 |
| `document_states` | documentId (FK CASCADE) | lastActivePageId | 与 library_items 级联删除 |
| `pages` | id (autoGen) | documentId(FK CASCADE), orderKey, logicalWidth, logicalHeight, backgroundPayload(BLOB) | (documentId, orderKey) UNIQUE |
| `page_elements` | id (autoGen) | pageId(FK CASCADE), orderKey, type(当前仅"STROKE"), payloadVersion, payload(BLOB) | (pageId, orderKey) UNIQUE |
| `resources` | id (autoGen) | sha256(UNIQUE), mimeType, relativePath, byteSize, referenceCount | **无外键**，引用计数应用层管理 |
| `applied_operations` | operationId(String) | appliedAtEpochMillis | 幂等键表，无外键 |

**关键设计**：
- `LibraryItemEntity` **同时承担 Document 和 Folder**（通过 `kind` 区分），与"Library → Document"的概念层级略有出入。
- `resources` 表无外键，`referenceCount` 由 `RoomDocumentRepository` 在事务内手动 `adjustResourceReferenceCount`（SQL 带 `WHERE referenceCount + :delta >= 0` 原子保护，防止负数）。
- `applied_operations` 表实现 OperationId 幂等：apply 前查 `hasAppliedOperation`，apply 后插 `AppliedOperationEntity`，在同一事务内。
- `orderKey` 用 `ORDER_STEP = 1024L` 步长，便于未来在两个 orderKey 之间插入。

### 6.3 Proto Schema（2 个文件）

**`app_settings.proto`**（DataStore 设置）：`AppSettingsPayload` 16 字段（含 1 个 `reserved 12`，说明有过 schema 演进），枚举均以 `*_UNSPECIFIED = 0` 起始。`optimize_for = LITE_RUNTIME` + `java_multiple_files = true`。

**`document_payload.proto`**（笔迹/背景/journal）：
- `StrokePayload`：brush_id, argb, width, blend_mode, pressure_sensitivity, repeated samples。
- `StrokeSamplePayload`：**delta encoding**（`delta_x`/`delta_y` 用 `sint32` zigzag），`pressure`, `elapsed_millis`, optional `tilt_x`/`tilt_y`。对小增量采样点非常省空间。
- `BackgroundPayload`：oneof（solid/transparent/pattern/asset）。
- `PendingCommandPayload`：operation_id, document_id, page_id, oneof command, sequence(uint64)。
- `ElementEnvelope`：内嵌 `bytes payload` 存具体 payload。

### 6.4 Repository 实现（4 个契约）

| 契约 | 实现 | 关键点 |
|---|---|---|
| `DocumentRepository` + `DocumentCommandStore` | `RoomDocumentRepository` | 一个类同时实现两个接口；所有写操作在 `database.withTransaction` 内；`apply` 先查 `hasAppliedOperation` 幂等；`ReplaceElements` 不涉及资源引用，`UpdateBackground` 在事务内调整 before/after 资源引用计数 |
| `BackgroundResourceRepository` | `ContentAddressedResourceRepository` | 目录 `context.filesDir/resources`；边复制边算 SHA-256；`findResourceByHash` 去重；`pruneUnreferenced` 删 referenceCount=0 |
| `SettingsRepository` | `ProtoSettingsRepository` | `DataStore<AppSettingsPayload>`；`update` 用 `dataStore.updateData { transform(current.toDomain()).toProto() }` |
| `PendingCommandJournal` | `FilePendingCommandJournal` | 目录 `context.noBackupFilesDir/pending_commands` |

### 6.5 Journal 实现细节

- **文件名**：`"%019d-%s.pb".format(sequence, operationId.fileKey())`，其中 `fileKey()` 是 OperationId 的 SHA-256 hex（64 字符）。19 位补零 sequence 保证字典序 = 时间序。
- **写入**：临时文件 `.tmp` → `output.fd.sync()`（fsync）→ `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)`。读者只看到完整或不存在。
- **sequence 防时钟回拨**：`maxOf(previous + 1, System.currentTimeMillis())`。
- **读取**：按 sequence 排序后返回。
- **删除**：按文件名后缀 `-${fileKey}.pb` 匹配删除。
- **错误处理**：所有异常（除 `CancellationException`）映射为 `DomainFailure.DatabaseUnavailable`（语义上不够精确，无法区分磁盘满 vs 权限错误）。

### 6.6 资源管理流程

`import(mimeType, input)`：
1. 校验 mimeType（`image/*` 或 `application/pdf`），否则 `InvalidResource`。
2. 创建临时文件 `.import-${UUID}`，边复制边算 SHA-256 + 累计 byteSize。
3. byteSize == 0 → `InvalidResource`。
4. `findResourceByHash`：命中则直接返回（临时文件 finally 删除）。
5. 不存在则 `renameTo` 到 `$hash.$extension`，`insertResource`（捕获并发冲突 → 改用 findResourceByHash）。
6. finally 删除临时文件。

`pruneUnreferenced()`：
- **DB 删除在事务内**，**文件删除在事务外**。
- `File.delete()` 失败不抛异常，无重试。
- **潜在问题**：DB 提交后应用崩溃会留下孤儿文件（DB 一致但文件多余）。这是与现有 ARCHITECTURE.md"资源文件和数据库记录必须保持一致"的轻微偏差。

### 6.7 DI 绑定

单一 Hilt Module `PersistenceModule`（`@InstallIn(SingletonComponent::class)`），11 个 @Provides：
- `HandwriteDatabase`（Singleton，`"handwrite.db"`）、`HandwriteDao`。
- `RoomDocumentRepository`（Singleton），同时暴露 `DocumentRepository` 和 `DocumentCommandStore` 契约（同一对象）。
- `ContentAddressedResourceRepository`（Singleton，目录 `context.filesDir/resources`，`ioDispatcher = Dispatchers.IO`）。
- `DataStore<AppSettingsPayload>`（Singleton，`ReplaceFileCorruptionHandler` 损坏时用默认值，文件 `context.dataStoreFile("app_settings.pb")`）。
- `ProtoSettingsRepository`（Singleton）。
- `FilePendingCommandJournal`（Singleton，目录 `context.noBackupFilesDir/pending_commands`）。
- `EpochClock`（`System::currentTimeMillis`）。
- `DurableCommandExecutor`（Singleton）。

> **注意**：`Dispatchers.IO` 直接在 Module 中硬编码，未通过 `@IoDispatcher` qualifier 提供，不利于测试替换。

> **注意**：DataStore 文件位于 `context.dataStoreFile("app_settings.pb")`（默认 `files/datastore/`，**参与设备迁移**），与 journal 的 `noBackupFilesDir`（不迁移）位置不同，符合"设置参与迁移、journal 不迁移"的意图。

---

## 7. 渲染（core:rendering）

### 7.1 模块特征

Android library + Compose，依赖 `:core:model`、`:core:document`、Compose UI、AndroidX Ink（`ink-rendering` + `ink-brush` + `ink-strokes` 1.0.0）。**不引入 `ink-authoring-compose`**（输入侧在 feature:editor）。无 Hilt、无 Room、无 DataStore。

### 7.2 两套并行渲染路径

#### 路径 A：实时编辑渲染（`InkDocumentRenderer`）

使用 AndroidX Ink 的 `CanvasStrokeRenderer`：
- `prepare(strokes)`：领域 `StrokeElement` → Ink `Stroke`（`toInkStroke`）。
- `draw(scope, strokes, scale, left, top)`：用 `Matrix` 把逻辑坐标缩放/平移到屏幕坐标，调用 `renderer.draw`。
- `drawInProgress(scope, strokes)`：用 identity Matrix（in-progress 笔迹已在屏幕坐标）。

**领域 → Ink 转换**：
- 采样点 `x`/`y` 直接传 float，压力 `pressure / MAX_PRESSURE`（0..65535 → 0..1）。
- tilt 转换：`(tiltX, tiltY)` 平面分量 → Ink 的 `(tiltRadians, orientationRadians)` 极坐标。
- Brush family 映射：`PRESSURE_PEN → StockBrushes.pressurePen`、`HIGHLIGHTER → StockBrushes.highlighter`、`else → StockBrushes.marker`。

> **注意**：`BrushId.MONOLINE` 落到 `else` 分支映射到 `marker` family。MONOLINE 语义（等宽线条）与 marker（马克笔效果）可能不匹配。

#### 路径 B：离屏渲染（`PageRenderEngine`）

**不使用 AndroidX Ink**，用原生 `Canvas` + `Path` + `Paint`：
- `renderPage(content, width, height, forceOpaque)` → `Bitmap`。
- `drawPage`：底色 → 横线/方格 → 图片/PDF 背景 → 笔迹。
- `drawStroke`：用 `Path` 的 `moveTo`/`lineTo` 直线段连接采样点，**不感知压力和倾斜**，笔宽固定 `stroke.style.width * minOf(scaleX, scaleY)`。HIGHLIGHT 模式用 `alpha * 0.4f`。
- 单点笔画：画圆。

> **重要问题**：导出渲染（`PageRenderEngine.drawStroke`）与编辑渲染（`InkDocumentRenderer`）**视觉不一致**。编辑时压感笔迹有粗细变化，导出后变成等宽折线。现有 ARCHITECTURE.md §2"AndroidX Ink 仅用于输入和显示"字面允许此差异，但用户体验上是隐患。

> **问题**：`PageRenderEngine.drawAsset` 资源解码失败时 `runCatching { ... }.getOrNull()` 静默忽略，背景留白。违反现有 ARCHITECTURE.md §9"领域失败必须显式表示"。

### 7.3 背景渲染

- `Solid`：`canvas.drawColor(argb)`（forceOpaque 时把 alpha 设 255）。
- `Transparent`：不绘制（forceOpaque 时画白）。
- `Pattern`（LINED/GRID）：`drawLine` 循环，间距 `PagePattern.LOGICAL_SPACING * width / logicalWidth`，颜色固定 `0x2F60746B`，起始位置 = 一个 spacing。
- `Asset`（IMAGE/PDF）：`clipRect` → `translate` → `rotate`（毫度→度，绕页面中心）→ `scale`（千分比→百分比，绕页面中心）→ `drawBitmap`。

### 7.4 资源解码（`BackgroundAssetImage.kt`）

- `rememberBackgroundAssetImage(background, resource)`：Composable，`produceState` 在 `Dispatchers.IO`（**硬编码**）异步解码，`PREVIEW_DECODE_EDGE = 2048`。
- `decodeImage(path, maximumEdge)`：`BitmapFactory.Options.inSampleSize` 下采样。
- `decodePdf(path, pageIndex, maximumEdge)`：`PdfRenderer`，先 `eraseColor(WHITE)`（PDF 透明变白），按比例缩放但 `coerceAtMost(1f)` 不放大。

### 7.5 导出编码（`PageImageEncoder`）

```kotlin
class PageImageEncoder(private val renderer: PageRenderEngine) {
    suspend fun write(content, output, imageFormat, resolution, quality)
}
```

- 分辨率：`SMALL=1280`、`STANDARD=2048`、`HIGH=3072`（长边像素）。
- 质量：`LOW=65`、`BALANCED=82`、`HIGH=95`。
- 格式：`PNG → PNG`、`JPEG → JPEG`、`AUTO/WEBP → WEBP_LOSSY`。
- `isLossy()`：`this != PNG`，非 PNG 时 `forceOpaque=true`（避免透明背景导出后变黑）。

> **注意**：`ImageFormat.AUTO` 与 `WEBP` 完全等价，没有根据内容智能选择 PNG/JPEG。

> **注意**：本模块只有图片导出。**多页长图、PDF 导出在 `:feature:export`**（用 `android.graphics.pdf.PdfDocument`，`PDF_LONG_EDGE = 1440`，无打印尺寸语义）。

---

## 8. 设计系统（core:designsystem）

### 8.1 模块特征

Android library + Compose，依赖 `:core:model`（**implementation**，非 api）、Compose BOM、foundation、material3。无测试、无资源文件。

### 8.2 唯一文件：`HandwriteTheme.kt`

- **Material3**：`lightColorScheme`/`darkColorScheme`，主色墨绿（`#176B52`/`#8ED6BB`），辅色暗红。
- `HandwriteTheme(themeMode: ThemeMode, content)`：`ThemeMode` 来自 core:model，`SYSTEM` 跟随系统。
- **未自定义 Typography/Shapes**，仅 colorScheme。
- 无共享组件（Button/Card 等封装），目前只有主题。

> **注意**：`HandwriteTheme` 的公开参数 `ThemeMode` 来自 core:model，但 designsystem 用 `implementation(project(":core:model"))`。按 Gradle 可见性规则，`implementation` 依赖的类型出现在公开 API 签名中属于"泄漏"。调用方（如 app）必须自己显式依赖 core:model 才能引用 `ThemeMode`。这可能是有意为之（强制上游显式依赖 model），也可能是疏忽（按 API 设计规范应改 `api`）。

---

## 9. 编辑器（feature:editor）

### 9.1 模块特征

Android library + Compose，依赖 `:core:model`、`:core:document`、`:core:rendering`、`:core:designsystem` + `androidx.ink.authoring.compose` + Hilt（KSP 路径）。无 WorkManager、无 navigation、无 serialization。

### 9.2 主要类

| 类 | 职责 |
|---|---|
| `EditorViewModel`（@HiltViewModel） | 编辑会话状态机：临时文档物化、撤销/重做、命令提交、设置同步 |
| `EditorUiState` | 单一 UI 状态容器 |
| `EditorUiEffect` | 一次性副作用（ShowMessage） |
| `EditorTool` | enum：PEN / ERASER |
| `EditorRoute` | 编辑页入口 Composable，接收导航回调 |
| `EditorToolbar` | 顶部工具栏 |
| `HandwriteCanvas` | Ink 画布：手势、视口、笔迹裁剪、橡皮命中 |
| `CanvasPageTransform` | 视口变换数学（internal） |
| `EditorShareRequest` | 分享请求载荷 |

### 9.3 UI 状态管理

标准 StateFlow + UiState pattern：`MutableStateFlow<EditorUiState>` + `Channel<EditorUiEffect>` 副作用。写操作用 `Mutex` 串行化。

### 9.4 Ink 输入处理

- **低延迟**：`Modifier.pointerInteropFilter { containingView.requestUnbufferedDispatch(event); false }` 保持 Ink 未缓冲分发，手势处理留在 Compose。
- **完成笔迹立即转领域模型**：`InProgressStrokes.onStrokesFinished` 回调中逐个 `StrokeInput` → `StrokeSample`（含压力 0..65535、elapsedMillis、tiltX/tiltY），`clipStrokeToPage` 裁剪后提交。
- **页面外起笔不创建笔迹**：`clipStrokeToPage` 处理——单点在页外返回空，从页外进入从入口点开始，离开再进入分割为多段。

### 9.5 手势处理

- **两指优先**：`if (pressed.size >= 2)` 分支在前，`consume()` 阻止绘制，执行 pan + zoom。
- **Stylus-only 模式**：`isSingleFingerNavigationPointer` 判定——`inputMode == STYLUS` 且指针非 stylus/eraser 时作 navigation pointer 平移；stylus 交 Ink 绘制。
- **侧键**：`BUTTON_STYLUS_PRIMARY/SECONDARY`，`TEMPORARY_ERASER`（按住生效）/`TOGGLE_ERASER`/`UNDO`。
- **侧键悬停不触发橡皮**：`isPointerContactActive` 守卫（`pressed || previousPressed`），必须接触才擦。
- **整笔橡皮擦**：`eraseAt` 按逻辑半径命中采样点后删除整笔，半径 `(900f / (zoom/100f)).coerceAtLeast(180f)`。

### 9.6 视口管理

- **缩放范围 100%~400%**：`coerceIn(100, 400)` 多处，`MIN_ZOOM_PERCENT=100`、`MAX_ZOOM_PERCENT=400`，按钮步进 25。
- **100% = 完整页面适配**（非 1:1 像素）：`fit = minOf((canvas.w - padding*2)/page.w, (canvas.h - padding*2)/page.h)`，`scale = fit * zoom`。
- **100% 时 pan 归零**：`LaunchedEffect(zoomPercent, canvasSize, pageSize) { pan = if (zoomPercent == 100) Offset.Zero else transform.clampPan(pan) }`。
- **pan 不移出视口**：`clampPan` 限制在 `[-panLimit, panLimit]`。

### 9.7 撤销/重做机制

- `history = CommandHistory(HistoryLimits(maxCommands = 100, maxEstimatedBytes = 64MB))`。
- **新会话清空历史**：`openDocument` 中 `history.clear()`。
- **Room 写入成功后才入历史**：`commit` 中 `commands.execute(...)` 成功后才 `history.recordCommitted(command)`。
- **文档/页面结构操作不进历史**：只有 `commitStroke`/`commitStrokes`/`eraseElements`/`setBackground`/`importBackground` 用 `recordHistory = true`；`undo`/`redo` 用 `recordHistory = false`；文档创建/删除不记录。
- **仅当前会话存在**：`history` 是 ViewModel 实例字段。

### 9.8 临时文档逻辑（核心机制）

**初始状态**：`documentId = null`、`pageId = null`、`documentName = "新文档"`。

**首次实质修改才物化**（`ensureDocument`）：
- 只在 `commitStroke`/`commitStrokes`/`setBackground`（已物化时）/`importBackground` 中调用。
- 创建文档 + 首页（orderKey = ORDER_STEP=1024）+ DocumentState，然后 `observeDocument` 订阅。

**非物化操作不创建文档**（`setBackground`）：
- 临时文档（documentId == null）改背景只更新 UI 状态 + 持久化到 `defaultBackground` 设置（但不创建文档）。
- 已物化文档改背景才提交 `UpdateBackground` 命令。

**空白临时文档丢弃**（`discardDocumentIfCanvasIsEmpty`）：
- 在 `eraseElements`/`undo`/`redo` 后调用。
- `loadSnapshot` 检查是否有内容（元素非空 OR 背景是 Asset）。
- 无内容则 `deleteDocument` + `history.clear()` + 重置状态。
- **有 Asset 背景不丢弃**（Asset 视为实质内容）。

**未物化临时文档不可导出/分享**：
- `onExport = { state.documentId?.value?.let(onExport) }`，documentId 为 null 不触发。
- 工具栏导出按钮 `enabled = state.documentId != null`。
- 分享也不创建文档（`toShareRequest` 用 `DocumentId(0)`/`PageId(0)` 占位）。

### 9.9 实质内容修改的定义

**物化文档**：新增/删除笔迹、导入资源（Asset 背景）。
**不物化**：缩放/平移、工具切换、颜色/笔宽变化、改背景（临时文档时只更新设置）。

---

## 10. 文档库（feature:library）

### 10.1 模块特征

Android library + Compose，依赖 `:core:model`、`:core:document`、`:core:designsystem` + Hilt。**不依赖 `:core:rendering`**（不渲染笔迹）。无 Ink、无 WorkManager、无 paging、无 activity.compose。仅 2 个源文件，**无测试**。

### 10.2 主要类

- `LibraryViewModel`（@HiltViewModel）：观察 `observeDocuments()` Flow，删除文档。
- `LibraryRoute`：`LazyColumn` 列表，点击打开文档，长按/图标删除。

### 10.3 列表机制

**无 Paging**。最简单的 `Flow<List<Document>>` + `LazyColumn` + `items(key = { it.id.value })`。

### 10.4 删除流程

- 点击删除图标 → `AlertDialog` 确认（"永久删除文档？"，提示"立即删除，无法恢复"）。
- `delete` 用 `deletingId` 防重入，调用 `repository.deleteDocument`。
- **无回收站**，立即永久删除。

---

## 11. 设置（feature:settings）

### 11.1 模块特征

Android library + Compose，依赖 `:core:model`、`:core:document`、`:core:designsystem` + Hilt。无 Ink、无 WorkManager、无 rendering。仅 2 个源文件，**无测试**。

### 11.2 设置项（UI 暴露）

- **输入**：`inputMode`（仅手写笔开关）、`sideButtonAction`。
- **外观**：`themeMode`。
- **导出**：`imageFormat`、`compressionQuality`。
- **导航**：`backBehavior`。

> **注意**：`AppSettings` 中还有 `colorSlots`、`widthSteps`、`activeBrushId`、`pressureSensitivity`、`defaultPageTemplate`、`defaultBackground`、`exportResolution` 等字段，但 settings UI **未暴露所有项**——颜色/笔宽/笔刷/压力敏感度/默认页面模板只能在编辑器工具栏间接修改。

### 11.3 设置同步流程

`SettingsViewModel` 用 `repository.settings.stateIn(...)` 暴露 StateFlow，`update(transform)` 写回。

**Proto 读写不在 settings 模块**：settings 只依赖 `:core:document` 的 `SettingsRepository` 契约，Proto schema/mapper/DataStore 实现在 `:core:data`。符合分层。

**EditorViewModel 也同步设置**：编辑器中改颜色/笔宽/背景会通过 `updateSettings { ... }` 写回 `SettingsRepository`，settings 页和其他会话能观察到。

---

## 12. 导出（feature:export）

### 12.1 模块特征

Android library + Compose，依赖 `:core:model`、`:core:document`、`:core:rendering`、`:core:designsystem` + **`androidx.work.runtime-ktx`** + **`kotlinx.serialization.json`** + Hilt + activity.compose。无 Ink。

### 12.2 主要类

| 类 | 职责 |
|---|---|
| `DocumentExportFormat` | enum：PAGE_IMAGE / LONG_IMAGE / HYBRID_PDF / NATIVE_PACKAGE |
| `ExportViewModel`（@HiltViewModel） | 入队 WorkManager、轮询状态 |
| `ExportRoute` | 导出页 UI + SAF 目标选择 |
| `DocumentExportWorker`（CoroutineWorker） | 实际导出逻辑 |
| `ExportWorkerDependencies`（@EntryPoint） | Worker 通过 EntryPointAccessors 获取依赖 |
| `NativePackageWriter`（internal） | 原生 ZIP 打包 |

### 12.3 WorkManager 使用

```kotlin
OneTimeWorkRequestBuilder<DocumentExportWorker>()
    .setInputData(Data.Builder().putLong(KEY_DOCUMENT_ID, ...).putString(KEY_DESTINATION_URI, ...)...)
    .build()
workManager.enqueueUniqueWork(EXPORT_WORK_NAME, ExistingWorkPolicy.KEEP, request)
```

- `ExistingWorkPolicy.KEEP` 避免重复导出。
- Worker 用 `@EntryPoint` + `EntryPointAccessors.fromApplication` 获取 `DocumentRepository` + `BackgroundResourceRepository`（Worker 不能直接 @Inject）。
- `ExportViewModel` 用 `delay(250ms)` + `getWorkInfoById().get()` **轮询**状态（非 `getWorkInfoByIdFlow()` 响应式）。

### 12.4 导出流程（`doWork`）

1. 从 inputData 读取 documentId、destination、format、imageFormat、resolution、quality。
2. `dependencies.documents().loadSnapshot(DocumentId(documentId))` —— **单次 Room transaction**，之后不观察 Flow。
3. `contentResolver.openOutputStream(destination, "wt")`。
4. 按 format 分发。

### 12.5 四种导出格式

| 格式 | 实现 |
|---|---|
| `PAGE_IMAGE` | `PageImageEncoder.write`，取 lastActivePage 或首页 |
| `LONG_IMAGE` | 多页垂直拼接，`MAX_LONG_IMAGE_EDGE = 30_000`、`MAX_LONG_IMAGE_PIXELS = 36_000_000` 限制下自动缩放 |
| `HYBRID_PDF` | `android.graphics.pdf.PdfDocument`，`PDF_LONG_EDGE = 1_440`，无打印尺寸语义 |
| `NATIVE_PACKAGE` | `NativePackageWriter` ZIP 打包 |

**原生包格式**：
- `manifest.json`：`format = "com.xsgovo.handwrite.package"`，`version = 1`，含文档名/时间/页面元数据。
- `pages/{n}.bin`：`PAGE_BINARY_MAGIC = 0x48575047`，`PAGE_BINARY_VERSION = 1`，含 stroke 的 orderKey/style/samples（增量编码坐标）。
- `resources/{sha256}.{ext}`：按 sha256 去重的背景资源文件。

### 12.6 SAF 写入

`ExportScreen` 用三个 `ActivityResultContracts.CreateDocument(...)` launcher（image/*、application/pdf、application/zip），`takePersistableUriPermission` 确保后台 Worker 有写入权限。Worker 中 `openOutputStream(destination, "wt")`。

### 12.7 "导出不修改文档"核实

`doWork` 中调用的 `DocumentRepository` 方法**只有 `loadSnapshot`**（读取）。无任何 `apply`/`createDocument`/`deleteDocument`/`renameDocument`/`createPage`/`deletePage`/`setLastActivePage` 调用。完全符合。

### 12.8 导出不依赖 Composable 生命周期

所有导出工作在 `CoroutineWorker.doWork` 中，由 WorkManager 调度。`ExportRoute` 销毁后 Worker 仍会完成。

---

## 13. 应用组合层（app）

### 13.1 模块特征

Application 模块，应用全部 9 个 core/feature 模块。源码仅 4 个 Kotlin 文件，无 DI module、无测试、无自定义 proguard-rules.pro。

### 13.2 build.gradle.kts 关键配置

- applicationId = `com.xsgovo.handwrite`，versionCode = 1，versionName = `1.0.0`。
- release：`isMinifyEnabled = true` + `isShrinkResources = true`，**仅用默认 `proguard-android-optimize.txt`**，无自定义 proguard-rules.pro。
- **无 `signingConfigs`** —— release 默认不签名，无法直接产出可安装包。
- 依赖全部 9 个模块 + Compose 全家桶 + Navigation Compose + Hilt + Serialization。

### 13.3 AndroidManifest

- **无任何 `<uses-permission>`**（无 INTERNET、无其他权限）。
- 组件：`.HandwriteApplication`、`.MainActivity`（exported，MAIN/LAUNCHER）、`androidx.core.content.FileProvider`（authority = `${applicationId}.fileprovider`，path = `@xml/file_paths`）。
- `allowBackup="true"`，`fullBackupContent=@xml/backup_rules`，`dataExtractionRules=@xml/data_extraction_rules`。
- **无 `androidx.startup.InitializationProvider` 声明**，WorkManager 走默认初始化。

### 13.4 Application 类（`HandwriteApplication`）

```kotlin
@HiltAndroidApp
class HandwriteApplication : Application() {
    @Inject lateinit var commandExecutor: DurableCommandExecutor
    @Inject lateinit var backgroundResources: BackgroundResourceRepository
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            commandExecutor.recover()              // 恢复 journal 未完成命令
            backgroundResources.pruneUnreferenced() // 清理引用计数=0 资源
        }
    }
}
```

- 启动时 fire-and-forget 恢复 + 清理，不阻塞主线程。
- **不实现 `Configuration.Provider`**，未自定义 WorkManager 配置。

### 13.5 MainActivity

- `@AndroidEntryPoint`，`enableEdgeToEdge()`。
- 通过 `HandwriteAppViewModel` 订阅 `AppSettings`，`HandwriteTheme(themeMode = settings.themeMode)` 驱动主题。
- 承载 `NavHost`，组合 4 个 feature Route。

### 13.6 导航图（类型安全路由）

```kotlin
@Serializable private data class EditorDestination(val sessionId: String, val documentId: Long? = null)
@Serializable private data object LibraryDestination
@Serializable private data object SettingsDestination
@Serializable private data class ExportDestination(val documentId: Long)
```

**默认目的地**：`EditorDestination(sessionId = UUID.randomUUID().toString())`，`documentId` 默认 `null` → **临时编辑会话**。每次冷启动生成新 sessionId。

**导航语义**：
- Editor → Library：`popUpTo<EditorDestination> { inclusive = true }`（临时会话被丢弃）。
- `onExitApplication = ::finishAndRemoveTask`。

### 13.7 PageImageSharer（分享，非导出）

app 层唯一的非 trivial 业务类，负责分享当前页图片到其他 App（与 `:feature:export` 的 SAF 导出是两条独立路径）：
- 复用 `:core:rendering` 的 `PageImageEncoder` + `PageRenderEngine`。
- 临时文件 `cacheDir/shared_notes`，通过 FileProvider 临时授权 `FLAG_GRANT_READ_URI_PERMISSION`。
- `pruneExpiredImages` 删除 24h 前的旧分享图片。
- `CancellationException` 重新抛出。

### 13.8 DI 绑定

**app 模块源码中无任何 `@Module`/`@Provides`/`@Binds`**。所有具体绑定在 `:core:data` 的 `PersistenceModule`。app 层角色：
- `@HiltAndroidApp` 触发 Hilt 聚合所有传递依赖的 `@Module`。
- `@AndroidEntryPoint MainActivity` 注入 `PageImageSharer`。
- `@HiltViewModel HandwriteAppViewModel` 构造注入 `SettingsRepository`。
- `PageImageSharer` 构造函数注入（`@ApplicationContext` + `BackgroundResourceRepository`）。

---

## 14. 测试与验证体系

### 14.1 测试文件全清单（11 个，无 androidTest）

| 模块 | 测试文件 |
|---|---|
| `:core:model` | `ModelTest.kt`（PageTemplate 尺寸、DisplayName 归一化、PageContent 排序） |
| `:core:document` | `CommandHistoryTest.kt`（撤销/重做 + 淘汰）、`DurableCommandExecutorTest.kt`（journal→store→remove 顺序 + 恢复） |
| `:core:data` | `PayloadCodecTest.kt`、`PendingCommandCodecTest.kt`（round-trip）、`FilePendingCommandJournalTest.kt`（真实临时目录）、`AppSettingsMapperTest.kt`（proto 往返） |
| `:core:rendering` | `PageImageEncoderTest.kt`（格式映射） |
| `:feature:editor` | `EditorToolbarTest.kt`（笔宽图标尺寸）、`EditorViewModelTest.kt`（17 用例：临时文档物化、撤销、橡皮、笔宽/颜色持久化、笔迹裁剪等） |
| `:feature:export` | `NativePackageWriterTest.kt`（ZIP 条目结构） |

**无测试的模块**：`:core:designsystem`、`:feature:library`、`:feature:settings`、`:app`。

### 14.2 测试风格

- **JUnit 4**（非 JUnit 5）。
- **kotlinx-coroutines-test**：`runTest`、`StandardTestDispatcher`、`advanceUntilIdle`、`Dispatchers.setMain`/`resetMain`。
- **不使用** MockK/Mockito/Truth/Robolectric/Paparazzi/Roborazzi/Espresso/Compose UI Test。
- **不使用** Room `MigrationTestHelper`（虽然 core:data 引入了 room-testing）。
- **内联 Fake 类**：所有 Fake（FakeDocumentRepository、FakeSettingsRepository、InMemoryJournal、FakeBackgroundResources）以 `private class` 定义在测试文件内，**无共享测试工具类/基类**。
- **Round-Trip 等价性**：codec/mapper 测试以"编码后解码等于原对象"为核心断言。
- **事件序列断言**：`DurableCommandExecutorTest` 用 `MutableList<String>` 收集事件名断言顺序。
- **临时目录清理**：`try { ... } finally { directory.deleteRecursively() }`。

### 14.3 关于"快照测试"的澄清

core 和 feature 下的 `.tab`/`.len`/`.bin` 文件**全部是构建产物**（Kotlin 增量编译缓存、Gradle Configuration Cache、AGP Transform 缓存、Lint 缓存），**不是测试快照数据**。源码目录中无任何此类文件。

项目**没有任何形式的快照测试**（无 Paparazzi/Roborazzi/Compose UI Test snapshot）。现有 AGENTS.md 提到的"导出快照一致性"目前仅由 `NativePackageWriterTest` 通过断言 ZIP 条目集合 `{manifest.json, pages/1.bin, resources/abc123.png}` 部分覆盖，**没有对比预存基准快照**。

### 14.4 Room schema 快照

`core/data/schemas/.../1.json`（Room schema v1）由 `room { schemaDirectory(...) }` 生成，是 Room 推荐的 schema 历史快照，用于未来编写 migration 测试时比对。**当前无 migration 测试使用它**。

---

## 15. 隐私与备份

### 15.1 权限与网络

- **manifest 无任何 `<uses-permission>`**，无 INTERNET。
- app 依赖清单无 firebase/crashlytics/analytics/ads 等网络/遥测库。
- 仅 Compose/Hilt/Navigation/Serialization/Ink/Room/DataStore/WorkManager 等本地库。

### 15.2 存储位置

| 数据 | 位置 | 迁移 |
|---|---|---|
| Room DB | `context` 默认数据库目录 | 参与设备迁移 |
| 资源文件 | `context.filesDir/resources` | 参与设备迁移 |
| 设置 DataStore | `context.dataStoreFile("app_settings.pb")`（`files/datastore/`） | 参与设备迁移 |
| Journal | `context.noBackupFilesDir/pending_commands` | **不参与**（noBackupFilesDir 语义） |
| 分享图片 | `context.cacheDir/shared_notes` | 不参与（缓存） |

### 15.3 备份规则

**`backup_rules.xml`**（Android 6~11 fullBackupContent）：排除全部域（root/file/database/sharedpref/external + device_*）。

**`data_extraction_rules.xml`**（Android 12+）：
- `<cloud-backup>`：排除全部域 → **禁止云备份**。
- `<device-transfer>`：include 全部持久域 → **允许设备迁移复制完整持久数据**。

### 15.4 需复核的不一致点

> **`noBackupFilesDir` journal 与 device-transfer 规则的张力**：现有 ARCHITECTURE.md 声明"journal 位于 noBackupFilesDir，不参与设备迁移"。但 `data_extraction_rules.xml` 的 `<device-transfer>` 显式 `include domain="file" path="."`，而 `noBackupFilesDir` 在文件系统层面属于 `file` 域。规则字面含义会包含 noBackupFilesDir 下的内容。
>
> Android 系统对 `noBackupFilesDir` 有"不被备份/迁移"的特殊语义，但 `dataExtractionRules` 的显式 include 是否覆盖该默认语义存在文档歧义。**建议在设备迁移场景下实测验证 journal 是否真的被排除**，或在 `<device-transfer>` 中显式 `<exclude domain="file" path="no_backup/" />` 消除歧义。

### 15.5 导出/分享的 URI 安全

- **导出**（feature:export）：仅写用户通过 SAF 选择的 URI（`CreateDocument` + `takePersistableUriPermission`）。
- **分享**（app:PageImageSharer）：通过 FileProvider 临时授权 `FLAG_GRANT_READ_URI_PERMISSION`，不写外部存储。

---

## 16. 与现有约束文件的对比

本节对比探索发现与现有 `AGENTS.md` / `ARCHITECTURE.md` 的描述，识别准确、需更新、待补的内容。

### 16.1 现有文档准确的部分（无需改动）

以下声明经代码核实**完全准确**：

- 模块边界（core:model 纯 Kotlin、core:document 契约、core:data 持久化、core:rendering 渲染、feature 互不依赖、app 组合）。
- Long ID（Document/Page/Element/Folder/Resource 全是 Long）。
- orderKey 排序（Page/PageElement 都有 orderKey，PageContent/DocumentSnapshot init 强制排序）。
- 65_535 逻辑坐标（`LogicalCanvas.LONG_EDGE`）。
- StrokeSample 0..65535 压力（`MAX_PRESSURE = 65_535`）。
- BrushStyle 字段（brush ID/颜色/笔宽/混合/压力敏感度）。
- 背景独立于 PageElement（密封接口 ≠ 接口）。
- 背景支持纯色/透明/横线方格/图片PDF。
- 1000:1414 兼容比例非 A4。
- journal→Room→OperationId→删journal 顺序（`DurableCommandExecutor`）。
- 启动恢复未完成命令（`Application.onCreate` 调 `recover()`）。
- 正常写入与恢复串行（`Mutex`）。
- 新命令清空 redo（`CommandHistory.recordCommitted`）。
- Room 写入成功后才入历史。
- 文档/页面结构操作不进撤销。
- 资源私有复制+SHA-256+引用计数+清理。
- 导出从 DocumentSnapshot 读取（`loadSnapshot`）。
- 导出使用 WorkManager、导出不修改文档。
- 领域失败显式表示（`DomainFailure` 11 子类型）。
- 不引入 A4/DPI/物理纸张。
- ImageFormat/ExportResolution/SideButtonAction 枚举。
- 缩放范围 100%~400%、100% pan 归零、pan 不移出视口。
- 两指优先、Stylus-only 手指平移、侧键三动作、侧键悬停不触发橡皮。
- 页面外起笔不创建笔迹、整笔橡皮擦。
- 完成笔迹立即转领域模型。
- 临时文档首次实质修改才物化、空白临时文档丢弃、未物化不可导出。
- 文档删除立即永久删除无回收站。
- 不声明 INTERNET、无网络/遥测。
- 禁止云备份、允许设备迁移。
- verifyLocal 任务组成。
- 原生格式带版本号（manifest version=1、page binary VERSION=1）。

### 16.2 现有文档需要更新/补充的部分

| # | 现有文档说法 | 实际情况 | 建议 |
|---|---|---|---|
| 1 | "OperationId 使用 UUID" | 类型是 `value class(String)`，仅 `isNotBlank` 校验，不强制 UUID 格式 | 改为"OperationId 是字符串幂等键，生产实现使用 UUID" |
| 2 | "Library → Document → Page → PageElement" | 代码无 `Library` 类，实际组织单元是 `Folder`（有 parentId/depth） | 改为"Folder → Document → Page → PageElement"，说明 Library 是概念性根 |
| 3 | core:document 职责含"会话" | 无 `Session` 接口/类，会话由 `CommandHistory` + `EpochClock` 间接承担，`sessionId` 在 app 导航层 | 明确"会话状态由 CommandHistory 承担，sessionId 在 app 层" |
| 4 | 未提及 JVM 版本差异 | core JVM 模块用 Toolchain 21，Android 模块用 Java 17 | 补充说明此差异 |
| 5 | 未提及 build-logic 版本双份维护 | build-logic 硬编码 AGP/Kotlin 版本，与 version catalog 重复 | 补充升级时需同步两处的提醒 |
| 6 | "导出快照一致性"测试 | 无真正快照测试，只有 ZIP 结构断言 | 澄清当前覆盖范围 |
| 7 | 未提及 Hilt 插件应用方式 | 只有 :app 应用 hilt 插件，其他模块用 KSP 路径 | 可补充说明 |
| 8 | 未提及约定插件是瘦插件 | 约定插件无共享 dependencies 块，所有依赖在模块自身声明 | 可补充 |
| 9 | 未提及 release 无签名配置 | app 无 signingConfigs，无法直接产出可安装 release 包 | 补充提醒 |
| 10 | 未提及无自定义 proguard-rules.pro | release 仅用默认 proguard-android-optimize.txt | 补充提醒 |

### 16.3 现有文档待补的缺口

| # | 缺口 | 说明 |
|---|---|---|
| 1 | **core 层无导出/导入契约** | `DomainFailure` 有 `UnsupportedPackageVersion`/`InvalidPackage`，但 core:document 无对应 Repository 接口。导出实现直接在 feature:export 调 `loadSnapshot`；导入侧（解析原生包）的实现位置未在本次探索中确认。按"新领域能力先进入 core 契约"原则，这是缺口 |
| 2 | **PageRenderEngine 资源解码失败静默忽略** | `drawAsset` 用 `runCatching.getOrNull()` 吞异常，违反"领域失败必须显式表示" |
| 3 | **导出渲染与编辑渲染视觉不一致** | `PageRenderEngine.drawStroke` 用直线段，不感知压力/倾斜；`InkDocumentRenderer` 用 Ink 支持压感。同一笔迹编辑与导出外观可能差异明显 |
| 4 | **pruneUnreferenced 文件删除无重试** | DB 删除在事务内，文件删除在事务外，`File.delete()` 失败不抛异常，可能产生孤儿文件 |
| 5 | **Journal 错误映射不精确** | 所有 IOException 映射为 `DatabaseUnavailable`，无法区分磁盘满/权限错误 |
| 6 | **noBackupFilesDir journal 与 device-transfer 规则张力** | `<device-transfer>` include 全 file 域，与 journal 不迁移声明有字面冲突，建议实测或显式 exclude |
| 7 | **Dispatchers.IO 硬编码** | `ContentAddressedResourceRepository`/`FilePendingCommandJournal` 通过构造注入（可测试），但 `rememberBackgroundAssetImage` 硬编码 `Dispatchers.IO`，`PersistenceModule` 中直接用 `Dispatchers.IO` 未通过 qualifier |
| 8 | **BrushId.MONOLINE 映射到 marker family** | `createInkBrush` 的 else 分支兜底，MONOLINE 实际用 marker，语义可能不匹配 |
| 9 | **ImageFormat.AUTO 等于 WEBP_LOSSY** | 没有根据内容智能选择 PNG/JPEG |
| 10 | **ExportViewModel 状态轮询** | 用 `delay(250ms)` 轮询而非 `getWorkInfoByIdFlow()` 响应式 |
| 11 | **app 模块无测试** | 声明了 testImplementation/androidTestImplementation 但无测试文件 |
| 12 | **settings UI 未暴露所有设置项** | colorSlots/widthSteps/activeBrushId/pressureSensitivity/defaultPageTemplate 只能在编辑器间接修改 |

### 16.4 现有文档准确但值得强调的部分

- **领域层不泄漏实现类型**：核实通过。`DocumentRepository` 方法签名只用 core:model 类型，无 Room Entity/Proto Message/Compose 状态/AndroidX Ink 类型泄漏。
- **feature 互不依赖**：核实通过。四个 feature 的 build.gradle.kts 均无 `project(":feature:...")`。
- **可靠写入二段式**：`DurableCommandExecutor` 严格 journal→store→remove，失败保留 journal。
- **两阶段撤销确认**：`commandToUndo()` 预览 + `confirmUndo()` 确认，比单阶段更健壮。
- **资源引用计数原子保护**：SQL `WHERE referenceCount + :delta >= 0` 防止负数。
- **journal 原子写入**：临时文件 + fsync + ATOMIC_MOVE 三重保护。
- **OperationId 幂等**：`applied_operations` 表 + `hasAppliedOperation` 检查，同一事务内。

---

## 17. 重写建议

基于以上探索，对重写 `AGENTS.md` 和 `ARCHITECTURE.md` 的建议：

### 17.1 ARCHITECTURE.md 重写建议

**保留**（经核实准确）：
- 产品边界（本地、无网络、无账号）。
- 领域模型约束（Long ID、orderKey、65_535 坐标、StrokeSample 压力范围、背景独立、文档至少一页）。
- 模块边界与依赖方向。
- 持久化策略（Room 事实来源、Proto 只存设置、资源 SHA-256 + 引用计数）。
- 可靠写入与撤销模型。
- 临时文档物化规则。
- 输入与编辑器规则（手势、视口、橡皮）。
- 导出规则（WorkManager、SAF、不修改文档、版本号）。
- 并发与错误（串行化、显式失败、取消异常传播）。
- 隐私与备份（无 INTERNET、私有目录、禁止云备份）。

**修正**：
- "Library → Document" 改为 "Folder → Document"，说明 Library 是概念性根。
- "OperationId 使用 UUID" 改为"OperationId 是字符串幂等键，生产实现使用 UUID，类型层不强制格式"。
- core:document 的"会话"职责说明由 CommandHistory 承担，sessionId 在 app 层。

**补充**：
- JVM 版本差异（core 用 21，Android 用 17）。
- build-logic 版本双份维护提醒。
- 约定插件是瘦插件（无共享 dependencies）。
- release 无签名配置、无自定义 proguard-rules.pro。
- Hilt 插件只在 :app 应用，其他模块用 KSP 路径。

**待解决/明确**：
- core 层导出/导入契约缺口（是否要在 core:document 定义原生包 Repository 接口）。
- PageRenderEngine 资源解码失败应显式失败（当前静默忽略违反约束）。
- 导出渲染与编辑渲染视觉一致性是否需要约束。
- noBackupFilesDir journal 与 device-transfer 规则的张力（建议实测或显式 exclude）。
- PageRenderEngine.drawStroke 不感知压力/倾斜是否可接受（影响导出视觉）。

### 17.2 AGENTS.md 重写建议

**保留**：
- 工作原则（复用契约、不跨边界、不顺手重构、修改前先读代码）。
- 模块与数据约束（core:model 纯 Kotlin、feature 互不依赖、Room 事实来源、Proto 只存设置、领域层不泄漏实现类型）。
- 测试与验证（verifyLocal 命令、验证级别选择、git diff --check）。
- Git 原子化提交。
- 新模块注册流程。
- 文件与提交（不得提交的目录/文件）。
- 完成任务前确认清单。

**补充/修正**：
- verifyLocal 的实际组成（JVM test + Android testDebugUnitTest + lint + app assembleDebug/Release），明确不含 instrumented test。
- 测试风格（JUnit4 + coroutines-test，无 MockK/Robolectric/Compose UI Test，无共享基类，内联 Fake）。
- "导出快照一致性"当前的实际覆盖（ZIP 结构断言，非真正快照比对）。
- 升级 AGP/Kotlin 时需同步 build-logic 与 version catalog 两处版本。
- release 构建需外部注入 keystore（当前无 signingConfigs）。
- 新增 PageElement 类型时的具体步骤（type/payloadVersion/codec/EntityMappers.toDomain 分发/PayloadCodec）。
- 新增设置时的具体步骤（Proto schema → AppSettingsMapper → AppSettings 默认值 → UI）。
- 新增 Room 字段时的具体步骤（Entity + schema 导出 + Migration + schema JSON）。

---

## 附录：关键文件路径索引

### 构建配置
- `settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradle/libs.versions.toml`
- `build-logic/build.gradle.kts`、`build-logic/settings.gradle.kts`
- `build-logic/src/main/kotlin/handwrite.android.application.gradle.kts`
- `build-logic/src/main/kotlin/handwrite.android.library.gradle.kts`
- `build-logic/src/main/kotlin/handwrite.android.compose.gradle.kts`
- `build-logic/src/main/kotlin/handwrite.kotlin.library.gradle.kts`

### core:model
- `core/model/src/main/kotlin/com/xsgovo/handwrite/core/model/Identifiers.kt`
- `core/model/src/main/kotlin/com/xsgovo/handwrite/core/model/Geometry.kt`
- `core/model/src/main/kotlin/com/xsgovo/handwrite/core/model/Stroke.kt`
- `core/model/src/main/kotlin/com/xsgovo/handwrite/core/model/Background.kt`
- `core/model/src/main/kotlin/com/xsgovo/handwrite/core/model/Document.kt`
- `core/model/src/main/kotlin/com/xsgovo/handwrite/core/model/Naming.kt`
- `core/model/src/main/kotlin/com/xsgovo/handwrite/core/model/Failure.kt`
- `core/model/src/main/kotlin/com/xsgovo/handwrite/core/model/AppSettings.kt`

### core:document
- `core/document/src/main/kotlin/com/xsgovo/handwrite/core/document/DocumentCommand.kt`
- `core/document/src/main/kotlin/com/xsgovo/handwrite/core/document/DocumentCommandStore.kt`
- `core/document/src/main/kotlin/com/xsgovo/handwrite/core/document/CommandHistory.kt`
- `core/document/src/main/kotlin/com/xsgovo/handwrite/core/document/DurableCommandExecutor.kt`
- `core/document/src/main/kotlin/com/xsgovo/handwrite/core/document/PendingCommandJournal.kt`
- `core/document/src/main/kotlin/com/xsgovo/handwrite/core/document/DocumentRepository.kt`
- `core/document/src/main/kotlin/com/xsgovo/handwrite/core/document/BackgroundResourceRepository.kt`
- `core/document/src/main/kotlin/com/xsgovo/handwrite/core/document/SettingsRepository.kt`
- `core/document/src/main/kotlin/com/xsgovo/handwrite/core/document/EpochClock.kt`

### core:data
- `core/data/src/main/kotlin/com/xsgovo/handwrite/core/data/db/Entities.kt`
- `core/data/src/main/kotlin/com/xsgovo/handwrite/core/data/db/Relations.kt`
- `core/data/src/main/kotlin/com/xsgovo/handwrite/core/data/db/HandwriteDatabase.kt`
- `core/data/src/main/kotlin/com/xsgovo/handwrite/core/data/db/HandwriteDao.kt`
- `core/data/src/main/kotlin/com/xsgovo/handwrite/core/data/db/EntityMappers.kt`
- `core/data/src/main/kotlin/com/xsgovo/handwrite/core/data/codec/PayloadCodec.kt`
- `core/data/src/main/kotlin/com/xsgovo/handwrite/core/data/codec/PendingCommandCodec.kt`
- `core/data/src/main/kotlin/com/xsgovo/handwrite/core/data/settings/AppSettingsSerializer.kt`
- `core/data/src/main/kotlin/com/xsgovo/handwrite/core/data/settings/AppSettingsMapper.kt`
- `core/data/src/main/kotlin/com/xsgovo/handwrite/core/data/di/PersistenceModule.kt`
- `core/data/src/main/kotlin/com/xsgovo/handwrite/core/data/RoomDocumentRepository.kt`
- `core/data/src/main/kotlin/com/xsgovo/handwrite/core/data/ProtoSettingsRepository.kt`
- `core/data/src/main/kotlin/com/xsgovo/handwrite/core/data/ContentAddressedResourceRepository.kt`
- `core/data/src/main/kotlin/com/xsgovo/handwrite/core/data/FilePendingCommandJournal.kt`
- `core/data/src/main/proto/app_settings.proto`
- `core/data/src/main/proto/document_payload.proto`
- `core/data/schemas/com.xsgovo.handwrite.core.data.db.HandwriteDatabase/1.json`

### core:rendering
- `core/rendering/src/main/kotlin/com/xsgovo/handwrite/core/rendering/InkDocumentRenderer.kt`
- `core/rendering/src/main/kotlin/com/xsgovo/handwrite/core/rendering/PageRenderEngine.kt`
- `core/rendering/src/main/kotlin/com/xsgovo/handwrite/core/rendering/BackgroundAssetImage.kt`
- `core/rendering/src/main/kotlin/com/xsgovo/handwrite/core/rendering/PageImageEncoder.kt`

### core:designsystem
- `core/designsystem/src/main/kotlin/com/xsgovo/handwrite/core/designsystem/HandwriteTheme.kt`

### feature:editor
- `feature/editor/src/main/kotlin/com/xsgovo/handwrite/feature/editor/EditorViewModel.kt`
- `feature/editor/src/main/kotlin/com/xsgovo/handwrite/feature/editor/EditorScreen.kt`
- `feature/editor/src/main/kotlin/com/xsgovo/handwrite/feature/editor/EditorToolbar.kt`
- `feature/editor/src/main/kotlin/com/xsgovo/handwrite/feature/editor/HandwriteCanvas.kt`
- `feature/editor/src/main/kotlin/com/xsgovo/handwrite/feature/editor/EditorShareRequest.kt`

### feature:library
- `feature/library/src/main/kotlin/com/xsgovo/handwrite/feature/library/LibraryViewModel.kt`
- `feature/library/src/main/kotlin/com/xsgovo/handwrite/feature/library/LibraryScreen.kt`

### feature:settings
- `feature/settings/src/main/kotlin/com/xsgovo/handwrite/feature/settings/SettingsViewModel.kt`
- `feature/settings/src/main/kotlin/com/xsgovo/handwrite/feature/settings/SettingsScreen.kt`

### feature:export
- `feature/export/src/main/kotlin/com/xsgovo/handwrite/feature/export/ExportContract.kt`
- `feature/export/src/main/kotlin/com/xsgovo/handwrite/feature/export/NativePackageWriter.kt`
- `feature/export/src/main/kotlin/com/xsgovo/handwrite/feature/export/ExportViewModel.kt`
- `feature/export/src/main/kotlin/com/xsgovo/handwrite/feature/export/ExportScreen.kt`
- `feature/export/src/main/kotlin/com/xsgovo/handwrite/feature/export/DocumentExportWorker.kt`

### app
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/xsgovo/handwrite/HandwriteApplication.kt`
- `app/src/main/java/com/xsgovo/handwrite/MainActivity.kt`
- `app/src/main/java/com/xsgovo/handwrite/HandwriteAppViewModel.kt`
- `app/src/main/java/com/xsgovo/handwrite/PageImageSharer.kt`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
- `app/src/main/res/xml/file_paths.xml`

---

*报告结束。本报告所有结论均基于源码探索，可作为重写 `AGENTS.md` 与 `ARCHITECTURE.md` 的依据。*
