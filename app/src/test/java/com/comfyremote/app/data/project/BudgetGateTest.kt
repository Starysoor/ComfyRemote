package com.comfyremote.app.data.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetGateTest {

    @Test
    fun nullOrZeroBudgetDisablesTheGate() {
        assertEquals(BudgetGate.Decision.Allowed, BudgetGate.check(100.0, 500.0, null))
        assertEquals(BudgetGate.Decision.Allowed, BudgetGate.check(100.0, 500.0, 0.0))
    }

    @Test
    fun reachingTheBudgetExactlyIsStillAllowed() {
        val decision = BudgetGate.check(spentEstimate = 8.0, newCost = 2.0, budget = 10.0)
        assertEquals(BudgetGate.Decision.Allowed, decision)
    }

    @Test
    fun goingOnePointPastTheBudgetBlocks() {
        val decision = BudgetGate.check(spentEstimate = 8.1, newCost = 2.0, budget = 10.0)
        assertTrue(decision is BudgetGate.Decision.Blocked)
        decision as BudgetGate.Decision.Blocked
        assertEquals(8.1, decision.spentEstimate, 1e-9)
        assertEquals(2.0, decision.newCost, 1e-9)
        assertEquals(10.0, decision.budget, 1e-9)
        assertEquals(10.1, decision.projectedTotal, 1e-9)
    }

    @Test
    fun beingOverBudgetBlocksEvenWhenTheNewBatchIsFree() {
        // newCost is clamped to >= 0; a project already past its budget blocks.
        assertTrue(BudgetGate.check(12.0, -5.0, 10.0) is BudgetGate.Decision.Blocked)
        // Within-budget project with a zero-cost batch stays allowed.
        assertEquals(BudgetGate.Decision.Allowed, BudgetGate.check(5.0, -5.0, 10.0))
    }

    @Test
    fun zeroSpendComparesAgainstTheBudgetDirectly() {
        assertTrue(BudgetGate.check(0.0, 11.0, 10.0) is BudgetGate.Decision.Blocked)
        assertEquals(BudgetGate.Decision.Allowed, BudgetGate.check(0.0, 10.0, 10.0))
    }
}
