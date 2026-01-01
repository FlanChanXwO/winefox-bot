package com.github.winefoxbot;

import cn.hutool.core.util.URLUtil;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author FlanChan (badapple495@outlook.com)
 * @since 2025-12-25-21:21
 */
public class PlayWrightTest {

    private static final boolean ENABLE_NUMBERING = true;      // 是否开启作品序号
    private static final boolean ENABLE_IMAGE_BLUR = false;       // 是否开启图片模糊
    private static final String BLUR_INTENSITY = "6px";         // 模糊强度 (例如: 5px, 10px, etc.)

    private static final String TAG_NAME = "ブルーアーカイブ"; // 目标标签名称

    private static final boolean ENABLE_R18 = false; // 是否启用 R18 模式

    private static final int pageNo = 59;

    private static final String sectionBaseCssSelector = "#__next > div > div:nth-child(2) > div.__top_side_menu_body > div > div:nth-child(3) > div > section";

    private static final String sectionCssSelector = sectionBaseCssSelector + " > div > div:nth-child(1) > ul";

    private static final String starIconCssSelector = sectionCssSelector + " > li > div:nth-child(1) > div > div > div:nth-child(2)";

    // 8,827
    private static final String totalSpanCssSelector = sectionBaseCssSelector + " > div > div:nth-child(1) > div span";

    public static void main(String[] args) {
        try (Playwright playwright = Playwright.create()) {
            try (Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setExecutablePath(Paths.get("C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"))
                    .setProxy("http://127.0.0.1:7890")
                    .setHeadless(true))) {

                // 1. 创建浏览器上下文 (BrowserContext)
                Browser.NewContextOptions contextOptions = new Browser.NewContextOptions();

                BrowserContext context = browser.newContext(contextOptions);

                // 2. 准备并添加 Cookie
                // Cookie 需要有 name, value, 和 domain。path 通常是 "/"
                // 请将 "your_cookie_name", "your_cookie_value" 等替换为你的真实 Cookie 信息
                List<Cookie> cookies = new ArrayList<>();
                cookies.add(new Cookie("p_ab_id", "5").setDomain(".pixiv.net").setPath("/"));
                cookies.add(new Cookie("PHPSESSID", "25649510_hrDTVtO8QcSJYRlFVtwi0AVFS8bQsF1s").setDomain(".pixiv.net").setPath("/"));

                context.addCookies(cookies);

                // 3. 从带有 Cookie 的上下文中创建新页面
                Page page = context.newPage();
                String mode = ENABLE_R18 ? "r18" : "safe";
                // 导航到目标页面
                page.navigate("https://www.pixiv.net/tags/%s/artworks?mode=%s&s_mode=s_tag&p=%s".formatted(URLUtil.encode(TAG_NAME), mode, pageNo));

                // 等待页面网络空闲，确保内容加载完成
                page.waitForLoadState(LoadState.NETWORKIDLE);
                System.out.println("页面加载完成。");
                // 2. 滚动页面以加载所有懒加载的图片
                System.out.println("开始向下滚动页面以加载所有图片...");
                smoothAutoScroll(page);
                System.out.println("滚动完成，所有图片应该已加载。");

                // 2. 注入序号到每个作品上
                if (ENABLE_NUMBERING) {
                    System.out.println("正在为每个作品添加序号...");
                    addNumberingToArtworks(page);
                    System.out.println("序号添加完成。");
                }
                // 3.模糊
                if (ENABLE_IMAGE_BLUR) {
                    System.out.println("正在对作品图片应用模糊效果...");
                    blurArtworks(page, BLUR_INTENSITY);
                    System.out.println("模糊效果应用完成。");
                }

                // 移除收藏星标图标
                System.out.println("正在移除收藏星标图标...");
                removeStarIcons(page);
                System.out.println("收藏星标图标移除完成。");

                // 4. 截取指定区域 (使用 Locator.screenshot())
                // 假设我们想截取包含所有作品缩略图的区域。
                // 通过浏览器开发者工具 (F12) 找到这个区域的 CSS 选择器。
                // Pixiv 上的作品列表区域的选择器可能会变化，这是一个示例，请根据实际情况调整。
                Locator artworksContainer = page.locator(sectionCssSelector);

                // 等待该元素在页面上可见
                artworksContainer.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.ATTACHED).setTimeout(50000));

                System.out.println("定位到作品列表区域，准备截图...");

                // 5. 对定位到的元素进行截图
                byte[] screenshot = artworksContainer.screenshot(new Locator.ScreenshotOptions()
                        .setType(ScreenshotType.PNG)
                        .setPath(Paths.get("pixiv_region.png"))); // 直接指定路径，更简洁


                // 6 提取作品数据
                System.out.println("正在提取作品数据...");
                List<Map<String, String>> artworksData = extractArtworksData(page);
                System.out.printf("成功提取了 %d 个作品的数据。\n", artworksData.size());
                System.out.println("指定区域截图已保存到 pixiv_region.png");

