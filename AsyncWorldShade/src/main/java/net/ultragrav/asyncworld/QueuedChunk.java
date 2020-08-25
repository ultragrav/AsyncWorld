package net.ultragrav.asyncworld;

import lombok.AllArgsConstructor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@AllArgsConstructor
public class QueuedChunk {
    private final AsyncChunk chunk;
    private List<Runnable> callbacks;

    public synchronized AsyncChunk getChunk() {
        return this.chunk;
    }
    public synchronized List<Runnable> getCallbacks() {
        return this.callbacks;
    }

    public synchronized void addCallback(Runnable callback) {
        this.callbacks.add(callback);
    }
}
