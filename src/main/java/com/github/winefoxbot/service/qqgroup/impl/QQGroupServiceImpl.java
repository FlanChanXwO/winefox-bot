package com.github.winefoxbot.service.qqgroup.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.github.winefoxbot.model.entity.QQGroupAutoHandleAddRequestFeatureConfig;
import com.github.winefoxbot.service.qqgroup.QQGroupAutoHandleAddRequestFeatureConfigService;
import com.github.winefoxbot.service.qqgroup.QQGroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-26-22:03
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QQGroupServiceImpl implements QQGroupService {
    private final QQGroupAutoHandleAddRequestFeatureConfigService featureConfigService;


    @Override
    public QQGroupAutoHandleAddRequestFeatureConfig getOrCreateAutoHandleAddRequestConfig(Long groupId) {
        LambdaQueryWrapper<QQGroupAutoHandleAddRequestFeatureConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(QQGroupAutoHandleAddRequestFeatureConfig::getGroupId, groupId);
        QQGroupAutoHandleAddRequestFeatureConfig config = featureConfigService.getOne(queryWrapper);

        if (config == null) {
            config = new QQGroupAutoHandleAddRequestFeatureConfig();
            config.setGroupId(groupId);
            config.setAutoHandleAddRequestEnabled(false);
            config.setBlockFeatureEnabled(false);
            featureConfigService.save(config);
        }
        return config;
    }

    @Override
    public boolean toggleAutoHandleAddRequestFeature(Long groupId, boolean enable, QQGroupAutoHandleAddRequestFeatureConfig config) {
        config.setAutoHandleAddRequestEnabled(enable);
        return featureConfigService.updateById(config);
    }

    @Override
    public boolean toggleAutoBlockAddRequestFeature(Long groupId, boolean enable, QQGroupAutoHandleAddRequestFeatureConfig config) {
        config.setBlockFeatureEnabled(enable);
        return featureConfigService.updateById(config);
    }

    /*

    @Override
    public int onGroupRequest(@NotNull Bot bot, @NotNull GroupRequestEvent event) {
        GroupFeatureConfig config = getOrCreateConfig(event.getGroupId());
        if (!config.isBlockFeatureEnabled() || !"add".equals(event.getSubType())) {
            return MESSAGE_IGNORE;
        }

        LambdaQueryWrapper<BlockedUser> query = new LambdaQueryWrapper<BlockedUser>()
                .eq(BlockedUser::getGroupId, event.getGroupId())
                .eq(BlockedUser::getUserId, event.getUserId());

        if (blockedUserMapper.exists(query)) {
            logger.info("检测到被屏蔽用户(QQ:{})申请加入群(群号:{}), 已自动拒绝.", event.getUserId(), event.getGroupId());
            bot.setGroupAddRequest(event.getFlag(), event.getSubType(), false, "您已被本群屏蔽，无法加入。");
            return MESSAGE_BLOCK;
        }
        return MESSAGE_IGNORE;
    }

    @Override
    public int onGroupMessage(@NotNull Bot bot, @NotNull GroupMessageEvent event) {

    }

    private void handleBlock(Bot bot, GroupMessageEvent event) {
        long groupId = event.getGroupId();
        long operatorId = event.getUserId();
        String operatorNickname = event.getSender().getCardOrNick();

        getTargetUserId(event).forEach(targetId -> {
            LambdaQueryWrapper<BlockedUser> query = new LambdaQueryWrapper<BlockedUser>()
                    .eq(BlockedUser::getGroupId, groupId)
                    .eq(BlockedUser::getUserId, targetId);

            if (blockedUserMapper.exists(query)) {
                bot.sendGroupMsg(groupId, MsgUtils.builder().at(operatorId).text("用户 " + targetId + " 已在本群的屏蔽列表中。").build(), false);
                return;
            }

            bot.getStrangerInfo(targetId, false).whenComplete((info, ex) -> {
                String targetNickname = (ex == null) ? info.getData().getNickname() : "未知";
                BlockedUser newBlock = new BlockedUser();
                newBlock.setGroupId(groupId);
                newBlock.setUserId(targetId);
                newBlock.setUserNickname(targetNickname);
                newBlock.setOperatorId(operatorId);
                newBlock.setOperatorNickname(operatorNickname);
                newBlock.setBlockTimestamp(OffsetDateTime.now());
                blockedUserMapper.insert(newBlock);
                logger.info("管理员(QQ:{})在群(群号:{})中屏蔽了用户(QQ:{})", operatorId, groupId, targetId);
                bot.sendGroupMsg(groupId, MsgUtils.builder().at(operatorId).text("已成功屏蔽用户: " + targetNickname + "(" + targetId + ")").build(), false);
            });
        });
    }

    private void handleUnblock(Bot bot, GroupMessageEvent event) {
        long groupId = event.getGroupId();
        long operatorId = event.getUserId();

        getTargetUserId(event).forEach(targetId -> {
            LambdaQueryWrapper<BlockedUser> query = new LambdaQueryWrapper<BlockedUser>()
                    .eq(BlockedUser::getGroupId, groupId)
                    .eq(BlockedUser::getUserId, targetId);
            int deletedRows = blockedUserMapper.delete(query);

            if (deletedRows > 0) {
                logger.info("管理员(QQ:{})在群(群号:{})中取消屏蔽了用户(QQ:{})", operatorId, groupId, targetId);
                bot.sendGroupMsg(groupId, MsgUtils.builder().at(operatorId).text("已成功取消对用户 " + targetId + " 的屏蔽。").build(), false);
            } else {
                bot.sendGroupMsg(groupId, MsgUtils.builder().at(operatorId).text("用户 " + targetId + " 不在本群的屏蔽列表中。").build(), false);
            }
        });
    }

    private void handleShowList(Bot bot, long groupId) {
        if (playwright == null || browser == null) {
            bot.sendGroupMsg(groupId, "图片渲染服务初始化失败，无法生成屏蔽列表图片。", false);
            return;
        }

        LambdaQueryWrapper<BlockedUser> query = new LambdaQueryWrapper<BlockedUser>()
                .eq(BlockedUser::getGroupId, groupId)
                .orderByDesc(BlockedUser::getBlockTimestamp);
        List<BlockedUser> blockedList = blockedUserMapper.selectList(query);

        if (blockedList.isEmpty()) {
            bot.sendGroupMsg(groupId, "本群当前屏蔽列表为空。", false);
            return;
        }

        bot.sendGroupMsg(groupId, "正在生成本群屏蔽列表图片，请稍候...", false);

        CompletableFuture.runAsync(() -> {
            try {
                String groupName = bot.getGroupInfo(groupId, false).getData().getGroupName();
                String htmlContent = generateHtml(groupId, groupName, blockedList);
                Path screenshotPath = Paths.get("data", "block_user_plugin", "blocked_list_" + groupId + "_" + System.currentTimeMillis() + ".png");

                BrowserContext context = browser.newContext();
                Page page = context.newPage();
                page.setContent(htmlContent);
                page.waitForLoadState();

                page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath).setType(ScreenshotType.PNG).setFullPage(true));

                page.close();
                context.close();

                bot.sendGroupMsg(groupId, MsgUtils.builder().img(screenshotPath.toFile().getAbsolutePath()).build(), false);
            } catch (Exception e) {
                logger.error("生成群 {} 的屏蔽列表图片失败", groupId, e);
                bot.sendGroupMsg(groupId, "生成屏蔽列表图片时发生错误，请查看后台日志。", false);
            }
        });
    }

    private Set<Long> getTargetUserId(GroupMessageEvent event) {
        String msg = event.getRawMessage();
        Set<Long> userIds = new HashSet<>();
        MsgUtils.getAt(msg).forEach(userIds::add);
        String[] parts = msg.split("\\s+");
        for (String part : parts) {
            if (part.matches("\\d{5,11}")) {
                try {
                    userIds.add(Long.parseLong(part));
                } catch (NumberFormatException ignored) {}
            }
        }
        if (userIds.isEmpty()) {
            bot.sendGroupMsg(event.getGroupId(), "请艾特用户或提供用户的QQ号。", false);
        }
        return userIds;
    }

    // HTML生成
    private String generateHtml(long groupId, String groupName, List<QQGroupAddRequestBlockedUsers> userList) {
        StringBuilder tableRows = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        userList.forEach(user -> {
            String formattedTime = user.getCreatedAt().format(formatter);
            tableRows.append("<tr>")
                    .append("<td>").append(user.getUserId()).append("</td>")
                    .append("<td>").append(escapeHtml(user.getUserNickname())).append("</td>")
                    .append("<td>").append(formattedTime).append("</td>")
                    .append("</tr>");
        });

        return """
                <!DOCTYPE html>
                <html lang="zh-CN">
                <head>
                    <meta charset="UTF-8">
                    <title>屏蔽用户列表</title>
                    <style>
                        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; background-color: #f8f9fa; margin: 0; padding: 20px; display: flex; justify-content: center; }
                        .container { width: 900px; background-color: white; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); overflow: hidden; }
                        header { background-color: #4a90e2; color: white; padding: 20px; text-align: center; }
                        header h1 { margin: 0; font-size: 24px; }
                        header p { margin: 5px 0 0; font-size: 14px; opacity: 0.9; }
                        table { width: 100%; border-collapse: collapse; }
                        th, td { padding: 15px; text-align: left; border-bottom: 1px solid #dee2e6; }
                        th { background-color: #f2f2f2; font-weight: 600; }
                        tr:last-child td { border-bottom: none; }
                        tr:nth-child(even) { background-color: #f9f9f9; }
                        tr:hover { background-color: #e9ecef; }
                        footer { padding: 15px; text-align: center; font-size: 12px; color: #868e96; background-color: #f8f9fa; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <header>
                            <h1>群屏蔽列表 - %s (%d)</h1>
                            <p>总计: %d 位用户被屏蔽</p>
                        </header>
                        <table>
                            <thead>
                                <tr>
                                    <th>被屏蔽QQ</th>
                                    <th>昵称</th>
                                    <th>操作管理员</th>
                                    <th>屏蔽时间</th>
                                </tr>
                            </thead>
                            <tbody>
                                %s
                            </tbody>
                        </table>
                        <footer>
                            Generated by Shiro Block Plugin at %s
                        </footer>
                    </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(groupName),
                groupId,
                userList.size(),
                tableRows.toString(),
                OffsetDateTime.now().format(formatter)
        );
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    } */
}