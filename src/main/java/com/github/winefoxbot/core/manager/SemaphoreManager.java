package com.github.winefoxbot.core.manager;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
public class SemaphoreManager {

    // 内部静态包装类
    private static class ManagedSemaphore {
        final Semaphore semaphore;
        final int permits;

        ManagedSemaphore(int permits) {
            this.semaphore = new Semaphore(permits, true);
            this.permits = permits;
        }
    }

    private final ConcurrentHashMap<String, ManagedSemaphore> semaphoreMap = new ConcurrentHashMap<>();

    // LockMap 仍然需要，用于确保 ManagedSemaphore 创建的原子性
    private final ConcurrentHashMap<String, Lock> lockMap = new ConcurrentHashMap<>();

    public Semaphore getSemaphore(String key, int maxPermits) {
        // 第一重检查
        ManagedSemaphore managedSemaphore = semaphoreMap.get(key);
        if (managedSemaphore != null && managedSemaphore.permits == maxPermits) {
            return managedSemaphore.semaphore;
        }

        Lock lock = lockMap.computeIfAbsent(key, k -> new ReentrantLock());

        lock.lock();
        try {
            // 第二重检查 (Double-Checked)
            managedSemaphore = semaphoreMap.get(key);
            if (managedSemaphore == null || managedSemaphore.permits != maxPermits) {
                log.info("为 session [{}] 创建或更新 Semaphore，新许可数: {}", key, maxPermits);
                managedSemaphore = new ManagedSemaphore(maxPermits); // 创建新的包装类实例
                semaphoreMap.put(key, managedSemaphore);
            }
            return managedSemaphore.semaphore;
        } finally {
            lock.unlock();
        }
    }
}
