package com.github.winefoxbot.core.aitools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.winefoxbot.core.model.enums.MessageType;
import com.github.winefoxbot.core.service.helpdoc.HelpImageService;
import com.github.winefoxbot.core.utils.SendMsgUtil;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.Optional;
import java.util.function.Function;

@Configuration(proxyBeanMethods = false)
@Slf4j
@RequiredArgsConstructor
public class HelpDocImageTool {

    private final HelpImageService helpImageService;
    private final BotContainer botContainer;

    public static final String CURRENT_TOOL = "helpDocumentTool";


    public record HelpCommandRequest(
            @ToolParam(required = true, description = "调用该工具所需的session_id，需要从json消息的session_id字段中获取")
            Long sessionId,
            @ToolParam(required = true, description = "调用该工具所需的message_type，需要从json消息的message_type字段中获取,该参数必须为小写")
            String messageType,
            @ToolParam(required = false,description = "功能组名称，例如'wf'、'管理'等。如果不指定则获取全部帮助。")
            String groupName

    ) {}

    public record HelpCommandResponse(
                                    @ToolParam(description = "是否调用工具成功：true:成功 false:失败")  Boolean success,
                                    @ToolParam(description = "错误信息") String message) {}

    @Bean(CURRENT_TOOL)
    @Description("获取系统的帮助文档图片。当用户询问'帮助'、'有什么功能'、'怎么用'、'说明'或想要某个'分组'的文档时，应调用此工具。它可以展示所有功能或特定分组的功能说明。")
    public Function<HelpCommandRequest,HelpCommandResponse> helpDocumentTool() {
        log.info("Help Document Tool Bean initialized");
        return request -> {
            try {
                Optional<Bot> botOpt = botContainer.robots.values().stream().findFirst();
                if (botOpt.isPresent()) {
                    log.info("Generating help image for group: {}", request.groupName);
                    byte[] imageBytes = request.groupName == null || request.groupName.isEmpty() ? helpImageService.generateAllHelpImage() : helpImageService.generateHelpImageByGroup(request.groupName());
                    if (imageBytes == null) {
                        log.warn("Help image generation failed");

                    }
                    String msg = MsgUtils.builder()
                            .img(imageBytes)
                            .build();
                    Bot bot = botOpt.get();
                    MessageType messageType = MessageType.fromValue(request.messageType.toLowerCase());
                    if (messageType == MessageType.GROUP) {
                        SendMsgUtil.sendGroupMsg(bot, request.sessionId, msg,false);
                    } else if (messageType == MessageType.PRIVATE) {
                        SendMsgUtil.sendPrivateMsg(bot, request.sessionId, msg,false);
                    }
                }
                return new HelpCommandResponse(true,  "帮助图片已生成");
            } catch (Exception e) {
                log.error("Error generating help image", e);
                return new HelpCommandResponse(false,  "生成帮助图片时发生错误: " + e.getMessage());
            }
        };
    }

}