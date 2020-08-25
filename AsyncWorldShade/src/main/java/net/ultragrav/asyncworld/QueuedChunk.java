package net.ultragrav.asyncworld;

import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@AllArgsConstructor
public class QueuedChunk {
    private final AsyncChunk chunk;
    private final List<Runnable> callbacks = new ArrayList<>();

    public QueuedChunk(AsyncChunk chunk, Runnable... callbacks) {
        this.chunk = chunk;
        this.callbacks.addAll(Arrays.asList(callbacks));
    }

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
