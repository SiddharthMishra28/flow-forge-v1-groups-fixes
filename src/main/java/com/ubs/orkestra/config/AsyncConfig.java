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
     * Thread pool for flow-execution tasks.
     *
     * With the scheduler-driven architecture, threads in this pool are held only
     * for the duration of a single GitLab API call (~1-5 s) – never for the full
     * lifetime of a pipeline run (minutes to hours).
     *
     * Sizing rationale:
     *   corePoolSize  = 5   – threads always warm
     *   maxPoolSize   = 15  – burst headroom
     *   queueCapacity = 200 – absorbs spikes; tasks are fast so the queue drains quickly
     *
     * Used by:
     *   - FlowExecutionService.executeFlowAsync            (register step-0 for polling)
     *   - FlowExecutionService.resumeFlowExecution         (trigger scheduled step)
     *   - FlowExecutionService.executeReplayFlowAsync      (trigger replay step)
     *   - FlowExecutionService.advanceFlowToNextStep       (trigger next step on completion)
     *   - FlowExecutionService.startPendingFlow            (start a PENDING flow)
     *
     * Return type is ThreadPoolTaskExecutor (not Executor) so that
     * FlowExecutionQueueService can inspect pool metrics.
     */
    @Bean(name = "flowExecutionTaskExecutor")
    public ThreadPoolTaskExecutor flowExecutionTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("FlowAdvancer-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Thread pool for background sync operations only.
     *
     * Used by:
     *   - SyncService.syncAllFlowExecutionDataAsync()
     *
     * NOTE: Individual pipeline-status polling runs on Spring's @Scheduled
     * executor (single-threaded), backed by an in-memory ConcurrentHashMap.
     * This pool has NO involvement in pipeline polling.
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
