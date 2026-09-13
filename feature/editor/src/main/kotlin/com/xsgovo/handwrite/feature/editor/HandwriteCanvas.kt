package com.xsgovo.handwrite.feature.editor

import android.view.MotionEvent
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
// androidx.ink.strokes.Stroke 占用了 Stroke 名称，drawscope 的描边参数用别名引入。
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.ink.authoring.compose.InProgressStrokes
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import com.xsgovo.handwrite.core.designsystem.LocalHandwriteColors
import com.xsgovo.handwrite.core.model.BrushId
import com.xsgovo.handwrite.core.model.ElementId
import com.xsgovo.handwrite.core.model.InputMode
import com.xsgovo.handwrite.core.model.LogicalPoint
import com.xsgovo.handwrite.core.model.LogicalSize
import com.xsgovo.handwrite.core.model.PageBackground
import com.xsgovo.handwrite.core.model.PageContent
import com.xsgovo.handwrite.core.model.PagePattern
import com.xsgovo.handwrite.core.model.PatternType
import com.xsgovo.handwrite.core.model.PressureSensitivity
import com.xsgovo.handwrite.core.model.SideButtonAction
import com.xsgovo.handwrite.core.model.StrokeElement
import com.xsgovo.handwrite.core.model.StrokeSample
import com.xsgovo.handwrite.core.rendering.InkDocumentRenderer
import com.xsgovo.handwrite.core.rendering.createInkBrush
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HandwriteCanvas(
    // 展示面：真实页面或待添加虚拟页，兼作会话状态键（切面时重建会话状态）。
    surface: EditorSurface?,
    pageSize: LogicalSize,
    background: PageBackground,
    backgroundImage: ImageBitmap?,
    strokes: List<StrokeElement>,
    tool: EditorTool,
    eraserMode: EraserMode,
    eraserSize: Int,
    inputMode: InputMode,
    // 待添加虚拟页上为 false：书写/擦除/缩放停用；翻页导航手势不受影响。
    inkingEnabled: Boolean,
    zoomPercent: Int,
    zoomLocked: Boolean,
    activeColor: Int,
    activeWidth: Int,
    activeBrushId: BrushId,
    pressureSensitivity: PressureSensitivity,
    sideButtonAction: SideButtonAction,
    onZoomChanged: (Int) -> Unit,
    onStrokesFinished: (List<List<StrokeSample>>, onCompleted: () -> Unit) -> Unit,
    onEraseFinished: (Set<ElementId>, onCompleted: () -> Unit) -> Unit,
    onErasePixelsFinished: (
        removedIds: Set<ElementId>,
        piecesByOriginalId: Map<ElementId, List<List<StrokeSample>>>,
        onCompleted: () -> Unit,
    ) -> Unit,
    onToggleEraser: () -> Unit,
    onUndo: () -> Unit,
    // 拖拽方向上的相邻展示面：真实页由画布绘制预览，待添加虚拟页由内容槽位呈现。
    prevNeighbor: EditorNeighbor?,
    nextNeighbor: EditorNeighbor?,
    // 待添加虚拟页的真实内容：露出与停留两个阶段复用同一组合，显示完全一致。
    pendingAddPageContent: @Composable () -> Unit,
    onSwipeNavigate: (Int) -> Unit,
    onTemporaryEraserChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    // 以下会话状态均以展示面为生命周期：画布常驻后用 surface 作 key，切面即重置。
    var erasedIds by remember(surface) { mutableStateOf<Set<ElementId>>(emptySet()) }
    var pendingErasedIds by remember(surface) { mutableStateOf<Set<ElementId>>(emptySet()) }
    // 像素橡皮的擦除预览：原笔画 id → 存留片段（负数临时 id 仅供显示），手势结束时一并提交。
    var pixelErasePieces by remember(surface) { mutableStateOf<Map<ElementId, List<StrokeElement>>>(emptyMap()) }
    var pixelErasedIds by remember(surface) { mutableStateOf<Set<ElementId>>(emptySet()) }
    // 已提交给 ViewModel、等待持久化回执的擦除内容；失败时恢复显示。
    var pendingPixelPieces by remember(surface) { mutableStateOf<Map<ElementId, List<StrokeElement>>>(emptyMap()) }
    var pendingPixelErasedIds by remember(surface) { mutableStateOf<Set<ElementId>>(emptySet()) }
    // 像素橡皮光标位置（屏幕坐标），用于绘制可调大小的橡皮圆盘。
    var pixelEraseCursor by remember(surface) { mutableStateOf<Offset?>(null) }
    // 预览片段每次重切都分配全新临时 id：渲染层按笔画 id 缓存 Ink 笔画且不看内容，
    // 复用 id 会让切分预览永远停留在首次接触时的内容。
    val previewPieceIds = remember { AtomicLong(0) }
    // 笔画包围盒在会话内不变，缓存后每次擦除事件对未触及笔画只需常数级剔除。
    val strokeBoundsCache = remember(surface) { mutableMapOf<ElementId, Rect>() }
    var pendingStrokes by remember(surface) { mutableStateOf<List<Stroke>>(emptyList()) }
    var pan by remember(surface) { mutableStateOf(Offset.Zero) }
    var zoomControlsVisible by remember(surface) { mutableStateOf(false) }
    var zoomGestureActive by remember(surface) { mutableStateOf(false) }
    var zoomControlInteraction by remember(surface) { mutableStateOf(0) }

    val transform = remember(canvasSize, pageSize, zoomPercent, pan) {
        CanvasPageTransform.create(canvasSize, pageSize, zoomPercent / 100f, pan)
    }
    val density = LocalDensity.current
    val pageSwipeThresholdPx = with(density) { PAGE_SWIPE_COMMIT_THRESHOLD_DP.dp.toPx() }
    val scope = rememberCoroutineScope()
    // 页面拖拽位移（像素，向右为正）：平移到横向边界后继续拖动，页面整体跟手滑出；
    // 松手按阈值与方向决定切面，否则弹回。
    var pageDragPx by remember(surface) { mutableFloatStateOf(0f) }
    var pageDragSettleJob by remember(surface) { mutableStateOf<Job?>(null) }
    val inkingEnabledState by rememberUpdatedState(inkingEnabled)
    // 待添加虚拟页内容的两个图层：页面图层之下的露出层（页面滑出时逐渐露出、返回时被
    // 逐渐覆盖）与停留在虚拟页时的最上层交互层。同一组合两个摆放位，切换同帧无缝；
    // 派生状态仅在翻转时触发重组。surface 必须参与 key：pageDragPx 随 surface 重建
    // 状态对象，派生状态不重建会一直读旧对象（物化文档等切面后露出失效）。
    val pendingAddUnderlayVisible by remember(surface, nextNeighbor) {
        derivedStateOf {
            (nextNeighbor is EditorNeighbor.PendingAdd && pageDragPx < -0.5f) ||
                (surface is EditorSurface.PendingAdd && pageDragPx > 0.5f)
        }
    }
    val pendingAddOverlayVisible by remember(surface) {
        derivedStateOf {
            surface is EditorSurface.PendingAdd && abs(pageDragPx) <= 0.5f
        }
    }
    val pointerEventToLogicalTransform = remember(transform) {
        transform.pointerEventToLogicalTransform()
    }
    val currentTransform by rememberUpdatedState(transform)
    val currentZoom by rememberUpdatedState(zoomPercent)
    val currentZoomLocked by rememberUpdatedState(zoomLocked)
    val currentStrokes by rememberUpdatedState(strokes)
    val strokesCallback by rememberUpdatedState(onStrokesFinished)
    val zoomCallback by rememberUpdatedState(onZoomChanged)
    val eraseCallback by rememberUpdatedState(onEraseFinished)
    val erasePixelsCallback by rememberUpdatedState(onErasePixelsFinished)
    val toggleEraserCallback by rememberUpdatedState(onToggleEraser)
    val undoCallback by rememberUpdatedState(onUndo)
    val prevNeighborState by rememberUpdatedState(prevNeighbor)
    val nextNeighborState by rememberUpdatedState(nextNeighbor)
    val swipeNavigateCallback by rememberUpdatedState(onSwipeNavigate)
    val temporaryEraserCallback by rememberUpdatedState(onTemporaryEraserChanged)

    LaunchedEffect(zoomPercent, canvasSize, pageSize) {
        pan = if (zoomPercent == 100) Offset.Zero else transform.clampPan(pan)
    }
    LaunchedEffect(zoomGestureActive, zoomControlInteraction) {
        if (zoomGestureActive) {
            zoomControlsVisible = true
        } else if (zoomControlsVisible) {
            delay(ZOOM_CONTROLS_HIDE_DELAY_MILLIS)
            zoomControlsVisible = false
        }
    }

    fun keepZoomControlsVisible() {
        zoomControlsVisible = true
        zoomControlInteraction++
    }

    fun eraseAt(point: LogicalPoint) {
        val radius = (900f / (currentZoom / 100f)).coerceAtLeast(180f)
        val hits = currentStrokes.asSequence()
            .filterNot { it.id in erasedIds || it.id in pendingErasedIds }
            .filter { stroke ->
                stroke.samples.any { candidate ->
                    hypot(
                        (candidate.point.x - point.x).toFloat(),
                        (candidate.point.y - point.y).toFloat(),
                    ) <= radius + stroke.style.width / 2f
                }
            }
            .map(StrokeElement::id)
            .toSet()
        if (hits.isNotEmpty()) erasedIds = erasedIds + hits
    }

    // 预览片段用递减的负数临时 id，避免与持久化 id 冲突；提交时由 ViewModel 重新分配。
    fun previewPieces(stroke: StrokeElement, pieces: List<List<StrokeSample>>): List<StrokeElement> =
        pieces.mapIndexed { _, pieceSamples ->
            StrokeElement(
                id = ElementId(previewPieceIds.decrementAndGet()),
                pageId = stroke.pageId,
                orderKey = stroke.orderKey,
                style = stroke.style,
                samples = pieceSamples,
            )
        }

    fun strokeBounds(stroke: StrokeElement): Rect = strokeBoundsCache.getOrPut(stroke.id) {
        var left = Int.MAX_VALUE
        var top = Int.MAX_VALUE
        var right = Int.MIN_VALUE
        var bottom = Int.MIN_VALUE
        stroke.samples.forEach { sample ->
            if (sample.point.x < left) left = sample.point.x
            if (sample.point.x > right) right = sample.point.x
            if (sample.point.y < top) top = sample.point.y
            if (sample.point.y > bottom) bottom = sample.point.y
        }
        Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
    }

    // 相邻路径点的间距不超过采样步长，端点都远于包围盒 hitRadius + 步长的线段必然
    // 碰不到笔画；按此剪除后，长距离擦除不会让每次重算都扫描整条不断增长的路径。
    fun pruneErasePath(path: List<LogicalPoint>, bounds: Rect, hitRadius: Float): List<LogicalPoint> {
        val reach = hitRadius + (eraserSize / 4f).coerceAtLeast(ERASER_PATH_MIN_STEP.toFloat())
        val pruned = path.filter { point ->
            point.x >= bounds.left - reach && point.x <= bounds.right + reach &&
                point.y >= bounds.top - reach && point.y <= bounds.bottom + reach
        }
        return if (pruned.size == path.size) path else pruned
    }

    fun erasePixelsAlongPath(path: List<LogicalPoint>) {
        if (path.isEmpty()) return
        val eraserRadius = eraserSize / 2f
        val cursorX = path.last().x.toFloat()
        val cursorY = path.last().y.toFloat()
        currentStrokes.asSequence()
            .filterNot { it.id in erasedIds || it.id in pendingErasedIds || it.id in pixelErasedIds }
            .forEach { stroke ->
                val hitRadius = eraserRadius + stroke.style.width / 2f
                val bounds = strokeBounds(stroke)
                // 圆盘未触及包围盒的笔画不会被新路径点影响，既有预览保持不变；
                // 手写笔报点率很高，这一步剔除决定了擦除的整体开销。
                if (cursorX < bounds.left - hitRadius || cursorX > bounds.right + hitRadius ||
                    cursorY < bounds.top - hitRadius || cursorY > bounds.bottom + hitRadius
                ) {
                    return@forEach
                }
                val localPath = pruneErasePath(path, bounds, hitRadius)
                if (localPath.isEmpty()) return@forEach
                val pieces = splitStrokeByErasePath(
                    samples = stroke.samples,
                    erasePath = localPath,
                    hitRadius = hitRadius,
                )
                when {
                    pieces.isEmpty() -> {
                        pixelErasePieces = pixelErasePieces - stroke.id
                        pixelErasedIds = pixelErasedIds + stroke.id
                    }
                    pieces.size == 1 && pieces.single().size == stroke.samples.size ->
                        pixelErasePieces = pixelErasePieces - stroke.id
                    else -> pixelErasePieces = pixelErasePieces + (stroke.id to previewPieces(stroke, pieces))
                }
            }
    }

    fun applyPanDelta(delta: Offset) {
        if (delta != Offset.Zero) pan = currentTransform.clampPan(pan + delta)
    }

    // 平移与翻页手势共用一条位移路径：横向平移触底后，剩余位移转为页面拖拽位移；
    // 缩放不大于 100% 时页面横向完全可见，横向拖动直接进入整屏翻页模式。
    // 拖拽位移存在时，反方向的位移先收回拖拽，再归还给平移。
    fun applyPanDeltaWithPageDrag(delta: Offset) {
        val panLimitX = if (currentZoom <= 100) 0f else currentTransform.panLimit.x
        val overscroll = pageDragOverscroll(delta.x, pan.x, panLimitX)
        if (overscroll != 0f) {
            pageDragSettleJob?.cancel()
            applyPanDelta(Offset(delta.x - overscroll, delta.y))
            // 右滑需要上一面存在；左滑需要下一面存在（最后一页的下一面是待添加虚拟页，
            // 虚拟页没有下一面，其上的左滑只剩带阻尼的橡皮筋反馈）。
            val hasTarget = if (overscroll < 0f) nextNeighborState != null else prevNeighborState != null
            // 没有目标面的一侧只给出带阻尼的橡皮筋反馈，松手弹回。
            val effective = if (hasTarget) overscroll else overscroll * PAGE_DRAG_DEAD_END_FACTOR
            val maxDrag = canvasSize.width.toFloat()
            pageDragPx = (pageDragPx + effective).coerceIn(-maxDrag, maxDrag)
            return
        }
        applyPanDelta(delta)
    }

    // eraserSize 必须在 key 中：pointerInput 不重启时协程闭包会一直捕获旧尺寸，
    // 导致橡皮光标显示新大小而实际擦除仍按旧大小计算。
    // pageId 也在 key 中：切页重置会话状态后，闭包必须重新捕获新的状态对象。
    val gestureModifier = Modifier.pointerInput(surface, tool, eraserMode, eraserSize, inputMode, sideButtonAction) {
        awaitEachGesture {
            var previousCentroid: Offset? = null
            var previousSpan = 0f
            var gestureZoom = currentZoom
            var sideActionHandled = false
            var zoomGestureDetected = false
            val erasePath = mutableListOf<LogicalPoint>()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }

                val motionEvent = event.motionEvent
                val sidePressed = motionEvent?.buttonState?.and(
                    MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY,
                ) != 0
                val sideForEraser = sidePressed && sideButtonAction == SideButtonAction.TEMPORARY_ERASER
                // awaitEachGesture 的手势循环同样会收到悬停事件；侧键动作只在笔尖接触时分发，
                // 悬停阶段只同步临时橡皮高亮，避免系统返回打断事件流时残留的键位翻转工具。
                if (sidePressed && pressed.isNotEmpty() && !sideActionHandled) {
                    when (sideButtonAction) {
                        SideButtonAction.TEMPORARY_ERASER -> Unit
                        SideButtonAction.TOGGLE_ERASER -> toggleEraserCallback()
                        SideButtonAction.UNDO -> undoCallback()
                    }
                    sideActionHandled = true
                }

                if (pressed.size >= 2) {
                    event.changes.forEach { it.consume() }
                    // 画布锁定或待添加虚拟页：双指事件只吞掉，缩放与平移全部冻结。
                    if (currentZoomLocked || !inkingEnabledState) continue
                    val centroid = pressed.map { it.position }.reduce(Offset::plus) / pressed.size.toFloat()
                    val span = hypot(
                        pressed[0].position.x - pressed[1].position.x,
                        pressed[0].position.y - pressed[1].position.y,
                    )
                    previousCentroid?.let { applyPanDeltaWithPageDrag(centroid - it) }
                    if (previousSpan > 0f && span > 0f) {
                        val scaleChange = span / previousSpan
                        val nextZoom = (gestureZoom * scaleChange).roundToInt()
                            .coerceIn(MIN_ZOOM_PERCENT, MAX_ZOOM_PERCENT)
                        if (nextZoom != gestureZoom || abs(scaleChange - 1f) >= ZOOM_GESTURE_SCALE_THRESHOLD) {
                            if (!zoomGestureDetected) {
                                zoomGestureDetected = true
                                zoomGestureActive = true
                                zoomControlsVisible = true
                            }
                        }
                        if (nextZoom != gestureZoom) {
                            gestureZoom = nextZoom
                            zoomCallback(gestureZoom)
                        }
                    }
                    previousCentroid = centroid
                    previousSpan = span
                } else {
                    previousCentroid = null
                    previousSpan = 0f
                    val change = pressed.firstOrNull() ?: event.changes.firstOrNull()
                    if (change != null) {
                        val contactActive = isPointerContactActive(change.pressed, change.previousPressed)
                        val erasing = tool == EditorTool.ERASER || change.type == PointerType.Eraser || sideForEraser
                        val navigationPointer = isSingleFingerNavigationPointer(
                            inputMode = inputMode,
                            pointerType = change.type,
                            rawToolType = motionEvent?.takeIf { it.pointerCount > 0 }?.getToolType(0),
                        )
                        when {
                            sidePressed && sideButtonAction != SideButtonAction.TEMPORARY_ERASER -> {
                                change.consume()
                            }
                        // 导航指针只在接触期间平移：悬停事件（如鼠标划过）不得带动页面。
                        navigationPointer && contactActive -> {
                            val delta = change.positionChange()
                            change.consume()
                            if (!currentZoomLocked) applyPanDeltaWithPageDrag(delta)
                        }
                            erasing && contactActive && inkingEnabledState -> {
                                change.consume()
                                if (eraserMode == EraserMode.PIXEL) {
                                    pixelEraseCursor = change.position
                                    currentTransform.toLogical(change.position)?.let { logical ->
                                        // 路径按步长采样：线段命中覆盖点间区域，精度无损；
                                        // 采样同时限定路径长度，长距离擦除不会逐事件全量重算。
                                        val step = (eraserSize / 4f).coerceAtLeast(ERASER_PATH_MIN_STEP.toFloat())
                                        val last = erasePath.lastOrNull()
                                        if (last == null ||
                                            abs(logical.x - last.x) >= step || abs(logical.y - last.y) >= step
                                        ) {
                                            erasePath += logical
                                            erasePixelsAlongPath(erasePath)
                                        }
                                    }
                                } else {
                                    currentTransform.toLogical(change.position)?.let(::eraseAt)
                                }
                            }
                        }
                    }
                }
            } while (event.changes.any { it.pressed })

            if (zoomGestureDetected) zoomGestureActive = false
            pixelEraseCursor = null

            // 手势收尾：达到阈值先把当前展示面连同相邻面预览顺滑推到位再回调切面，否则弹回。
            // 方向语义：手指右滑回到上一面，手指左滑进入下一面（最后一页的下一面即待添加虚拟页）。
            // 所有展示面切换后都渲染在 translate 0，因此回调后直接归位拖拽位移。
            val dragAtRelease = pageDragPx
            if (abs(dragAtRelease) > 0.5f) {
                val direction = if (dragAtRelease > 0f) SWIPE_NAVIGATE_PREV else SWIPE_NAVIGATE_NEXT
                val hasTarget = if (direction == SWIPE_NAVIGATE_PREV) {
                    prevNeighborState != null
                } else {
                    nextNeighborState != null
                }
                val committed = abs(dragAtRelease) >= pageSwipeThresholdPx && hasTarget
                if (committed) {
                    // 受限的指针作用域内不能挂起，滑出与回弹动画都交给组合协程。
                    // 多滑出一个页间隙：相邻面预览恰好停在 translate 0，与切面后画布的首帧
                    // 逐像素衔接，切换瞬间不留任何过渡动画。
                    pageDragSettleJob = scope.launch {
                        animate(
                            initialValue = dragAtRelease,
                            targetValue = sign(dragAtRelease) *
                                (canvasSize.width + NEIGHBOR_PAGE_GAP_DP.dp.toPx()),
                            animationSpec = tween(PAGE_SWIPE_SETTLE_MILLIS),
                        ) { value, _ -> pageDragPx = value }
                        swipeNavigateCallback(direction)
                        pageDragPx = 0f
                    }
                } else {
                    pageDragSettleJob = scope.launch {
                        animate(
                            initialValue = dragAtRelease,
                            targetValue = 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                        ) { value, _ -> pageDragPx = value }
                    }
                }
            }

            if (erasedIds.isNotEmpty()) {
                val completed = erasedIds
                erasedIds = emptySet()
                pendingErasedIds = pendingErasedIds + completed
                eraseCallback(completed) {
                    pendingErasedIds = pendingErasedIds - completed
                }
            }

            val pixelRemovedIds = pixelErasePieces.keys + pixelErasedIds
            if (pixelRemovedIds.isNotEmpty()) {
                val piecesByOriginalId = pixelErasePieces.mapValues { (_, pieces) ->
                    pieces.map(StrokeElement::samples)
                }
                pendingPixelPieces = pixelErasePieces
                pendingPixelErasedIds = pixelErasedIds
                pixelErasePieces = emptyMap()
                pixelErasedIds = emptySet()
                erasePixelsCallback(pixelRemovedIds, piecesByOriginalId) {
                    pendingPixelPieces = emptyMap()
                    pendingPixelErasedIds = emptySet()
                }
            }
        }
    }

    // 侧键在悬停阶段按下时也要同步临时橡皮状态，因此用常驻事件循环监听包含 hover 在内的
    // 全部指针事件；事件流被系统返回等交互终结后释放事件可能不再送达，终结事件强制复位。
    // 覆盖层接管交互期间不复位也不高亮，避免隐藏画布的工具栏状态被悬停事件翻转。
    val sideButtonWatcherModifier = Modifier.pointerInput(sideButtonAction) {
        awaitPointerEventScope {
            var temporaryEraserActive = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val motionEvent = event.motionEvent
                val sideForEraser = inkingEnabledState && isTemporaryEraserEvent(
                    sideButtonAction = sideButtonAction,
                    actionMasked = motionEvent?.actionMasked,
                    buttonState = motionEvent?.buttonState ?: 0,
                )
                if (sideForEraser != temporaryEraserActive) {
                    temporaryEraserActive = sideForEraser
                    temporaryEraserCallback(sideForEraser)
                }
            }
        }
    }

    val inkRenderer = remember { InkDocumentRenderer() }
    val hiddenOriginalIds = erasedIds + pendingErasedIds +
        pixelErasePieces.keys + pixelErasedIds + pendingPixelPieces.keys + pendingPixelErasedIds
    val displayStrokes = remember(strokes, hiddenOriginalIds, pixelErasePieces, pendingPixelPieces) {
        strokes.filterNot { it.id in hiddenOriginalIds } +
            (pixelErasePieces.values + pendingPixelPieces.values).flatten()
    }
    val preparedStrokes = remember(displayStrokes) {
        runCatching { inkRenderer.prepare(displayStrokes) }
            .onFailure { error -> Log.e(LOG_TAG, "Unable to prepare persisted Ink stroke", error) }
            .getOrDefault(emptyList())
    }
    // 相邻真实页的静态渲染预置：翻页拖拽时直接绘制；渲染层按笔画 id 缓存，重复 prepare 开销很小。
    val prevPreparedStrokes = remember(prevNeighbor) {
        runCatching {
            inkRenderer.prepare(
                (prevNeighbor as? EditorNeighbor.Page)?.content?.elements
                    .orEmpty().filterIsInstance<StrokeElement>(),
            )
        }.getOrDefault(emptyList())
    }
    val nextPreparedStrokes = remember(nextNeighbor) {
        runCatching {
            inkRenderer.prepare(
                (nextNeighbor as? EditorNeighbor.Page)?.content?.elements
                    .orEmpty().filterIsInstance<StrokeElement>(),
            )
        }.getOrDefault(emptyList())
    }
    val wetBrush = remember(activeBrushId, activeColor, activeWidth, tool, pressureSensitivity, inkingEnabled) {
        if (tool == EditorTool.PEN && inkingEnabled) {
            createInkBrush(
                brushId = activeBrushId,
                argb = activeColor,
                size = activeWidth.toFloat(),
                pressureSensitivity = pressureSensitivity,
            )
        } else {
            null
        }
    }
    val currentWetBrush by rememberUpdatedState(wetBrush)
    // 透明背景的页面以半透明的主题底色显示：跟随主题色相的同时透出桌面，比实底更通透；
    // 导出仍为真透明，由 PageRenderEngine 负责。
    val transparentPageColor = MaterialTheme.colorScheme.surface.copy(alpha = TRANSPARENT_PAGE_FILL_ALPHA)
    val maskPath = remember(canvasSize, transform.pageRect) {
        Path().apply {
            val canvasWidth = canvasSize.width.toFloat()
            val canvasHeight = canvasSize.height.toFloat()
            val pageLeft = transform.pageRect.left.coerceIn(0f, canvasWidth)
            val pageTop = transform.pageRect.top.coerceIn(0f, canvasHeight)
            val pageRight = transform.pageRect.right.coerceIn(0f, canvasWidth)
            val pageBottom = transform.pageRect.bottom.coerceIn(0f, canvasHeight)

            fun addMaskRect(left: Float, top: Float, right: Float, bottom: Float) {
                if (right > left && bottom > top) addRect(Rect(left, top, right, bottom))
            }

            addMaskRect(0f, 0f, canvasWidth, pageTop)
            addMaskRect(0f, pageBottom, canvasWidth, canvasHeight)
            addMaskRect(0f, pageTop, pageLeft, pageBottom)
            addMaskRect(pageRight, pageTop, canvasWidth, pageBottom)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LocalHandwriteColors.current.canvasDesk)
            .onSizeChanged { canvasSize = it },
    ) {
        // 待添加虚拟页露出阶段：内容铺在页面图层之下，随页面滑出逐渐露出；此阶段由画布
        // 手势接管，内容无需交互。
        if (pendingAddUnderlayVisible) {
            pendingAddPageContent()
        }
        Box(
            Modifier
                .fillMaxSize()
                .then(gestureModifier)
                .then(sideButtonWatcherModifier),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val drag = pageDragPx
                val slideStep = canvasSize.width + NEIGHBOR_PAGE_GAP_DP.dp.toPx()
                // 拖拽中在移动方向的一侧渲染相邻真实页：右滑上一页自左侧跟入，左滑下一页自右侧
                // 跟入，与松手后的切面位置衔接，形成整屏翻页的连续预览。相邻面是待添加虚拟页时
                // 不在此绘制：其内容由槽位图层铺在页面之下呈现。
                val neighbor = when {
                    drag > 0.5f -> prevNeighborState
                    drag < -0.5f -> nextNeighborState
                    else -> null
                }
                if (neighbor is EditorNeighbor.Page) {
                    val content = neighbor.content
                    val neighborOffset = drag - sign(drag) * slideStep
                    // 相邻页按切换后的最终布局渲染（自身页面尺寸、无平移），松手落位与
                    // 切面后画布的首帧逐像素一致；沿用当前页变换会在缩放平移时错位。
                    val neighborTransform = CanvasPageTransform.create(
                        canvasSize,
                        content.page.size,
                        currentZoom / 100f,
                        Offset.Zero,
                    )
                    withTransform({ translate(neighborOffset, 0f) }) {
                        drawPaperLayer(
                            renderer = inkRenderer,
                            rect = neighborTransform.pageRect,
                            background = content.page.background,
                            scale = neighborTransform.scale,
                            transparentFillColor = transparentPageColor,
                            assetImage = null,
                            preparedStrokes = if (drag > 0.5f) prevPreparedStrokes else nextPreparedStrokes,
                        )
                    }
                }
                // 当前展示面是待添加虚拟页时没有纸面图层：内容完全交给槽位图层呈现。
                if (surface !is EditorSurface.PendingAdd) {
                    withTransform({ translate(drag, 0f) }) {
                        drawPaperLayer(
                            renderer = inkRenderer,
                            rect = transform.pageRect,
                            background = background,
                            scale = transform.scale,
                            transparentFillColor = transparentPageColor,
                            assetImage = backgroundImage,
                            preparedStrokes = preparedStrokes,
                        )
                    }
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val page = transform.pageRect
                clipRect(page.left, page.top, page.right, page.bottom) {
                    inkRenderer.drawInProgress(
                        this,
                        pendingStrokes,
                        transform.scale,
                        page.left,
                        page.top,
                    )
                }
            }
            InProgressStrokes(
                defaultBrush = wetBrush,
                nextBrush = { currentWetBrush },
                pointerEventToWorldTransform = pointerEventToLogicalTransform,
                maskPath = maskPath,
                onStrokesFinished = { completed ->
                    pendingStrokes = pendingStrokes + completed
                    val clippedStrokes = completed.flatMap { stroke ->
                        val samples = buildList {
                            val scratch = StrokeInput()
                            repeat(stroke.inputs.size) { index ->
                                stroke.inputs.populate(index, scratch)
                                val tilt = scratch.toTiltVector()
                                add(
                                    StrokeSample(
                                        point = LogicalPoint(
                                            scratch.x.roundToInt(),
                                            scratch.y.roundToInt(),
                                        ),
                                        pressure = if (scratch.hasPressure) {
                                            (scratch.pressure * StrokeSample.MAX_PRESSURE).roundToInt()
                                                .coerceIn(0, StrokeSample.MAX_PRESSURE)
                                        } else {
                                            StrokeSample.MAX_PRESSURE
                                        },
                                        elapsedMillis = scratch.elapsedTimeMillis
                                            .coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                                        tiltX = tilt?.first,
                                        tiltY = tilt?.second,
                                    ),
                                )
                            }
                        }
                        clipStrokeToPage(samples, pageSize)
                    }
                    val removeCompletedFromPending = {
                        pendingStrokes = pendingStrokes.filterNot { pending ->
                            completed.any { it === pending }
                        }
                    }
                    if (clippedStrokes.isNotEmpty()) {
                        strokesCallback(clippedStrokes, removeCompletedFromPending)
                    } else {
                        removeCompletedFromPending()
                    }
                },
            )
        // 像素橡皮光标：圆盘直径即逻辑橡皮大小，随缩放同步，便于对准擦除轨迹。
        val eraserCursorColor = MaterialTheme.colorScheme.primary
        Canvas(Modifier.fillMaxSize()) {
            pixelEraseCursor?.let { position ->
                val radius = (eraserSize / 2f) * transform.scale
                drawCircle(
                    color = eraserCursorColor.copy(alpha = ERASER_CURSOR_FILL_ALPHA),
                    radius = radius,
                    center = position,
                )
                drawCircle(
                    color = eraserCursorColor,
                    radius = radius,
                    center = position,
                    style = DrawStroke(
                        width = ERASER_CURSOR_BORDER_WIDTH_DP.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                    ),
                )
            }
        }
        }
        ZoomControlsOverlay(
            visible = zoomControlsVisible && !zoomLocked,
            zoomPercent = zoomPercent,
            onZoomOut = {
                keepZoomControlsVisible()
                zoomCallback((currentZoom - ZOOM_BUTTON_STEP).coerceAtLeast(MIN_ZOOM_PERCENT))
            },
            onZoomIn = {
                keepZoomControlsVisible()
                zoomCallback((currentZoom + ZOOM_BUTTON_STEP).coerceAtMost(MAX_ZOOM_PERCENT))
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp),
        )

        // 待添加虚拟页停留阶段：内容铺在手势层之上供按钮点击。此阶段没有纸面图层，
        // 视觉与露出时一致；与露出层为同一组合、同帧切换，画面无缝。
        if (pendingAddOverlayVisible) {
            pendingAddPageContent()
        }
    }
}

