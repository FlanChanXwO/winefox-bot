package com.github.winefoxbot.plugins;

import com.github.winefoxbot.annotation.PluginFunction;
import com.github.winefoxbot.model.dto.pixiv.PixivDetail;
import com.github.winefoxbot.model.dto.pixiv.PixivSearchParams;
import com.github.winefoxbot.model.dto.pixiv.PixivSearchResult;
import com.github.winefoxbot.model.enums.Permission;
import com.github.winefoxbot.model.enums.PixivArtworkType;
import com.github.winefoxbot.model.enums.SessionType;
import com.github.winefoxbot.service.pixiv.PixivSearchService;
import com.github.winefoxbot.service.pixiv.PixivService;
import com.github.winefoxbot.utils.BotUtils;
import com.github.winefoxbot.utils.DocxUtil;
import com.github.winefoxbot.utils.FileUtil;
import com.github.winefoxbot.utils.PdfUtil;
import com.mikuac.shiro.annotation.AnyMessageHandler;
import com.mikuac.shiro.annotation.MessageHandlerFilter;
import com.mikuac.shiro.annotation.common.Order;
import com.mikuac.shiro.annotation.common.Shiro;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.action.common.ActionData;
import com.mikuac.shiro.dto.action.common.ActionRaw;
import com.mikuac.shiro.dto.action.common.MsgId;
import com.mikuac.shiro.dto.action.response.GroupFilesResp;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import com.mikuac.shiro.enums.MsgTypeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.tomcat.util.buf.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.github.winefoxbot.config.app.WineFoxBotConfig.COMMAND_PREFIX_REGEX;
import static com.github.winefoxbot.config.app.WineFoxBotConfig.COMMAND_SUFFIX_REGEX;
import static com.github.winefoxbot.utils.BotUtils.*;
import static com.mikuac.shiro.core.BotPlugin.MESSAGE_BLOCK;
import static com.mikuac.shiro.core.BotPlugin.MESSAGE_IGNORE;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2026-01-01-16:29
 */
@Shiro
@Component
@RequiredArgsConstructor
@Slf4j
public class PixivSearchPlugin {
    private final PixivSearchService pixivSearchService;
    private final PixivService pixivService;

    // 存储会话的搜索结果
    private final  Map<String, LastSearchResult> lastSearchResultMap = new ConcurrentHashMap<>();
    // 存储会话的超时任务，以便可以取消和重置
    private final Map<String, ScheduledFuture<?>> sessionTimeoutTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private static final Pattern NUMBER_SELECTION_PATTERN = Pattern.compile("^[\\d,，\\s]+$");
    private static final long SESSION_TIMEOUT_SECONDS = 60 * 5;
    private static final String FILE_OUTPUT_DIR = "data/files/pixiv/wrappers";
    private static class LastSearchResult {
        PixivSearchParams params;
        PixivSearchResult result;
        AnyMessageEvent event; // 原始事件
        Long initiatorUserId;  // 新增：会话发起者的用户ID

        LastSearchResult(PixivSearchParams params, PixivSearchResult result, AnyMessageEvent event) {
            this.params = params;
            this.result = result;
            this.event = event;
            this.initiatorUserId = event.getUserId(); // 在构造时记录发起者
        }
    }

