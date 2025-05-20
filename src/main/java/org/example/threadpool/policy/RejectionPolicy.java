package org.example.threadpool.policy;

import org.example.threadpool.executor.CustomThreadPool;

/**
 * RejectionPolicy defines how to handle tasks that cannot be accepted by the pool.
 */
public interface RejectionPolicy {

    void reject(Runnable task, CustomThreadPool pool);
}
