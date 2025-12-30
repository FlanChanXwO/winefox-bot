package com.github.winefoxbot.model.dto.file;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * 描述一个被 FileStorageService 管理的文件的元数据。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * 文件的绝对路径。
     */
    private String absolutePath;

    /**
     * 文件的写入时间。
     */
    private Instant writeTime;

    /**
     * 文件的过期时间，如果不过期则为 null。
     */
    private Instant expireTime;

    /**
     * 文件删除时的回调函数。
     * 使用 transient 关键字，因为 Consumer 不可序列化。
     * 回调逻辑需要在应用运行时重新建立。
     */
    @JsonIgnore // 确保 Jackson 不会尝试序列化它
    private transient Consumer<Path> onDeleteCallback;
}
