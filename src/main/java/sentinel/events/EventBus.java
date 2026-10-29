package sentinel.events;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import sentinel.domain.ProgressEvent;

/** Observer dispatch isolated from case transactions: a failing view cannot undo accepted work. */
public final class EventBus {
    private final CopyOnWriteArrayList<ProgressListener> listeners = new CopyOnWriteArrayList<>();
    private final AtomicInteger failures = new AtomicInteger();

    public void subscribe(ProgressListener listener) { listeners.addIfAbsent(Objects.requireNonNull(listener)); }
    public void unsubscribe(ProgressListener listener) { listeners.remove(Objects.requireNonNull(listener)); }
    public int subscriberFailures() { return failures.get(); }

    public void publish(ProgressEvent event) {
        Objects.requireNonNull(event, "event");
        for (ProgressListener listener : listeners) {
            try { listener.update(event); }
            catch (RuntimeException ex) { failures.incrementAndGet(); }
        }
    }
}
