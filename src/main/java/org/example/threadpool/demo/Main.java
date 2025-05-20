package org.example.threadpool.demo;

import org.example.threadpool.executor.CustomThreadPool;
import org.example.threadpool.policy.AbortPolicy;

import java.util.concurrent.TimeUnit;

/**
 * Demonstrates the behavior of the custom thread pool under load and shutdown.
 */
public class Main {

    public static void main(String[] args) throws InterruptedException {
        // Инициализируем пул с параметрами из задания
        CustomThreadPool pool = new CustomThreadPool(
                2,                 // corePoolSize
                4,                 // maxPoolSize
                5,                 // keepAliveTime
                TimeUnit.SECONDS,  // timeUnit
                5,                 // queueSize
                1,                 // minSpareThreads
                new AbortPolicy()  // RejectionPolicy
        );

        // Создаём и отправляем 10 задач, каждая работает по 3 секунды
        for (int i = 1; i <= 10; i++) {
            int taskId = i;
            pool.execute(() -> {
                String threadName = Thread.currentThread().getName();
                System.out.println("[Task " + taskId + "] Started in " + threadName);
                try {
                    Thread.sleep(3000); // имитация работы
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                System.out.println("[Task " + taskId + "] Finished in " + threadName);
            });
        }

        // Даём время на выполнение
        Thread.sleep(10000);

        // Завершаем работу пула
        pool.shutdown();

        // Ждём завершения всех потоков
        Thread.sleep(5000);

        System.out.println("[Main] Demo complete.");
    }
}
