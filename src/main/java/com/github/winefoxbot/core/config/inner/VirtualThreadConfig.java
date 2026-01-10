package com.github.winefoxbot.core.config.inner;

import lombok.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnThreading;
import org.springframework.boot.thread.Threading;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class VirtualThreadConfig {

    private final static String COMMON_VIRTUAL_THREAD_NAME_PREFIX = "virtual-common-";
    private final static String SCHEDULED_VIRTUAL_THREAD_NAME_PREFIX = "virtual-schedule-";
    private final static String TASK_SCHEDULE_THREAD_NAME_PREFIX = "virtual-task-schedule-";
    private final static int SCHEDULED_POOL_SIZE = 1000;
    private final static String SHIRO_VIRTUAL_THREAD_NAME_PREFIX = "virtual-shiro-";

    @Bean
    public Executor virtualThreadExecutor() {
        // 创建虚拟线程执行器，底层用的是 Java 21 的虚拟线程
        // 这玩意儿比传统线程池强多了，性能提升不是一点半点
        return Executors.newVirtualThreadPerTaskExecutor();
    }
    // 配置异步任务使用虚拟线程


    @Bean
    public TaskExecutor taskExecutor() {
        // 返回虚拟线程执行器，@Async 注解会使用这个执行器
        return new TaskExecutor() {
            private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            @Override
            public void execute(Runnable task) {
                executor.submit(task);
            }
        };
    }



    /**
     * shiro框架使用的任务执行器
     * @return {@link ThreadPoolTaskExecutor}
     */
    @Bean("shiroTaskExecutor")
    @ConditionalOnThreading(Threading.VIRTUAL)
    public ThreadPoolTaskExecutor shiroTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setKeepAliveSeconds(60); // 线程存活时间
        executor.setThreadNamePrefix(SHIRO_VIRTUAL_THREAD_NAME_PREFIX);    // 设置线程池的线程前缀
        executor.setThreadFactory(new ThreadFactory() {
            private final ThreadFactory virtualFactory = Thread.ofVirtual().factory();
            @Override
            public Thread newThread(@NonNull Runnable r) {
                return virtualFactory.newThread(r);
            }
        }); // 线程工厂
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy()); // 拒绝策略
        return executor;
    }





    /**
     * 当 spring.threads.virtual.enabled=true 时，Spring Boot 不再自动配置 TaskScheduler。
     * 我们需要手动创建一个，并让它使用虚拟线程。
     */
    @Bean
    @ConditionalOnThreading(Threading.VIRTUAL)
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadFactory(Thread.ofVirtual().name(COMMON_VIRTUAL_THREAD_NAME_PREFIX, 0).factory());
        // 设置线程名前缀（这会影响调度器内部管理线程的名称，而不是任务执行线程）
        scheduler.setThreadNamePrefix(TASK_SCHEDULE_THREAD_NAME_PREFIX);
        scheduler.setPoolSize(10);
        // scheduler.initialize(); // Spring Bean 生命周期会自动调用，所以这里可以不写
        return scheduler;
    }

    @Bean
    public ScheduledExecutorService scheduledExecutorService() {
        return new ScheduledThreadPoolExecutor(SCHEDULED_POOL_SIZE, new ThreadFactory() {
            private final ThreadFactory virtualFactory = Thread.ofVirtual().factory();
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(@NonNull Runnable r) {
                Thread thread = virtualFactory.newThread(r);
                thread.setName(SCHEDULED_VIRTUAL_THREAD_NAME_PREFIX + threadNumber.getAndIncrement());
                return thread;
            }
        } , new ThreadPoolExecutor.AbortPolicy());
    }


}
