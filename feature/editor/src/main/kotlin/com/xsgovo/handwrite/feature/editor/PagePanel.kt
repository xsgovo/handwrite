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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.xsgovo.handwrite.core.model.LogicalSize
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PageContent
import com.xsgovo.handwrite.core.model.PageId
import com.xsgovo.handwrite.core.model.StrokeElement
import com.xsgovo.handwrite.core.rendering.InkDocumentRenderer

// 页面管理面板：从右侧滑出的页面缩略图列表，支持切换、添加、删除、书签与移动页面。
@Composable
fun PagePanel(
    visible: Boolean,
    pages: List<PageContent>,
    currentPageId: PageId?,
    addPageSize: LogicalSize,
    onSelectPage: (PageId) -> Unit,
    onAddPage: () -> Unit,
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
    onSelectPage: (PageId) -> Unit,
    onAddPage: () -> Unit,
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

@Composable
private fun PageThumbnailList(
    pages: List<PageContent>,
    currentPageId: PageId?,
    addPageSize: LogicalSize,
    listState: LazyListState,
    onSelectPage: (PageId) -> Unit,
    onAddPage: () -> Unit,
    onDeletePage: (PageId) -> Unit,
    onToggleBookmark: (PageId) -> Unit,
    onMovePage: (PageId, Int) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
    ) {
        items(pages.size, key = { pages[it].page.id.value }) { index ->
            val page = pages[index]
            PageThumbnailCell(
                page = page,
                pageNumber = index + 1,
                index = index,
                pageCount = pages.size,
                selected = page.page.id == currentPageId,
                onSelect = { onSelectPage(page.page.id) },
                onDelete = { onDeletePage(page.page.id) },
                onToggleBookmark = { onToggleBookmark(page.page.id) },
                onMove = { onMovePage(page.page.id, it) },
            )
        }
        item(key = ADD_PAGE_ITEM_KEY) {
            AddPageCell(addPageSize, onAddPage)
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
        verticalArrangement = Arrangement.spacedBy(20.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
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
                onMove = { onMovePage(page.page.id, it) },
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
                .combinedClickable(onClick = onSelect, onLongClick = { menuOpen = true }),
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
            if (page.page.bookmarked) {
                Icon(
                    Icons.Default.Bookmark,
                    contentDescription = "已加书签",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp),
                )
            }
            Text(
                text = "$pageNumber",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItemText(
                    label = if (page.page.bookmarked) "移出书签" else "添加到书签",
                    onClick = { menuOpen = false; onToggleBookmark() },
                )
                DropdownMenuItemText(
                    label = "上移",
                    enabled = index > 0,
                    onClick = { menuOpen = false; onMove(-1) },
                )
                DropdownMenuItemText(
                    label = "下移",
                    enabled = index < pageCount - 1,
                    onClick = { menuOpen = false; onMove(1) },
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

@Composable
private fun AddPageCell(pageSize: LogicalSize, onAddPage: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
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
                        onClick = onAddPage,
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
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private const val SCRIM_ALPHA = 0.45f
private const val PANEL_WIDTH_FRACTION = 0.4f
private const val THUMBNAIL_WIDTH_FRACTION = 0.86f
private const val THUMBNAIL_TRANSPARENT_FILL_ALPHA = 0.45f
private const val TAB_PAGES = 0
private const val TAB_BOOKMARKS = 1
private const val ADD_PAGE_ITEM_KEY = "add_page"
