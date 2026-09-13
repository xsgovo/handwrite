package com.xsgovo.handwrite.feature.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import com.xsgovo.handwrite.core.model.LogicalSize
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PageContent
import com.xsgovo.handwrite.core.model.PageId
import com.xsgovo.handwrite.core.model.PatternType
import com.xsgovo.handwrite.core.model.StrokeElement
import com.xsgovo.handwrite.core.rendering.InkDocumentRenderer
import kotlinx.coroutines.delay

// 页面管理面板：从右侧滑出的页面缩略图列表，支持切换、添加、删除、书签与拖拽移动页面。
@Composable
fun PagePanel(
    visible: Boolean,
    pages: List<PageContent>,
    currentPageId: PageId?,
    addPageSize: LogicalSize,
    currentBackground: PageBackground,
    onSelectPage: (PageId) -> Unit,
    onAddPage: (PageBackground?) -> Unit,
    onDeletePage: (PageId) -> Unit,
    onToggleBookmark: (PageId) -> Unit,
    onMovePage: (PageId, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = SCRIM_ALPHA))
                    .pointerInput(Unit) { detectTapGestures { onDismiss() } },
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
        ) {
            PagePanelSheet(
                pages = pages,
                currentPageId = currentPageId,
                addPageSize = addPageSize,
                currentBackground = currentBackground,
                onSelectPage = onSelectPage,
                onAddPage = onAddPage,
                onDeletePage = onDeletePage,
                onToggleBookmark = onToggleBookmark,
                onMovePage = onMovePage,
            )
        }
    }
}

@Composable
private fun PagePanelSheet(
    pages: List<PageContent>,
    currentPageId: PageId?,
    addPageSize: LogicalSize,
    currentBackground: PageBackground,
    onSelectPage: (PageId) -> Unit,
    onAddPage: (PageBackground?) -> Unit,
    onDeletePage: (PageId) -> Unit,
    onToggleBookmark: (PageId) -> Unit,
    onMovePage: (PageId, Int) -> Unit,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(TAB_PAGES) }
    val listState = rememberLazyListState()
    // 面板只在展开时进入组合：首次组合定位到当前页，加页后跟随新页。
    LaunchedEffect(pages.size, currentPageId, selectedTab) {
        val index = pages.indexOfFirst { it.page.id == currentPageId }
        if (selectedTab == TAB_PAGES && index > 0) listState.scrollToItem(index)
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(PANEL_WIDTH_FRACTION),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            PanelTabs(selectedTab) { selectedTab = it }
            when (selectedTab) {
                TAB_PAGES -> PageThumbnailList(
                    pages = pages,
                    currentPageId = currentPageId,
                    addPageSize = addPageSize,
                    currentBackground = currentBackground,
                    listState = listState,
                    onSelectPage = onSelectPage,
                    onAddPage = onAddPage,
                    onDeletePage = onDeletePage,
                    onToggleBookmark = onToggleBookmark,
                    onMovePage = onMovePage,
                )
                else -> BookmarkThumbnailList(
                    pages = pages,
                    currentPageId = currentPageId,
                    onSelectPage = onSelectPage,
                    onDeletePage = onDeletePage,
                    onToggleBookmark = onToggleBookmark,
                    onMovePage = onMovePage,
                )
            }
        }
    }
}

@Composable
private fun PanelTabs(selectedIndex: Int, onSelect: (Int) -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .height(42.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clip(shape),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        PanelTab("所有页", selectedIndex == TAB_PAGES, Modifier.weight(1f)) { onSelect(TAB_PAGES) }
        PanelTab("书签", selectedIndex == TAB_BOOKMARKS, Modifier.weight(1f)) { onSelect(TAB_BOOKMARKS) }
    }
}

