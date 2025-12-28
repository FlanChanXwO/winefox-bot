package com.github.winefoxbot.config.inner;

import lombok.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnThreading;
import org.springframework.boot.autoconfigure.thread.Threading;
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
    private final String THREAD_NAME_PREFIX = "virtual-thread-";


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
        //        executor.setCorePoolSize(CORE_POOL_SIZE); // 核心线程数
//        executor.setMaxPoolSize(MAX_POOL_SIZE); // 最大线程数
//        executor.setQueueCapacity(QUEUE_CAPACITY); // 队列容量
        executor.setKeepAliveSeconds(60); // 线程存活时间
        executor.setThreadNamePrefix("shiro-task-");    // 设置线程池的线程前缀
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



    private final static int SCHEDULED_POOL_SIZE = 1000;


    /**
     * 当 spring.threads.virtual.enabled=true 时，Spring Boot 不再自动配置 TaskScheduler。
     * 我们需要手动创建一个，并让它使用虚拟线程。
     */
    @Bean
    @ConditionalOnThreading(Threading.VIRTUAL)
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        // 关键：设置一个能够创建虚拟线程的 ThreadFactory。
        // 当调度器需要执行任务时，它会通过这个工厂获取一个线程，
        // 而这个线程将是一个虚拟线程。
        // Thread.ofVirtual() 是 JDK 21 提供的便捷 API。
        // .name() 方法可以为虚拟线程设置名称前缀，方便调试和日志分析。
        scheduler.setThreadFactory(Thread.ofVirtual().name("vt-scheduler-", 0).factory());

        // 设置线程名前缀（这会影响调度器内部管理线程的名称，而不是任务执行线程）
        scheduler.setThreadNamePrefix("scheduler-");

        // 对于虚拟线程，poolSize 的意义有所不同。它不再是平台线程的硬性限制。
        // 这里它控制的是调度器本身用于管理和触发任务的并发度。
        // 一个较小的值（例如 1 或 CPU 核心数）通常就足够了，因为实际的任务执行
        // 不会阻塞这些线程。但为了安全起见，可以设置一个合理的并发数，比如 10。
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
                thread.setName("schedule-task-" + threadNumber.getAndIncrement());
                return thread;
            }
        } , new ThreadPoolExecutor.AbortPolicy());
    }


}
