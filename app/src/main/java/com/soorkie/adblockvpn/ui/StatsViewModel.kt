package com.soorkie.adblockvpn.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.soorkie.adblockvpn.MainApp
import com.soorkie.adblockvpn.data.DomainStat
import com.soorkie.adblockvpn.data.StatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SortColumn { App, Domain, Count, LastSeen }

enum class BlockedFilter { All, Blocked, Unblocked }

data class SortState(val column: SortColumn = SortColumn.Count, val descending: Boolean = true)

/** Empty selection sets mean "no filter / All". */
data class FilterState(
    val appPackages: Set<String> = emptySet(),
    val domains: Set<String> = emptySet(),
    val blocked: BlockedFilter = BlockedFilter.All,
)

data class AppOption(val pkg: String, val label: String)

data class StatsUiState(
    val rows: List<DomainStat> = emptyList(),
    val blockedDomains: Set<String> = emptySet(),
    val totalDomains: Int = 0,
    val totalRequests: Long = 0,
    val blockedCount: Int = 0,
    val blockedRequests: Long = 0,
    val sort: SortState = SortState(),
    val filter: FilterState = FilterState(),
    val appOptions: List<AppOption> = emptyList(),
    val domainOptions: List<String> = emptyList(),
    val visibleCount: Int = 0,
)

class StatsViewModel(private val repository: StatsRepository) : ViewModel() {

    private val sort = MutableStateFlow(SortState())
    private val filter = MutableStateFlow(FilterState())

    val state: StateFlow<StatsUiState> = combine(
        repository.observeStats(),
        repository.observeBlockedDomains(),
        repository.observeDomainCount(),
        repository.observeRequestTotal(),
        sort,
        filter,
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val domains = args[0] as List<DomainStat>
        @Suppress("UNCHECKED_CAST")
        val blockedList = args[1] as List<String>
        val count = args[2] as Int
        val total = args[3] as Long
        val sortState = args[4] as SortState
        val filterState = args[5] as FilterState

        val blockedSet = blockedList.toHashSet()
        val blockedRows = domains.filter { it.domain in blockedSet }

        // Base set: observed rows + placeholder rows for blocked domains
        // that have never been seen (so they remain visible/manageable).
        val seen = blockedRows.map { it.domain }.toHashSet()
        val placeholders = blockedList
            .filter { it !in seen }
            .map { DomainStat(it, PLACEHOLDER_PKG, PLACEHOLDER_LABEL, 0, 0) }
        val baseRows = domains + placeholders

        // Options derived from the unfiltered base set so the dropdowns
        // always show every available choice, regardless of current filters.
        val appOptions = baseRows
            .filter { it.appPackage.isNotEmpty() }
            .distinctBy { it.appPackage }
            .map { AppOption(it.appPackage, it.appLabel) }
            .sortedBy { it.label.lowercase() }
        val domainOptions = baseRows
            .map { it.domain }
            .distinct()
            .sorted()

        val filtered = baseRows.filter { row ->
            val appOk = filterState.appPackages.isEmpty() ||
                row.appPackage in filterState.appPackages
            val domainOk = filterState.domains.isEmpty() ||
                row.domain in filterState.domains
            val blockedOk = when (filterState.blocked) {
                BlockedFilter.All -> true
                BlockedFilter.Blocked -> row.domain in blockedSet
                BlockedFilter.Unblocked -> row.domain !in blockedSet
            }
            appOk && domainOk && blockedOk
        }

        StatsUiState(
            rows = filtered.sortedWith(comparatorFor(sortState)),
            blockedDomains = blockedSet,
            totalDomains = count,
            totalRequests = total,
            blockedCount = blockedSet.size,
            blockedRequests = blockedRows.sumOf { it.count },
            sort = sortState,
            filter = filterState,
            appOptions = appOptions,
            domainOptions = domainOptions,
            visibleCount = filtered.size,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatsUiState(),
    )

    fun setAppFilter(pkgs: Set<String>) {
        filter.value = filter.value.copy(appPackages = pkgs)
    }

    fun setDomainFilter(domains: Set<String>) {
        filter.value = filter.value.copy(domains = domains)
    }

    fun setBlockedFilter(b: BlockedFilter) {
        filter.value = filter.value.copy(blocked = b)
    }

    fun clearFilters() {
        filter.value = FilterState()
    }

    fun toggleBlocked(domain: String, currentlyBlocked: Boolean) {
        viewModelScope.launch {
            repository.setBlocked(domain, !currentlyBlocked)
        }
    }

    fun toggleSort(column: SortColumn) {
        val current = sort.value
        sort.value = if (current.column == column) {
            current.copy(descending = !current.descending)
        } else {
            val desc = column == SortColumn.Count || column == SortColumn.LastSeen
            SortState(column = column, descending = desc)
        }
    }

    fun clear() {
        viewModelScope.launch { repository.clear() }
    }

    private fun comparatorFor(sortState: SortState): Comparator<DomainStat> {
        val base: Comparator<DomainStat> = when (sortState.column) {
            SortColumn.App -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.appLabel }
            SortColumn.Domain -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.domain }
            SortColumn.Count -> compareBy { it.count }
            SortColumn.LastSeen -> compareBy { it.lastSeenMs }
        }
        val withTie = base
            .thenByDescending { it.lastSeenMs }
            .thenBy { it.domain }
            .thenBy { it.appPackage }
        return if (sortState.descending) withTie.reversed() else withTie
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StatsViewModel(MainApp.instance.repository) as T
        }
    }

    companion object {
        const val PLACEHOLDER_PKG = ""
        const val PLACEHOLDER_LABEL = "—"
    }
}
