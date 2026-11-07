package com.comfyremote.app.data.project

import com.comfyremote.app.data.generation.GenerationTask

/** Aggregated estimate spend for one project's generation tasks. */
data class TaskCostStats(val count: Int, val costSum: Double)

/**
 * Sums the estimated cloud cost of the tasks that belong to [projectId] via
 * the storyboard association. Pure so both the project home banner and the
 * pre-submission budget gate aggregate identically.
 */
fun taskCostStats(tasks: List<GenerationTask>, projectId: String): TaskCostStats {
    val project = tasks.filter { it.request.storyboardProjectId == projectId }
    return TaskCostStats(
        count = project.size,
        costSum = project.sumOf { it.request.estimatedCloudCost?.toDouble() ?: 0.0 },
    )
}

/**
 * Pre-submission budget enforcement for project shots (#57 P3): blocks cloud
 * submissions whose estimated cost would push the project past its stored
 * budget. Estimates only — the decision compares the sum of this project's
 * task cost estimates with the new batch estimate.
 */
object BudgetGate {

    sealed interface Decision {
        /** Budget absent/zero, or the new cost still fits. */
        data object Allowed : Decision

        /** Spent + new exceeds the budget; the caller must refuse to submit. */
        data class Blocked(
            val spentEstimate: Double,
            val newCost: Double,
            val budget: Double,
        ) : Decision {
            val projectedTotal: Double get() = spentEstimate + newCost
        }
    }

    /**
     * [spentEstimate] — sum of the project's task cost estimates;
     * [newCost] — estimate of the batch about to be submitted;
     * [budget] — project budget in points; null/zero disables the gate.
     * Reaching the budget exactly is still allowed; only going past blocks.
     */
    fun check(spentEstimate: Double, newCost: Double, budget: Double?): Decision {
        if (budget == null || budget <= 0.0) return Decision.Allowed
        val projected = spentEstimate + newCost.coerceAtLeast(0.0)
        return if (projected > budget) {
            Decision.Blocked(spentEstimate, newCost, budget)
        } else {
            Decision.Allowed
        }
    }
}