private const val LOG_TAG = "HandwriteCanvas"

// 缩放范围由手势与工具栏按钮共用；编辑器 ViewModel 的 setZoom 也引用这两个常量。
internal const val MIN_ZOOM_PERCENT = 75
internal const val MAX_ZOOM_PERCENT = 400

// 透明页面填充的主题色不透明度：过低会与桌面混淆，过高则失去通透感。
internal const val TRANSPARENT_PAGE_FILL_ALPHA = 0.45f

// 像素橡皮大小为逻辑直径（文档坐标，随缩放同步）；下限覆盖常用笔宽，上限到笔画橡皮半径的两倍。
internal const val MIN_ERASER_SIZE = 200
internal const val MAX_ERASER_SIZE = 2_000
internal const val DEFAULT_ERASER_SIZE = 600

// 像素橡皮光标的填充不透明度与描边宽度：填充保证覆盖范围可感知，虚线边呼应选区类视觉。
private const val ERASER_CURSOR_FILL_ALPHA = 0.12f
private const val ERASER_CURSOR_BORDER_WIDTH_DP = 1.5f

// 擦除路径的最小采样步长（逻辑单位）：路径按不小于该值的间距记录，限制路径规模。
internal const val ERASER_PATH_MIN_STEP = 32

// 向左右滑翻页：达到阈值松手翻页（最后一页右滑进入待添加页），拖拽方向以像素计（向右为正）。
internal const val PAGE_SWIPE_COMMIT_THRESHOLD_DP = 96
internal const val PAGE_SWIPE_SETTLE_MILLIS = 160

