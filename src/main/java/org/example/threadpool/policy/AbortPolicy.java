package org.example.threadpool.policy;

import org.example.threadpool.executor.CustomThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AbortPolicy rejects the task and logs a warning without executing it.
 */
public class AbortPolicy implements RejectionPolicy {

    private static final Logger logger = LoggerFactory.getLogger(AbortPolicy.class);

    @Override
    public void reject(Runnable task, CustomThreadPool pool) {
        // Просто логируем отказ и ничего не делаем
        logger.warn("[Rejected] Task {} was rejected due to overload!", task);
    }
}