    @Async
    @PluginFunction(
            group = "Pixiv", name = "Pixiv搜索",
            permission = Permission.USER,
            description = "在Pixiv上搜索插画作品。命令格式：/pixiv搜索 <标签1> <标签2> ... [-p<页码>] [-r]。其中 -p 用于指定页码，-r 用于开启R18搜索。",
            commands = {"/Pixiv搜索","/pixiv搜索","/P站搜索","/p站搜索"}, hidden = false)
    @AnyMessageHandler
    @MessageHandlerFilter(types = MsgTypeEnum.text, cmd = COMMAND_PREFIX_REGEX + "(?:p|P)(?:ixiv|站)搜索\\s+(.+?)(?=\\s+-|$)\\s*(.*)" + COMMAND_SUFFIX_REGEX)
    public void handlePixivSearch(Bot bot, AnyMessageEvent event, Matcher matcher) {
        PixivSearchParams params = new PixivSearchParams();
        params.setPageNo(1);
        params.setR18(false);
        String keywords = matcher.group(1).trim();
        if (keywords.isEmpty()) { bot.sendMsg(event, "请输入至少一个搜索标签！", false); return; }
        List<String> tags = new ArrayList<>(Arrays.asList(keywords.split("\\s+")));
        params.setTags(tags);
        String arguments = matcher.group(2).trim();
        if (!arguments.isEmpty()) {
            String[] args = arguments.split("\\s+");
            for (String arg : args) {
                if ("-r".equalsIgnoreCase(arg)) { params.setR18(true); continue; }
                if (arg.toLowerCase().startsWith("-p")) {
                    String pageStr = arg.substring(2);
                    if (!pageStr.isEmpty()) {
                        try {
                            int pageNo = Integer.parseInt(pageStr);
                            if (pageNo > 0) { params.setPageNo(pageNo); }
                            else { bot.sendMsg(event, "页码必须是大于0的整数哦。", false); return; }
                        } catch (NumberFormatException e) {
                            log.warn("无效的页码参数: {}", arg);
                            bot.sendMsg(event, "页码参数格式不正确，应为 -p<数字>，例如 -p2。", false);
                            return;
                        }
                    }
                }
            }
        }
        executeSearch(bot, event, params);
    }
    @Async
    @AnyMessageHandler
    @Order(1)
    @MessageHandlerFilter(types = MsgTypeEnum.text)
    public Future<Integer> handleSearchResultInteraction(Bot bot, AnyMessageEvent event) {
        String sessionId = getSessionIdWithPrefix(event);
        LastSearchResult lastSearch = lastSearchResultMap.get(sessionId);

        // 条件1: 如果没有活跃会话，忽略消息
        if (lastSearch == null) {
            return CompletableFuture.completedFuture(MESSAGE_IGNORE);
        }

        // 条件2: 如果消息不是由会话发起人发送的，忽略消息
        if (!Objects.equals(lastSearch.initiatorUserId, event.getUserId())) {
            return CompletableFuture.completedFuture(MESSAGE_IGNORE);
        }

        String message = event.getMessage().trim();

        // 检查是否为已知交互指令
        boolean isExit = "退出".equals(message) || "exit".equalsIgnoreCase(message);
        boolean isPaging = "下一页".equals(message) || "上一页".equals(message);
        boolean isSelection = NUMBER_SELECTION_PATTERN.matcher(message).matches();

        // 条件3: 如果不是任何一种已知交互指令，忽略消息 (而不是退出会话)
        // 这是修复交互问题的关键点
        if (!isExit && !isPaging && !isSelection) {
            return CompletableFuture.completedFuture(MESSAGE_IGNORE);
        }


        // 更新事件对象，用于后续的回复和超时提醒
        lastSearch.event = event;

        if (isExit) {
            clearSession(sessionId);
            String tipMessage = "已退出当前搜索会话";
            SessionType sessionType = checkStrictSessionIdType(sessionId);
            String quitMessage = switch (sessionType) {
                case GROUP -> MsgUtils.builder().at(removeSessionIdPrefix(sessionId)).text(" " + tipMessage).build();
                case PRIVATE -> MsgUtils.builder().text(tipMessage).build();
            };
            bot.sendMsg(event, quitMessage, false);
            return CompletableFuture.completedFuture(MESSAGE_BLOCK);// 退出后，阻塞消息，防止其他插件响应
        }

        if (isPaging) {
            int currentPage = lastSearch.result.getCurrentPage();
            int totalPages = lastSearch.result.getTotalPages();
            if ("下一页".equals(message)) {
                if (currentPage >= totalPages) {
                    bot.sendMsg(event, "已经是最后一页啦！", false);
                } else {
                    lastSearch.params.setPageNo(currentPage + 1);
                    // 异步执行耗时搜索，立即响应用户
                    executeSearch(bot, event, lastSearch.params);
                }
            } else { // 上一页
                if (currentPage <= 1) {
                    bot.sendMsg(event, "已经是第一页啦！", false);
                } else {
                    lastSearch.params.setPageNo(currentPage - 1);
                    // 异步执行耗时搜索
                    executeSearch(bot, event, lastSearch.params);
                }
            }
        } else if (isSelection) {
            List<Integer> selectedIndexes = Arrays.stream(message.split("[,，\\s]+"))
                    .filter(s -> !s.isEmpty()).map(Integer::parseInt).collect(Collectors.toList());
            List<PixivSearchResult.ArtworkData> artworks = lastSearch.result.getArtworks();
            if (artworks == null || artworks.isEmpty()) {
                bot.sendMsg(event, "当前搜索结果中没有作品数据，无法选择。", false);
            } else {
                bot.sendMsg(event, String.format("收到！准备发送你选择的 %d 个作品...", selectedIndexes.size()), false);
                for (int index : selectedIndexes) {
                    if (index > 0 && index <= artworks.size()) {
                        String pid = artworks.get(index - 1).getPid();
                        // sendArtworkByPidAsync 本身就是异步的，可以直接调用
                        sendArtworkByPidAsync(bot, event, pid);
                    } else {
                        bot.sendMsg(event, String.format("序号 %d 超出范围啦，请输入 1 到 %d 之间的数字。", index, artworks.size()), false);
                    }
                }
            }
        }

        // 任何有效的交互（翻页、选图）都应该重置超时
        resetSessionTimeout(bot, sessionId);

        return CompletableFuture.completedFuture(MESSAGE_BLOCK); // 阻塞消息，防止其他插件响应"下一页"等指令
    }