// 没有目标页（首页继续左滑）时的橡皮筋阻尼系数。
internal const val PAGE_DRAG_DEAD_END_FACTOR = 0.25f

// 翻页手势回调方向常量：待添加虚拟页作为普通翻页目标参与 NEXT。
internal const val SWIPE_NAVIGATE_PREV = -1
internal const val SWIPE_NAVIGATE_NEXT = 1

// 横向平移触底后继续拖动的位移量（像素，向右为正），即页面拖拽位移的增量。
internal fun pageDragOverscroll(deltaX: Float, panX: Float, panLimitX: Float): Float = when {
    deltaX > 0f -> maxOf(deltaX - (panLimitX - panX).coerceAtLeast(0f), 0f)
    deltaX < 0f -> minOf(deltaX + (panLimitX + panX).coerceAtLeast(0f), 0f)
    else -> 0f
}

// 翻页拖拽时相邻页与当前页的间距。
internal const val NEIGHBOR_PAGE_GAP_DP = 14

// 纸张层绘制：底色 + 阴影 + 花纹/图片背景 + 笔迹；供当前页与相邻页预览复用。
private fun DrawScope.drawPaperLayer(
    renderer: InkDocumentRenderer,
    rect: Rect,
    background: PageBackground,
    scale: Float,
    transparentFillColor: Color,
    assetImage: ImageBitmap?,
    preparedStrokes: List<Stroke>,
) {
    drawRect(background.baseColor(transparentFillColor), topLeft = rect.topLeft, size = rect.size)
    // 页底 3px 阴影：整页阴影会透过半透明填充使页面发灰，只保留底边一条。
    drawRect(
        Color(0x26000000),
        topLeft = Offset(rect.left, rect.bottom),
        size = Size(rect.width, 3f),
    )
    clipRect(rect.left, rect.top, rect.right, rect.bottom) {
        when (background) {
            is PageBackground.Pattern -> drawPattern(background, rect, scale)
            is PageBackground.Asset -> assetImage?.let { image ->
                val assetScale = background.transform.scalePermille / 1_000f
                val translation = Offset(
                    background.transform.translation.x * scale,
                    background.transform.translation.y * scale,
                )
                withTransform({
                    translate(translation.x, translation.y)
                    rotate(background.transform.rotationMilliDegrees / 1_000f, rect.center)
                    scale(assetScale, assetScale, rect.center)
                }) {
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                        dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
                    )
                }
            }
            else -> Unit
        }
        renderer.draw(this, preparedStrokes, scale, rect.left, rect.top)
    }
}

