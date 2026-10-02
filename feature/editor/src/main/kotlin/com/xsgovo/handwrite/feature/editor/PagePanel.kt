package com.xsgovo.handwrite.feature.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
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
import kotlin.math.abs
import kotlin.math.exp

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
    // 面板只在展开时进入组合：首次组合定位到当前页一次。不跟随后续数据与选中态变化
    // 自动滚动——拖拽排序松手、删除页面等链路一旦触发这里的强制滚动，就会把选中页
    // 顶到视口最顶端；加页的新单元格出现在列表尾部，本身就在用户操作位置附近。
    LaunchedEffect(Unit) {
        val index = pages.indexOfFirst { it.page.id == currentPageId }
        if (selectedTab == TAB_PAGES && index > 0) listState.scrollToItem(index)
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(PANEL_WIDTH_FRACTION)
            // 面板从状态栏之下开始：顶部圆角不被通知栏压住，与底部圆角对称。
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                stringResource(R.string.editor_panel_title_pages),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
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
        PanelTab(stringResource(R.string.editor_panel_tab_all_pages), selectedIndex == TAB_PAGES, Modifier.weight(1f).fillMaxHeight()) { onSelect(TAB_PAGES) }
        PanelTab(stringResource(R.string.editor_panel_tab_bookmarks), selectedIndex == TAB_BOOKMARKS, Modifier.weight(1f).fillMaxHeight()) { onSelect(TAB_BOOKMARKS) }
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
    val dragState = remember(listState) { PageDragState(listState) }
    // 被拖项在当前 pages 顺序中的位置：数据重排生效后它等于 targetIndex。
    val draggedIndex = pages.indexOfFirst { it.page.id == dragState.draggedId }.takeIf { it >= 0 }
    LaunchedEffect(dragState.draggedId) {
        var previousFrameMillis = withFrameNanos { it / 1_000_000L }
        while (dragState.draggedId != null) {
            val frameMillis = withFrameNanos { it / 1_000_000L }
            dragState.frameStep(frameMillis - previousFrameMillis, frameMillis)
            previousFrameMillis = frameMillis
        }
    }
    // 数据层顺序一有变化就通报拖拽状态机：被拖项出现在目标格位即重排已落地，此后
    // 掠过的页面不再走避让渐变，直接以最终布局位为视觉基准静止到位（无回正动画）。
    SideEffect { dragState.onPagesChanged(pages) }
    // 原地长按（未拖动）松手弹出该页功能菜单；菜单状态提升到列表层，由容器手势设置。
    var menuTargetId by remember { mutableStateOf<PageId?>(null) }
    // 拖拽浮层的实测高度：用于把被拖项视觉的中心锚定在手指处。
    var draggedGhostHeight by remember { mutableStateOf(0f) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // 长按拖拽手势挂在列表容器上：容器组合不随列表滚动回收。边缘自动滚动
                // 会把被拖项的布局槽推出视口（item 被回收），挂在单项上的手势会随之
                // 取消导致拖拽中断；容器级手势不受影响，拖拽会话持续存活。
                .pointerInput(onMovePage) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { touchOffset ->
                            val info = listState.layoutInfo
                            val hit = info.visibleItemsInfo.firstOrNull {
                                it.index < info.totalItemsCount - 1 &&
                                    touchOffset.y >= it.offset &&
                                    touchOffset.y < it.offset + it.size
                            } ?: return@detectDragGesturesAfterLongPress
                            dragState.start(PageId(hit.key as Long), touchOffset.y - hit.offset)
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            dragState.drag(amount.y, change.uptimeMillis)
                        },
                        onDragEnd = {
                            val finishedId = dragState.end(onMovePage)
                            if (finishedId != null && !dragState.lastDragMoved) menuTargetId = finishedId
                        },
                        onDragCancel = { dragState.end(onMovePage) },
                    )
                },
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 20.dp),
        ) {
            items(pages.size, key = { pages[it].page.id.value }) { index ->
                val page = pages[index]
                val isDragging = page.page.id == dragState.draggedId
                val avoidanceY = dragState.avoidanceTranslation(index, draggedIndex)
                val animatedAvoidanceY by animateFloatAsState(avoidanceY, label = "page avoidance")
                Box(
                    Modifier
                        .then(if (dragState.draggedId != null) Modifier else Modifier.animateItem())
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragState.dataArrived) 0f else animatedAvoidanceY
                        },
                ) {
                    PageThumbnailCell(
                        page = page,
                        pageNumber = index + 1,
                        index = index,
                        pageCount = pages.size,
                        selected = page.page.id == currentPageId,
                        menuOpen = menuTargetId == page.page.id,
                        onMenuDismiss = { menuTargetId = null },
                        onSelect = { onSelectPage(page.page.id) },
                        onDelete = { onDeletePage(page.page.id) },
                        onToggleBookmark = { onToggleBookmark(page.page.id) },
                        onMove = { targetIndex -> onMovePage(page.page.id, targetIndex) },
                        hiddenForDragging = isDragging,
                    )
                }
            }
            item(key = ADD_PAGE_ITEM_KEY) {
                AddPageCell(addPageSize, currentBackground, onAddPage)
            }
        }
        // 拖拽浮层：被拖项的视觉由列表外层承载并锚定手指。布局槽被边缘滚动推出视口、
        // item 被回收时，浮层不随列表回收，拖拽目标依旧完整可见；页码随预演目标格位
        // 实时更新。
        dragState.draggedId?.let { draggedId ->
            val draggedPage = pages.firstOrNull { it.page.id == draggedId } ?: return@let
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .onSizeChanged { draggedGhostHeight = it.height.toFloat() }
                    .graphicsLayer {
                        translationY = dragState.dragFingerViewportY - draggedGhostHeight / 2f
                    },
                contentAlignment = Alignment.Center,
            ) {
                PageThumbnailVisual(
                    page = draggedPage,
                    pageNumber = (dragState.previewTargetIndex + 1).coerceAtMost(pages.size),
                    selected = draggedPage.page.id == currentPageId,
                )
            }
        }
    }
}