                // (可选) 打印提取到的数据进行验证
                for (int i = 0; i < artworksData.size(); i++) {
                    Map<String, String> data = artworksData.get(i);
                    System.out.printf(
                            "作品 %d: 作者链接 = %s, 作品链接 = %s\n",
                            i + 1,
                            data.get("authorUrl"),
                            data.get("artworkUrl")
                    );
                }


                // 如果你想使用 setClip 的方式（需要知道精确坐标）
                /*
                byte[] screenshotClip = page.screenshot(new Page.ScreenshotOptions()
                        .setPath(Paths.get("pixiv_clip.png"))
                        .setClip(100, 200, 800, 600)); // (x, y, width, height)
                System.out.println("通过Clip截图已保存到 pixiv_clip.png");
                */


                // 完成后关闭上下文和浏览器
                context.close();

            }
        }
    }

    /**
     * 从页面提取所有作品的作者链接和作品链接
     *
     * @param page Playwright Page 对象
     * @return 包含作品数据的List
     */
    private static List<Map<String, String>> extractArtworksData(Page page) {
        String jsScript =
                "() => {" +
                        "    const artworks = document.querySelectorAll('" + sectionCssSelector + " > li" + "');" +
                        "    const data = [];" +
                        "    artworks.forEach(artwork => {" +
                        "        const artworkLinkElement = artwork.querySelector('a[href*=\"/artworks/\"]');" + // 查找作品链接
                        "        const authorLinkElement = artwork.querySelector('a[href*=\"/users/\"]');" + // 查找作者链接
                        "        if (artworkLinkElement && authorLinkElement) {" + // 确保两个链接都找到了
                        "            data.push({" +
                        "                artworkUrl: artworkLinkElement.href," +
                        "                authorUrl: authorLinkElement.href" +
                        "            });" +
                        "        }" +
                        "    });" +
                        "    return data;" + // 返回数据数组
                        "}";

        // page.evaluate()会返回JS脚本的返回值，并自动转换为Java类型
        @SuppressWarnings("unchecked")
        List<Map<String, String>> result = (List<Map<String, String>>) page.evaluate(jsScript);
        return result;
    }


    /**
     * 对所有作品的预览图应用模糊效果
     *
     * @param page      Playwright Page 对象
     * @param blurValue CSS blur() 函数的值, e.g., "8px"
     */
    private static void blurArtworks(Page page, String blurValue) {
        // P站的作品图片在一个特定的 div 容器里
        String imageContainerSelector = sectionCssSelector + " > li > div > div:nth-child(1) img";
        String jsScript = String.format(
                "() => {" +
                        "    const imageContainers = document.querySelectorAll('%s');" +
                        "    imageContainers.forEach(container => {" +
                        "        container.style.filter = 'blur(%s)';" +
                        "    });" +
                        "}", imageContainerSelector, blurValue);
        page.evaluate(jsScript);
    }


    /**
     * 动态地为页面上的每个作品添加一个带样式的序号标签
     *
     * @param page Playwright Page 对象
     */
    private static void addNumberingToArtworks(Page page) {
        // 这是将在浏览器中执行的 JavaScript 代码
        String jsScript =
                "() => {" +
                        "    const artworks = document.querySelectorAll('" + sectionCssSelector + " > li" + "');" +
                        "    artworks.forEach((artwork, index) => {" +
                        "        artwork.style.position = 'relative';" + // 确保父元素是相对定位
                        "        const numberLabel = document.createElement('div');" +
                        "        numberLabel.innerText = index + 1;" + // 序号从1开始
                        "        numberLabel.style.position = 'absolute';" +
                        "        numberLabel.style.top = '50%';" +
                        "        numberLabel.style.left = '50%';" +
                        "        numberLabel.style.transform = 'translate(-50%, -50%)';" +
                        "        numberLabel.style.zIndex = '100';" + // 确保在顶层显示
                        "        numberLabel.style.backgroundColor = 'rgba(40, 40, 40, 0.8)';" + // 半透明的深灰色背景
                        "        numberLabel.style.color = 'white';" + // 白色文字
                        "        numberLabel.style.borderRadius = '50%';" + // 圆形
                        "        numberLabel.style.width = '60px';" + // 可以稍微调大一点，使其更醒目
                        "        numberLabel.style.height = '60px';" + // 高度
                        "        numberLabel.style.display = 'flex';" +
                        "        numberLabel.style.alignItems = 'center';" +
                        "        numberLabel.style.justifyContent = 'center';" +
                        "        numberLabel.style.fontSize = '28px';" + // 字体也可以稍大
                        "        numberLabel.style.fontWeight = 'bold';" +
                        "        numberLabel.style.boxShadow = '0 0 5px rgba(0,0,0,0.7)';" + // 阴影更明显
                        "        artwork.appendChild(numberLabel);" +
                        "    });" +
                        "}";

        // 执行脚本
        page.evaluate(jsScript);
    }


    /**
     * 自动向下滚动页面的辅助函数
     *
     * @param page Playwright Page 对象
     */
    private static void autoScroll(Page page) throws InterruptedException {
        // 使用 JavaScript 来执行滚动
        Object lastHeight = page.evaluate("document.body.scrollHeight");

        while (true) {
            // 向下滚动一屏
            page.evaluate("window.scrollTo(0, document.body.scrollHeight)");

            // 等待新内容加载。waitForLoadState(LoadState.NETWORKIDLE) 是一个很好的选择，
            // 它会等待网络请求在 500ms 内不再活跃。
            // 这比固定的 Thread.sleep() 更可靠。
            try {
                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(2000)); // 设置2秒超时
            } catch (TimeoutError e) {
                // 如果超时，说明可能还在加载或者已经到底了，可以忽略并继续
                System.out.println("等待网络空闲超时，继续滚动...");
            }

            // 计算新的滚动高度并与上次的高度进行比较
            Object newHeight = page.evaluate("document.body.scrollHeight");

            // 如果滚动后高度没有变化，说明已经到达页面底部
            if (newHeight.equals(lastHeight)) {
                System.out.println("已到达页面底部。");
                break;
            }
            lastHeight = newHeight;
        }
    }


    private static void removeStarIcons(Page page) {
        String jsScript =
                "() => {" +
                        "    const starIcons = document.querySelectorAll('" + starIconCssSelector + "');" + // 替换为实际的类名或选择器
                        "    starIcons.forEach(icon => icon.remove());" +
                        "}";
        page.evaluate(jsScript);
    }

    /**
     * 模拟用户平滑地、分步地向下滚动页面，直到页面底部
     * @param page Playwright Page 对象
     */
    /**
     * 修正后的平滑滚动函数，能正确滚动到页面底部
     *
     * @param page Playwright Page 对象
     */
    private static void smoothAutoScroll(Page page) {
        int maxScrolls = 100; // 防止无限循环
        int scrollCount = 0;

        while (scrollCount < maxScrolls) {
            // 执行JS来判断是否已在页面底部
            boolean isAtBottom = (Boolean) page.evaluate(
                    "() => {" +
                            "    const scrollableHeight = document.documentElement.scrollHeight - window.innerHeight;" +
                            // 增加1-2像素的容错，处理高分屏下的像素计算差异
                            "    return window.scrollY >= scrollableHeight - 2;" +
                            "}"
            );

            // 如果已经在底部，就不再滚动，直接进入图片加载检查
            if (isAtBottom) {
                System.out.println("已到达页面物理底部。");
                break;
            }

            // 向下滚动一整屏
            page.evaluate("window.scrollBy(0, window.innerHeight)");
            scrollCount++;
            System.out.printf("已滚动 %d 次...\n", scrollCount);

            // 等待滚动后的网络活动基本完成
            try {
                page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions().setTimeout(1500));
            } catch (TimeoutError e) {
                // 超时是正常的，可能图片还在加载，继续即可
                System.out.println("等待网络空闲超时，继续...");
            }
        }

        if (scrollCount >= maxScrolls) {
            System.out.println("达到最大滚动次数。");
        }

        // --- 关键步骤：滚动结束后，最终确认所有图片是否加载完毕 ---
        System.out.println("滚动完成。现在开始确认所有图片是否已加载...");
        try {
            // 使用 page.waitForFunction 来轮询检查所有图片的 'complete' 状态
            page.waitForFunction(
                    "() => {" +
                            "    const images = document.querySelectorAll('" + sectionCssSelector + " img');" +
                            "    if (images.length === 0) return false;" + // 如果还没找到图片，说明页面没加载好，继续等待
                            "    for (const img of images) {" +
                            // 'complete' 属性表示图片是否已完全加载（无论成功还是失败）
                            // 'naturalWidth > 0' 确保图片不是一个损坏的空图片
                            "        if (!img.complete || img.naturalWidth === 0) {" +
                            "            return false;" + // 只要有一张图片没加载完，就返回 false
                            "        }" +
                            "    }" +
                            "    console.log('所有 ' + images.length + ' 张图片都已加载完成。');" +
                            "    return true;" + // 所有图片都检查完毕，返回 true
                            "}",
                    null, // 无参数
                    new Page.WaitForFunctionOptions().setTimeout(15000) // 设置一个足够长的超时时间，例如15秒
            );
            System.out.println("确认成功：容器内的所有图片均已加载完毕。");
        } catch (TimeoutError e) {
            System.out.println("警告：等待图片加载完成超时。截图可能不完整。");
        }
    }
}