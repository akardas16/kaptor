package com.akardas.kaptor.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.akardas.kaptor.model.HttpTransaction
import com.akardas.kaptor.model.TransactionStatus
import com.akardas.kaptor.store.TransactionRepository
import com.akardas.kaptor.util.FormatUtils
import kotlinx.coroutines.launch

/**
 * Entry-point composable. Embed it anywhere in your app (e.g. behind a debug menu or shake
 * gesture) to browse captured Ktor traffic. Works identically on Android and iOS.
 */
@Composable
fun KaptorScreen(
    repository: TransactionRepository,
    modifier: Modifier = Modifier,
) {
    var selectedId by remember { mutableStateOf<Long?>(null) }
    val selected = selectedId

    if (selected == null) {
        TransactionListScreen(repository, modifier) { selectedId = it }
    } else {
        TransactionDetailScreen(
            repository = repository,
            transactionId = selected,
            modifier = modifier,
            onBack = { selectedId = null },
        )
    }
}

/** The segmented filters shown above the request list. */
private enum class TxFilter(val label: String) {
    All("All"),
    Errors("Errors"),
    Server("5xx"),
    Client("4xx"),
}

private fun TxFilter.matches(tx: HttpTransaction): Boolean {
    val code = tx.responseCode ?: 0
    return when (this) {
        TxFilter.All -> true
        TxFilter.Errors -> tx.status == TransactionStatus.Failed
        TxFilter.Server -> code in 500..599
        TxFilter.Client -> code in 400..499
    }
}

/** Colors for the surrounding chrome (screen, cards, chips) — theme-aware. */
internal class Chrome(
    val screen: Color,
    val card: Color,
    val bar: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
)

@Composable
internal fun rememberChrome(): Chrome {
    val dark = isSystemInDarkTheme()
    return if (dark) {
        Chrome(
            screen = Color(0xFF0A0A12),
            card = Color(0xFF16161F),
            bar = Color(0xFF14141C),
            accent = Color(0xFF7C6BF0),
            textPrimary = Color(0xFFF1F1F6),
            textSecondary = Color(0xFF8A8A98),
            divider = Color(0x14FFFFFF),
        )
    } else {
        Chrome(
            screen = Color(0xFFF5F5FA),
            card = Color(0xFFFFFFFF),
            bar = Color(0xFFECECF3),
            accent = Color(0xFF6650E0),
            textPrimary = Color(0xFF16161C),
            textSecondary = Color(0xFF6B6B78),
            divider = Color(0x14000000),
        )
    }
}

/** The status badge tile's content + tint for a transaction. */
internal class StatusStyle(
    val code: String,
    val label: String,
    val color: Color,
    val pending: Boolean = false,
)

@Composable
internal fun statusStyle(tx: HttpTransaction): StatusStyle {
    val dark = isSystemInDarkTheme()
    val green = if (dark) Color(0xFF5CCB7A) else Color(0xFF2E7D32)
    val blue = if (dark) Color(0xFF5AB0F0) else Color(0xFF0277BD)
    val red = if (dark) Color(0xFFFF5A5A) else Color(0xFFD50000)
    val amber = if (dark) Color(0xFFF5A623) else Color(0xFFEF8C00)
    val gray = MaterialTheme.colorScheme.onSurfaceVariant
    val code = tx.responseCode
    return when {
        tx.status == TransactionStatus.Requested -> StatusStyle("", "PENDING", gray, pending = true)
        tx.status == TransactionStatus.Failed -> StatusStyle("ERR", "FAILED", red)
        code == null -> StatusStyle("?", "UNKNOWN", gray)
        code in 200..299 -> StatusStyle("$code", "OK", green)
        code in 300..399 -> StatusStyle("$code", "REDIRECT", blue)
        code in 400..499 -> StatusStyle("$code", "ERROR", red)
        code >= 500 -> StatusStyle("$code", "SERVER", amber)
        else -> StatusStyle("$code", "STATUS", gray)
    }
}

