package com.comfyremote.app.feature.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.comfyremote.app.AppContainer
import com.comfyremote.app.data.cloud.CloudHealth
import com.comfyremote.app.data.cloud.CloudHealthStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

data class CloudProviderRow(
    val id: String,
    val label: String,
    val summary: String,
    val configured: Boolean,
    val checking: Boolean = false,
    val health: CloudHealth? = null,
)

data class CloudServicesUiState(
    val providers: List<CloudProviderRow> = emptyList(),
    val spendToday: Int = 0,
    val spendMonth: Int = 0,
    val spendCount: Int = 0,
    /** This month's local estimate, grouped by cloud provider id. */
    val spendByProvider: Map<String, Int> = emptyMap(),
)

/** Cloud Services hub: per-provider health plus local spend summary. */
class CloudServicesViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(
        CloudServicesUiState(
            providers = container.cloudProviders.info().map { info ->
                CloudProviderRow(
                    id = info.id,
                    label = info.label,
                    summary = info.summary,
                    configured = info.configured,
                )
            },
        )
    )
    val state: StateFlow<CloudServicesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.cloudSpendStore.entries.collect { entries ->
                val monthStart = startOfMonth()
                val todayStart = startOfToday()
                _state.update {
                    it.copy(
                        spendToday = entries.sumOf { entry ->
                            if (entry.createdAt >= todayStart) entry.points ?: 0 else 0
                        },
                        spendMonth = entries.sumOf { entry ->
                            if (entry.createdAt >= monthStart) entry.points ?: 0 else 0
                        },
                        spendCount = entries.size,
                        spendByProvider = entries.asSequence()
                            .filter { entry -> entry.createdAt >= monthStart && entry.providerId != null }
                            .groupBy { entry -> entry.providerId.orEmpty() }
                            .mapValues { (_, rows) -> rows.sumOf { row -> row.points ?: 0 } },
                    )
                }
            }
        }
        checkAll()
    }

    fun checkAll() = _state.value.providers.forEach { check(it.id) }

    fun check(providerId: String) {
        val provider = container.cloudProviders.byId(providerId) ?: return
        _state.update { state ->
            state.copy(
                providers = state.providers.map { row ->
                    if (row.id != providerId) row
                    else row.copy(
                        checking = true,
                        health = (row.health ?: CloudHealth(CloudHealthStatus.CHECKING))
                            .copy(status = CloudHealthStatus.CHECKING),
                    )
                },
            )
        }
        viewModelScope.launch {
            val health = withContext(Dispatchers.IO) { provider.check() }
            _state.update { state ->
                state.copy(
                    providers = state.providers.map { row ->
                        if (row.id == providerId) row.copy(checking = false, health = health) else row
                    },
                )
            }
        }
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
