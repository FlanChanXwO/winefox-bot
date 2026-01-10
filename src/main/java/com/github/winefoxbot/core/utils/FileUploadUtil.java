package com.github.winefoxbot.core.utils;

import com.github.winefoxbot.core.model.dto.SendMsgResult;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.common.ActionRaw;
import com.mikuac.shiro.dto.action.response.GroupFilesResp;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.dto.event.message.GroupMessageEvent;
import com.mikuac.shiro.dto.event.message.MessageEvent;
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * @author FlanChan
 */
@Slf4j
public final class FileUploadUtil {

    private FileUploadUtil () {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    /**
     * 上传文件到指定的群。
     * 这是最核心和通用的群文件上传方法。
     *
     * @param bot       机器人实例
     * @param groupId   目标群号
     * @param filePath  文件在本地的路径
     * @param fileName  上传后在群里显示的文件名
     * @return 上传结果
     * @throws BusinessException 上传失败时抛出
     */
    public static SendMsgResult uploadGroupFile(Bot bot, Long groupId, Path filePath, String fileName) {
        log.info("准备上传群文件: GroupId={}, FilePath='{}', FileName='{}'", groupId, filePath, fileName);
        ActionRaw actionRaw = bot.uploadGroupFile(groupId, filePath.toAbsolutePath().toString(), fileName);
        return processUploadResult(actionRaw, fileName, "无法上传群文件");
    }

    /**
     * 上传文件给指定的用户 (私聊)。
     * 这是最核心和通用的私聊文件上传方法。
     *
     * @param bot      机器人实例
     * @param userId   目标用户QQ号
     * @param filePath 文件在本地的路径
     * @param fileName 上传后显示的文件名
     * @return 上传结果
     * @throws BusinessException 上传失败时抛出
     */
    public static SendMsgResult uploadPrivateFile(Bot bot, Long userId, Path filePath, String fileName) {
        log.info("准备上传私聊文件: UserId={}, FilePath='{}', FileName='{}'", userId, filePath, fileName);
        ActionRaw actionRaw = bot.uploadPrivateFile(userId, filePath.toAbsolutePath().toString(), fileName);
        return processUploadResult(actionRaw, fileName, "无法上传私聊文件");
    }

    /**
     * 根据消息事件的上下文自动判断并上传文件（群聊或私聊）。
     * 这是一个便捷方法，内部会解析Event并调用更通用的核心上传方法。
     *
     * @param bot      机器人实例
     * @param event    消息事件 (PrivateMessageEvent, GroupMessageEvent 等)
     * @param filePath 文件在本地的路径
     * @param fileName 上传后显示的文件名
     * @return 上传结果
     * @throws IllegalStateException 如果事件类型不被支持
     */
    public static SendMsgResult uploadFile(Bot bot, MessageEvent event, Path filePath, String fileName) {
        if (event instanceof GroupMessageEvent e) {
            // 群消息事件
            return uploadGroupFile(bot, e.getGroupId(), filePath, fileName);
        } else if (event instanceof PrivateMessageEvent e) {
            // 私聊消息事件
            return uploadPrivateFile(bot, e.getUserId(), filePath, fileName);
        } else if (event instanceof AnyMessageEvent e) {
            // 任意消息事件，需要判断是群聊还是私聊
            if (e.getGroupId() != null) {
                return uploadGroupFile(bot, e.getGroupId(), filePath, fileName);
            } else {
                return uploadPrivateFile(bot, e.getUserId(), filePath, fileName);
            }
        } else {
            log.warn("不支持的事件类型，无法自动判断上传目标: {}", event.getClass().getName());
            throw new IllegalStateException("不支持的消息事件类型: " + event.getClass().getName());
        }
    }


    // ***************************************************************
    // 3. 异步上传方法
    // ***************************************************************

    /**
     * 异步上传文件到指定的群。
     */
    public static CompletableFuture<SendMsgResult> uploadGroupFileAsync(Bot bot, Long groupId, Path filePath, String fileName) {
        return CompletableFuture.supplyAsync(() -> uploadGroupFile(bot, groupId, filePath, fileName));
    }

    /**
     * 异步上传文件给指定的用户 (私聊)。
     */
    public static CompletableFuture<SendMsgResult> uploadPrivateFileAsync(Bot bot, Long userId, Path filePath, String fileName) {
        return CompletableFuture.supplyAsync(() -> uploadPrivateFile(bot, userId, filePath, fileName));
    }

    /**
     * (便捷方法) 根据消息事件的上下文异步上传文件。
     */
    public static CompletableFuture<SendMsgResult> uploadFileAsync(Bot bot, MessageEvent event, Path filePath, String fileName) {
        return CompletableFuture.supplyAsync(() -> uploadFile(bot, event, filePath, fileName));
    }

    public static void deleteGroupFile(Bot bot, GroupMessageEvent groupMessageEvent, String fileName) {
        Long groupId = groupMessageEvent.getGroupId();
        deleteGroupFile(bot, groupId, fileName);
    }


    public static void deleteGroupFile(Bot bot, Long groupId, String fileName) {
        ActionData<GroupFilesResp> groupRootFiles = bot.getGroupRootFiles(groupId);
        GroupFilesResp data = groupRootFiles.getData();
        for (GroupFilesResp.Files file : data.getFiles()) {
            if (file.getFileName().equals(fileName)) {
                bot.deleteGroupFile(groupId, file.getFileId(), file.getBusId());
                break;
            }
        }
    }


    /**
     * 处理上传API的返回结果，检查是否成功，并记录日志。
     *
     * @param actionRaw      API的原始返回数据
     * @param fileName       文件名，用于日志
     * @param failureMessage 失败时 BusinessException 中包含的消息
     * @return SendMsgResult
     */
    private static SendMsgResult processUploadResult(ActionRaw actionRaw, String fileName, String failureMessage) {
        Integer retCode = actionRaw.getRetCode();
        // retCode为0通常代表成功接收指令
        if (retCode != 0) {
            log.error("文件上传指令失败: FileName={}, RetCode={}, Status={}, Echo={}",
                    fileName, retCode, actionRaw.getStatus(), actionRaw.getEcho());
            // 你可以根据 actionRaw.getMsg() 提供更详细的错误信息
            String errorDetail = failureMessage + " (RetCode: " + retCode + ", Status: " + actionRaw.getStatus() + ")";
            throw new BusinessException(errorDetail, null); // 假设 BusinessException 是你的自定义异常
        }
        log.info("文件上传指令已发送: FileName='{}', RetCode={}, Status='{}'", fileName, retCode, actionRaw.getStatus());
        return new SendMsgResult(true, "文件上传指令已成功发送");
    }

    // 假设的 BusinessException 和 SendMsgResult 类
    public static class BusinessException extends RuntimeException {
        public BusinessException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
}
