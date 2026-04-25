package com.soorkie.adblockvpn.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soorkie.adblockvpn.data.DomainStat
import com.soorkie.adblockvpn.net.PrivateDnsMode
import com.soorkie.adblockvpn.net.PrivateDnsStatus
import com.soorkie.adblockvpn.net.observePrivateDnsStatus
import java.text.DateFormat
import java.util.Date

@Composable
fun StatsScreen(
    isVpnRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val vm: StatsViewModel = viewModel(factory = StatsViewModel.Factory())
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Pending import URI awaiting the user's merge/replace choice.
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) vm.exportBlocklist(context, uri)
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) pendingImportUri = uri
    }

    // Surface VM events as toasts.
    LaunchedEffect(vm) {
        vm.events.collect { msg ->
            Toast.makeText(context, msg.text, Toast.LENGTH_SHORT).show()
        }
    }

    pendingImportUri?.let { uri ->
        ImportModeDialog(
            onDismiss = { pendingImportUri = null },
            onMerge = {
                vm.importBlocklist(context, uri, replace = false)
                pendingImportUri = null
            },
            onReplace = {
                vm.importBlocklist(context, uri, replace = true)
                pendingImportUri = null
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SkeuoColors.Desktop),
    ) {
        SkeuoTitleBar(title = "Adblock VPN  —  Control Panel")
        var filterExpanded by rememberSaveable { mutableStateOf(true) }
        var sortExpanded by rememberSaveable { mutableStateOf(true) }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(SkeuoColors.WindowBg)
                .skeuoRaisedBevel()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item(key = "banner") { PrivateDnsBanner() }
            item(key = "header") {
                Header(
                    isRunning = isVpnRunning,
                    totalDomains = state.totalDomains,
                    totalRequests = state.totalRequests,
                    blockedCount = state.blockedCount,
                    blockedRequests = state.blockedRequests,
                    onStart = onStart,
                    onStop = onStop,
                    onClear = vm::clear,
                    onExport = {
                        exportLauncher.launch(suggestedExportName())
                    },
                    onImport = {
                        importLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                    },
                )
            }
            item(key = "filters") {
                FiltersBar(
                    filter = state.filter,
                    appOptions = state.appOptions,
                    domainOptions = state.domainOptions,
                    visibleCount = state.visibleCount,
                    expanded = filterExpanded,
                    onToggleExpanded = { filterExpanded = !filterExpanded },
                    onAppFilter = vm::setAppFilter,
                    onDomainFilter = vm::setDomainFilter,
                    onBlockedFilter = vm::setBlockedFilter,
                    onClearFilters = vm::clearFilters,
                )
            }
            item(key = "sort") {
                SortBar(
                    sort = state.sort,
                    expanded = sortExpanded,
                    onToggleExpanded = { sortExpanded = !sortExpanded },
                    onSortChange = vm::toggleSort,
                )
            }
            if (state.rows.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No DNS requests yet — start the VPN and use any app.",
                            fontFamily = SkeuoFont,
                            fontSize = 13.sp,
                            color = SkeuoColors.TextMuted,
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = state.rows,
                    key = { _, r -> "${r.domain}\u0000${r.appPackage}" },
                ) { index, row ->
                    val isBlocked = row.domain in state.blockedDomains
                    val rowBg = if (index % 2 == 0) SkeuoColors.InsetBg else SkeuoColors.InsetAlt
                    Column {
                        TableRow(
                            stat = row,
                            isBlocked = isBlocked,
                            background = rowBg,
                            onToggleBlocked = { vm.toggleBlocked(row.domain, isBlocked) },
                        )
                        HorizontalDivider(color = Color(0xFFDCE3EC))
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivateDnsBanner() {
    val context = LocalContext.current
    val flow = remember(context) { observePrivateDnsStatus(context) }
    val status by flow.collectAsState(
        initial = PrivateDnsStatus(
            mode = PrivateDnsMode.Off,
            active = false,
            hostname = null,
        ),
    )

    // "Problematic" = Private DNS will bypass our VPN's blocker.
    // That's true whenever the user configured anything other than Off
    // (Automatic may or may not actually upgrade, but we warn anyway since
    // it's silent when it does).
    val problematic = status.mode != PrivateDnsMode.Off

    val topColor = if (problematic) SkeuoColors.WarnBannerTop else SkeuoColors.OkBannerTop
    val bottomColor = if (problematic) SkeuoColors.WarnBannerBottom else SkeuoColors.OkBannerBottom
    val borderColor = if (problematic) SkeuoColors.WarnBannerBorder else SkeuoColors.OkBannerBorder

    val title = when (status.mode) {
        PrivateDnsMode.Off -> "Private DNS: OFF"
        PrivateDnsMode.Automatic ->
            if (status.active) "Private DNS: AUTOMATIC (active)"
            else "Private DNS: AUTOMATIC"
        PrivateDnsMode.Strict -> "Private DNS: ON"
    }

    val body = when (status.mode) {
        PrivateDnsMode.Off ->
            "Good — DNS queries flow through this VPN and can be tracked/blocked."
        PrivateDnsMode.Automatic -> buildString {
            append("System may silently upgrade DNS to encrypted DoT")
            if (status.active) append(" (currently active)")
            append(". When it does, this VPN can't see or block those queries — ")
            append("set Private DNS to Off for reliable blocking.")
        }
        PrivateDnsMode.Strict -> buildString {
            append("DNS is sent encrypted")
            status.hostname?.let { append(" to $it") }
            append(". This VPN can't see or block those queries — turn Private DNS off.")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to topColor,
                        1f to bottomColor,
                    ),
                )
                val w = size.width; val h = size.height
                drawLine(borderColor, Offset(0f, 0.5f), Offset(w, 0.5f), strokeWidth = 1f)
                drawLine(borderColor, Offset(0f, h - 0.5f), Offset(w, h - 0.5f), strokeWidth = 1f)
                drawLine(borderColor, Offset(0.5f, 0f), Offset(0.5f, h), strokeWidth = 1f)
                drawLine(borderColor, Offset(w - 0.5f, 0f), Offset(w - 0.5f, h), strokeWidth = 1f)
            }
            .padding(10.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SkeuoLed(on = !problematic)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontFamily = SkeuoFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = SkeuoColors.Text,
                    )
                }
                SkeuoButton(
                    text = "Settings…",
                    onClick = {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_WIRELESS_SETTINGS
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(intent) }
                    },
                )
            }
            Text(
                text = body,
                fontFamily = SkeuoFont,
                fontSize = 12.sp,
                color = SkeuoColors.Text,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Header(
    isRunning: Boolean,
    totalDomains: Int,
    totalRequests: Long,
    blockedCount: Int,
    blockedRequests: Long,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    SkeuoPanel(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeuoLed(on = isRunning)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (isRunning) "VPN: RUNNING" else "VPN: STOPPED",
                    fontFamily = SkeuoFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (isRunning) SkeuoColors.GoBorder else SkeuoColors.StopBorder,
                )
            }
            Spacer(Modifier.padding(top = 8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LcdReadout("DOMAINS", totalDomains.toString(), Modifier.weight(1f))
                LcdReadout("REQUESTS", totalRequests.toString(), Modifier.weight(1f))
                LcdReadout("BLOCKED", "$blockedCount / $blockedRequests", Modifier.weight(1.2f))
            }
            Spacer(Modifier.padding(top = 10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isRunning) {
                    SkeuoButton(
                        text = "■  Stop VPN",
                        onClick = onStop,
                        style = SkeuoButtonStyle.Stop,
                    )
                } else {
                    SkeuoButton(
                        text = "▶  Start VPN",
                        onClick = onStart,
                        style = SkeuoButtonStyle.Go,
                    )
                }
                SkeuoButton(text = "Clear stats", onClick = onClear)
            }
            Spacer(Modifier.padding(top = 6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SkeuoButton(text = "Export blocklist…", onClick = onExport)
                SkeuoButton(text = "Import blocklist…", onClick = onImport)
            }
        }
    }
}

@Composable
private fun LcdReadout(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            fontFamily = SkeuoFont,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            color = SkeuoColors.TextMuted,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
                .background(Color(0xFF0A2A10))
                .skeuoSunkenBevel(
                    light = Color(0xFF2A4A2A),
                    shadow = Color(0xFF000000),
                    dark = Color(0xFF000000),
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                text = value,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF7CFF7C),
            )
        }
    }
}

