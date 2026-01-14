package com.github.winefoxbot.plugins.pixiv.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.github.winefoxbot.plugins.pixiv.model.entity.PixivBookmark;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
* @author FlanChan
* @description 针对表【pixiv_bookmark(存储特定用户 P站收藏作品的信息)】的数据库操作Service
* @createDate 2026-01-04 16:05:55
*/
public interface PixivBookmarkService extends IService<PixivBookmark> {

    /**
     * 同步 Pixiv 收藏夹中的作品。
     * 该方法会从 Pixiv 获取指定用户的收藏夹数据，并将其存储到数据库中。
     */
    @Transactional
    void syncBookmarks();

    /**
     * 定时同步 Pixiv 收藏夹中的作品。
     */
    @Scheduled(cron = "${pixiv.bookmark.tracker.full-cron}")
    void scheduleSyncBookmarks();

    /**
     * 同步最新的 Pixiv 收藏作品。
     */
    @Transactional(rollbackFor = Exception.class)
    void syncLatestBookmarks();

    /**
     * 定时同步最新的 Pixiv 收藏作品。
     */
    @Scheduled(cron = "${pixiv.bookmark.tracker.light-cron}")
    void scheduleSyncLatestBookmarks();

    List<Long> getAllBookmarkIds();

    /**
     * 根据用户和群组的配置，随机获取一个收藏作品。
     * @param userId 用户ID
     * @param groupId 群组ID
     * @return 随机作品的 Optional
     */
    Optional<PixivBookmark> getRandomBookmark(Long userId, Long groupId);

    /**
     * 添加单个作品到收藏夹
     * @param illustId 作品ID
     * @param restrict 0: 公开, 1: 私人(非公开)
     * @return 是否成功
     */
    boolean addBookmark(String illustId, Integer restrict);
    /**
     * 移除单个作品收藏
     * 会自动查询该作品的 bookmark_id 并进行移除
     * @param illustId 作品PID
     * @return 是否成功（如果本就未收藏，也会返回 true）
     */
    boolean removeBookmark(String illustId);
    /**
     * 爬取指定用户的全部作品并加入收藏
     * 该操作耗时较长，建议异步执行
     * @param targetUserId 目标画师ID
     * @return 提交的任务数量（作品总数）
     */
    int crawlUserArtworksToBookmark(String targetUserId);


    /**
     * 转移（克隆）指定用户的公开收藏夹
     * 获取目标用户的公开收藏，并将其加入到当前账号的收藏夹中。
     * @param sourceUserId 来源用户的ID
     * @return 预计处理的作品总数
     */
    int transferUserBookmarks(String sourceUserId);
}