// 拖拽排序状态。手势与视觉由列表容器承载（容器级长按手势 + 列表外层的拖拽浮层），
// 被拖项的组合槽位在拖拽期间留空——边缘自动滚动把槽位推出视口、item 被回收时，
// 手势与视觉均不受影响，拖拽会话持续存活。拖拽期间被拖项保持拖拽态（zIndex 提升、
// animateItem 缺席、避让维持），松手后进入落位阶段并继续持有拖拽态：此时重排尚未
// 写回，若立即清空，被拖项会先跳回原布局位，随后数据重排到达时 animateItem 重新
// 挂上，把这段移动重放一遍，且重放期间已失去 zIndex 提升而以底层姿态掠过其他页面。
// 落位阶段等待重排写回（被拖项出现在目标格位），被拖项吸附到目标格中心，收敛后才
// 解除拖拽态。
// 避让动画只作为拖动过程中的让位表现：重排落地后布局位与避让预演位一致，掠过的
// 页面的绘制位移直接归零静止，不播放任何回正（归位）过渡动画。
// 浮层位置 = 手指视口纵坐标 - 浮层实测高度的一半（dragFingerViewportY / 浮层
// onSizeChanged 实测），列表滚动、重排换位、首可见项锚定补偿等一切布局扰动都不影响
// 锚定，不跟手漂移；手指偏离被拖项中心超过半格时，预演顺序向偏离方向移动一格。
private class PageDragState(private val listState: LazyListState) {
    var draggedId by mutableStateOf<PageId?>(null)
        private set
    private var fingerViewportY by mutableStateOf(0f)
    private var targetIndex by mutableIntStateOf(-1)
    private var originIndex = -1
    private var userMoved = false
    private var settling = false