private const val ZOOM_BUTTON_STEP = 25
private const val ZOOM_GESTURE_SCALE_THRESHOLD = 0.005f
private const val ZOOM_CONTROLS_HIDE_DELAY_MILLIS = 1_800L

@Composable
private fun ZoomControlsOverlay(
    visible: Boolean,
    zoomPercent: Int,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 3.dp,
            shadowElevation = 2.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.height(48.dp),
            ) {
                IconButton(onClick = onZoomOut, enabled = zoomPercent > MIN_ZOOM_PERCENT) {
                    Icon(Icons.Default.Remove, contentDescription = "缩小页面")
                }
                Text(
                    text = "$zoomPercent%",
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(64.dp),
                )
                IconButton(onClick = onZoomIn, enabled = zoomPercent < MAX_ZOOM_PERCENT) {
                    Icon(Icons.Default.Add, contentDescription = "放大页面")
                }
            }
        }
    }
}

internal fun isSingleFingerNavigationPointer(
    inputMode: InputMode,
    pointerType: PointerType,
    rawToolType: Int?,
): Boolean = inputMode == InputMode.STYLUS &&
    pointerType != PointerType.Stylus &&
    pointerType != PointerType.Eraser &&
    rawToolType != MotionEvent.TOOL_TYPE_STYLUS &&
    rawToolType != MotionEvent.TOOL_TYPE_ERASER

