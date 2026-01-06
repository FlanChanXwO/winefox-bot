package com.github.winefoxbot.core.service.denpencyversion;

import java.util.Optional;

public interface DependencyVersionService {

    Optional<String> getVersion(Class<?> aClass);
}