    // 边缘自动滚动的当前速度与平滑后的手指速度：均带符号（px/秒），帧率无关。
    private var scrollSpeedPxPerSec = 0f
    private var fingerVelocityPxPerSec = 0f
    private var lastDragEventMillis = 0L

    // 重排数据是否已落地（被拖项出现在目标格位）。落地前掠过的页面停留在避让预演位；
    // 落地后布局位与预演位一致，绘制位移直接归零，不再有任何回正过渡。状态跨落位
    // 收敛保持，直到下一次拖拽开始才复位。
    var dataArrived by mutableStateOf(false)
        private set
    private var settleFrameCount = 0

    // 拖拽中手指的视口纵坐标：拖拽浮层据此把被拖项视觉锚定在手指处。
    val dragFingerViewportY: Float
        get() = fingerViewportY

    // 拖拽会话的当前目标格位：浮层的页码随预演位置实时更新。
    val previewTargetIndex: Int
        get() = targetIndex

    // 原地长按（未拖动）松手后要弹出功能菜单的页面：由容器级手势在拖拽结束时设置。
    var menuTargetId by mutableStateOf<PageId?>(null)
        private set

    fun dismissMenu() {
        menuTargetId = null
    }

    fun start(pageId: PageId, touchYInItem: Float) {
        val self = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == pageId.value } ?: return
        draggedId = pageId
        menuTargetId = null
        fingerViewportY = touchYInItem + self.offset
        userMoved = false
        settling = false
        dataArrived = false
        settleFrameCount = 0
        targetIndex = self.index
        originIndex = self.index
        scrollSpeedPxPerSec = 0f
        fingerVelocityPxPerSec = 0f
        lastDragEventMillis = 0L
    }

    // 数据层顺序通报：进入落位阶段后，被拖项出现在目标格位即重排已写回。此后绘制层
    // 的避让位移直接归零（布局位已与避让预演位一致），掠过的页面不再有回正过渡。
    // 必须以 settling 为前提：拖动中数据顺序未变，被拖项本就位于 originIndex 处，
    // 未换格时该条件天然成立，若无守卫会把避让动画整体跳过；movePage 对未换格是
    // no-op（不发射数据），落位中数据顺序本就等于预演顺序，判定也天然成立。
    fun onPagesChanged(pages: List<PageContent>) {
        if (!dataArrived && settling && draggedId != null && pages.getOrNull(targetIndex)?.page?.id == draggedId) {
            dataArrived = true
        }
    }

    // index 是当前 pages 顺序中的位置；draggedIndex 是被拖项在同一顺序中的位置。
    // 避让动画只存在于拖动过程中；重排落地（draggedIndex == targetIndex）后绘制层
    // 直接以布局位为基准归零，不再有任何回正过渡。
    fun avoidanceTranslation(index: Int, draggedIndex: Int?): Float {
        if (draggedId == null || draggedIndex == null || draggedIndex == targetIndex) return 0f
        if (index == originIndex) return 0f
        val distance = listState.layoutInfo.visibleItemsInfo
            .zipWithNext()
            .map { (first, second) -> second.offset - first.offset }
            .filter { it > 0 }
            .average()
            .takeIf { it.isFinite() }
            ?.toFloat()
            ?: 0f
        return when {
            targetIndex > originIndex && index in (originIndex + 1)..targetIndex -> -distance
            targetIndex < originIndex && index in targetIndex until originIndex -> distance
            else -> 0f
        }
    }

    fun drag(amount: Float, eventTimeMillis: Long) {
        if (settling) return
        fingerViewportY += amount
        userMoved = true
        // 手指速度估计：按事件间隔折算成带符号的 px/秒并指数平滑。事件流中断后
        // 恢复时间隔封顶，避免速度被极端低估；首个事件自会话起点计，速度自然从零起步。
        val sampleMillis = (eventTimeMillis - lastDragEventMillis).coerceIn(1L, GESTURE_SPEED_SAMPLE_MAX_INTERVAL_MS)
        lastDragEventMillis = eventTimeMillis
        val instantVelocity = amount / sampleMillis * 1000f
        fingerVelocityPxPerSec += (instantVelocity - fingerVelocityPxPerSec) * (1f - exp(-sampleMillis / FINGER_SPEED_TAU_MS))
        advance()
    }

    // 每帧推进：拖拽中做换格预览与边缘滚动；落位中等待重排生效并吸附。
    // frameDeltaMillis 为帧间时长（滚动速度按秒定义、按帧折算位移，帧率无关），
    // frameTimeMillis 为当前帧时间戳，与手势事件共用 uptime 时钟。
    fun frameStep(frameDeltaMillis: Long, frameTimeMillis: Long) {
        if (draggedId == null) return
        if (settling) {
            settleStep()
            return
        }
        advance()
        if (!userMoved) return
        // 手指停止拖动后手势速度逐帧回落，速度加成随之平滑消失，滚动收敛到深度基准速度。
        if (frameTimeMillis - lastDragEventMillis > FINGER_SPEED_IDLE_DELAY_MS && frameDeltaMillis > 0) {
            fingerVelocityPxPerSec *= exp(-frameDeltaMillis / FINGER_SPEED_IDLE_TAU_MS)
        }
        val layout = listState.layoutInfo
        val viewportHeight = (layout.viewportEndOffset - layout.viewportStartOffset).toFloat()
        if (viewportHeight <= 0f) return
        val edgeZone = viewportHeight * AUTO_SCROLL_EDGE_FRACTION
        val topDepth = layout.viewportStartOffset + edgeZone - fingerViewportY
        val bottomDepth = fingerViewportY - (layout.viewportEndOffset - edgeZone)
        val (direction, depth) = when {
            topDepth > 0f -> -1f to topDepth
            bottomDepth > 0f -> 1f to bottomDepth
            else -> 0f to 0f
        }
        // 目标速度：基础速度随超出边缘的深度线性提升；只有与滚动同向的手指速度提供
        // 加成，快速拖拽时列表跟得上手势，往回拖时列表不被惯性冲过头。
        val target = if (direction == 0f) {
            0f
        } else {
            val depthFactor = (depth / edgeZone).coerceIn(0f, 1f)
            val base = viewportHeight * (AUTO_SCROLL_MIN_SPEED_FRACTION +
                (AUTO_SCROLL_MAX_SPEED_FRACTION - AUTO_SCROLL_MIN_SPEED_FRACTION) * depthFactor)
            val sameDirVelocity = (fingerVelocityPxPerSec * direction).coerceAtLeast(0f)
            val boost = 1f + (sameDirVelocity / (viewportHeight * AUTO_SCROLL_GESTURE_REF_FRACTION))
                .coerceIn(0f, AUTO_SCROLL_MAX_BOOST - 1f)
            direction * base * boost
        }
        // 滚动速度向目标指数趋近：同向提升用短时间常数保证响应及时，减速与反向用
        // 长时间常数保证收势平稳，杜绝触边突进与离边骤停。
        val accelerating = target * scrollSpeedPxPerSec >= 0f && abs(target) > abs(scrollSpeedPxPerSec)
        val tauMillis = if (accelerating) AUTO_SCROLL_ACCEL_TAU_MS else AUTO_SCROLL_DECEL_TAU_MS
        if (frameDeltaMillis > 0) {
            scrollSpeedPxPerSec += (target - scrollSpeedPxPerSec) * (1f - exp(-frameDeltaMillis / tauMillis))
            if (target == 0f && abs(scrollSpeedPxPerSec) < AUTO_SCROLL_STOP_PX_PER_SEC) scrollSpeedPxPerSec = 0f
            if (scrollSpeedPxPerSec != 0f) {
                listState.dispatchRawDelta(scrollSpeedPxPerSec * frameDeltaMillis / 1000f)
                advance()
            }
        }
    }

    // 返回结束拖拽的页面：调用方据此判定“原地长按（未拖动）松手弹出功能菜单”。
    fun end(onCommit: (PageId, Int) -> Unit): PageId? {
        val id = draggedId ?: return null
        advance()
        // 不清空拖拽态：提交后重排经 Room 异步写回，落位阶段继续持有拖拽态，
        // 被 frameStep 推进直到收敛后解除。
        settling = true
        onCommit(id, targetIndex)
        return id
    }

    // 落位开始时的拖动标记，供容器级手势区分“原地长按”与真实拖动。
    val lastDragMoved: Boolean
        get() = userMoved

    private fun settleStep() {
        val id = draggedId ?: return
        val self = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id.value }
        if (self == null) {
            // 布局槽暂不可见（边缘滚动把原槽位推出了视口）：数据重排会把它带到目标
            // 格位（就在手指附近），等待下一帧实测即可；超时兜底解除，避免状态悬挂。
            if (settleFrameCount >= SETTLE_MAX_FRAMES) clear() else settleFrameCount++
            return
        }
        if (settleFrameCount >= SETTLE_MAX_FRAMES) {
            // 重排迟迟未写回（提交被丢弃）：解除拖拽态，避免状态悬挂。
            clear()
            return
        }
        settleFrameCount++
        if (self.index != targetIndex) return
        // 重排已生效：布局已在最终位置（避让归零由重组期的 draggedIndex == targetIndex
        // 判定负责）。手指锚点向实测格中心指数趋近，收敛后解除拖拽态（残余位移低于
        // 感知阈值）。targetIndex 保持不变，让本判定逐帧持续成立。
        val center = self.offset + self.size / 2f
        fingerViewportY += (center - fingerViewportY) * SETTLE_APPROACH_FACTOR
        if (abs(center - fingerViewportY) < SETTLE_SNAP_PX) clear()
    }

    private fun clear() {
        draggedId = null
        fingerViewportY = 0f
        targetIndex = -1
        originIndex = -1
        userMoved = false
        settling = false
        settleFrameCount = 0
        scrollSpeedPxPerSec = 0f
        fingerVelocityPxPerSec = 0f
        lastDragEventMillis = 0L
    }

    // 换格判定：目标格位 = 手指中心线之上的页面项数量（被拖项自身不计）。
    // 可见项逐个实测中心；视口上方的不可见页面项全部位于手指之上，按序位补计——
    // 列表处于滚动状态时若只按可见项计数，拖动尾部页面会把目标格位错误地压缩到
    // 视口内（页面看似被移出列表、上方页面补位），且永远无法插回列表末尾。
    private fun advance() {
        val info = listState.layoutInfo
        val draggedKey = draggedId?.value
        // 末尾的添加页单元格不参与换格计数。
        val pageItemLimit = info.totalItemsCount - 1
        val visible = info.visibleItemsInfo
        val visiblePassed = visible.count {
            it.index < pageItemLimit && it.key != draggedKey && fingerViewportY > it.offset + it.size / 2
        }
        val firstVisiblePageIndex = visible.firstOrNull { it.index < pageItemLimit }?.index ?: pageItemLimit
        var hiddenAbove = firstVisiblePageIndex
        // 被拖项的布局槽固定在数据顺序的原位，滚动后可能位于视口上方，不计入手指标位。
        if (originIndex in 0 until firstVisiblePageIndex) hiddenAbove -= 1
        targetIndex = (visiblePassed + hiddenAbove).coerceIn(0, info.totalItemsCount - 2)
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
                stringResource(R.string.editor_no_bookmarks),
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
                menuOpen = false,
                onMenuDismiss = {},
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
    menuOpen: Boolean,
    onMenuDismiss: () -> Unit,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onToggleBookmark: () -> Unit,
    onMove: (Int) -> Unit,
    hiddenForDragging: Boolean = false,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            // 被拖项的视觉由列表外层的拖拽浮层承载（不随布局槽滚动回收），槽位处留空。
            .drawWithContent { if (!hiddenForDragging) drawContent() },
        contentAlignment = Alignment.Center,
    ) {
        PageThumbnailVisual(page = page, pageNumber = pageNumber, selected = selected, onClick = onSelect)
        DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuDismiss) {
            DropdownMenuItemText(
                label = stringResource(
                    if (page.page.bookmarked) R.string.editor_remove_bookmark else R.string.editor_add_bookmark,
                ),
                onClick = { onMenuDismiss(); onToggleBookmark() },
            )
            DropdownMenuItemText(
                label = stringResource(R.string.editor_move_page_up),
                enabled = index > 0,
                onClick = { onMenuDismiss(); onMove(index - 1) },
            )
            DropdownMenuItemText(
                label = stringResource(R.string.editor_move_page_down),
                enabled = index < pageCount - 1,
                onClick = { onMenuDismiss(); onMove(index + 1) },
            )
            HorizontalDivider()
            DropdownMenuItemText(
                label = stringResource(R.string.editor_delete_page),
                onClick = { onMenuDismiss(); onDelete() },
            )
        }
    }
}