internal fun isPointerContactActive(pressed: Boolean, previousPressed: Boolean): Boolean =
    pressed || previousPressed

// 像素橡皮的切分规则：样本点到擦除路径（点/线段）的距离不超过命中半径（橡皮半径 + 半个
// 笔宽）即被擦除；存留样本按原顺序分段，不足两个点的片段无法构成笔画，直接丢弃。
internal const val MIN_PIECE_SAMPLES = 2

internal fun splitStrokeByErasePath(
    samples: List<StrokeSample>,
    erasePath: List<LogicalPoint>,
    hitRadius: Float,
): List<List<StrokeSample>> {
    if (samples.isEmpty()) return emptyList()
    if (erasePath.isEmpty()) return listOf(samples)
    val hitRadiusSquared = hitRadius * hitRadius
    val erased = BooleanArray(samples.size)
    var anyErased = false
    samples.forEachIndexed { index, sample ->
        val x = sample.point.x.toFloat()
        val y = sample.point.y.toFloat()
        var previousX = Float.NaN
        var previousY = Float.NaN
        for (point in erasePath) {
            val pointX = point.x.toFloat()
            val pointY = point.y.toFloat()
            val hit = if (previousX.isNaN()) {
                squaredDistance(x, y, pointX, pointY) <= hitRadiusSquared
            } else {
                squaredSegmentDistance(x, y, previousX, previousY, pointX, pointY) <= hitRadiusSquared
            }
            if (hit) {
                erased[index] = true
                anyErased = true
                break
            }
            previousX = pointX
            previousY = pointY
        }
    }
    if (!anyErased) return listOf(samples)
    val pieces = mutableListOf<List<StrokeSample>>()
    var current = mutableListOf<StrokeSample>()
    samples.forEachIndexed { index, sample ->
        if (erased[index]) {
            if (current.size >= MIN_PIECE_SAMPLES) pieces += current
            current = mutableListOf()
        } else {
            current += sample
        }
    }
    if (current.size >= MIN_PIECE_SAMPLES) pieces += current
    return pieces
}