@Composable
private fun TransactionListScreen(
    repository: TransactionRepository,
    modifier: Modifier,
    onOpen: (Long) -> Unit,
) {
    val transactions by repository.transactions.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val rerunner = LocalKaptorRequestRerunner.current
    val mockRequests = LocalKaptorMockRequests.current
    val chrome = rememberChrome()
    val listState = rememberLazyListState()
    var scrollToTopPending by remember { mutableStateOf(false) }
    var showMockSheet by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(TxFilter.All) }

    // When a rerun fires, the new capture lands at the top; animate to it once it arrives.
    val firstId = transactions.firstOrNull()?.id
    LaunchedEffect(firstId) {
        if (scrollToTopPending) {
            scrollToTopPending = false
            listState.animateScrollToItem(0)
        }
    }

    val errorCount = transactions.count { it.status == TransactionStatus.Failed }
    val serverCount = transactions.count { (it.responseCode ?: 0) in 500..599 }
    val clientCount = transactions.count { (it.responseCode ?: 0) in 400..499 }
    val visible = transactions.filter { filter.matches(it) }

    Column(modifier.fillMaxSize().background(chrome.screen).statusBarsPadding()) {
        ListHeader(
            total = transactions.size,
            chrome = chrome,
            onClear = { scope.launch { repository.clear() } },
            onAddMock = if (mockRequests.isNotEmpty()) ({ showMockSheet = true }) else null,
        )
        FilterBar(
            selected = filter,
            errorCount = errorCount,
            serverCount = serverCount,
            clientCount = clientCount,
            chrome = chrome,
            onSelect = { filter = it },
        )
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (transactions.isEmpty()) "No requests captured yet" else "Nothing matches this filter",
                    color = chrome.textSecondary,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(vertical = 6.dp),
            ) {
                items(visible, key = { it.id }) { tx ->
                    SwipeableTransactionCard(
                        tx = tx,
                        chrome = chrome,
                        onClick = { onOpen(tx.id) },
                        onDelete = { scope.launch { repository.delete(tx.id) } },
                        onRerun = rerunner?.let { r -> { scrollToTopPending = true; r.rerun(tx) } },
                    )
                }
            }
        }
    }

    if (showMockSheet) {
        MockRequestsSheet(
            requests = mockRequests,
            chrome = chrome,
            onDismiss = { showMockSheet = false },
            onRun = { req ->
                scrollToTopPending = true
                scope.launch { runCatching { req.send() } }
                showMockSheet = false
            },
        )
    }
}

@Composable
private fun ListHeader(total: Int, chrome: Chrome, onClear: () -> Unit, onAddMock: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Kaptor",
                    color = chrome.textPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(10.dp))
                CountPill(total, chrome)
            }
            Text(
                "HTTP Inspector",
                color = chrome.textSecondary,
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
            )
        }
        if (onAddMock != null) {
            HeaderIconButton(
                icon = Icons.Default.Add,
                contentDescription = "Send a mock request",
                color = chrome.accent,
                onClick = onAddMock,
            )
            Spacer(Modifier.width(10.dp))
        }
        HeaderIconButton(
            icon = Icons.Default.Delete,
            contentDescription = "Clear all",
            color = Color(0xFFFF5A5A),
            onClick = onClear,
        )
    }
}

@Composable
private fun HeaderIconButton(
    icon: ImageVector,
    contentDescription: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = color, modifier = Modifier.size(22.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MockRequestsSheet(
    requests: List<KaptorMockRequest>,
    chrome: Chrome,
    onDismiss: () -> Unit,
    onRun: (KaptorMockRequest) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = chrome.card,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        ) {
            Text("Mock requests", color = chrome.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Fire a sample request to generate captured traffic.",
                color = chrome.textSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(requests) { req ->
                    MockRequestRow(req, chrome, onClick = { onRun(req) })
                }
            }
        }
    }
}

