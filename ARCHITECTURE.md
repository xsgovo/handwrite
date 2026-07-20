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
- 最长边为 `65535`，另一边按比例计算。
- 显示和导出分别映射到像素空间。
- 历史 `1000:1414` 仅作为兼容比例模板，不代表 A4。

笔迹：

- 持久化使用自有领域模型，不依赖 AndroidX Ink 类型。
- `BrushStyle` 保存 brush ID、颜色、逻辑笔宽、混合模式和压力敏感度。
- AndroidX Ink 仅用于输入和显示，完成笔迹必须转换为领域模型后持久化。

背景：

- 背景属于 `PageBackground`，不是 PageElement。
- 支持纯色、透明、横线/方格、图片/PDF。
- 外部资源必须复制到应用私有存储，不长期依赖来源 URI。

## 3. 模块架构

- `:core:model`：纯 Kotlin 领域模型、ID、坐标、笔迹、背景、文档、设置、错误
- `:core:document`：文档命令、Repository 契约、会话和可靠写入契约
- `:core:data`：资源和持久化实现
- `:core:rendering`：笔迹、背景和资源渲染
- `:core:designsystem`：共享 UI 基础
- `:feature:*`：各功能 UI 和业务流程
- `:app`：应用入口、导航和依赖组合

约束：

- feature 之间禁止直接依赖，由 `:app` 负责组合。
- core 不得依赖 feature。
- `:core:model` 和 `:core:document` 保持平台无关。
- 新领域能力先进入 core 契约，再由 data/feature 实现。

## 4. 事实来源

架构约束以本文为准；具体实现以代码为准：

- 依赖和版本：Gradle Version Catalog / 构建脚本
- 模块边界：Gradle 配置
- 数据库结构：Room Entity / Migration
- 设置结构：Proto schema
- API 契约：core 模块代码
- 当前功能状态：实际实现和测试

不要在本文重复维护上述信息。