// --- Filters ---------------------------------------------------------------

@Composable
private fun FiltersBar(
    filter: FilterState,
    appOptions: List<AppOption>,
    domainOptions: List<String>,
    visibleCount: Int,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onAppFilter: (Set<String>) -> Unit,
    onDomainFilter: (Set<String>) -> Unit,
    onBlockedFilter: (BlockedFilter) -> Unit,
    onClearFilters: () -> Unit,
) {
    val anyActive = filter.appPackages.isNotEmpty() ||
        filter.domains.isNotEmpty() ||
        filter.blocked != BlockedFilter.All
    SkeuoPanel(modifier = Modifier.fillMaxWidth()) {
        Column {
            CollapseHeader(
                title = "Filter",
                summary = if (anyActive) "$visibleCount shown · active" else "$visibleCount shown",
                expanded = expanded,
                onToggle = onToggleExpanded,
            )
            if (expanded) {
                Spacer(Modifier.padding(top = 6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MultiSelectButton(
                        modifier = Modifier.weight(1f),
                        allLabel = "All apps",
                        selected = filter.appPackages,
                        options = appOptions.map { SelectableOption(it.pkg, it.label) },
                        onConfirm = onAppFilter,
                        dialogTitle = "Filter by app",
                    )
                    MultiSelectButton(
                        modifier = Modifier.weight(1f),
                        allLabel = "All domains",
                        selected = filter.domains,
                        options = domainOptions.map { SelectableOption(it, it) },
                        onConfirm = onDomainFilter,
                        dialogTitle = "Filter by domain",
                    )
                    BlockedFilterDropdown(
                        selected = filter.blocked,
                        onSelect = onBlockedFilter,
                    )
                }
                if (anyActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        SkeuoButton(
                            text = "Clear filters",
                            onClick = onClearFilters,
                            style = SkeuoButtonStyle.Link,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollapseHeader(
    title: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (expanded) "▾" else "▸",
                fontFamily = SkeuoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = SkeuoColors.TitleBarTop,
                modifier = Modifier.padding(end = 6.dp),
            )
            Text(
                text = title,
                fontFamily = SkeuoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = SkeuoColors.TitleBarTop,
            )
        }
        Text(
            text = summary,
            fontFamily = SkeuoFont,
            fontSize = 11.sp,
            color = SkeuoColors.TextMuted,
        )
    }
}

@Composable
private fun BlockedFilterDropdown(
    selected: BlockedFilter,
    onSelect: (BlockedFilter) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (selected) {
        BlockedFilter.All -> "All"
        BlockedFilter.Blocked -> "Blocked"
        BlockedFilter.Unblocked -> "Unblocked"
    }
    Box {
        SkeuoButton(text = "$label  ▾", onClick = { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(SkeuoColors.WindowBg),
        ) {
            BlockedFilter.entries.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (opt) {
                                BlockedFilter.All -> "All"
                                BlockedFilter.Blocked -> "Blocked"
                                BlockedFilter.Unblocked -> "Unblocked"
                            },
                            fontFamily = SkeuoFont,
                            color = SkeuoColors.Text,
                        )
                    },
                    onClick = {
                        onSelect(opt)
                        expanded = false
                    },
                )
            }
        }
    }
}

private data class SelectableOption(val value: String, val label: String)

@Composable
private fun MultiSelectButton(
    modifier: Modifier = Modifier,
    allLabel: String,
    selected: Set<String>,
    options: List<SelectableOption>,
    onConfirm: (Set<String>) -> Unit,
    dialogTitle: String,
) {
    var open by remember { mutableStateOf(false) }
    val buttonLabel = when {
        selected.isEmpty() -> allLabel
        selected.size == 1 -> options.firstOrNull { it.value == selected.first() }?.label
            ?: selected.first()
        else -> "${selected.size} selected"
    }

    Box(modifier = modifier) {
        SkeuoButton(
            text = "$buttonLabel  ▾",
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (open) {
        MultiSelectDialog(
            title = dialogTitle,
            allLabel = allLabel,
            initialSelection = selected,
            options = options,
            onDismiss = { open = false },
            onConfirm = {
                onConfirm(it)
                open = false
            },
        )
    }
}

@Composable
private fun MultiSelectDialog(
    title: String,
    allLabel: String,
    initialSelection: Set<String>,
    options: List<SelectableOption>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var current by remember { mutableStateOf(initialSelection) }
    val filtered = remember(query, options) {
        if (query.isBlank()) options
        else {
            val q = query.trim().lowercase()
            options.filter { it.label.lowercase().contains(q) || it.value.lowercase().contains(q) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SkeuoColors.WindowBg,
        titleContentColor = SkeuoColors.Text,
        textContentColor = SkeuoColors.Text,
        title = {
            Text(
                title,
                fontFamily = SkeuoFont,
                fontWeight = FontWeight.Bold,
                color = SkeuoColors.Text,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            "Type to filter…",
                            fontFamily = SkeuoFont,
                            color = SkeuoColors.TextMuted,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontFamily = SkeuoFont, color = SkeuoColors.Text),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedTextColor = SkeuoColors.Text,
                        unfocusedTextColor = SkeuoColors.Text,
                        focusedIndicatorColor = SkeuoColors.TitleBarTop,
                        unfocusedIndicatorColor = SkeuoColors.PanelBorderShadow,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SkeuoButton(
                        text = allLabel,
                        style = SkeuoButtonStyle.Link,
                        onClick = { current = emptySet() },
                    )
                    Text(
                        "${current.size} selected",
                        fontFamily = SkeuoFont,
                        fontSize = 11.sp,
                        color = SkeuoColors.TextMuted,
                    )
                }
                HorizontalDivider(color = SkeuoColors.PanelBorderShadow)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .background(SkeuoColors.InsetBg)
                        .skeuoSunkenBevel(),
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp, max = 360.dp)
                            .padding(4.dp),
                    ) {
                        itemsIndexed(
                            items = filtered,
                            key = { _, o -> o.value },
                        ) { _, opt ->
                            val isOn = opt.value in current
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        current = if (isOn) current - opt.value
                                        else current + opt.value
                                    }
                                    .padding(vertical = 3.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = isOn,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = SkeuoColors.TitleBarTop,
                                        uncheckedColor = SkeuoColors.PanelBorderDark,
                                        checkmarkColor = Color.White,
                                    ),
                                )
                                Text(
                                    text = opt.label,
                                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                                    fontFamily = SkeuoFont,
                                    fontSize = 13.sp,
                                    color = SkeuoColors.Text,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            SkeuoButton(
                text = "Apply",
                style = SkeuoButtonStyle.Go,
                onClick = { onConfirm(current) },
            )
        },
        dismissButton = {
            SkeuoButton(text = "Cancel", onClick = onDismiss)
        },
    )
}

// --- Table -----------------------------------------------------------------

@Composable
private fun SortBar(
    sort: SortState,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onSortChange: (SortColumn) -> Unit,
) {
    val activeLabel = when (sort.column) {
        SortColumn.Domain -> "Domain"
        SortColumn.Count -> "Request count"
        SortColumn.LastSeen -> "Last seen"
        SortColumn.App -> "App"
    } + if (sort.descending) " ▼" else " ▲"
    SkeuoPanel(modifier = Modifier.fillMaxWidth()) {
        Column {
            CollapseHeader(
                title = "Sort",
                summary = activeLabel,
                expanded = expanded,
                onToggle = onToggleExpanded,
            )
            if (expanded) {
                Spacer(Modifier.padding(top = 6.dp))
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SortChip("Domain", SortColumn.Domain, sort, onSortChange)
                    SortChip("Request count", SortColumn.Count, sort, onSortChange)
                    SortChip("Last seen", SortColumn.LastSeen, sort, onSortChange)
                    SortChip("App", SortColumn.App, sort, onSortChange)
                }
            }
        }
    }
}

@Composable
private fun SortChip(
    label: String,
    column: SortColumn,
    sort: SortState,
    onSortChange: (SortColumn) -> Unit,
) {
    val active = sort.column == column
    val arrow = when {
        !active -> "  ▾"
        sort.descending -> "  ▼"
        else -> "  ▲"
    }
    SkeuoButton(
        text = label + arrow,
        onClick = { onSortChange(column) },
    )
}

@Composable
private fun TableRow(
    stat: DomainStat,
    isBlocked: Boolean,
    background: Color,
    onToggleBlocked: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isBlocked) Color(0xFFFFE0E0) else background)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: domain (primary) + app chip below
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = stat.domain,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = SkeuoColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.padding(top = 4.dp))
            AppChip(stat.appLabel)
        }

        // Middle: big count, small label, relative time underneath
        Column(
            modifier = Modifier.padding(end = 10.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = formatCount(stat.count),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = SkeuoColors.Text,
                    maxLines = 1,
                )
                Text(
                    text = " req",
                    fontFamily = SkeuoFont,
                    fontSize = 10.sp,
                    color = SkeuoColors.TextMuted,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Text(
                text = if (stat.lastSeenMs == 0L) "—" else formatRelative(stat.lastSeenMs),
                fontFamily = SkeuoFont,
                fontSize = 11.sp,
                color = SkeuoColors.TextMuted,
                maxLines = 1,
            )
        }

        StatusToggle(isBlocked = isBlocked, onClick = onToggleBlocked)
    }
}