@Composable
private fun MockRequestRow(req: KaptorMockRequest, chrome: Chrome, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(chrome.screen)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        req.method?.let {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(chrome.accent.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(it, color = chrome.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(req.title, color = chrome.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            req.subtitle?.let {
                Text(it, color = chrome.textSecondary, fontSize = 13.sp)
            }
        }
        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = chrome.accent, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun CountPill(count: Int, chrome: Chrome) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(chrome.accent.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            count.toString(),
            color = chrome.accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun FilterBar(
    selected: TxFilter,
    errorCount: Int,
    serverCount: Int,
    clientCount: Int,
    chrome: Chrome,
    onSelect: (TxFilter) -> Unit,
) {
    val red = Color(0xFFFF5A5A)
    val amber = Color(0xFFF5A623)
    val entries = listOf(
        Triple(TxFilter.All, null as Int?, null as Color?),
        Triple(TxFilter.Errors, errorCount.takeIf { it > 0 }, red),
        Triple(TxFilter.Server, serverCount.takeIf { it > 0 }, amber),
        Triple(TxFilter.Client, clientCount.takeIf { it > 0 }, amber),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(chrome.bar)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEachIndexed { i, entry ->
            val (filter, count, countColor) = entry
            if (i > 0) {
                // Hide the divider when it would touch the highlighted (pill-filled) chip.
                val touchesSelected = selected == filter || selected == entries[i - 1].first
                Box(
                    Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(if (touchesSelected) Color.Transparent else chrome.divider),
                )
            }
            FilterChip(filter, selected, count, countColor, chrome, Modifier.weight(1f), onSelect)
        }
    }
}

@Composable
private fun FilterChip(
    filter: TxFilter,
    selected: TxFilter,
    count: Int?,
    countColor: Color?,
    chrome: Chrome,
    modifier: Modifier,
    onSelect: (TxFilter) -> Unit,
) {
    val isSelected = filter == selected
    val bg = if (isSelected) chrome.accent else Color.Transparent
    val fg = if (isSelected) Color.White else chrome.textSecondary
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable { onSelect(filter) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(filter.label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (count != null && countColor != null) {
            Spacer(Modifier.width(5.dp))
            Box(
                modifier = Modifier
                    .size(19.dp)
                    .clip(CircleShape)
                    .background(countColor),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    count.toString(),
                    color = Color.White,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        lineHeightStyle = LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                    ),
                )
            }
        }
    }
}

/**
 * A request card that reveals Delete (and, when a rerunner is available, Rerun) action buttons
 * when swiped left. Rerun re-issues the request as a NEW capture; the original row is unaffected.
 */
@Composable
private fun SwipeableTransactionCard(
    tx: HttpTransaction,
    chrome: Chrome,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRerun: (() -> Unit)?,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val buttonWidth = 76.dp
    val gap = 8.dp
    val count = if (onRerun != null) 2 else 1
    val revealWidth = buttonWidth * count + gap * (count - 1) + gap
    val revealPx = with(density) { revealWidth.toPx() }
    val offsetX = remember { Animatable(0f) }

    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
        // Action buttons behind the card.
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onRerun != null) {
                SwipeActionButton(
                    label = "Rerun",
                    icon = Icons.Default.Refresh,
                    container = Color(0xFF1565C0),
                    width = buttonWidth,
                    onClick = {
                        scope.launch { offsetX.animateTo(0f) }
                        onRerun()
                    },
                )
            }
            SwipeActionButton(
                label = "Delete",
                icon = Icons.Default.Delete,
                container = Color(0xFFD32F2F),
                width = buttonWidth,
                onClick = onDelete,
            )
        }

        // Foreground card, draggable horizontally to reveal the actions.
        Box(
            Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + delta).coerceIn(-revealPx, 0f))
                        }
                    },
                    onDragStopped = {
                        scope.launch {
                            offsetX.animateTo(if (offsetX.value < -revealPx / 2) -revealPx else 0f)
                        }
                    },
                ),
        ) {
            TransactionCard(tx, chrome, onClick = {
                if (offsetX.value != 0f) scope.launch { offsetX.animateTo(0f) } else onClick()
            })
        }
    }
}

@Composable
private fun SwipeActionButton(
    label: String,
    icon: ImageVector,
    container: Color,
    width: Dp,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(width)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White)
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}

@Composable
private fun TransactionCard(tx: HttpTransaction, chrome: Chrome, onClick: () -> Unit) {
    val style = statusStyle(tx)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(chrome.card)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusBadge(style)
        Column(Modifier.weight(1f)) {
            Text(
                "${tx.method.orEmpty()} ${tx.path.orEmpty()}",
                color = chrome.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(tx.host.orEmpty(), color = chrome.textSecondary, fontSize = 13.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            val timeColor = if (tx.status == TransactionStatus.Failed) style.color else chrome.textPrimary
            tx.tookMs?.let { Text(FormatUtils.formatDuration(it), color = timeColor, fontSize = 13.sp) }
            tx.responseContentLength?.let {
                Text(FormatUtils.formatBytes(it), color = chrome.textSecondary, fontSize = 12.sp)
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = chrome.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun StatusBadge(style: StatusStyle) {
    Column(
        modifier = Modifier
            .size(width = 64.dp, height = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(style.color.copy(alpha = 0.14f))
            .border(1.dp, style.color.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (style.pending) {
            AnimatedEllipsis(style.color)
        } else {
            Text(
                style.code,
                color = style.color,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            style.label,
            color = style.color,
            fontSize = 8.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/**
 * A three-dot ellipsis whose dots pulse in a staggered wave — a live "waiting for response"
 * indicator shown in place of a static status code.
 */
@Composable
private fun AnimatedEllipsis(color: Color) {
    val transition = rememberInfiniteTransition(label = "ellipsis")
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = index * 160),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Text(
                ".",
                color = color.copy(alpha = alpha),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
