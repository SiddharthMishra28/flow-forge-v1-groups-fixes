package com.ubs.orkestra.service;

import com.ubs.orkestra.enums.ExecutionStatus;
import com.ubs.orkestra.model.InvokeScheduler;
import com.ubs.orkestra.model.Timer;
import com.ubs.orkestra.model.PipelineExecution;
import com.ubs.orkestra.repository.PipelineExecutionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class SchedulingService {

    private static final Logger logger = LoggerFactory.getLogger(SchedulingService.class);

    @Autowired
    private PipelineExecutionRepository pipelineExecutionRepository;

    @Autowired
    @Lazy
    private FlowExecutionService flowExecutionService;

    @Value("${scheduling.pipeline-status.polling-interval:60000}")
    private long pollingInterval;

    /**
     * Calculates the resume time based on InvokeScheduler configuration
     */
    public LocalDateTime calculateResumeTime(LocalDateTime previousStepEndTime, InvokeScheduler invokeScheduler) {
        if (invokeScheduler == null || invokeScheduler.getTimer() == null) {
            return null;
        }

        Timer timer = invokeScheduler.getTimer();
        String type = invokeScheduler.getType();

        if ("delayed".equals(type)) {
            return calculateDelayedResumeTime(previousStepEndTime, timer);
        } else if ("scheduled".equals(type)) {
            return calculateScheduledResumeTime(timer);
        }

        logger.warn("Unknown scheduler type: {}", type);
        return null;
    }

    /**
     * Calculates resume time for delayed type (relative to previous step end time)
     */
    private LocalDateTime calculateDelayedResumeTime(LocalDateTime previousStepEndTime, Timer timer) {
        LocalDateTime resumeTime = previousStepEndTime;

        // Add days
        if (timer.getDays() != null && !timer.getDays().isEmpty()) {
            try {
                String daysStr = timer.getDays().replace("+", "");
                int days = Integer.parseInt(daysStr);
                resumeTime = resumeTime.plusDays(days);
            } catch (NumberFormatException e) {
                logger.warn("Invalid days format in delayed timer: {}", timer.getDays());
            }
        }

        // Add hours
        if (timer.getHours() != null && !timer.getHours().isEmpty()) {
            try {
                String hoursStr = timer.getHours().replace("+", "");
                int hours = Integer.parseInt(hoursStr);
                resumeTime = resumeTime.plusHours(hours);
            } catch (NumberFormatException e) {
                logger.warn("Invalid hours format in delayed timer: {}", timer.getHours());
            }
        }

        // Add minutes
        if (timer.getMinutes() != null && !timer.getMinutes().isEmpty()) {
            try {
                String minutesStr = timer.getMinutes().replace("+", "");
                int minutes = Integer.parseInt(minutesStr);
                resumeTime = resumeTime.plusMinutes(minutes);
            } catch (NumberFormatException e) {
                logger.warn("Invalid minutes format in delayed timer: {}", timer.getMinutes());
            }
        }

        return resumeTime;
    }

    /**
     * Calculates resume time for scheduled type (absolute time)
     */
    private LocalDateTime calculateScheduledResumeTime(Timer timer) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime resumeTime = now;

        // Calculate days from now
        if (timer.getDays() != null && !timer.getDays().isEmpty()) {
            try {
                int days = Integer.parseInt(timer.getDays());
                resumeTime = resumeTime.plusDays(days);
            } catch (NumberFormatException e) {
                logger.warn("Invalid days format in scheduled timer: {}", timer.getDays());
                return null;
            }
        }

        // Set specific time
        int targetHour = 0;
        int targetMinute = 0;

        if (timer.getHours() != null && !timer.getHours().isEmpty()) {
            try {
                targetHour = Integer.parseInt(timer.getHours());
                if (targetHour < 0 || targetHour > 23) {
                    logger.warn("Invalid hour value in scheduled timer: {}", timer.getHours());
                    return null;
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid hours format in scheduled timer: {}", timer.getHours());
                return null;
            }
        }

        if (timer.getMinutes() != null && !timer.getMinutes().isEmpty()) {
            try {
                targetMinute = Integer.parseInt(timer.getMinutes());
                if (targetMinute < 0 || targetMinute > 59) {
                    logger.warn("Invalid minute value in scheduled timer: {}", timer.getMinutes());
                    return null;
                }
            } catch (NumberFormatException e) {
                logger.warn("Invalid minutes format in scheduled timer: {}", timer.getMinutes());
                return null;
            }
        }

        // Set the specific time (hour and minute)
        resumeTime = resumeTime.with(LocalTime.of(targetHour, targetMinute, 0));

        // If the calculated time is in the past and no days offset, schedule for next day
        if (resumeTime.isBefore(now) && (timer.getDays() == null || timer.getDays().isEmpty())) {
            resumeTime = resumeTime.plusDays(1);
        }

        return resumeTime;
    }

    /**
     * Background scheduler that checks for scheduled pipeline executions that are ready to resume
     * Uses configurable polling interval
     */
    @Scheduled(fixedRateString = "${scheduling.pipeline-status.polling-interval:60000}")
    @Transactional
    public void processScheduledExecutions() {
        logger.debug("Checking for scheduled pipeline executions ready to resume...");
        
        LocalDateTime now = LocalDateTime.now();
        List<PipelineExecution> scheduledExecutions = pipelineExecutionRepository.findByStatusAndResumeTimeBefore(
            ExecutionStatus.SCHEDULED, now);

        logger.info("Found {} scheduled executions ready to resume", scheduledExecutions.size());

        for (PipelineExecution execution : scheduledExecutions) {
            try {
                logger.info("Resuming scheduled pipeline execution ID: {} for flow step ID: {}", 
                           execution.getId(), execution.getFlowStepId());
                
                // Update status from SCHEDULED to IN_PROGRESS to avoid replay delays
                execution.setStatus(ExecutionStatus.IN_PROGRESS);
                execution.setStartTime(LocalDateTime.now());
                execution.setResumeTime(null); // Clear resume time
                pipelineExecutionRepository.save(execution);

                // Resume the flow execution from this step onwards
                flowExecutionService.resumeFlowExecution(execution.getFlowExecutionId(), execution.getFlowStepId());

                logger.info("Pipeline execution ID: {} marked as IN_PROGRESS and flow execution resumed", execution.getId());
                
            } catch (Exception e) {
                logger.error("Error resuming scheduled pipeline execution ID: {}", execution.getId(), e);
                // Mark as failed if resume fails
                execution.setStatus(ExecutionStatus.FAILED);
                execution.setEndTime(LocalDateTime.now());
                pipelineExecutionRepository.save(execution);
            }
        }
    }

    /**
     * Schedules a pipeline execution to run at a specific time
     */
    @Transactional
    public void schedulePipelineExecution(PipelineExecution execution, LocalDateTime resumeTime) {
        execution.setStatus(ExecutionStatus.SCHEDULED);
        execution.setResumeTime(resumeTime);
        pipelineExecutionRepository.save(execution);
        
        logger.info("Scheduled pipeline execution ID: {} to resume at: {}", execution.getId(), resumeTime);
    }
}