// 页面缩略图的纯视觉呈现：底色/线格背景、笔迹渲染、书签角标与页码。页面列表与拖拽
// 浮层共用，保证两个宿主里的目标外观完全一致。
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PageThumbnailVisual(
    page: PageContent,
    pageNumber: Int,
    selected: Boolean,
    onClick: (() -> Unit)? = null,
    onToggleBookmark: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val renderer = remember { InkDocumentRenderer() }
    // 与主画布一致地渲染笔迹；渲染层按笔画 id 缓存，元素不变时 prepare 结果可复用。
    val prepared = remember(page.elements) {
        runCatching { renderer.prepare(page.elements.filterIsInstance<StrokeElement>()) }.getOrDefault(emptyList())
    }
    val transparentFillColor = MaterialTheme.colorScheme.surface.copy(alpha = THUMBNAIL_TRANSPARENT_FILL_ALPHA)
    val shape = RoundedCornerShape(10.dp)
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = modifier
            .fillMaxWidth(THUMBNAIL_WIDTH_FRACTION)
            .widthIn(max = THUMBNAIL_MAX_WIDTH)
            .aspectRatio(page.page.size.width.toFloat() / page.page.size.height.toFloat())
            .clip(shape)
            .border(width = if (selected) 2.dp else 1.dp, color = borderColor, shape = shape)
            .then(if (onClick != null) Modifier.combinedClickable(onClick = onClick) else Modifier),
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
        val bookmarkActionDescription = stringResource(
            if (page.page.bookmarked) R.string.editor_remove_bookmark else R.string.editor_add_bookmark,
        )
        Icon(
            if (page.page.bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            contentDescription = if (onToggleBookmark != null) bookmarkActionDescription else null,
            tint = if (page.page.bookmarked) BOOKMARK_TINT else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.TopEnd)
                // 顶边与缩略图顶边对齐，只保留水平方向的可点余量；无底色遮罩。
                .padding(start = 10.dp, bottom = 10.dp)
                .size(26.dp)
                .then(
                    if (onToggleBookmark != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleBookmark,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(3.dp)
                .semantics {
                    if (onToggleBookmark != null) contentDescription = bookmarkActionDescription
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth(THUMBNAIL_WIDTH_FRACTION).widthIn(max = THUMBNAIL_MAX_WIDTH)) {
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
                    contentDescription = stringResource(R.string.editor_add_page),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                )
            }
            // 菜单锚定在文字自身，弹出位置紧贴“新页面”文字，而不是整个单元格。
            Box(Modifier.padding(top = 6.dp)) {
                Text(
                    stringResource(R.string.editor_new_page),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { menuOpen = true },
                    ),
                )
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
            stringResource(R.string.editor_base_color),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            backgroundSwatchChoices.forEach { (labelRes, argb) ->
                AddPageSwatch(
                    labelRes = labelRes,
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
            listOf(
                null to R.string.editor_background_style_none,
                PatternType.LINED to R.string.editor_background_style_lined,
                PatternType.GRID to R.string.editor_background_style_grid,
            ).forEach { (style, labelRes) ->
                FilterChip(
                    selected = currentStyle == style,
                    onClick = { onChange(backgroundWithStyle(currentColor, style)) },
                    label = { Text(stringResource(labelRes)) },
                )
            }
        }
        Button(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 8.dp),
        ) {
            Text(stringResource(R.string.editor_add_page))
        }
    }
}

// 底色色块：透明底垫棋盘格，选中时主色描边加对勾。
@Composable
private fun AddPageSwatch(labelRes: Int, argb: Int, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    val label = stringResource(labelRes)
    val contentDescriptionText = stringResource(R.string.editor_background_swatch_content_description, label)
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
            .semantics { contentDescription = contentDescriptionText },
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
// 缩略图宽度上限：横屏下面板变宽，用固定上限防止缩略图随面板等比膨胀。
private val THUMBNAIL_MAX_WIDTH = 180.dp
private const val THUMBNAIL_TRANSPARENT_FILL_ALPHA = 0.45f
private const val PENDING_ORDER_TIMEOUT_MILLIS = 700L
// 拖拽自动滚动：手指进入视口上下各一成半高度的区域即持续滚动。
private const val AUTO_SCROLL_EDGE_FRACTION = 0.15f
// 基础滚动速度随超出边缘的深度线性提升：刚触边为视口高度的 0.45 倍/秒，深抵视口
// 边界时升至 2.2 倍/秒（速度按秒定义、按帧间时长折算位移，帧率无关）。
private const val AUTO_SCROLL_MIN_SPEED_FRACTION = 0.45f
private const val AUTO_SCROLL_MAX_SPEED_FRACTION = 2.2f
// 手势加速：与滚动同向的手指速度达到视口高度 * 该系数 / 秒时，加成升满。
private const val AUTO_SCROLL_GESTURE_REF_FRACTION = 1.5f
private const val AUTO_SCROLL_MAX_BOOST = 2.5f
// 滚动速度的指数平滑时间常数：加速取短保证响应及时，减速取长保证收势平稳（毫秒）。
private const val AUTO_SCROLL_ACCEL_TAU_MS = 60f
private const val AUTO_SCROLL_DECEL_TAU_MS = 160f
// 低于该速度即视为停止，滚动速度归零（px/秒）。
private const val AUTO_SCROLL_STOP_PX_PER_SEC = 1f
// 手指速度估计：指数平滑时间常数、事件间隔折算上限与停止拖动后的回落参数（毫秒）。
private const val FINGER_SPEED_TAU_MS = 60f
private const val GESTURE_SPEED_SAMPLE_MAX_INTERVAL_MS = 100L
private const val FINGER_SPEED_IDLE_DELAY_MS = 64L
private const val FINGER_SPEED_IDLE_TAU_MS = 90f
// 松手落位吸附：每帧把手位锚点向格中心指数趋近，低于半像素即视为落位完成。
private const val SETTLE_APPROACH_FACTOR = 0.35f
private const val SETTLE_SNAP_PX = 0.5f
// 落位等待帧数上限（约两秒）：重排提交被丢弃时兜底解除拖拽态，避免状态悬挂。
private const val SETTLE_MAX_FRAMES = 120
private const val TAB_PAGES = 0
private const val TAB_BOOKMARKS = 1
private const val ADD_PAGE_ITEM_KEY = "add_page"

// 已加书签的图标用琥珀金，与半透明深色底形成稳定对比，不随纸面底色失真。
private val BOOKMARK_TINT = Color(0xFFFFC107)
