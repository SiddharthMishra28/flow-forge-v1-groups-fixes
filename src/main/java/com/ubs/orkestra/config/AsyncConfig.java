package com.ubs.orkestra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Thread pool for executing flows concurrently.
     *
     * Sizing rationale for 50-70 parallel flows:
     *   - corePoolSize=20 : keep 20 threads warm at all times
     *   - maxPoolSize=70  : allow up to 70 concurrent flow-execution threads
     *   - queueCapacity=5 : intentionally tiny – acts only as a small burst buffer.
     *                        Overflow MUST go to the DB-backed queue (queued_flow_executions)
     *                        so it survives restarts.  A large internal queue would silently
     *                        swallow flows that would be lost on restart.
     *
     * Return type is ThreadPoolTaskExecutor (not the Executor interface) so that
     * FlowExecutionQueueService can inspect pool statistics (activeCount, maxPoolSize, etc.).
     */
    @Bean(name = "flowExecutionTaskExecutor")
    public ThreadPoolTaskExecutor flowExecutionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20);
        executor.setMaxPoolSize(70);
        executor.setQueueCapacity(5);
        executor.setThreadNamePrefix("FlowExecution-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        // Throw TaskRejectedException on overflow; callers catch this and route to DB queue.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Thread pool for background/async operations that are NOT flow-execution threads.
     *
     * Currently used by:
     *   - SyncService.syncAllFlowExecutionDataAsync()  (manual GitLab re-sync)
     *
     * NOTE: Individual pipeline-status polling is NOT handled here.
     * PipelineStatusPollingService uses a @Scheduled method (Spring's TaskScheduler)
     * backed by a ConcurrentHashMap of active polls – no separate thread pool needed.
     */
    @Bean(name = "pipelinePollingTaskExecutor")
    public Executor pipelinePollingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("BackgroundSync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}