private fun squaredDistance(x: Float, y: Float, otherX: Float, otherY: Float): Float {
    val dx = x - otherX
    val dy = y - otherY
    return dx * dx + dy * dy
}

private fun squaredSegmentDistance(
    x: Float,
    y: Float,
    ax: Float,
    ay: Float,
    bx: Float,
    by: Float,
): Float {
    val abX = bx - ax
    val abY = by - ay
    val apX = x - ax
    val apY = y - ay
    val lengthSquared = abX * abX + abY * abY
    val t = if (lengthSquared == 0f) 0f else ((apX * abX + apY * abY) / lengthSquared).coerceIn(0f, 1f)
    val dx = apX - abX * t
    val dy = apY - abY * t
    return dx * dx + dy * dy
}

// 临时橡皮只跟随手写笔侧键位；系统返回等交互会终结指针事件流，侧键释放事件可能不再
// 送达，悬停退出与取消事件必须强制复位，避免工具栏停留在橡皮高亮上。
internal fun isTemporaryEraserEvent(
    sideButtonAction: SideButtonAction,
    actionMasked: Int?,
    buttonState: Int,
): Boolean {
    if (sideButtonAction != SideButtonAction.TEMPORARY_ERASER) return false
    if (actionMasked == null || actionMasked == MotionEvent.ACTION_HOVER_EXIT ||
        actionMasked == MotionEvent.ACTION_CANCEL
    ) {
        return false
    }
    return buttonState.and(
        MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY,
    ) != 0
}

