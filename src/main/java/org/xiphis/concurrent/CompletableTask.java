package org.xiphis.concurrent;

import org.xiphis.util.RunOnce;

import java.util.concurrent.*;

public class CompletableTask<V> extends CancellableFuture<V> implements RunnableCompletion<V> {
    private final RunOnce runOnce;
    private volatile Thread current;
    private boolean interrupted;

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Callable}.
     *
     * @param  callable the callable task
     * @throws NullPointerException if the callable is null
     */
    public CompletableTask(ExecutorService executor, Callable<V> callable) {
        super(executor);
        if (callable == null)
            throw new NullPointerException();
        CompletableFuture<Callable<V>> start = new CompletableFuture<>() {
            @Override
            @SuppressWarnings({"unchecked", "rawtypes"})
            public <U> CompletableFuture<U> newIncompleteFuture() {
                return (CompletableFuture) CompletableTask.this;
            }
        };
        runOnce = RunOnce.of(() -> start.complete(callable));
        CompletableFuture<V> future = start.thenApply(this::invoke);
        assert future == this;
        future.exceptionally(ex -> null).thenRun(this::done);
    }

    protected boolean isInterrupted() {
        return interrupted;
    }

    protected void done() {
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (super.cancel(mayInterruptIfRunning)) {
            Thread thread = current;
            if (mayInterruptIfRunning && thread != null) {
                thread.interrupt();
            }
            return true;
        }
        return false;
    }

    private V invoke(Callable<V> task) {
        try {
            current = Thread.currentThread();
            return task.call();
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            interrupted = e instanceof InterruptedException;
            throw new CompletionException(e);
        } finally {
            current = null;
            interrupted |= Thread.interrupted();
        }
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Runnable}, and arrange that {@code get} will return the
     * given result on successful completion.
     *
     * @param runnable the runnable task
     * @param result the result to return on successful completion. If
     * you don't need a particular result, consider using
     * constructions of the form:
     * {@code Future<?> f = new FutureTask<Void>(runnable, null)}
     * @throws NullPointerException if the runnable is null
     */
    public CompletableTask(ExecutorService executorService, Runnable runnable, V result) {
        this(executorService, Executors.callable(runnable, result));

    }
    @Override
    public void run() {
        runOnce.run();
    }
}