@Composable
private fun PanelTab(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageThumbnailList(
    pages: List<PageContent>,
    currentPageId: PageId?,
    addPageSize: LogicalSize,
    currentBackground: PageBackground,
    listState: LazyListState,
    onSelectPage: (PageId) -> Unit,
    onAddPage: (PageBackground?) -> Unit,
    onDeletePage: (PageId) -> Unit,
    onToggleBookmark: (PageId) -> Unit,
    onMovePage: (PageId, Int) -> Unit,
) {
    // 拖拽排序：拖拽中在 dragOrder 上即时预览，抬手后经 onMovePage 落库一次；
    // pendingOrder 在仓储刷新前保持界面顺序，避免数据流回放把页面闪回原位。
    var draggingPageId by remember { mutableStateOf<PageId?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }
    var dragOrder by remember { mutableStateOf<List<PageId>?>(null) }
    var pendingOrder by remember { mutableStateOf<List<PageId>?>(null) }

    LaunchedEffect(pendingOrder) {
        if (pendingOrder != null) {
            // 正常路径下仓储刷新远快于此；兜底清除让落库失败的界面回到真实顺序。
            delay(PENDING_ORDER_TIMEOUT_MILLIS)
            pendingOrder = null
        }
    }

    fun displayPages(): List<PageContent> {
        val order = dragOrder ?: pendingOrder ?: return pages
        val byId = pages.associateBy { it.page.id }
        return order.mapNotNull { byId[it] }.takeIf { it.size == pages.size } ?: pages
    }

    // 每帧一次越点检测：被拖页的视觉中心落入相邻页范围即把预览顺序移动一格。
    // 锚点随交换切换，反向补偿拖拽偏移让纸面视觉位置保持连续；layoutInfo 下一帧
    // 才反映交换，因此不做循环推进，快速拖动时逐帧追赶。
    fun resolveDragOverPoint(draggedId: PageId) {
        val info = listState.layoutInfo.visibleItemsInfo
        val self = info.firstOrNull { it.key == draggedId.value } ?: return
        val visualCenter = self.offset + self.size / 2f + dragOffsetY
        val target = info.firstOrNull { item ->
            item.key != draggedId.value && item.key is Long &&
                visualCenter >= item.offset && visualCenter < item.offset + item.size
        } ?: return
        val order = (dragOrder ?: pages.map { it.page.id }).toMutableList()
        val from = order.indexOf(draggedId)
        val to = order.indexOf(PageId(target.key as Long))
        if (from < 0 || to < 0 || from == to) return
        order.add(to, order.removeAt(from))
        dragOrder = order.toList()
        dragOffsetY += (self.offset + self.size / 2f) - (target.offset + target.size / 2f)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
    ) {
        val displayPages = displayPages()
        items(displayPages.size, key = { displayPages[it].page.id.value }) { index ->
            val page = displayPages[index]
            val isDragging = page.page.id == draggingPageId
            Box(
                Modifier
                    .then(if (isDragging) Modifier else Modifier.animateItem())
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffsetY else 0f
                    },
            ) {
                PageThumbnailCell(
                    page = page,
                    pageNumber = index + 1,
                    index = index,
                    pageCount = displayPages.size,
                    selected = page.page.id == currentPageId,
                    onSelect = { onSelectPage(page.page.id) },
                    onDelete = { onDeletePage(page.page.id) },
                    onToggleBookmark = { onToggleBookmark(page.page.id) },
                    onMove = { targetIndex -> onMovePage(page.page.id, targetIndex) },
                    onLongPressDragStart = {
                        draggingPageId = page.page.id
                        dragOffsetY = 0f
                        dragOrder = null
                    },
                    onLongPressDrag = { amount ->
                        dragOffsetY += amount
                        resolveDragOverPoint(page.page.id)
                    },
                    onLongPressDragEnd = {
                        val draggedId = draggingPageId
                        val finalOrder = dragOrder
                        if (draggedId != null && finalOrder != null) {
                            pendingOrder = finalOrder
                            onMovePage(draggedId, finalOrder.indexOf(draggedId))
                        }
                        draggingPageId = null
                        dragOrder = null
                        dragOffsetY = 0f
                    },
                )
            }
        }
        item(key = ADD_PAGE_ITEM_KEY) {
            AddPageCell(addPageSize, currentBackground, onAddPage)
        }
    }
}

