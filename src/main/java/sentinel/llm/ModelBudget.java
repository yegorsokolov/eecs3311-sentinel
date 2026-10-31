package sentinel.llm;

import java.util.concurrent.atomic.AtomicInteger;

/** One explicit operation/run shares this counter across requests, retries and repairs. */
public final class ModelBudget {
    private final int limit;
    private final AtomicInteger used = new AtomicInteger();

    public ModelBudget() { this(6); }
    public ModelBudget(int limit) {
        if (limit < 1 || limit > 6) throw new IllegalArgumentException("Model call limit must be from 1 to 6.");
        this.limit = limit;
    }
    public void acquire() {
        while (true) {
            int before = used.get();
            if (before >= limit) throw new ModelException(ModelException.Code.BUDGET_EXHAUSTED, "Model-call budget exhausted.");
            if (used.compareAndSet(before, before + 1)) return;
        }
    }
    public int used() { return used.get(); }
    public int remaining() { return limit - used.get(); }
}