@Composable
private fun AppChip(label: String) {
    Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
            .background(Color(0xFFE3EAF5))
            .border(
                androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFB6C4DA)),
                androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            fontFamily = SkeuoFont,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = SkeuoColors.TitleBarBottom,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatCount(n: Long): String = when {
    n < 1_000 -> n.toString()
    n < 10_000 -> "%.1fk".format(n / 1000.0)
    n < 1_000_000 -> "${n / 1000}k"
    else -> "%.1fM".format(n / 1_000_000.0)
}

private fun formatRelative(ms: Long): String {
    val diff = System.currentTimeMillis() - ms
    if (diff < 0) return formatTimestamp(ms)
    val sec = diff / 1000
    return when {
        sec < 5 -> "just now"
        sec < 60 -> "${sec}s ago"
        sec < 3600 -> "${sec / 60}m ago"
        sec < 86_400 -> "${sec / 3600}h ago"
        sec < 7 * 86_400 -> "${sec / 86_400}d ago"
        else -> formatTimestamp(ms)
    }
}

@Composable
private fun StatusToggle(isBlocked: Boolean, onClick: () -> Unit) {
    // Neutral silver pill whose LED + label shows CURRENT state.
    // Click toggles. Color communicates status, not action, so it's unambiguous.
    val label = if (isBlocked) "Blocked" else "Allowed"
    Box(
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
            .border(
                androidx.compose.foundation.BorderStroke(1.dp, SkeuoColors.BtnBorder),
                androidx.compose.foundation.shape.RoundedCornerShape(7.dp),
            )
            .skeuoGlossy(
                top = SkeuoColors.BtnTop,
                mid = SkeuoColors.BtnMid,
                bottom = SkeuoColors.BtnBottom,
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkeuoLed(on = !isBlocked, size = 10.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                fontFamily = SkeuoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = SkeuoColors.Text,
            )
        }
    }
}