@Composable
private fun BookmarkThumbnailList(
    pages: List<PageContent>,
    currentPageId: PageId?,
    onSelectPage: (PageId) -> Unit,
    onDeletePage: (PageId) -> Unit,
    onToggleBookmark: (PageId) -> Unit,
    onMovePage: (PageId, Int) -> Unit,
) {
    val bookmarked = pages.withIndex().filter { it.value.page.bookmarked }
    if (bookmarked.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "暂无书签",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
    ) {
        items(bookmarked.size, key = { bookmarked[it].value.page.id.value }) { index ->
            val (originalIndex, page) = bookmarked[index]
            PageThumbnailCell(
                page = page,
                pageNumber = originalIndex + 1,
                index = originalIndex,
                pageCount = pages.size,
                selected = page.page.id == currentPageId,
                onSelect = { onSelectPage(page.page.id) },
                onDelete = { onDeletePage(page.page.id) },
                onToggleBookmark = { onToggleBookmark(page.page.id) },
                onMove = { targetIndex -> onMovePage(page.page.id, targetIndex) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageThumbnailCell(
    page: PageContent,
    pageNumber: Int,
    index: Int,
    pageCount: Int,
    selected: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onToggleBookmark: () -> Unit,
    onMove: (Int) -> Unit,
    onLongPressDragStart: (() -> Unit)? = null,
    onLongPressDrag: ((Float) -> Unit)? = null,
    onLongPressDragEnd: (() -> Unit)? = null,
) {
    val renderer = remember { InkDocumentRenderer() }
    // 与主画布一致地渲染笔迹；渲染层按笔画 id 缓存，元素不变时 prepare 结果可复用。
    val prepared = remember(page.elements) {
        runCatching { renderer.prepare(page.elements.filterIsInstance<StrokeElement>()) }.getOrDefault(emptyList())
    }
    val transparentFillColor = MaterialTheme.colorScheme.surface.copy(alpha = THUMBNAIL_TRANSPARENT_FILL_ALPHA)
    val shape = RoundedCornerShape(10.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    var menuOpen by remember { mutableStateOf(false) }
    val dragStart = onLongPressDragStart
    val dragBy = onLongPressDrag
    val dragEnd = onLongPressDragEnd
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(THUMBNAIL_WIDTH_FRACTION)
                .aspectRatio(page.page.size.width.toFloat() / page.page.size.height.toFloat())
                .clip(shape)
                .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = shape)
                .combinedClickable(onClick = onSelect, onLongClick = { menuOpen = true })
                .then(
                    if (dragStart != null && dragBy != null && dragEnd != null) {
                        // 长按先弹出功能菜单；手指继续移动即收起菜单进入拖拽排序，
                        // 原地抬手则只留菜单供点选。
                        Modifier.pointerInput(page.page.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    menuOpen = true
                                    dragStart()
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    if (menuOpen) menuOpen = false
                                    dragBy(amount.y)
                                },
                                onDragEnd = dragEnd,
                                onDragCancel = dragEnd,
                            )
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val scale = size.width / page.page.size.width
                drawRect(page.page.background.baseColor(transparentFillColor), topLeft = Offset.Zero, size = size)
                when (val background = page.page.background) {
                    is PageBackground.Pattern -> drawPattern(background, Rect(0f, 0f, size.width, size.height), scale)
                    else -> Unit
                }
                clipRect(0f, 0f, size.width, size.height) {
                    renderer.draw(this, prepared, scale, 0f, 0f)
                }
            }
            Icon(
                if (page.page.bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                contentDescription = null,
                tint = if (page.page.bookmarked) BOOKMARK_TINT else MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    // 顶边与缩略图顶边对齐，只保留水平方向的可点余量；无底色遮罩。
                    .padding(start = 10.dp, bottom = 10.dp)
                    .size(26.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleBookmark,
                    )
                    .padding(3.dp)
                    .semantics {
                        contentDescription = if (page.page.bookmarked) "移出书签" else "添加到书签"
                    },
            )
            Text(
                text = "$pageNumber",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItemText(
                    label = if (page.page.bookmarked) "移出书签" else "添加到书签",
                    onClick = { menuOpen = false; onToggleBookmark() },
                )
                DropdownMenuItemText(
                    label = "上移",
                    enabled = index > 0,
                    onClick = { menuOpen = false; onMove(index - 1) },
                )
                DropdownMenuItemText(
                    label = "下移",
                    enabled = index < pageCount - 1,
                    onClick = { menuOpen = false; onMove(index + 1) },
                )
                HorizontalDivider()
                DropdownMenuItemText(
                    label = "删除页面",
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun DropdownMenuItemText(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    androidx.compose.material3.DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        enabled = enabled,
    )
}

// 新页面单元格：点击 + 号纸面按原有语义直接加页（沿用当前纸面背景），
// 点击“新页面”文字弹出样式菜单，自定义底色与线型后添加。
@Composable
private fun AddPageCell(
    pageSize: LogicalSize,
    currentBackground: PageBackground,
    onAddPage: (PageBackground?) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // 每次打开菜单回到“沿用当前纸面”的默认；图片/PDF 背景不可复制到新页，由调用方归一为可复制背景。
    var draft by remember(menuOpen) { mutableStateOf(currentBackground) }
    val shape = RoundedCornerShape(10.dp)
    Box {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.fillMaxWidth(THUMBNAIL_WIDTH_FRACTION)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(pageSize.width.toFloat() / pageSize.height.toFloat())
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onAddPage(null) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "添加页面",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Text(
                    "新页面",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { menuOpen = true },
                        ),
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            AddPageStyleMenu(
                pageSize = pageSize,
                draft = draft,
                onChange = { draft = it },
                onAdd = {
                    menuOpen = false
                    onAddPage(draft)
                },
            )
        }
    }
}

// 新页面样式菜单：纸面预览所见即所得，底色与线型相互独立、自由组合。
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPageStyleMenu(
    pageSize: LogicalSize,
    draft: PageBackground,
    onChange: (PageBackground) -> Unit,
    onAdd: () -> Unit,
) {
    val currentColor = when (draft) {
        is PageBackground.Solid -> draft.argb
        is PageBackground.Pattern -> draft.baseArgb
        PageBackground.Transparent -> PageBackground.TRANSPARENT
        is PageBackground.Asset -> PageBackground.WHITE
    }
    val currentStyle = (draft as? PageBackground.Pattern)?.type
    val transparentPreviewFill = MaterialTheme.colorScheme.surface.copy(alpha = THUMBNAIL_TRANSPARENT_FILL_ALPHA)
    Column(Modifier.widthIn(min = 260.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(
                Modifier
                    .height(84.dp)
                    .aspectRatio(pageSize.width.toFloat() / pageSize.height.toFloat())
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp)),
            ) {
                drawRect(draft.baseColor(transparentPreviewFill), topLeft = Offset.Zero, size = size)
                if (draft is PageBackground.Pattern) {
                    val scale = size.width / pageSize.width
                    drawPattern(draft, Rect(0f, 0f, size.width, size.height), scale)
                }
            }
        }
        Text(
            "底色",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            backgroundSwatchChoices.forEach { (label, argb) ->
                AddPageSwatch(
                    label = label,
                    argb = argb,
                    selected = currentColor == argb,
                    onClick = { onChange(backgroundWithStyle(argb, currentStyle)) },
                )
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            listOf(null to "无样式", PatternType.LINED to "横线", PatternType.GRID to "方格").forEach { (style, label) ->
                FilterChip(
                    selected = currentStyle == style,
                    onClick = { onChange(backgroundWithStyle(currentColor, style)) },
                    label = { Text(label) },
                )
            }
        }
        Button(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp),
        ) {
            Text("添加页面")
        }
    }
}

// 底色色块：透明底垫棋盘格，选中时主色描边加对勾。
@Composable
private fun AddPageSwatch(label: String, argb: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(shape)
            .then(if (argb == PageBackground.TRANSPARENT) Modifier.checkerboardBackdrop() else Modifier)
            .background(Color(argb))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$label 背景" },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private const val SCRIM_ALPHA = 0.45f
private const val PANEL_WIDTH_FRACTION = 0.4f
private const val THUMBNAIL_WIDTH_FRACTION = 0.58f
private const val THUMBNAIL_TRANSPARENT_FILL_ALPHA = 0.45f
private const val PENDING_ORDER_TIMEOUT_MILLIS = 700L
private const val TAB_PAGES = 0
private const val TAB_BOOKMARKS = 1
private const val ADD_PAGE_ITEM_KEY = "add_page"

// 已加书签的图标用琥珀金，与半透明深色底形成稳定对比，不随纸面底色失真。
private val BOOKMARK_TINT = Color(0xFFFFC107)
