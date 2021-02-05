package net.ultragrav.asyncworld.test.utils;

import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Extension of ReentrantLock with a method to perform a Runnable
 */
public class CustomLock extends ReentrantLock {
    public CustomLock() {
        super();
    }

    public CustomLock(boolean fair) {
        super(fair);
    }

    public <T> T perform(Supplier<T> sup) {
        this.lock();
        try {
            return sup.get();
        } finally {
            this.unlock();
        }
    }

    public void perform(Runnable run) {
        this.lock();
        try {
            run.run();
        } finally {
            this.unlock();
        }
    }
}
