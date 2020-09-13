package net.ultragrav.asyncworld.relighter;

import net.ultragrav.asyncworld.AsyncChunk;

public interface Relighter {
    enum RelightAction {
        ACTION_RELIGHT,
        ACTION_SKIP_SOLID,
        ACTION_SKIP_AIR;
    }
    void queueSkyRelight(AsyncChunk chunk, RelightAction[] sectionMask);
    void queueRelight(AsyncChunk chunk, RelightAction[] sectionMask);
}
