package org.xiphis.concurrent;

import java.util.concurrent.RunnableFuture;

public interface RunnableCompletion<V> extends RunnableFuture<V>, CompletionFuture<V> {
}