private val SkeuoTableBody = TextStyle(
    fontFamily = SkeuoFont,
    fontSize = 13.sp,
    color = SkeuoColors.Text,
)

private val DATE_FMT: DateFormat =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

private fun formatTimestamp(ms: Long): String = DATE_FMT.format(Date(ms))

private fun suggestedExportName(): String {
    val ts = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
        .format(Date())
    return "adblock-vpn-blocklist-$ts.txt"
}

@Composable
private fun ImportModeDialog(
    onDismiss: () -> Unit,
    onMerge: () -> Unit,
    onReplace: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SkeuoColors.WindowBg,
        titleContentColor = SkeuoColors.Text,
        textContentColor = SkeuoColors.Text,
        title = {
            Text(
                "Import blocklist",
                fontFamily = SkeuoFont,
                fontWeight = FontWeight.Bold,
                color = SkeuoColors.Text,
            )
        },
        text = {
            Text(
                "Merge the imported domains with your current blocklist, " +
                    "or replace it entirely?",
                fontFamily = SkeuoFont,
                fontSize = 13.sp,
                color = SkeuoColors.Text,
            )
        },
        confirmButton = {
            SkeuoButton(
                text = "Merge",
                style = SkeuoButtonStyle.Go,
                onClick = onMerge,
            )
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeuoButton(
                    text = "Replace",
                    style = SkeuoButtonStyle.Stop,
                    onClick = onReplace,
                )
                SkeuoButton(text = "Cancel", onClick = onDismiss)
            }
        },
    )
}
