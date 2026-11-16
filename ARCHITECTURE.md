本文记录项目的模块划分与长期架构约束。实现细节以代码为准；架构发生变化时，先更新本文，再修改实现。

## 模块总览

项目共 10 个 Gradle 模块，依赖方向严格单向，不允许反向或循环依赖：

```
:app
  └── :feature:editor / :feature:library / :feature:settings / :feature:export
        └── :core:rendering / :core:designsystem
              └── :core:document ──→ :core:model
:app 另直接依赖 :core:data（仅为 Hilt 聚合绑定，不引用其类型）
:core:data ──→ :core:document ──→ :core:model
```

依赖方向规则：`:app` 在最上层，可以依赖所有模块；feature 只能依赖 core，不能依赖其他 feature，也不能被其他模块依赖（`:app` 除外）；core 可以依赖其他 core，不能依赖 feature 或 `:app`；`:core:model` 在最底层，不依赖任何项目模块。所有 feature 是普通 Android library，静态打包进 `:app`，不使用动态功能分发。

## 全局约束

- feature 之间禁止直接依赖、禁止互相 import，跨 feature 的页面跳转与协作全部由 `:app` 编排。
- core 不得依赖 feature 或 `:app`。
- `:core:model` 与 `:core:document` 保持平台无关（纯 JVM 模块），不引入 Android 依赖。
- 模块内部实现用 Kotlin `internal` 或 `private` 封闭，对外只暴露必要的公开契约。
- Gradle 依赖优先使用 `implementation`；仅当公开签名确实暴露某依赖的类型时才用 `api`（当前唯一用例：`:core:document` 对 `:core:model` 与 kotlinx.coroutines）。
- 新领域能力先在 core 契约层定义接口，再由 data 或 feature 实现，不允许 feature 直接发明持久化方案。
- 文档写入直接在 Room 事务内完成；不引入写前日志、操作幂等表或启动回放机制。
- 构建配置统一由 build-logic 约定插件管理，各模块 build.gradle.kts 只声明依赖，不重复公共配置。

## 逐模块说明

### :app（应用入口与集成层）

- **职责**：应用入口、全局导航编排、依赖组合。包含 Application、MainActivity、导航目的地定义，以及系统分享执行（PageImageSharer）。
- **适用范围**：依赖全部 feature 与 core 模块。其中对 `:core:data` 的依赖仅为让 Hilt 在编译期聚合其绑定模块，源码不引用 `:core:data` 的任何类型。
- **限制条件**：不放业务逻辑；feature 之间的跳转通过各 feature 对外暴露的 Route composable 的回调参数编排（如 onLibrary、onExport、onBack），feature 不知道导航目的地的存在。领域主题枚举（core:model 的 ThemeMode）到设计系统主题枚举（HandwriteThemeMode）的映射收口在本模块。

### :core:model（领域模型）

- **职责**：全项目共享的不可变领域模型：文档、页面、笔画、背景、设置、ID、几何、错误类型。
- **适用范围**：被所有其他模块依赖；本身不依赖任何项目模块，是纯 Kotlin（JVM）模块。
- **限制条件**：只放数据与纯函数，不放行为逻辑、不放 Android 类型。任何模块需要共享模型时只能加到本模块，不允许在各自模块里重复定义。

### :core:document（文档契约与文档命令）

- **职责**：两部分。一是读写契约：`DocumentRepository`、`SettingsRepository`、`BackgroundResourceRepository` 三个接口，由 `:core:data` 实现，feature 只面向这些接口编程。二是文档修改命令：`DocumentCommand` 是 `DocumentRepository.apply` 的参数类型，属于契约本体；`CommandHistory` 是基于命令的进程内撤销/重做栈；`EpochClock` 是可注入时钟。
- **适用范围**：依赖 `:core:model`（api），被 `:core:data`、`:core:rendering` 和全部 feature 依赖。纯 JVM 模块，可跑快速单元测试。
- **限制条件**：契约只暴露领域类型与协程 Flow，不暴露 Room、Proto、DataStore 等实现细节。`CommandHistory` 不做线程同步，依赖调用方串行访问（当前由 EditorViewModel 的提交串行化保证）；其字节计数随栈操作增量维护，修改栈操作时必须同步更新计数。
- **已知偏差（待整改）**：`CommandHistory` 与 `HistoryLimits` 目前只有 `:feature:editor` 使用，属于编辑器会话逻辑，长期看应迁入 editor 或独立的编辑会话模块；本次保留在原地，属有意妥协而非设计目标。

### :core:data（持久化实现）

- **职责**：实现 `:core:document` 定义的三个契约：Room 文档存储（RoomDocumentRepository）、Proto DataStore 设置存储（ProtoSettingsRepository）、内容寻址的背景资源存储（ContentAddressedResourceRepository），以及 Room/Proto 的实体与编解码。
- **适用范围**：依赖 `:core:model`、`:core:document`。只被 `:app` 依赖（供 Hilt 聚合），feature 不知道本模块存在。
- **限制条件**：所有实现细节（数据库、DAO、实体、Proto 载荷）封闭在模块内部；对外只通过 `di/PersistenceModule` 把实现绑定到契约接口。背景资源采用内容寻址：相同字节内容即同一文件（按哈希去重），因此"同一路径即同一内容"成立，下游可以安全地按路径做缓存键。

