package org.xiphis.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class ConstrainedExecutor extends AbstractExecutorService {

    private static final AtomicIntegerFieldUpdater<ConstrainedExecutor> RUNNING
            = AtomicIntegerFieldUpdater.newUpdater(ConstrainedExecutor.class, "running");

    private final AbstractExecutorService executor;
    private final BlockingQueue<RunnableCompletion<?>> pending;
    private final Semaphore semaphore;
    private final CountDownLatch terminated;
    private volatile boolean shutdown;
    private volatile int running;

    public ConstrainedExecutor(Executor executor, int limit) {
        this(of(executor), limit, new LinkedBlockingQueue<>());
    }

    public ConstrainedExecutor(AbstractExecutorService executor, int limit, BlockingQueue<RunnableCompletion<?>> queue) {
        this.executor = Objects.requireNonNull(executor, "executor");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be a positive integer");
        }
        pending = Objects.requireNonNull(queue, "queue");
        semaphore = new Semaphore(limit);
        terminated = new CountDownLatch(1);
    }

    @Override
    public void shutdown() {
        shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown();
        ArrayList<Runnable> tasks = new ArrayList<>();
        pending.drainTo(tasks);
        return tasks;
    }

    @Override
    public boolean isShutdown() {
        return shutdown || executor.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return executor.isTerminated() || (isShutdown() && pending.isEmpty());
    }

    @Override
    public boolean awaitTermination(long l, TimeUnit timeUnit) throws InterruptedException {
        return terminated.await(l, timeUnit);
    }

    private void release() {
        semaphore.release();
        if (shutdown && pending.isEmpty() && running == 0) {
            terminated.countDown();
            terminated();
        }
    }

    protected void terminated() {
    }

    private void onComplete(Object o, Throwable ex) {
        boolean doRelease = true;
        try {
            RunnableCompletion<?> next = pending.poll();
            if (next != null) {
                executor.execute(next);
                doRelease = false;
                next.whenComplete(this::onComplete);
            }
        } finally {
            if (doRelease) {
                RUNNING.decrementAndGet(this);
                release();
            }
        }
    }

    @Override
    public void execute(Runnable runnable) {
        if (shutdown) {
            throw new RejectedExecutionException();
        }
        RUNNING.incrementAndGet(this);
        boolean isRunning = false;
        boolean doRelease = false;
        try {
            if (semaphore.tryAcquire()) {
                doRelease = true;
                CompletionFuture<?> task = executor.submit(runnable);
                doRelease = false;
                isRunning = true;
                task.whenComplete(this::onComplete);
            } else {
                RunnableCompletion<?> task = executor.newTaskFor(runnable, null);
                if (!pending.offer(task)) {
                    reject(task);
                }
            }
        } finally {
            if (!isRunning) {
                RUNNING.decrementAndGet(this);
            }
            if (doRelease) {
                release();
            }
        }
    }

    protected void reject(RunnableCompletion<?> task) {
        task.cancel(false);
        throw new RejectedExecutionException();
    }
}
