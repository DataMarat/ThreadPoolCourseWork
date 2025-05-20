package org.example.threadpool.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ThreadFactoryImpl creates new threads with custom names and logs their creation.
 */
public class ThreadFactoryImpl implements ThreadFactory {

    private static final Logger logger = LoggerFactory.getLogger(ThreadFactoryImpl.class);

    private final AtomicInteger counter = new AtomicInteger(1);
    private final String namePrefix;

    public ThreadFactoryImpl(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    @Override
    public Thread newThread(Runnable r) {
        String threadName = namePrefix + "-" + counter.getAndIncrement();
        logger.info("[ThreadFactory] Creating new thread: {}", threadName);

        // Создаём поток с заданным именем
        Thread thread = new Thread(r, threadName);

        // Можно при необходимости настроить daemon / priority
        return thread;
    }
}