    @Async("taskExecutor")
    public void sendArtworkByPidAsync(Bot bot, AnyMessageEvent event, String pid) {
        boolean isInGroup = event.getGroupId() != null;
        Integer messageId = event.getMessageId();
        String fileName = null;
        String filePath = null;
        try {
            // 1. 获取作品详细信息
            PixivDetail pixivDetail = pixivService.getPixivArtworkDetail(pid);

            // 2. 异步下载图片文件
            List<File> files = pixivService.fetchImages(pid).join();

            if (files == null || files.isEmpty()) {
                bot.sendMsg(event, MsgUtils.builder()
                        .reply(messageId)
                        .text("未能获取到PID: " + pid + " 的图片文件！")
                        .build(), false);
                return;
            }

            // 3. 构建通用文本信息
            MsgUtils builder = MsgUtils.builder();
            // (注意：getPixivPic方法中有description，这里根据你的旧方法保持原样，如果需要可以加上)
            builder.text(String.format("""
                            作品标题：%s (%s)
                            作者：%s (%s)
                            作品链接：https://www.pixiv.net/artworks/%s
                            标签：%s
                            """, pixivDetail.getTitle(), pixivDetail.getPid(),
                    pixivDetail.getUserName(), pixivDetail.getUid(),
                    pixivDetail.getPid(), StringUtils.join(pixivDetail.getTags(), ',')));

            // 4. 根据是否为R18内容，选择不同发送策略
            if (pixivDetail.getIsR18()) {
                // --- R18 内容处理逻辑 ---

                // 先发送作品的文字信息
                bot.sendMsg(event, builder.build(), false);

                List<byte[]> imageBytes = new ArrayList<>();
                for (File file : files) {
                    imageBytes.add(FileUtils.readFileToByteArray(file));
                }

                // 根据作品类型（GIF或其他）选择打包方式
                filePath = pixivDetail.getType() == PixivArtworkType.GIF
                        ? DocxUtil.wrapImagesIntoDocx(imageBytes, FILE_OUTPUT_DIR)
                        : PdfUtil.wrapByteImagesIntoPdf(imageBytes, FILE_OUTPUT_DIR);

                if (filePath == null) {
                    log.error("生成R18文件包失败, pid={}", pid);
                    bot.sendMsg(event, MsgUtils.builder().text("生成R18文件包失败，请稍后重试。").build(), false);
                    return;
                }

                Path packagedFilePath = Paths.get(filePath);
                fileName = packagedFilePath.getFileName().toString();

                // 根据场景（群聊/私聊）上传文件
                ActionRaw actionRaw = isInGroup
                        ? bot.uploadGroupFile(event.getGroupId(), packagedFilePath.toAbsolutePath().toString(), fileName)
                        : bot.uploadPrivateFile(event.getUserId(), packagedFilePath.toAbsolutePath().toString(), fileName);

                if (actionRaw.getRetCode() != 0) {
                    log.error("上传 Pixiv R18 文件失败，pid={}, retcode={}, message={}", pid, actionRaw.getRetCode(), actionRaw.getStatus());
                    // 可以选择性地通知用户上传失败
                    bot.sendMsg(event, MsgUtils.builder().text("文件上传失败，请联系管理员查看日志。").build(), false);
                } else {
                    log.info("已成功发起 Pixiv R18 文件上传, pid={}, file={}", pid, filePath);
                }

            } else {
                // --- 非R18 内容处理逻辑 ---
                for (File file : files) {
                    builder.img(FileUtil.getFileUrlPrefix() + file.getAbsolutePath());
                }
                // 在图片后追加提示信息
                builder.text("\n可以继续发送【序号】获取其他作品，或发送【退出】结束本次搜索。");

                ActionData<MsgId> sendResp = bot.sendMsg(event, builder.build(), false);

                // 添加重试逻辑
                int retryTimes = 3;
                while ((sendResp == null || sendResp.getRetCode() != 0) && retryTimes-- > 0) {
                    log.warn("发送 Pixiv 图片失败，正在重试，剩余次数={}，pid={}", retryTimes, pid);
                    // 稍作等待再重试
                    Thread.sleep(1000);
                    sendResp = bot.sendMsg(event, builder.build(), false);
                }
                if (sendResp == null || sendResp.getRetCode() != 0) {
                    log.error("发送 Pixiv 图片最终失败，pid={}", pid);
                    bot.sendMsg(event, MsgUtils.builder().text("图片发送失败，请稍后重试。").build(), false);
                }
            }

        } catch (IOException e) {
            log.error("获取 Pixiv 作品信息时发生IO异常 pid={}", pid, e);
            bot.sendMsg(event, MsgUtils.builder().reply(messageId).text("获取 Pixiv 作品信息失败，可能是网络问题，请重试。").build(), false);
        } catch (Exception e) {
            log.error("处理 Pixiv 图片失败 pid={}", pid, e);
            bot.sendMsg(event, MsgUtils.builder().reply(messageId).text("处理 Pixiv 图片时发生未知错误：" + e.getMessage()).build(), false);
        } finally {
            clearFile(filePath);
            if (isInGroup) {
                deleteGroupFile(bot, event, fileName);
            }
        }
    }

