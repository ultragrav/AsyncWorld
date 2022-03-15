package net.ultragrav.asyncworld.scheduler;

public class SyncTask {
    private final Runnable runnable;
    private volatile boolean completed = false;

    public SyncTask(Runnable runnable) {
        this.runnable = runnable;
    }

    public Runnable getRunnable() {
        return runnable;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}
