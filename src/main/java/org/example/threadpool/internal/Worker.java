package org.example.threadpool.internal;

import org.example.threadpool.executor.CustomThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class Worker implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(Worker.class);

    private final BlockingQueue<Runnable> personalQueue;
    private final CustomThreadPool pool;
    private final long keepAliveTime;
    private final TimeUnit timeUnit;
    private volatile boolean running = true;
    private Thread thread; // Ссылка на собственный поток

    public Worker(BlockingQueue<Runnable> personalQueue,
                  CustomThreadPool pool,
                  long keepAliveTime,
                  TimeUnit timeUnit) {
        this.personalQueue = personalQueue;
        this.pool = pool;
        this.keepAliveTime = keepAliveTime;
        this.timeUnit = timeUnit;
    }

    // Вызывается пулом после создания потока
    public void setThread(Thread thread) {
        this.thread = thread;
    }

    @Override
    public void run() {
        this.thread = Thread.currentThread(); // safety net
        logger.info("[ThreadFactory] Creating new thread: {}", thread.getName());

        try {
            while (running && !thread.isInterrupted()) {
                Runnable task = personalQueue.poll(keepAliveTime, timeUnit);

                if (task != null) {
                    if (pool.isShutdown()) {
                        logger.info("[Worker] {} skipping task due to shutdown.", thread.getName());
                        continue;
                    }

                    logger.info("[Worker] {} executes {}", thread.getName(), task);
                    try {
                        task.run();
                    } catch (Throwable t) {
                        logger.error("[Worker] {} encountered error: {}", thread.getName(), t.toString());
                    }
                } else {
                    logger.info("[Worker] {} idle timeout, stopping.", thread.getName());
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            pool.notifyWorkerTerminated(this, personalQueue);
        }
    }

    public void interrupt() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }
}