    private void clearFile(String filePath) {
        if (filePath != null) {
            try {
                Files.deleteIfExists(Paths.get(filePath));
                log.info("已删除临时文件: {}", filePath);
            } catch (IOException e) {
                log.warn("删除临时文件失败: {}", filePath, e);
            }
        }
    }

    @Async
    protected void deleteGroupFile(Bot bot, AnyMessageEvent anyMessageEvent, String fileName) {
        try {
            TimeUnit.SECONDS.sleep(30);
            Long groupId = anyMessageEvent.getGroupId();
            ActionData<GroupFilesResp> groupRootFiles = bot.getGroupRootFiles(groupId);
            GroupFilesResp data = groupRootFiles.getData();
            for (GroupFilesResp.Files file : data.getFiles()) {
                if (file.getFileName().equals(fileName)) {
                    bot.deleteGroupFile(groupId, file.getFileId(), file.getBusId());
                    break;
                }
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void executeSearch(Bot bot, AnyMessageEvent event, PixivSearchParams params) {
        log.info("开始Pixiv搜索，关键词: {}, 参数: pageNo={}, isR18={}", params.getTags(), params.getPageNo(), params.isR18());
        bot.sendMsg(event, "正在搜索，请稍候...", false);
        try {
            PixivSearchResult result = pixivSearchService.search(params);
            String sessionId = getSessionIdWithPrefix(event);
            if (result != null && result.getScreenshot() != null && result.getTotalArtworks() > 0) {
                lastSearchResultMap.put(sessionId, new LastSearchResult(params, result, event));
                resetSessionTimeout(bot, sessionId); // 设置/重置超时

                String tagsString = String.join(" ", params.getTags());
                String r18Flag = params.isR18() ? " -r" : "";
                String previousCommand = String.format("pixiv搜索 %s%s -p", tagsString, r18Flag);
                MsgUtils msg = MsgUtils.builder()
                        .text(String.format("为你找到了关于 [%s] 的以下结果：\n", String.join(", ", params.getTags())))
                        .text(String.format("共 %d 个作品，当前在第 %d/%d 页。\n",
                                result.getTotalArtworks(), result.getCurrentPage(), result.getTotalPages()))
                        .img(result.getScreenshot())
                        .text(String.format("\n你可以发送【上一页】/【下一页】翻页，或【%s<页码>】跳转。\n", previousCommand))
                        .text(String.format("发送图片上的【序号】可获取原图。发送【退出】结束会话。\n(会话将在%d秒后无操作自动结束)", SESSION_TIMEOUT_SECONDS));
                bot.sendMsg(event, msg.build(), false);
            } else {
                clearSession(sessionId);
                String noResultMessage = String.format("抱歉，没有找到关于 [%s] 的结果呢。", String.join(" ", params.getTags()));
                if (params.isR18()) noResultMessage += " (已在R18分类下搜索)";
                if (params.getPageNo() > 1) noResultMessage += String.format(" (在第%d页)", params.getPageNo());
                bot.sendMsg(event, noResultMessage, false);
            }
        } catch (Exception e) {
            log.error("Pixiv搜索时发生异常", e);
            bot.sendMsg(event, "搜索过程中发生内部错误，请联系管理员。", false);
        }
    }

    private void resetSessionTimeout(Bot bot, String sessionId) {
        // 先取消上一个定时任务
        ScheduledFuture<?> oldTask = sessionTimeoutTasks.remove(sessionId);
        if (oldTask != null) {
            oldTask.cancel(false);
        }

        LastSearchResult currentSearch = lastSearchResultMap.get(sessionId);
        if (currentSearch == null) {
            // 如果会话已经不存在（可能被刚刚到期的旧任务清除了），则不创建新任务。
            log.warn("尝试重置超时任务时，会话 [{}] 已被清除，操作终止。", sessionId);
            return;
        }

        // 创建并存储新的定时任务
        ScheduledFuture<?> newTask = scheduler.schedule(() -> {
            // 使用原子操作 remove，它会返回被移除的值
            LastSearchResult removedSearch = lastSearchResultMap.remove(sessionId);
            if (removedSearch != null) {
                // 只有成功移除了会话（意味着在此期间没有其他操作清除它），才发送超时消息。
                SessionType sessionType = checkStrictSessionIdType(sessionId);
                sessionTimeoutTasks.remove(sessionId); // 确保超时任务映射也被清理
                String tipMessage = "Pixiv搜索会话已超时，请重新发起搜索。";
                log.info("Pixiv搜索会话 [{}] 因超时已自动结束。", sessionId);
                String message = switch (sessionType) {
                    case GROUP -> MsgUtils.builder().at(removeSessionIdPrefix(sessionId)).text(" " + tipMessage).build();
                    case PRIVATE -> MsgUtils.builder().text(tipMessage).build();
                };
                bot.sendMsg(removedSearch.event, message, false);
            }
            // 如果 removedSearch 是 null，说明会话已经被其他操作（如 '退出' 或新的交互）清除了，此时就不该发送超时消息。
        }, SESSION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        sessionTimeoutTasks.put(sessionId, newTask);
    }


    private LastSearchResult clearSession(String sessionId) {
        // 先取消可能存在的超时任务
        ScheduledFuture<?> task = sessionTimeoutTasks.remove(sessionId);
        if (task != null) {
            task.cancel(false);
        }
        // 然后移除会话并返回被移除的对象
        return lastSearchResultMap.remove(sessionId);
    }

}