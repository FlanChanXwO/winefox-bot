package com.github.winefoxbot.service.task.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.winefoxbot.model.entity.ScheduleTask;
import com.github.winefoxbot.service.task.TaskDispatcherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskDispatcherServiceImpl implements TaskDispatcherService {

    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper; // 注入 ObjectMapper

    @Override
    public void executeTask(ScheduleTask task) throws Exception {
        if (!StringUtils.hasText(task.getBeanName()) || !StringUtils.hasText(task.getMethodName())) {
            throw new IllegalArgumentException(/* ... */);
        }

        Object targetBean = applicationContext.getBean(task.getBeanName());

        // 查找与参数匹配的方法
        Method targetMethod = findMatchingMethod(targetBean.getClass(), task.getMethodName(), task.getTaskParams());

        if (targetMethod == null) {
            log.error("任务执行失败：在 Bean '{}' 中找不到与参数匹配的方法 '{}'。", task.getBeanName(), task.getMethodName());
            throw new NoSuchMethodException("在 " + task.getBeanName() + " 中找不到合适的方法 " + task.getMethodName());
        }

        Object[] args;
        if (targetMethod.getParameterCount() == 1) {
            // 将 JSON 字符串反序列化为方法的参数类型对象
            Class<?> paramType = targetMethod.getParameterTypes()[0];
            Object paramObject = objectMapper.readValue(task.getTaskParams(), paramType);
            args = new Object[]{paramObject};
        } else {
            args = new Object[0];
        }

        targetMethod.invoke(targetBean, args);
        log.info("任务 [ID: {}] 的方法 '{}' 已成功调用。", task.getTaskId(), task.getMethodName());
    }

    private Method findMatchingMethod(Class<?> beanClass, String methodName, String taskParams) {
        for (Method method : beanClass.getMethods()) {
            if (method.getName().equals(methodName)) {
                Parameter[] parameters = method.getParameters();
                if (StringUtils.hasText(taskParams) && parameters.length == 1) {
                    // 找到了一个带单参数的方法，就用它
                    return method;
                }
                if (!StringUtils.hasText(taskParams) && parameters.length == 0) {
                    // 找到了一个无参方法，就用它
                    return method;
                }
            }
        }
        // 未找到匹配的方法
        return null;
    }
}
