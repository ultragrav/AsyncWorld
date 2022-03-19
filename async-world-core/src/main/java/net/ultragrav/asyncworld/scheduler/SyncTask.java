package net.ultragrav.asyncworld.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;

public class SyncTask {
    private final Runnable runnable;
    private AtomicBoolean completed = new AtomicBoolean(false);

    public SyncTask(Runnable runnable) {
        this.runnable = runnable;
    }

    public Runnable getRunnable() {
        return runnable;
    }

    public boolean isCompleted() {
        return completed.get();
    }

    public boolean tryComplete() {
        return completed.compareAndSet(false, true);
    }
}
