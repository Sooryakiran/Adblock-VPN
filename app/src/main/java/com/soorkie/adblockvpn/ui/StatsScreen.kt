package com.soorkie.adblockvpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SkeuoColors.Desktop),
    ) {
        SkeuoTitleBar(title = "Adblock VPN  —  Control Panel")
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(SkeuoColors.WindowBg)
                .skeuoRaisedBevel()
                .padding(8.dp),
        ) {
            PrivateDnsBanner()
            Spacer(Modifier.padding(top = 6.dp))
            Header(
                isRunning = isVpnRunning,
                totalDomains = state.totalDomains,
                totalRequests = state.totalRequests,
                blockedCount = state.blockedCount,
                blockedRequests = state.blockedRequests,
                onStart = onStart,
                onStop = onStop,
                onClear = vm::clear,
            )
            Spacer(Modifier.padding(top = 6.dp))
            FiltersBar(
                filter = state.filter,
                appOptions = state.appOptions,
                domainOptions = state.domainOptions,
                visibleCount = state.visibleCount,
                onAppFilter = vm::setAppFilter,
                onDomainFilter = vm::setDomainFilter,
                onBlockedFilter = vm::setBlockedFilter,
                onClearFilters = vm::clearFilters,
            )
            Spacer(Modifier.padding(top = 6.dp))
            StatsTable(
                rows = state.rows,
                blockedDomains = state.blockedDomains,
                sort = state.sort,
                onSortChange = vm::toggleSort,
                onToggleBlocked = vm::toggleBlocked,
            )
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
    onAppFilter: (Set<String>) -> Unit,
    onDomainFilter: (Set<String>) -> Unit,
    onBlockedFilter: (BlockedFilter) -> Unit,
    onClearFilters: () -> Unit,
) {
    SkeuoPanel(modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(
                text = "Filter",
                fontFamily = SkeuoFont,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = SkeuoColors.TitleBarTop,
            )
            Spacer(Modifier.padding(top = 4.dp))
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
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$visibleCount shown",
                    fontFamily = SkeuoFont,
                    fontSize = 11.sp,
                    color = SkeuoColors.TextMuted,
                )
                val anyActive = filter.appPackages.isNotEmpty() ||
                    filter.domains.isNotEmpty() ||
                    filter.blocked != BlockedFilter.All
                if (anyActive) {
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

private const val W_APP = 1.3f
private const val W_DOMAIN = 2.2f
private const val W_COUNT = 0.6f
private const val W_LAST = 1.4f
private const val W_ACTION = 0.9f

@Composable
private fun StatsTable(
    rows: List<DomainStat>,
    blockedDomains: Set<String>,
    sort: SortState,
    onSortChange: (SortColumn) -> Unit,
    onToggleBlocked: (String, Boolean) -> Unit,
) {
    SkeuoWell(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TableHeader(sort = sort, onSortChange = onSortChange)
            if (rows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No DNS requests yet — start the VPN and use any app.",
                        fontFamily = SkeuoFont,
                        fontSize = 13.sp,
                        color = SkeuoColors.TextMuted,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxHeight(),
                    contentPadding = PaddingValues(vertical = 2.dp),
                ) {
                    itemsIndexed(
                        items = rows,
                        key = { _, r -> "${r.domain}\u0000${r.appPackage}" },
                    ) { index, row ->
                        val isBlocked = row.domain in blockedDomains
                        val rowBg = if (index % 2 == 0) SkeuoColors.InsetBg else SkeuoColors.InsetAlt
                        TableRow(
                            stat = row,
                            isBlocked = isBlocked,
                            background = rowBg,
                            onToggleBlocked = { onToggleBlocked(row.domain, isBlocked) },
                        )
                        HorizontalDivider(color = Color(0xFFDCE3EC))
                    }
                }
            }
        }
    }
}

@Composable
private fun TableHeader(
    sort: SortState,
    onSortChange: (SortColumn) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .skeuoGlossy(
                top = Color(0xFFF6F6F6),
                mid = Color(0xFFDEDEDE),
                bottom = Color(0xFFB8B8B8),
            )
            .skeuoRaisedBevel()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell("App", SortColumn.App, sort, onSortChange, weight = W_APP)
        HeaderCell("Domain", SortColumn.Domain, sort, onSortChange, weight = W_DOMAIN)
        HeaderCell("#", SortColumn.Count, sort, onSortChange, weight = W_COUNT)
        HeaderCell("Last seen", SortColumn.LastSeen, sort, onSortChange, weight = W_LAST)
        Text(text = "", modifier = Modifier.weight(W_ACTION))
    }
}

@Composable
private fun RowScope.HeaderCell(
    label: String,
    column: SortColumn,
    sort: SortState,
    onSortChange: (SortColumn) -> Unit,
    weight: Float,
) {
    val active = sort.column == column
    val arrow = when {
        !active -> ""
        sort.descending -> "  ▼"
        else -> "  ▲"
    }
    Text(
        text = label + arrow,
        modifier = Modifier
            .weight(weight)
            .clickable { onSortChange(column) }
            .padding(horizontal = 4.dp),
        fontFamily = SkeuoFont,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        color = if (active) SkeuoColors.TitleBarTop else SkeuoColors.Text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
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
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell(
            text = stat.appLabel,
            weight = W_APP,
            style = SkeuoTableBody,
        )
        Cell(
            text = stat.domain,
            weight = W_DOMAIN,
            style = SkeuoTableBody.copy(fontFamily = FontFamily.Monospace),
        )
        Cell(
            text = stat.count.toString(),
            weight = W_COUNT,
            style = SkeuoTableBody.copy(fontWeight = FontWeight.Bold),
        )
        Cell(
            text = if (stat.lastSeenMs == 0L) "—" else formatTimestamp(stat.lastSeenMs),
            weight = W_LAST,
            style = SkeuoTableBody.copy(fontSize = 11.sp, color = SkeuoColors.TextMuted),
        )
        Box(modifier = Modifier.weight(W_ACTION), contentAlignment = Alignment.CenterEnd) {
            StatusToggle(isBlocked = isBlocked, onClick = onToggleBlocked)
        }
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

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    style: TextStyle,
) {
    Text(
        text = text,
        modifier = Modifier
            .weight(weight)
            .padding(horizontal = 4.dp),
        style = style,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

private val DATE_FMT: DateFormat =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

private fun formatTimestamp(ms: Long): String = DATE_FMT.format(Date(ms))
