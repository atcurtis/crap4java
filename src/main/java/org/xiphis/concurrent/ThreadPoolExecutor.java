package org.xiphis.concurrent;

import java.util.List;
import java.util.concurrent.*;

public class ThreadPoolExecutor extends AbstractExecutorService {

    private final TPE executorService;

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        this(new TPE(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue));
    }

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        this(new TPE(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory));
    }

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        this(new TPE(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler));
    }

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        this(new TPE(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler));
    }

    private ThreadPoolExecutor(TPE executorService) {
        this.executorService = executorService;
        executorService.outer = this;
    }

    protected void terminated() {
    }

    protected void beforeExecute(Thread t, Runnable r) {
    }

    protected void afterExecute(Runnable r, Throwable t) {
    }

    @Override
    public void shutdown() {
        executorService.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return executorService.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return executorService.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return executorService.isTerminated();
    }

    @Override
    public boolean awaitTermination(long l, TimeUnit timeUnit) throws InterruptedException {
        return executorService.awaitTermination(l, timeUnit);
    }

    @Override
    public void execute(Runnable runnable) {
        executorService.execute(runnable);
    }

    private static class TPE extends java.util.concurrent.ThreadPoolExecutor {
        private ThreadPoolExecutor outer;

        public TPE(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
        }

        public TPE(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
        }

        public TPE(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
        }

        public TPE(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        }

        @Override
        protected void terminated() {
            outer.terminated();
        }

        @Override
        protected void beforeExecute(Thread t, Runnable r) {
            outer.beforeExecute(t, r);
        }

        @Override
        protected void afterExecute(Runnable r, Throwable t) {
            outer.afterExecute(r, t);
        }
    }
}
