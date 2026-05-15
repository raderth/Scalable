package net.raderth.scalable.util;

/**
 * Tracks time within a single server tick, shared across all worlds.
 * In automatic mode (-1) the budget is 40 ms (leaving 10 ms headroom).
 */
public final class TickBudget {

    public static final long DEFAULT_BUDGET_MS = 40L;  // leave 10ms free

    private volatile long fixedBudgetMs = DEFAULT_BUDGET_MS;
    private volatile long serverTickStartNs;
    private volatile long effectiveBudgetNs;

    /** Called once at the start of every server tick, before any world ticks. */
    public void startServerTick(long tickStartNs) {
        this.serverTickStartNs = tickStartNs;
        if (fixedBudgetMs < 0) {
            // Automatic: cap at 40ms to leave 10ms free in a 50ms tick window.
            this.effectiveBudgetNs = 40L * 1_000_000L;
        } else {
            this.effectiveBudgetNs = fixedBudgetMs * 1_000_000L;
        }
    }

    /** True when total elapsed time since tick start has exceeded the budget. */
    public boolean isOverBudget() {
        return (System.nanoTime() - serverTickStartNs) >= effectiveBudgetNs;
    }

    public void setFixed(long ms) { this.fixedBudgetMs = ms; }
    public void setAutomatic()    { this.fixedBudgetMs = -1; }
    public boolean isAutomatic()  { return fixedBudgetMs < 0; }

    /** Elapsed ms since tick start, for the query command. */
    public long elapsedMs() {
        return (System.nanoTime() - serverTickStartNs) / 1_000_000L;
    }

    public long getFixedBudgetMs() { return fixedBudgetMs; }
}