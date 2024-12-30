package org.xiphis.concurrent;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

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

    static AbstractExecutorService of(Executor executor) {
        if (executor instanceof AbstractExecutorService aes) {
            return aes;
        }
        if (executor instanceof ExecutorService es) {
            return new AbstractExecutorService() {
                @Override
                public void shutdown() {
                    es.shutdown();
                }

                @Override
                public List<Runnable> shutdownNow() {
                    return es.shutdownNow();
                }

                @Override
                public boolean isShutdown() {
                    return es.isShutdown();
                }

                @Override
                public boolean isTerminated() {
                    return es.isTerminated();
                }

                @Override
                public boolean awaitTermination(long l, TimeUnit timeUnit) throws InterruptedException {
                    return es.awaitTermination(l, timeUnit);
                }

                @Override
                public void execute(Runnable runnable) {

                }
            };
        }
        //TODO fixme
        return null;
    }

    private static class ExecutorServiceWrap<E extends ExecutorService> extends ExecutorWrap<E> {
        private ExecutorServiceWrap(E executor) {
            super(executor);
        }

        @Override
        public void shutdown() {
            super.shutdown();
            executor.shutdown();
        }

        @Override
        public List<Runnable> shutdownNow() {
            super.shutdown();
            return executor.shutdownNow();
        }

        @Override
        public boolean isShutdown() {
            return executor.isShutdown();
        }

        @Override
        public boolean isTerminated() {
            return executor.isTerminated();
        }

        @Override
        public boolean awaitTermination(long l, TimeUnit timeUnit) throws InterruptedException {
            return executor.awaitTermination(l, timeUnit);
        }
    }

    private static class ExecutorWrap<E extends Executor> extends AbstractExecutorService {

        protected final E executor;
        protected boolean shutdown;

        private ExecutorWrap(E executor) {
            this.executor = executor;
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return false;
        }

        @Override
        public boolean awaitTermination(long l, TimeUnit timeUnit) throws InterruptedException {
            return false;
        }

        @Override
        public void execute(Runnable runnable) {
            executor.execute(runnable);
        }
    }

}
