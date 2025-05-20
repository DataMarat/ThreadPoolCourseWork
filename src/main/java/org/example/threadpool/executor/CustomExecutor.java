package org.example.threadpool.executor;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;

/**
 * CustomExecutor defines the contract for a configurable thread pool.
 * It supports task submission, execution, and graceful or immediate shutdown.
 */
public interface CustomExecutor extends Executor {

    @Override
    void execute(Runnable command);

    <T> Future<T> submit(Callable<T> callable);

    void shutdown();

    void shutdownNow();
}
