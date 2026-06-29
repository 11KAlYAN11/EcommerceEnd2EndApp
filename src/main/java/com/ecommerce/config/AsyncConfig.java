package com.ecommerce.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * WHY A CUSTOM THREAD POOL FOR ASYNC?
 *
 * @EnableAsync without configuration uses SimpleAsyncTaskExecutor:
 *   - Creates a NEW thread for every @Async call
 *   - No reuse, no limit → can spawn thousands of threads under load
 *   - Thread creation is expensive (JVM stack allocation)
 *
 * ThreadPoolTaskExecutor:
 *   - Pre-creates CORE_POOL_SIZE threads on startup (ready to use)
 *   - Reuses threads for subsequent tasks (pool pattern)
 *   - MAX_POOL_SIZE cap prevents unbounded thread creation
 *   - QUEUE_CAPACITY: when pool is full, tasks wait here
 *   - If queue fills up too → RejectedExecutionException (configurable)
 *
 * Naming threads "email-" prefix:
 *   Thread dump / logs show "email-1", "email-2" → easy to diagnose
 *   Without naming: "pool-3-thread-7" tells you nothing
 */
@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "emailExecutor")
    public Executor emailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);       // always-ready threads for email
        executor.setMaxPoolSize(5);        // max concurrent email sends
        executor.setQueueCapacity(100);    // queue 100 emails if threads busy
        executor.setThreadNamePrefix("email-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30); // graceful shutdown: finish pending emails
        executor.initialize();
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        // Catch unchecked exceptions from @Async void methods (checked ones are already caught in EmailService)
        return (ex, method, params) ->
                log.error("Async error in {}: {}", method.getName(), ex.getMessage(), ex);
    }
}
