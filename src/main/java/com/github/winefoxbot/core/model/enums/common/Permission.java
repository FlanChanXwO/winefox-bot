package com.github.winefoxbot.core.model.enums.common;// package com.yourproject.common.enums; // 放在你的枚举包下

import lombok.Getter;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 插件功能权限等级枚举
 * 定义了从高到低的权限级别，并提供与群成员角色(GroupMemberRole)的转换和比较。
 */
@Getter
public enum Permission {

    /**
     * 超级管理员 (机器人配置文件中指定)
     */
    SUPERADMIN(3, "超级管理员"),

    /**
     * 群主
     */
    OWNER(2, "群主"),

    /**
     * 管理员
     */
    ADMIN(1, "管理员"),

    /**
     * 普通用户/群成员
     */
    USER(0, "普通用户");

    // 权限等级，数字越大权限越高
    private final int level;
    // 权限描述
    private final String description;

    Permission(int level, String description) {
        this.level = level;
        this.description = description;
    }

    /**
     * 将字符串形式的权限名转换为 Permission 枚举。
     *
     * @param permissionStr 权限字符串，如 "群主", "管理员", "普通用户"
     * @return 对应的 Permission 枚举
     * @throws IllegalArgumentException 如果找不到匹配的权限
     */
    public static Permission fromString(String permissionStr) {
        if (permissionStr == null) {
            return USER; // 默认返回最低权限
        }
        return switch (permissionStr.trim()) {
            case "超级管理员", "superadmin", "master" -> SUPERADMIN;
            case "群主", "owner" -> OWNER;
            case "管理员", "admin" -> ADMIN;
            case "普通用户", "member", "user" -> USER;
            default -> throw new IllegalArgumentException("未知的权限字符串: " + permissionStr);
        };
    }

    /**
     * 根据群成员角色(GroupMemberRole)获取对应的权限等级。
     *
     * @param role 群成员角色枚举
     * @return 对应的 Permission 枚举
     */
    public static Permission fromGroupMemberRole(GroupMemberRole role) {
        if (role == null) {
            return USER;
        }
        return switch (role) {
            case OWNER -> OWNER;
            case ADMIN -> ADMIN;
            case MEMBER -> USER;
        };
    }

    /**
     * 检查当前权限是否足以满足要求的权限。
     *
     * @param requiredPermission 需要的最低权限
     * @return 如果当前权限级别大于或等于所需权限级别，则返回 true
     */
    public boolean isSufficient(Permission requiredPermission) {
        return this.level >= requiredPermission.level;
    }

    /**
     * 根据当前权限（作为最低要求），生成一个描述性的、包含所有适用角色的字符串。
     * @return 格式化后的权限描述字符串，例如 "群主 / 管理员 / 超级管理员"
     */
    public String getApplicableRolesDescription() {
        // 如果是普通用户，直接返回 "所有用户"，更简洁
        if (this == USER) {
            return "所有用户";
        }

        // 否则，找出所有级别大于等于当前级别的权限
        return Arrays.stream(Permission.values())
                .filter(p -> p.getLevel() >= this.level) // 筛选出所有符合要求的权限
                .map(Permission::getDescription)          // 获取权限的描述文字
                .collect(Collectors.joining(" / "));  // 使用 " / " 连接
    }
}
