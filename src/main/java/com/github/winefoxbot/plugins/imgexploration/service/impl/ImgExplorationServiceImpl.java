package com.github.winefoxbot.plugins.imgexploration.service.impl;


import com.github.winefoxbot.plugins.imgexploration.model.dto.ExplorationResultDTO;
import com.github.winefoxbot.plugins.imgexploration.model.dto.SearchResultItemDTO;
import com.github.winefoxbot.plugins.imgexploration.service.ImgExplorationService;
import com.github.winefoxbot.plugins.imgexploration.strategy.ImageSearchStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * 搜图服务实现类
 *
 * @author FlanChan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImgExplorationServiceImpl implements ImgExplorationService {

    private final List<ImageSearchStrategy> strategies;
    
    private final ExecutorService virtualThreadExecutor;
    
    private final OkHttpClient okHttpClient;

    @Override
    public CompletableFuture<ExplorationResultDTO> explore(String imageUrl) {
        // 使用 supplyAsync 将整个流程放入异步线程执行，不阻塞主线程
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            log.info("开始搜图任务，目标URL: {}, 启用策略数: {}", imageUrl, strategies.size());

            try {
                // 1. 【策略分发】并行调用所有搜图策略
                List<CompletableFuture<List<SearchResultItemDTO>>> futures = strategies.stream()
                        .map(strategy -> strategy.search(imageUrl)
                                .exceptionally(ex -> {
                                    log.error("策略 [{}] 执行失败", strategy.getServiceName(), ex);
                                    return Collections.emptyList(); // 失败返回空列表，不影响其他策略
                                }))
                        .toList();

                // 2. 【等待结果】阻塞虚拟线程直到所有策略完成
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

                // 3. 【聚合结果】将所有 List 合并为一个大的 List
                List<SearchResultItemDTO> allItems = futures.stream()
                        .map(CompletableFuture::join)
                        .flatMap(List::stream)
                        .collect(Collectors.toCollection(ArrayList::new)); // 转为 ArrayList 以便后续修改

                log.info("搜图完成，共获取 {} 条结果，开始下载缩略图...", allItems.size());

                // 4. 【后处理】并行下载缩略图 (这一步很耗时，必须并行)
                fillThumbnails(allItems);
                
                log.info("任务结束，总耗时: {}ms", System.currentTimeMillis() - startTime);

                return new ExplorationResultDTO(allItems);

            } catch (Exception e) {
                log.error("搜图主流程发生严重错误", e);
                // 抛出异常，让上层 handle
                throw new RuntimeException("搜图服务内部错误", e);
            }
        }, virtualThreadExecutor);
    }

    /**
     * 并行下载列表中的缩略图并回填到对象中
     */
    private void fillThumbnails(List<SearchResultItemDTO> items) {
        // 使用虚拟线程 Scope 或者流式并行处理下载任务
        List<CompletableFuture<Void>> downloadTasks = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            SearchResultItemDTO item = items.get(i);

            // 如果已经有图（比如 Google 策略直接解析出了 Base64），或者没有 URL，跳过
            if (item.thumbnailBytes() != null || item.thumbnail() == null || item.thumbnail().isBlank()) {
                continue;
            }

            // 提交下载任务
            downloadTasks.add(CompletableFuture.runAsync(() -> {
                byte[] bytes = downloadBytes(item.thumbnail());
                if (bytes != null) {
                    SearchResultItemDTO newItem = new SearchResultItemDTO(
                            item.title(), item.url(), item.thumbnail(),
                            bytes, // 填充下载好的字节
                            item.source(), item.similarity(), item.description(), item.domain()
                    );
                    synchronized (items) {
                        items.set(index, newItem);
                    }
                }
            }, virtualThreadExecutor));
        }

        // 等待所有图片下载完成
        CompletableFuture.allOf(downloadTasks.toArray(new CompletableFuture[0])).join();
    }

    private byte[] downloadBytes(String url) {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return response.body().bytes();
            }
        } catch (IOException e) {
            // 缩略图下载失败不应中断流程，只打印 debug
            log.debug("缩略图下载失败: {}", url);
        }
        return null;
    }
}