### :core:rendering（渲染与编码）

- **职责**：两条渲染路径。屏幕渲染：InkDocumentRenderer 基于 androidx Ink 把笔画转为 Ink Stroke 并按 id 缓存，供编辑器画布与页面缩略图使用。光栅导出：PageRenderEngine 把页面（背景 + 笔画）绘制到软件位图，PageImageEncoder 再把位图编码为 PNG/JPEG/WebP；压力曲线（PressureRendering）是两条路径共用的唯一宽度映射，保证屏幕与导出观感一致。另提供图片/PDF 背景解码（decodeImage/decodePdf）与 Compose 侧背景图加载（rememberBackgroundAssetImage）。
- **适用范围**：依赖 `:core:model`、`:core:document`（仅用到背景资源契约）。被 `:feature:editor`、`:feature:export` 和 `:app`（分享）使用。
- **限制条件**：
  - PageRenderEngine 通过 `BackgroundResourceRepository` 契约解析背景资源，渲染层只依赖接口、不知道 Room/文件存储的实现；这是当前有意保留的折中（让渲染自包含），长期更纯的方向是由调用方传入已解析的资源。
  - PageRenderEngine 内置容量为 2 的背景位图 LRU 缓存（避免长图导出逐页重复解码同一背景）；**实例用完后必须调用 `release()` 回收缓存位图**，当前两个持有方（导出 Worker、分享）都已在 finally 中释放。缓存键含路径、资源类型、PDF 页码与目标尺寸，路径即内容身份（内容寻址），无需额外失效策略。
  - PDF 背景按矢量光栅化：decodePdf 不限制放大，高分辨率导出时背景按目标尺寸渲染；位图背景（decodeImage）只做降采样、不放大。
  - InkDocumentRenderer 传给 Ink 渲染器的 strokeToScreenTransform 不会被 Ink 应用到画布（Ink 官方 API 语义，仅用于抗锯齿与纹理质量计算），真正定位笔画的是代码里的 canvas.concat；两处传同一矩阵是刻意写法，不可删其一。
  - 光栅导出是软件位图路径，无法复用 Ink 的 GPU 渲染，因此笔画光栅化存在屏幕（Ink mesh）与导出（手写 Path/线段）两套实现；二者必须通过共享的压力曲线保持一致，修改压力映射时两条路径一起改。

### :core:designsystem（设计系统）

- **职责**：应用主题（HandwriteTheme：配色、形状、明暗与动态取色）、手绘风格矢量图标集（HandwriteIcons）、共享扁平控件（图标按钮、徽章、笔宽按钮、滑块、数值标签）。
- **适用范围**：只依赖 Compose 与 Material 3，**不依赖任何项目模块**。被全部 feature 与 `:app` 使用。
- **限制条件**：主题模式使用本模块自定义的 `HandwriteThemeMode` 枚举；core:model 的 ThemeMode 到它的映射由 `:app` 完成，本模块不反向引用领域枚举。主题之外的共享颜色通过 `LocalHandwriteColors` 提供，其默认值是亮色组，读取方必须处于 `HandwriteTheme` 之内。

### :feature:editor（编辑器）

- **职责**：手写编辑全部功能：画布交互（Ink 湿墨输入与干墨渲染）、页面管理（增删移页、书签、页面面板）、画笔与取色器、橡皮、背景设置与导入、撤销/重做、分享请求构造。
- **适用范围**：依赖 `:core:model`、`:core:document`、`:core:rendering`、`:core:designsystem`。对外只暴露 `EditorRoute`（参数为 documentId 与导航/分享回调）和分享请求类型 `EditorShareRequest`。
- **限制条件**：撤销/重做历史保存在进程内（EditorViewModel 持有的 CommandHistory），进程结束后不恢复；页面删除不可撤销。写入一律经 `DocumentRepository.apply(DocumentCommand)` 提交，撤销与重做通过命令逆变换重放。

### :feature:library（文档库）

- **职责**：文档列表展示、新建、打开、删除。
- **适用范围**：依赖 `:core:model`、`:core:document`、`:core:designsystem`。对外只暴露 `LibraryRoute`（参数为新建/打开/设置回调）。
- **限制条件**：不直接读写存储，一切读写经 `DocumentRepository` 契约。

### :feature:settings（设置）

- **职责**：应用设置的展示与修改（主题、导出格式、分辨率、质量、侧键行为等）。
- **适用范围**：依赖 `:core:model`、`:core:document`、`:core:designsystem`。对外只暴露 `SettingsRoute`（参数为返回回调）。
- **限制条件**：只通过 `SettingsRepository` 契约读写设置，不知道 DataStore/Proto 的存在。

### :feature:export（导出）

- **职责**：文档导出：单页图片、长图、混合 PDF、原生包四种格式。后台执行用 WorkManager（DocumentExportWorker），依赖通过 Hilt EntryPoint 获取。
- **适用范围**：依赖 `:core:model`、`:core:document`、`:core:rendering`、`:core:designsystem`。对外只暴露 `ExportRoute`（参数为 documentId 与返回回调）。
- **限制条件**：使用 PageRenderEngine 后必须在 finally 中调用 `release()`（已落实）。长图导出会把所有页面合成一张大位图，页数很多时内存峰值高，这是已知风险（未做分块流式编码），目前靠导出分辨率档位控制。
