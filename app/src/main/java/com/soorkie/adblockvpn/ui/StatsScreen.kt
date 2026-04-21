package com.soorkie.adblockvpn.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soorkie.adblockvpn.data.DomainStat
import com.soorkie.adblockvpn.net.PrivateDnsStatus
import com.soorkie.adblockvpn.net.observePrivateDnsStatus
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    isVpnRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val vm: StatsViewModel = viewModel(factory = StatsViewModel.Factory())
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Adblock VPN") }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            PrivateDnsBanner()
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
            HorizontalDivider()
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
    val status by flow.collectAsState(initial = PrivateDnsStatus(active = false, hostname = null))

    val (container, onContainer) = if (status.active) {
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = container,
            contentColor = onContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (status.active) "Private DNS: ON" else "Private DNS: OFF",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_WIRELESS_SETTINGS
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                }) {
                    Text("Settings")
                }
            }
            Text(
                text = if (status.active) {
                    buildString {
                        append("DNS is sent encrypted")
                        status.hostname?.let { append(" to $it") }
                        append(". This VPN can't see or block those queries — turn Private DNS off.")
                    }
                } else {
                    "Good — DNS queries flow through this VPN and can be tracked/blocked."
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
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
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isRunning) "VPN: running" else "VPN: stopped",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Domains: $totalDomains")
                Text("Requests: $totalRequests")
                Text("Blocked: $blockedCount / $blockedRequests")
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isRunning) {
                    Button(onClick = onStop) { Text("Stop VPN") }
                } else {
                    Button(onClick = onStart) { Text("Start VPN") }
                }
                OutlinedButton(onClick = onClear) { Text("Clear stats") }
            }
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
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "$visibleCount shown",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val anyActive = filter.appPackages.isNotEmpty() ||
                filter.domains.isNotEmpty() ||
                filter.blocked != BlockedFilter.All
            if (anyActive) {
                TextButton(onClick = onClearFilters) {
                    Text("Clear filters", style = MaterialTheme.typography.labelMedium)
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
        OutlinedButton(onClick = { expanded = true }) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BlockedFilter.entries.forEach { opt ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (opt) {
                                BlockedFilter.All -> "All"
                                BlockedFilter.Blocked -> "Blocked"
                                BlockedFilter.Unblocked -> "Unblocked"
                            }
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

@OptIn(ExperimentalMaterial3Api::class)
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

    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Text(buttonLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Type to filter…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { current = emptySet() }) { Text(allLabel) }
                    Text(
                        "${current.size} selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 360.dp),
                ) {
                    items(items = filtered, key = { it.value }) { opt ->
                        val isOn = opt.value in current
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    current = if (isOn) current - opt.value
                                    else current + opt.value
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = isOn, onCheckedChange = null)
                            Text(
                                text = opt.label,
                                modifier = Modifier.padding(start = 8.dp).weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(current) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
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
    Column(modifier = Modifier.fillMaxSize()) {
        TableHeader(sort = sort, onSortChange = onSortChange)
        HorizontalDivider()
        if (rows.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No DNS requests yet — start the VPN and use any app.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxHeight(),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(items = rows, key = { "${it.domain}\u0000${it.appPackage}" }) { row ->
                    val isBlocked = row.domain in blockedDomains
                    TableRow(
                        stat = row,
                        isBlocked = isBlocked,
                        onToggleBlocked = { onToggleBlocked(row.domain, isBlocked) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
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
        sort.descending -> "  ↓"
        else -> "  ↑"
    }
    Text(
        text = label + arrow,
        modifier = Modifier
            .weight(weight)
            .clickable { onSortChange(column) }
            .padding(horizontal = 4.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun TableRow(
    stat: DomainStat,
    isBlocked: Boolean,
    onToggleBlocked: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell(
            text = stat.appLabel,
            weight = W_APP,
            style = MaterialTheme.typography.bodyMedium,
        )
        Cell(
            text = stat.domain,
            weight = W_DOMAIN,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        )
        Cell(
            text = stat.count.toString(),
            weight = W_COUNT,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        )
        Cell(
            text = if (stat.lastSeenMs == 0L) "—" else formatTimestamp(stat.lastSeenMs),
            weight = W_LAST,
            style = MaterialTheme.typography.bodySmall,
        )
        Box(modifier = Modifier.weight(W_ACTION), contentAlignment = Alignment.CenterEnd) {
            TextButton(onClick = onToggleBlocked) {
                Text(
                    text = if (isBlocked) "Unblock" else "Block",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isBlocked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

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
