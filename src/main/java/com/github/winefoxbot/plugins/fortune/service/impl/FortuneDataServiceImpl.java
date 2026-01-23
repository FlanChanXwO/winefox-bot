package com.github.winefoxbot.plugins.fortune.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.winefoxbot.core.annotation.common.RedissonLock;
import com.github.winefoxbot.core.context.BotContext;
import com.github.winefoxbot.core.model.enums.common.MessageType;
import com.github.winefoxbot.plugins.fortune.config.FortuneApiConfig;
import com.github.winefoxbot.plugins.fortune.config.FortunePluginConfig;
import com.github.winefoxbot.plugins.fortune.mapper.FortuneDataMapper;
import com.github.winefoxbot.plugins.fortune.model.entity.FortuneData;
import com.github.winefoxbot.plugins.fortune.model.vo.FortuneRenderVO;
import com.github.winefoxbot.plugins.fortune.service.FortuneDataService;
import com.github.winefoxbot.plugins.fortune.service.FortuneRenderService;
import com.jayway.jsonpath.JsonPath;
import com.mikuac.shiro.common.utils.MsgUtils;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.dto.event.message.AnyMessageEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Random;

/**
 * @author FlanChan
 * @description 针对表【fortune_data(今日运势数据表)】的数据库操作Service实现
 * @createDate 2026-01-10 05:06:35
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FortuneDataServiceImpl extends ServiceImpl<FortuneDataMapper, FortuneData>
        implements FortuneDataService {

    private final FortuneApiConfig apiConfig;
    private final OkHttpClient httpClient;
    private final FortuneRenderService renderService;

    @Lazy
    @Autowired
    private FortuneDataService self;

    private final double[] WEIGHTS = {0.1, 0.15, 0.2, 0.25, 0.15, 0.12, 0.07, 0.005};

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void getFortune(Bot bot, AnyMessageEvent event) {
        final long userId = event.getUserId();

        // 获取用户显示名称 (群名片 > 昵称)
        String displayName = "OP";
        if (event.getSender() != null) {
            displayName = event.getSender().getCard() != null && !event.getSender().getCard().isEmpty()
                    ? event.getSender().getCard()
                    : event.getSender().getNickname();
        }

        FortuneRenderVO vo = getFortuneRenderVO(userId, displayName);
        MessageType type = MessageType.fromValue(event.getMessageType());
        Long groupId = type == MessageType.GROUP ? event.getGroupId() : null;

        sendFortuneImage(bot, userId, groupId, type, vo);
    }

    /**
     * 获取运势渲染数据
     * 注意：移除了 @Transactional，防止锁在事务提交前释放导致并发读取到旧数据
     * MyBatis-Plus 的 saveOrUpdate 内部自带事务，这里依靠 RedissonLock 保证业务原子性
     */
    @Override
    @RedissonLock(prefix = "fortune:lock" ,key = "#userId")
    public FortuneRenderVO getFortuneRenderVO(long userId, String displayName) {
        final var today = LocalDate.now();

        // 1. 业务逻辑：获取或生成运势数据
        FortuneData data = this.getById(userId);

        int starNum;
        boolean needNewFortune = false;

        if (data == null) {
            needNewFortune = true;
            starNum = 0;
        } else {
            boolean isToday = data.getFortuneDate().equals(today);
            if (isToday) {
                starNum = data.getStarNum();
            } else {
                if (apiConfig.isAutoRefreshJrys()) {
                    needNewFortune = true;
                    starNum = 0;
                } else {
                    starNum = data.getStarNum();
                }
            }
        }

        if (needNewFortune) {
            starNum = calculateLuck();
            var newData = FortuneData.builder()
                    .userId(userId)
                    .starNum(starNum)
                    .fortuneDate(today)
                    .build();
            this.saveOrUpdate(newData);
        }

        // 2. 数据组装：准备渲染所需的 VO 对象
        String imageUrl = null;
        if ( !"none".equals(apiConfig.getApi())) {
            try {
                imageUrl = self.getSyncedImageUrl(apiConfig.getApi(), userId, today.toString());
            } catch (Exception e) {
                log.error("获取运势图片失败", e);
            }
        }

        log.info("最终使用的图片URL: {}", imageUrl);

        String title = apiConfig.getJrysTitles().get(Math.min(starNum, apiConfig.getJrysTitles().size() - 1));
        String desc = apiConfig.getJrysMessages().get(Math.min(starNum, apiConfig.getJrysMessages().size() - 1));

        String themeClass = switch (starNum) {
            case 7, 6 -> "theme-red";
            case 5, 4 -> "theme-gold";
            case 1, 0 -> "theme-gray";
            default -> "theme-blue";
        };

        return new FortuneRenderVO(
                displayName == null ? "指挥官" : displayName,
                today.toString(),
                title,
                desc,
                apiConfig.getJrysExtraMessage(),
                starNum,
                imageUrl,
                themeClass
        );
    }


    @Cacheable(value = "fortune:img", key = "#userId + ':' + #dateStr", unless = "#result == null")
    @Override
    public String getSyncedImageUrl(String apiType, long userId, String dateStr) {
        log.debug("Cache Miss - 调用 API 获取图片: User={}, Date={}", userId, dateStr);
        return getImageUrlInternal(apiType);
    }

    @Override
    @Async
    public void sendFortuneImage(Bot bot, long userId, Long groupId, MessageType type, FortuneRenderVO vo) {
        try {
            byte[] imgBytes = renderService.render(vo);
            var sendMsg = MsgUtils.builder();
            if (type == MessageType.GROUP) {
                sendMsg.at(userId);
            }
            sendMsg.img(imgBytes);

            if (type == MessageType.GROUP && groupId != null) {
                bot.sendGroupMsg(groupId, sendMsg.build(), false);
            } else {
                bot.sendPrivateMsg(userId, sendMsg.build(), false);
            }
        } catch (Exception e) {
            log.error("图片渲染失败，降级为文本发送", e);
            fallbackTextFortune(bot, userId, groupId, type, vo.starCount(), vo.title(), vo.description(), apiConfig.getJrysExtraMessage());
        }
    }

    private void fallbackTextFortune(Bot bot, Long userId, Long groupId, MessageType type, int starNum, String title, String desc, String extra) {
        StringBuilder stars = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            stars.append(i < starNum ? "★" : "☆");
        }
        String msg = String.format("\n%s\n%s\n%s", title, desc, stars);
        if (extra != null && !extra.isEmpty()) msg += "\n" + extra;

        var sendMsg = MsgUtils.builder();
        if (type == MessageType.GROUP) {
            sendMsg.at(userId);
        }
        sendMsg.text(msg);

        if (type == MessageType.GROUP && groupId != null) {
            bot.sendGroupMsg(groupId, sendMsg.build(), false);
        } else {
            bot.sendPrivateMsg(userId, sendMsg.build(), false);
        }
    }

    @Override
    public void refreshFortune(Bot bot, AnyMessageEvent event) {
        this.removeById(event.getUserId());
        // 注意：刷新运势时，可能需要清理缓存，或者等待第二天自动过期
        // 如果想立即刷新图片，可以注入 CacheManager 手动 evict，这里暂时只清理 DB
        MsgUtils msg = MsgUtils.builder().at(event.getUserId()).text(" 已刷新你的今日运势！");
        bot.sendMsg(event, msg.build(), false);
    }

    @Override
    public void refreshAllFortune(Bot bot, AnyMessageEvent event) {
        this.remove(new LambdaQueryWrapper<>(FortuneData.class));
        bot.sendMsg(event, "已刷新全局今日运势！", false);
    }

    private int calculateLuck() {
        double totalWeight = 0;
        for (double w : WEIGHTS) totalWeight += w;
        double randomValue = new Random().nextDouble() * totalWeight;
        double currentWeight = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            currentWeight += WEIGHTS[i];
            if (randomValue <= currentWeight) return i;
        }
        return 0;
    }

    // 将原 getImageUrl 改名为 Internal，供缓存方法调用
    private String getImageUrlInternal(String apiType) {
        if ("none".equals(apiType)) {
            return null;
        }
        Optional<FortunePluginConfig> config = BotContext.getPluginConfig(FortunePluginConfig.class);
        FortunePluginConfig fortuneConfig = config.orElseThrow(() -> new IllegalStateException("无法获取 FortunePluginConfig 配置"));
        try {
            return switch (apiType) {
                case "wr" -> fetchUrlFromJson("https://api.obfs.dev/api/bafortune", "$.url");
                case "lolicon" -> {
                    String loliconBase = "https://api.lolicon.app/setu/v1";
                    HttpUrl loliconUrl = HttpUrl.parse(loliconBase).newBuilder()
                            .addQueryParameter("r18", "0")
                            .addQueryParameter("tag", fortuneConfig.getTag())
                            .addQueryParameter("excludeAI", "true")
                            .build();
                    yield fetchUrlFromJson(loliconUrl.toString(), "$.data[0].url");
                }
                case "custom" -> {
                    FortuneApiConfig.CustomApiConfig custom = apiConfig.getCustomApi();
                    if (custom == null || custom.getUrl() == null) yield null;
                    String targetUrl = buildUrlWithParams(custom.getUrl(), custom.getParams());
                    if (custom.getResponseType() == FortuneApiConfig.ResponseType.IMAGE) yield targetUrl;
                    yield fetchUrlFromJson(targetUrl, custom.getJsonPath());
                }
                case "local" -> null;
                default -> null;
            };
        } catch (Exception e) {
            log.error("获取运势图片异常: apiType={}", apiType, e);
            return null;
        }
    }

    private String fetchUrlFromJson(String url, String jsonPath) {
        if (jsonPath == null || jsonPath.isBlank()) return null;
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            Object result = JsonPath.read(response.body().string(), jsonPath);
            if (result instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
                return list.get(0).toString();
            } else if (result != null) {
                return result.toString();
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private String buildUrlWithParams(String baseUrl, FortuneApiConfig.Params params) {
        Optional<FortunePluginConfig> config = BotContext.getPluginConfig(FortunePluginConfig.class);
        FortunePluginConfig fortuneConfig = config.orElseThrow(() -> new IllegalStateException("无法获取 FortunePluginConfig 配置"));
        HttpUrl httpUrl = HttpUrl.parse(baseUrl);
        if (httpUrl == null) return baseUrl;
        HttpUrl.Builder builder = httpUrl.newBuilder();
        if (params != null && params.getStaticParams() != null) {
            for (FortuneApiConfig.ParamItem item : params.getStaticParams()) {
                builder.addQueryParameter(item.getKey(), item.getValue());
            }
            builder.addQueryParameter("tag",fortuneConfig.getTag());
        }
        return builder.build().toString();
    }
}
