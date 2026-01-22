package com.github.winefoxbot.core.config.inner;

import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
@RequiredArgsConstructor
public class JacksonConfig {

    private static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    @Bean
    public JsonMapperBuilderCustomizer jsonCustomizer() {
        return builder -> {
            // 添加 LocalDateTime 的序列化与反序列化
            builder.addModule(
                    new SimpleModule()
                            // Long 的序列化
                            .addSerializer(Long.class, new LongSerializer())
                            // LocalDateTime 的序列化
                            .addSerializer(LocalDateTime.class, new LocalDateTimeSerializer())
                            // LocalDateTime 的反序列化
                            .addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer())
            );
        };
    }

    /**
     * Long 的序列化
     */
    private static class LongSerializer extends ValueSerializer<Long> {
        @Override
        public void serialize(Long value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            if (value != null) {
                gen.writeString(value.toString());
            }
        }
    }

    /**
     * LocalDateTime 的序列化
     */
    private static class LocalDateTimeSerializer extends ValueSerializer<LocalDateTime> {
        @Override
        public void serialize(LocalDateTime value, JsonGenerator gen, SerializationContext ctxt) throws JacksonException {
            if (value != null) {
                gen.writeString(value.format(DATE_TIME_FORMATTER));
            } else {
                gen.writeNull();
            }
        }
    }

    /**
     * LocalDateTime 的反序列化
     */
    private static class LocalDateTimeDeserializer extends ValueDeserializer<LocalDateTime> {
        @Override
        public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
            String dateString = p.getString();
            if (dateString == null || dateString.trim().isEmpty()) {
                return null;
            }
            return java.time.LocalDateTime.parse(dateString, DATE_TIME_FORMATTER);
        }
    }
}
