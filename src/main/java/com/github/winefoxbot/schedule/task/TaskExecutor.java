// file: com/github/winefoxbotbackend/schedule/task/TaskExecutor.java

package com.github.winefoxbot.schedule.task;

/**
 * 通用任务执行器接口。
 * 所有需要被动态调度的任务都应该实现此接口。
 *
 * @param <T> 任务参数的类型
 */
public interface TaskExecutor<T> {

    /**
     * 执行任务的具体逻辑。
     *
     * @param params 任务参数，由调度器从数据库反序列化后传入
     */
    void execute(T params);

    /**
     * 返回此执行器对应的参数类型，用于JSON反序列化。
     *
     * @return 参数的Class对象
     */
    Class<T> getParameterType();
}
