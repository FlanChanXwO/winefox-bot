package com.github.winefoxbot.core.event;

import com.github.winefoxbot.core.annotation.plugin.Plugin;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * @author FlanChan
 */
@Getter
public class PluginCalledEvent  extends ApplicationEvent {
    private final String className;
    private final Plugin pluginInfo;

    public PluginCalledEvent(Object source, String className, Plugin pluginInfo) {
        super(source);
        this.className = className;
        this.pluginInfo = pluginInfo;
    }
}