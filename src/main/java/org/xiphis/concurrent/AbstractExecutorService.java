package org.xiphis.concurrent;

import java.util.concurrent.Callable;

public abstract class AbstractExecutorService extends java.util.concurrent.AbstractExecutorService {

    @Override
    protected <T> RunnableCompletion<T> newTaskFor(Runnable runnable, T value) {
        return new CompletableTask<>(this, runnable, value);
    }

    @Override
    protected <T> RunnableCompletion<T> newTaskFor(Callable<T> callable) {
        return new CompletableTask<>(this, callable);
    }

    @Override
    public CompletionFuture<?> submit(Runnable task) {
        return (CompletionFuture<?>) super.submit(task);
    }

    @Override
    public <T> CompletionFuture<T> submit(Runnable task, T result) {
        return (CompletionFuture<T>) super.submit(task, result);
    }

    @Override
    public <T> CompletionFuture<T> submit(Callable<T> task) {
        return (CompletionFuture<T>) super.submit(task);
    }
}
