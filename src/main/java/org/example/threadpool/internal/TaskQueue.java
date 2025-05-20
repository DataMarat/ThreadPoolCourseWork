package org.example.threadpool.internal;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * TaskQueue wraps a BlockingQueue to provide a flexible interface
 * for task management and future extensions like metrics or routing.
 */
public class TaskQueue {

    private final BlockingQueue<Runnable> queue;

    public TaskQueue(BlockingQueue<Runnable> queue) {
        this.queue = queue;
    }

    public boolean offer(Runnable task) {
        // Пытаемся вставить задачу, если есть место
        return queue.offer(task);
    }

    public void put(Runnable task) throws InterruptedException {
        // Блокирующая вставка, если очередь полна
        queue.put(task);
    }

    public Runnable take() throws InterruptedException {
        // Блокирующее извлечение задачи
        return queue.take();
    }

    public Runnable poll(long timeout, TimeUnit unit) throws InterruptedException {
        // Извлечение с таймаутом
        return queue.poll(timeout, unit);
    }

    public int size() {
        return queue.size();
    }
}