internal fun clipStrokeToPage(
    samples: List<StrokeSample>,
    pageSize: LogicalSize,
): List<List<StrokeSample>> {
    if (samples.isEmpty()) return emptyList()
    if (samples.size == 1) return if (pageSize.contains(samples.single().point)) listOf(samples) else emptyList()

    val clippedStrokes = mutableListOf<List<StrokeSample>>()
    val activeStroke = mutableListOf<StrokeSample>()

    fun append(sample: StrokeSample) {
        if (activeStroke.lastOrNull()?.point != sample.point) activeStroke += sample
    }

    fun finishSegment() {
        if (activeStroke.size >= 2) clippedStrokes += activeStroke.toList()
        activeStroke.clear()
    }

    samples.zipWithNext().forEach { (first, second) ->
        val clipped = clipSegmentToPage(first, second, pageSize)
        if (clipped == null) {
            finishSegment()
        } else {
            append(clipped.first)
            append(clipped.second)
            if (clipped.secondParameter < 1.0) finishSegment()
        }
    }
    finishSegment()
    return clippedStrokes
}

private data class ClippedSegment(
    val first: StrokeSample,
    val second: StrokeSample,
    val secondParameter: Double,
)

private fun clipSegmentToPage(
    first: StrokeSample,
    second: StrokeSample,
    pageSize: LogicalSize,
): ClippedSegment? {
    val x0 = first.point.x.toDouble()
    val y0 = first.point.y.toDouble()
    val dx = second.point.x - first.point.x.toDouble()
    val dy = second.point.y - first.point.y.toDouble()
    val boundaries = listOf(
        -dx to x0,
        dx to pageSize.width - x0,
        -dy to y0,
        dy to pageSize.height - y0,
    )
    var entry = 0.0
    var exit = 1.0
    boundaries.forEach { (delta, distance) ->
        if (delta == 0.0) {
            if (distance < 0.0) return null
        } else {
            val parameter = distance / delta
            if (delta < 0.0) {
                entry = maxOf(entry, parameter)
            } else {
                exit = minOf(exit, parameter)
            }
        }
    }
    if (entry > exit || exit < 0.0 || entry > 1.0) return null
    val firstParameter = entry.coerceIn(0.0, 1.0)
    val secondParameter = exit.coerceIn(0.0, 1.0)
    return ClippedSegment(
        first = first.interpolate(second, firstParameter, pageSize),
        second = first.interpolate(second, secondParameter, pageSize),
        secondParameter = secondParameter,
    )
}

