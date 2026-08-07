package com.akardas.kaptor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akardas.kaptor.model.HttpHeader
import com.akardas.kaptor.model.HttpTransaction
import com.akardas.kaptor.share.TransactionFormats
import com.akardas.kaptor.store.TransactionRepository
import com.akardas.kaptor.util.FormatUtils
import com.akardas.kaptor.util.formatEpochMillis
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign

private enum class DetailTab(val label: String) {
    Overview("Overview"),
    Request("Request"),
    Response("Response"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransactionDetailScreen(
    repository: TransactionRepository,
    transactionId: Long,
    modifier: Modifier,
    onBack: () -> Unit,
) {
    val transaction by repository.transaction(transactionId).collectAsState(initial = null)
    var tab by remember { mutableStateOf(DetailTab.Overview) }
    val shareHandler = LocalKaptorShareHandler.current
    val chrome = rememberChrome()

    Scaffold(
        modifier = modifier,
        containerColor = chrome.screen,
        topBar = {
            TopAppBar(
                title = { Text(transaction?.shortLabel ?: "Transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val tx = transaction
                    if (tx != null && shareHandler != null) {
                        ShareMenu(tx, shareHandler)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = chrome.screen,
                    titleContentColor = chrome.textPrimary,
                    navigationIconContentColor = chrome.textPrimary,
                    actionIconContentColor = chrome.textPrimary,
                ),
            )
        },
    ) { padding ->
        val tx = transaction
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = tab.ordinal,
                containerColor = chrome.screen,
                contentColor = chrome.accent,
            ) {
                DetailTab.entries.forEach { entry ->
                    Tab(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        text = { Text(entry.label) },
                        selectedContentColor = chrome.accent,
                        unselectedContentColor = chrome.textSecondary,
                    )
                }
            }
            if (tx == null) {
                Text("Loading…", Modifier.padding(16.dp))
            } else {
                when (tab) {
                    DetailTab.Overview -> OverviewTab(tx)
                    DetailTab.Request -> BodyTab(tx.requestHeaders, tx.requestBody, tx.requestContentType, tx.requestBodyIsPlainText)
                    DetailTab.Response -> BodyTab(tx.responseHeaders, tx.responseBody, tx.responseContentType, tx.responseBodyIsPlainText, searchable = true)
                }
            }
        }
    }
}

@Composable
private fun OverviewTab(tx: HttpTransaction) {
    val chrome = rememberChrome()
    Column(
        Modifier
            .fillMaxSize()
            .background(chrome.screen)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        HeroCard(tx, chrome)
        DetailCard(tx, chrome)
        ServerCard(tx, chrome)
    }
}

@Composable
private fun HeroCard(tx: HttpTransaction, chrome: Chrome) {
    val style = statusStyle(tx)
    val heroLabel = if (style.label == "OK") "SUCCESS" else style.label
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.verticalGradient(listOf(style.color.copy(alpha = 0.14f), chrome.card)))
            .border(1.dp, style.color.copy(alpha = 0.30f), RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // Big status badge.
            Column(
                modifier = Modifier
                    .size(width = 74.dp, height = 66.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(style.color.copy(alpha = 0.16f))
                    .border(1.dp, style.color.copy(alpha = 0.45f), RoundedCornerShape(14.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    style.code.ifEmpty { "···" },
                    color = style.color,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
                Text(heroLabel, color = style.color, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = chrome.textPrimary)) {
                            append(tx.method.orEmpty())
                        }
                        append("  ")
                        withStyle(SpanStyle(color = chrome.textSecondary)) {
                            append(tx.url.orEmpty())
                        }
                    },
                    fontSize = 15.sp,
                    lineHeight = 15.sp,
                )
                tx.protocol?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, color = chrome.textSecondary, fontSize = 13.sp)
                }
            }
        }
        tx.tookMs?.let { ms ->
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(style.color.copy(alpha = 0.14f))
                    .border(1.dp, style.color.copy(alpha = 0.40f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = style.color, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(5.dp))
                Text(FormatUtils.formatDuration(ms), color = style.color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun DetailCard(tx: HttpTransaction, chrome: Chrome) {
    val style = statusStyle(tx)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(chrome.card)
            .padding(vertical = 4.dp),
    ) {
        val rows = buildList {
            add(DetailEntry(Icons.Default.Link, "URL", tx.url))
            add(DetailEntry(Icons.Default.Code, "Method", tx.method))
            add(DetailEntry(
                Icons.Default.CheckCircle,
                "Status",
                (tx.responseCode?.toString() ?: tx.statusLabel) + (tx.responseMessage?.let { " $it" } ?: ""),
                valueColor = style.color,
            ))
            add(DetailEntry(Icons.Default.Layers, "Protocol", tx.protocol))
            add(DetailEntry(Icons.Default.Schedule, "Request time", tx.requestDate?.let { formatEpochMillis(it) }))
            add(DetailEntry(Icons.Default.Schedule, "Response time", tx.responseDate?.let { formatEpochMillis(it) }))
            add(DetailEntry(Icons.Default.Timer, "Duration", tx.tookMs?.let { FormatUtils.formatDuration(it) }))
            add(DetailEntry(Icons.Default.Download, "Request size", tx.requestContentLength?.let { FormatUtils.formatBytes(it) }))
            add(DetailEntry(Icons.Default.Upload, "Response size", tx.responseContentLength?.let { FormatUtils.formatBytes(it) }))
            tx.error?.let { add(DetailEntry(Icons.Default.Code, "Error", it, valueColor = style.color)) }
        }.filter { it.value != null }

        rows.forEachIndexed { i, entry ->
            if (i > 0) {
                Box(Modifier.fillMaxWidth().padding(start = 62.dp).height(1.dp).background(chrome.divider))
            }
            DetailRow(entry, chrome)
        }
    }
}

private class DetailEntry(
    val icon: ImageVector,
    val label: String,
    val value: String?,
    val valueColor: Color? = null,
)

@Composable
private fun DetailRow(entry: DetailEntry, chrome: Chrome) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(chrome.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(entry.icon, contentDescription = null, tint = chrome.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(entry.label, color = chrome.textSecondary, fontSize = 14.sp, lineHeight = 14.sp, modifier = Modifier.width(96.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            entry.value.orEmpty(),
            color = entry.valueColor ?: chrome.textPrimary,
            fontSize = 14.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ServerCard(tx: HttpTransaction, chrome: Chrome) {
    val software = tx.responseHeaders.firstOrNull { it.name.equals("Server", ignoreCase = true) }?.value
    val ipHeaders = setOf("x-server-ip", "x-real-ip", "x-forwarded-for")
    val ip = tx.responseHeaders.firstOrNull { it.name.lowercase() in ipHeaders }?.value
    if (software == null && ip == null) return

    val dark = isSystemInDarkTheme()
    val green = if (dark) Color(0xFF5CCB7A) else Color(0xFF2E7D32)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(chrome.card)
            .border(1.dp, green.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(34.dp).clip(CircleShape).background(green.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = green, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("Server", color = chrome.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        ip?.let {
            Spacer(Modifier.height(12.dp))
            ServerLine("IP Address", it, chrome)
        }
        software?.let {
            Spacer(Modifier.height(10.dp))
            ServerLine("Software", it, chrome)
        }
    }
}

@Composable
private fun ServerLine(label: String, value: String, chrome: Chrome) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = chrome.textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = chrome.textPrimary, fontSize = 14.sp, textAlign = TextAlign.End)
    }
}

@Composable
private fun BodyTab(
    headers: List<HttpHeader>,
    body: String?,
    contentType: String?,
    isPlainText: Boolean,
    searchable: Boolean = false,
) {
    val chrome = rememberChrome()
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var query by remember { mutableStateOf("") }

    val isJson = FormatUtils.isJson(contentType)
    // The exact string the body is rendered from — match offsets index into this.
    val displayText = remember(body, isJson) {
        val b = body ?: ""
        if (isJson) FormatUtils.formatJson(b) else b
    }
    val matches = remember(displayText, query) { searchMatches(displayText, query) }
    var current by remember(matches) { mutableStateOf(0) }
    val activeStart = matches.getOrNull(current) ?: -1

    Column(Modifier.fillMaxSize().background(chrome.screen)) {
        // Pinned search bar — stays put while the body scrolls, so match nav is always reachable.
        if (searchable && !body.isNullOrEmpty()) {
            SearchBar(
                query = query,
                matchCount = matches.size,
                current = current,
                chrome = chrome,
                onQuery = { query = it },
                onClear = { query = ""; focusManager.clearFocus() },
                onPrev = { if (matches.isNotEmpty()) { current = (current - 1 + matches.size) % matches.size; focusManager.clearFocus() } },
                onNext = { if (matches.isNotEmpty()) { current = (current + 1) % matches.size; focusManager.clearFocus() } },
            )
        }
        Column(
            Modifier
                .onGloballyPositioned { rootCoords = it }
                .fillMaxWidth()
                .weight(1f)
                // Tap anywhere outside the search field to drop focus and dismiss the keyboard.
                .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (headers.isNotEmpty()) {
                HeadersCard(headers, chrome)
            }
            BodyCard(body, contentType, isPlainText, isJson, query, chrome, activeStart, scrollState, rootCoords)
        }
    }
}

@Composable
private fun SearchBar(
    query: String,
    matchCount: Int,
    current: Int,
    chrome: Chrome,
    onQuery: (String) -> Unit,
    onClear: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {
        // A compact custom pill (BasicTextField) — shorter than the 56dp OutlinedTextField.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(CircleShape)
                .background(chrome.card)
                .border(1.dp, chrome.divider, CircleShape)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = chrome.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text("Search response", color = chrome.textSecondary, fontSize = 14.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(color = chrome.textPrimary, fontSize = 14.sp),
                    cursorBrush = SolidColor(chrome.accent),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Clear search",
                    tint = chrome.textSecondary,
                    modifier = Modifier.size(18.dp).clickable(onClick = onClear),
                )
            }
        }
        if (query.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (matchCount == 0) "No matches" else "${current + 1} / $matchCount",
                    color = chrome.textSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                if (matchCount > 0) {
                    IconButton(onClick = onPrev, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match", tint = chrome.accent)
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(onClick = onNext, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match", tint = chrome.accent)
                    }
                }
            }
        }
    }
}

/** A card section header: a purple icon chip, a title, and optional trailing content. */
@Composable
private fun CardHeader(
    icon: ImageVector,
    title: String,
    chrome: Chrome,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(34.dp).clip(CircleShape).background(chrome.accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = chrome.accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, color = chrome.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun HeadersCard(headers: List<HttpHeader>, chrome: Chrome) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(chrome.card).padding(16.dp),
    ) {
        CardHeader(Icons.AutoMirrored.Filled.List, "Headers (${headers.size})", chrome)
        headers.forEachIndexed { i, h ->
            if (i > 0) {
                Spacer(Modifier.height(10.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(chrome.divider))
                Spacer(Modifier.height(10.dp))
            } else {
                Spacer(Modifier.height(12.dp))
            }
            Text(h.name, color = chrome.accent, fontSize = 13.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            SelectionContainer {
                Text(h.value, color = chrome.textPrimary, fontSize = 14.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun BodyCard(
    body: String?,
    contentType: String?,
    isPlainText: Boolean,
    isJson: Boolean,
    query: String,
    chrome: Chrome,
    activeStart: Int,
    scrollState: ScrollState,
    rootCoords: LayoutCoordinates?,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(chrome.card).padding(16.dp),
    ) {
        CardHeader(Icons.Default.Code, "Body", chrome) {
            if (!body.isNullOrEmpty() && isPlainText) {
                val clipboard = LocalClipboardManager.current
                IconButton(onClick = {
                    val text = if (isJson) FormatUtils.formatJson(body) else body
                    clipboard.setText(AnnotatedString(text))
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy body", tint = chrome.accent)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        BodyContent(body, contentType, isPlainText, isJson, query, chrome, activeStart, scrollState, rootCoords)
    }
}

@Composable
private fun BodyContent(
    body: String?,
    contentType: String?,
    isPlainText: Boolean,
    isJson: Boolean,
    query: String,
    chrome: Chrome,
    activeStart: Int,
    scrollState: ScrollState,
    rootCoords: LayoutCoordinates?,
) {
    val jsonColors = rememberJsonColors()
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var textCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Scroll the active match into view whenever it changes.
    LaunchedEffect(activeStart, textLayout, rootCoords) {
        val layout = textLayout
        val tc = textCoords
        val rc = rootCoords
        if (activeStart >= 0 && layout != null && tc != null && rc != null &&
            tc.isAttached && rc.isAttached && activeStart < layout.layoutInput.text.length
        ) {
            val box = layout.getBoundingBox(activeStart)
            val yInViewport = rc.localPositionOf(tc, Offset(0f, box.top)).y
            val target = (scrollState.value + yInViewport - 140f).toInt().coerceIn(0, scrollState.maxValue)
            scrollState.animateScrollTo(target)
        }
    }

    val monospace = @Composable { text: AnnotatedString ->
        SelectionContainer {
            Text(
                text,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = chrome.textPrimary,
                onTextLayout = { textLayout = it },
                modifier = Modifier.onGloballyPositioned { textCoords = it },
            )
        }
    }

    when {
        body.isNullOrEmpty() -> monospace(AnnotatedString("(empty)"))
        !isPlainText -> monospace(AnnotatedString("(binary body — ${contentType ?: "unknown"})"))
        isJson -> {
            val pretty = remember(body) { FormatUtils.formatJson(body) }
            val node = remember(body, query) {
                if (query.isBlank()) JsonParser.parseOrNull(body) else null
            }
            if (node != null) {
                // Collapsible tree when not searching; falls back to flat search view otherwise.
                CollapsibleJson(node, jsonColors, fontSize = 15.sp, lineHeight = 22.sp)
            } else {
                monospace(jsonToAnnotatedString(pretty, jsonColors).withSearchHighlight(pretty, query, activeStart))
            }
        }
        else -> monospace(highlight(body, query, activeStart))
    }
}

@Composable
private fun ShareMenu(tx: HttpTransaction, shareHandler: KaptorShareHandler) {
    var expanded by remember { mutableStateOf(false) }
    val label = tx.shortLabel

    val chrome = rememberChrome()
    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Default.Share, contentDescription = "Share")
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false },
        shape = RoundedCornerShape(18.dp),
        containerColor = chrome.card,
    ) {
        DropdownMenuItem(
            text = { Text("Share as text") },
            onClick = {
                expanded = false
                shareHandler.shareText(TransactionFormats.shareText(tx), label)
            },
        )
        DropdownMenuItem(
            text = { Text("Share as cURL") },
            onClick = {
                expanded = false
                shareHandler.shareText(TransactionFormats.curl(tx), label)
            },
        )
        DropdownMenuItem(
            text = { Text("Share as file") },
            onClick = {
                expanded = false
                shareHandler.shareFile("transaction-${tx.id}.txt", TransactionFormats.shareText(tx), "text/plain")
            },
        )
    }
}

