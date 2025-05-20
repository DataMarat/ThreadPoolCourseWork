package org.example.threadpool.executor;

import org.example.threadpool.internal.TaskQueue;
import org.example.threadpool.internal.ThreadFactoryImpl;
import org.example.threadpool.internal.Worker;
import org.example.threadpool.policy.RejectionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CustomThreadPool is a thread pool implementation with support for
 * task queueing, dynamic thread creation, and rejection policies.
 */
public class CustomThreadPool implements CustomExecutor {

    private static final Logger logger = LoggerFactory.getLogger(CustomThreadPool.class);

    // Основные параметры пула
    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final int minSpareThreads;

    // Служебные переменные
    private final AtomicInteger threadCount = new AtomicInteger(0);
    private final BlockingQueue<Runnable> globalQueue;
    private final Set<Worker> workers = ConcurrentHashMap.newKeySet();
    private final ThreadFactory threadFactory;
    private final RejectionPolicy rejectionPolicy;

    private volatile boolean isShutdown = false;

    /**
     * Constructs the custom thread pool with provided configuration.
     */
    public CustomThreadPool(int corePoolSize,
                            int maxPoolSize,
                            long keepAliveTime,
                            TimeUnit timeUnit,
                            int queueSize,
                            int minSpareThreads,
                            RejectionPolicy rejectionPolicy) {

        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
        this.queueSize = queueSize;
        this.minSpareThreads = minSpareThreads;
        this.rejectionPolicy = rejectionPolicy;

        // Очередь с ограничением по размеру
        this.globalQueue = new ArrayBlockingQueue<>(queueSize);
        this.threadFactory = new ThreadFactoryImpl("MyPool-worker");

        // Инициализация базовых потоков
        for (int i = 0; i < corePoolSize; i++) {
            addWorker();
        }
    }

    /**
     * Submits a runnable task for execution.
     */
    @Override
    public void execute(Runnable command) {
        if (isShutdown) {
            logger.warn("[Pool] Rejecting task due to shutdown.");
            rejectionPolicy.reject(command, this);
            return;
        }

        // Добавляем задачу или применяем политику отказа
        boolean offered = globalQueue.offer(command);
        if (!offered) {
            logger.warn("[Rejected] Task was rejected due to overload!");
            rejectionPolicy.reject(command, this);
        } else {
            logger.info("[Pool] Task accepted into queue: {}", command);
            maybeAddWorker();
        }
    }

    /**
     * Submits a Callable task for execution and returns a Future.
     */
    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        FutureTask<T> future = new FutureTask<>(callable);
        execute(future);
        return future;
    }

    /**
     * Gracefully shuts down the thread pool.
     */
    @Override
    public void shutdown() {
        isShutdown = true;
        logger.info("[Pool] Shutdown initiated.");
    }

    /**
     * Immediately shuts down the thread pool and interrupts workers.
     */
    @Override
    public void shutdownNow() {
        isShutdown = true;
        logger.info("[Pool] Immediate shutdown initiated.");

        for (Worker worker : workers) {
            worker.interrupt();
        }
    }

    /**
     * Adds a new worker thread if under maxPoolSize and needed.
     */
    private void maybeAddWorker() {
        // Если задач много, потоков мало — создаём нового воркера
        if (threadCount.get() < maxPoolSize &&
                (globalQueue.size() > 0 || threadCount.get() < minSpareThreads)) {
            addWorker();
        }
    }

    /**
     * Creates and starts a new worker thread.
     */
    private void addWorker() {
        Worker worker = new Worker(globalQueue, this, keepAliveTime, timeUnit);
        Thread thread = threadFactory.newThread(worker);
        workers.add(worker);
        threadCount.incrementAndGet();
        thread.start();
    }

    /**
     * Removes a finished worker from tracking.
     */
    public void notifyWorkerTerminated(Worker worker) {
        workers.remove(worker);
        threadCount.decrementAndGet();
        logger.info("[Worker] {} terminated.", Thread.currentThread().getName());
    }

    public boolean isShutdown() {
        return isShutdown;
    }
}
