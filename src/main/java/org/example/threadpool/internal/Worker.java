package org.example.threadpool.internal;

import org.example.threadpool.executor.CustomThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Worker is a thread responsible for executing tasks from the task queue.
 * It terminates itself if idle for keepAliveTime and exceeds core pool size.
 */
public class Worker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Worker.class);

    private final BlockingQueue<Runnable> queue;
    private final CustomThreadPool pool;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private volatile boolean running = true;

    public Worker(BlockingQueue<Runnable> queue,
                  CustomThreadPool pool,
                  long keepAliveTime,
                  TimeUnit timeUnit) {
        this.queue = queue;
        this.pool = pool;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
    }

    @Override
    public void run() {
        Thread currentThread = Thread.currentThread();
        logger.info("[ThreadFactory] Creating new thread: {}", currentThread.getName());

        try {
            while (running && !Thread.currentThread().isInterrupted()) {

                // Получаем задачу с таймаутом
                Runnable task = queue.poll(keepAliveTime, timeUnit);

                if (task != null) {
                    // Выполняем задачу, если пул не завершён
                    if (pool.isShutdown()) {
                        logger.info("[Worker] {} skipping task due to shutdown.", currentThread.getName());
                        continue;
                    }

                    logger.info("[Worker] {} executes {}", currentThread.getName(), task);
                    try {
                        task.run();
                    } catch (Throwable t) {
                        logger.error("[Worker] {} encountered error: {}", currentThread.getName(), t.toString());
                    }

                } else {
                    // Если таймаут без задач — пытаемся завершить воркера
                    logger.info("[Worker] {} idle timeout, stopping.", currentThread.getName());
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.notifyWorkerTerminated(this);
        }
    }

    public void interrupt() {
        running = false;
        Thread.currentThread().interrupt();
    }
}