private fun StrokeSample.interpolate(
    other: StrokeSample,
    parameter: Double,
    pageSize: LogicalSize,
): StrokeSample = StrokeSample(
    point = LogicalPoint(
        (point.x + (other.point.x - point.x) * parameter).roundToInt().coerceIn(0, pageSize.width),
        (point.y + (other.point.y - point.y) * parameter).roundToInt().coerceIn(0, pageSize.height),
    ),
    pressure = (pressure + (other.pressure - pressure) * parameter).roundToInt()
        .coerceIn(0, StrokeSample.MAX_PRESSURE),
    elapsedMillis = (elapsedMillis + (other.elapsedMillis - elapsedMillis) * parameter).roundToInt()
        .coerceAtLeast(0),
    tiltX = interpolateTilt(tiltX, other.tiltX, parameter),
    tiltY = interpolateTilt(tiltY, other.tiltY, parameter),
)

private fun interpolateTilt(first: Int?, second: Int?, parameter: Double): Int? = when {
    first == null || second == null -> if (parameter < 0.5) first else second
    else -> (first + (second - first) * parameter).roundToInt()
}

internal data class CanvasPageTransform(
    val pageSize: LogicalSize,
    val scale: Float,
    val pageRect: Rect,
    val panLimit: Offset,
) {
    fun clampPan(pan: Offset): Offset = Offset(
        pan.x.coerceIn(-panLimit.x, panLimit.x),
        pan.y.coerceIn(-panLimit.y, panLimit.y),
    )

    fun toLogical(point: Offset): LogicalPoint? {
        if (!pageRect.contains(point)) return null
        return toLogicalClamped(point)
    }

    fun toLogicalClamped(point: Offset): LogicalPoint = LogicalPoint(
        ((point.x - pageRect.left) / scale).roundToInt().coerceIn(0, pageSize.width),
        ((point.y - pageRect.top) / scale).roundToInt().coerceIn(0, pageSize.height),
    )

    fun pointerEventToLogicalTransform(): Matrix = Matrix().apply {
        this[0, 0] = 1f / scale
        this[1, 1] = 1f / scale
        this[3, 0] = -pageRect.left / scale
        this[3, 1] = -pageRect.top / scale
    }

    companion object {
        fun create(canvas: IntSize, page: LogicalSize, zoom: Float, pan: Offset): CanvasPageTransform {
            if (canvas.width == 0 || canvas.height == 0) {
                return CanvasPageTransform(page, 1f, Rect.Zero, Offset.Zero)
            }
            // 不留边距：100% 时页面边缘直接贴合屏幕，锁定屏幕比例后可铺满整个绘图区。
            val fit = minOf(
                canvas.width / page.width.toFloat(),
                canvas.height / page.height.toFloat(),
            ).coerceAtLeast(0.0001f)
            val scale = fit * zoom
            val width = page.width * scale
            val height = page.height * scale
            val overflowX = ((width - canvas.width) / 2f).coerceAtLeast(0f)
            val overflowY = ((height - canvas.height) / 2f).coerceAtLeast(0f)
            val left = (canvas.width - width) / 2f + pan.x.coerceIn(-overflowX, overflowX)
            val top = (canvas.height - height) / 2f + pan.y.coerceIn(-overflowY, overflowY)
            return CanvasPageTransform(
                pageSize = page,
                scale = scale,
                pageRect = Rect(left, top, left + width, top + height),
                panLimit = Offset(overflowX, overflowY),
            )
        }
    }
}

// 透明底（含透明底叠加样式的 Pattern）在画布上以主题底色显示，与周边界面保持一致；
// 画布所见与导出不同：导出保持真透明，由 PageRenderEngine 负责。
internal fun PageBackground.baseColor(transparentFallback: Color): Color = when (this) {
    is PageBackground.Solid -> Color(argb)
    PageBackground.Transparent -> transparentFallback
    is PageBackground.Pattern -> if (baseArgb == PageBackground.TRANSPARENT) transparentFallback else Color(baseArgb)
    is PageBackground.Asset -> Color.White
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPattern(
    pattern: PageBackground.Pattern,
    page: Rect,
    scale: Float,
) {
    val color = Color(0x2F60746B)
    val step = PagePattern.LOGICAL_SPACING * scale
    var y = page.top + step
    while (y < page.bottom) {
        drawLine(color, Offset(page.left, y), Offset(page.right, y), strokeWidth = 1f)
        y += step
    }
    if (pattern.type == PatternType.GRID) {
        var x = page.left + step
        while (x < page.right) {
            drawLine(color, Offset(x, page.top), Offset(x, page.bottom), strokeWidth = 1f)
            x += step
        }
    }
}

private fun StrokeInput.toTiltVector(): Pair<Int, Int>? {
    if (!hasTilt || !hasOrientation) return null
    val magnitude = sin(tiltRadians)
    return (
        magnitude * cos(orientationRadians) * StrokeSample.MAX_TILT
    ).roundToInt().coerceIn(StrokeSample.MIN_TILT, StrokeSample.MAX_TILT) to (
        magnitude * sin(orientationRadians) * StrokeSample.MAX_TILT
    ).roundToInt().coerceIn(StrokeSample.MIN_TILT, StrokeSample.MAX_TILT)
}
