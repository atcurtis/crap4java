package org.xiphis.concurrent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class CancellableFuture<T> extends CompletableFuture<T> {

    private static final Logger LOG = LoggerFactory.getLogger(CancellableFuture.class);

    private final Executor defaultExecutor;

    public CancellableFuture(Executor defaultExecutor) {
        this.defaultExecutor = defaultExecutor;
    }

    @Override
    public <U> CompletableFuture<U> newIncompleteFuture() {
        final CancellableFuture<T> self = this;
        return new CancellableFuture<>(defaultExecutor) {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                if (cancel0(mayInterruptIfRunning)) {
                    if (self.cancel(mayInterruptIfRunning)) {
                        return true;
                    }
                    LOG.atDebug().log("partial cancellation");
                    return true;
                }
                return false;
            }
        };
    }

    @Override
    public Executor defaultExecutor() {
        return defaultExecutor != null ? defaultExecutor : super.defaultExecutor();
    }

    protected boolean cancel0(boolean mayInterruptIfRunning) {
        return super.cancel(mayInterruptIfRunning);
    }
}
