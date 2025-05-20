package org.example.threadpool.executor;

import org.example.threadpool.internal.Worker;
import org.example.threadpool.internal.ThreadFactoryImpl;
import org.example.threadpool.policy.RejectionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CustomThreadPool distributes tasks to per-worker queues using Round Robin strategy.
 */
public class CustomThreadPool implements CustomExecutor {

    private static final Logger logger = LoggerFactory.getLogger(CustomThreadPool.class);

    private final int corePoolSize;
    private final int maxPoolSize;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private final int queueSize;
    private final int minSpareThreads;

    private final AtomicInteger threadCount = new AtomicInteger(0);
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    private final List<BlockingQueue<Runnable>> workerQueues = new ArrayList<>();
    private final Set<Worker> workers = ConcurrentHashMap.newKeySet();
    private final ThreadFactory threadFactory;
    private final RejectionPolicy rejectionPolicy;

    private volatile boolean isShutdown = false;

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
        this.threadFactory = new ThreadFactoryImpl("MyPool-worker");

        // Инициализируем минимальное количество потоков
        for (int i = 0; i < corePoolSize; i++) {
            addWorker();
        }
    }

    @Override
    public void execute(Runnable command) {
        if (isShutdown) {
            logger.warn("[Pool] Rejecting task due to shutdown.");
            rejectionPolicy.reject(command, this);
            return;
        }

        int currentIndex = roundRobinIndex.getAndIncrement();
        BlockingQueue<Runnable> queue;

        synchronized (workerQueues) {
            if (workerQueues.isEmpty()) {
                logger.warn("[Pool] No workers available, rejecting task.");
                rejectionPolicy.reject(command, this);
                return;
            }

            // Round Robin: выбираем очередь по индексу
            queue = workerQueues.get(currentIndex % workerQueues.size());
        }

        boolean offered = queue.offer(command);
        if (!offered) {
            logger.warn("[Rejected] Task was rejected due to overload!");
            rejectionPolicy.reject(command, this);
        } else {
            logger.info("[Pool] Task accepted into queue #{}: {}", currentIndex % workerQueues.size(), command);
            maybeAddWorker();
        }
    }

    @Override
    public <T> Future<T> submit(Callable<T> callable) {
        FutureTask<T> future = new FutureTask<>(callable);
        execute(future);
        return future;
    }

    @Override
    public void shutdown() {
        isShutdown = true;
        logger.info("[Pool] Shutdown initiated.");
    }

    @Override
    public List<Runnable> shutdownNow() {
        isShutdown = true;
        logger.info("[Pool] Immediate shutdown initiated.");

        List<Runnable> pendingTasks = new ArrayList<>();

        // Собираем все оставшиеся задачи из всех очередей воркеров
        synchronized (workerQueues) {
            for (BlockingQueue<Runnable> queue : workerQueues) {
                queue.drainTo(pendingTasks);
            }
        }

        // Прерываем потоки воркеров (если реализовано правильно)
        for (Worker worker : workers) {
            worker.interrupt();
        }

        return pendingTasks;
    }

    private void maybeAddWorker() {
        if (threadCount.get() < maxPoolSize &&
                (threadCount.get() < minSpareThreads)) {
            addWorker();
        }
    }

    private void addWorker() {
        BlockingQueue<Runnable> personalQueue = new ArrayBlockingQueue<>(queueSize);
        Worker worker = new Worker(personalQueue, this, keepAliveTime, timeUnit);
        Thread thread = threadFactory.newThread(worker);
        worker.setThread(thread); // Важно!

        synchronized (workerQueues) {
            workerQueues.add(personalQueue);
        }

        workers.add(worker);
        threadCount.incrementAndGet();
        thread.start();
    }

    public void notifyWorkerTerminated(Worker worker, BlockingQueue<Runnable> queue) {
        workers.remove(worker);
        threadCount.decrementAndGet();

        synchronized (workerQueues) {
            workerQueues.remove(queue);
        }

        logger.info("[Worker] {} terminated.", Thread.currentThread().getName());
    }

    public boolean isShutdown() {
        return isShutdown;
    }
}
