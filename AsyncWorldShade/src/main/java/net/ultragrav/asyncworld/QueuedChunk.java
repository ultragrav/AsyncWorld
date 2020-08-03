package net.ultragrav.asyncworld;

import lombok.AllArgsConstructor;

import java.util.concurrent.CompletableFuture;

@AllArgsConstructor
public class QueuedChunk {
    private final AsyncChunk chunk;
    private CompletableFuture<Void> callback;

    public synchronized AsyncChunk getChunk() {
        return this.chunk;
    }
    public synchronized CompletableFuture<Void> getCallback() {
        return this.callback;
    }

    public synchronized void setCallback(CompletableFuture<Void> future) {
        this.callback = future;
    }
}
