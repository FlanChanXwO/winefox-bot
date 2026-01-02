package com.github.winefoxbot.utils;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 抽奖工具类
 * <p>
 * 使用方法:
 * 1. 创建一个 Map<Double, List<Object>>，Key为概率，Value为奖品列表。
 *    - Key (Double): 代表抽取到对应Value列表的整体概率。所有Key的总和建议为1.0 (100%)。
 *                    如果总和不为1.0，工具类会自动进行归一化处理。
 *    - Value (List<Object>): 奖品列表。一旦抽中这个列表，其中每个物品被抽到的概率是均等的。
 * 2. 调用 LotteryUtils.draw(prizeMap) 方法进行抽奖。
 *
 * @author FlanChanXwO
 */
public final class LotteryUtils {

    /**
     * 私有构造函数，防止实例化
     */
    private LotteryUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static  class PrizeCategory {
        private final String categoryId;       // 类别唯一ID，如 "SSR", "SR"
        private final List<PrizeItem> items;   // 此类别下的奖品列表
        private final int pityThreshold;       // 保底阈值（触发保底的抽奖次数），0或负数表示无保底

        public PrizeCategory(String categoryId, List<PrizeItem> items, int pityThreshold) {
            if (categoryId == null || categoryId.trim().isEmpty()) {
                throw new IllegalArgumentException("Category ID cannot be null or empty.");
            }
            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("Prize items cannot be null or empty.");
            }
            this.categoryId = categoryId;
            this.items = items;
            this.pityThreshold = pityThreshold;
        }

        // Getters
        public String getCategoryId() { return categoryId; }
        public List<PrizeItem> getItems() { return items; }
        public int getPityThreshold() { return pityThreshold; }

        // 重写 equals 和 hashCode，确保能作为 Map 的 Key
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PrizeCategory that = (PrizeCategory) o;
            return categoryId.equals(that.categoryId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(categoryId);
        }
    }

    public static class PrizeItem {
        private final String id;      // 奖品唯一ID
        private final String name;    // 奖品名称
        private final Object data;    // 奖品附加数据

        public PrizeItem(String id, String name, Object data) {
            this.id = id;
            this.name = name;
            this.data = data;
        }

        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public Object getData() { return data; }

        @Override
        public String toString() {
            return "PrizeItem{" + "id='" + id + '\'' + ", name='" + name + '\'' + '}';
        }
    }


    /**
     * 带保底机制的抽奖方法
     *
     * @param prizePool  奖品池。Key是概率，Value是奖品类别对象。
     * @param pityCounters 用户的保底计数器。Key是奖品类别，Value是该类别已经连续未抽中的次数。
     *                   【注意】这个Map会被方法直接修改！
     * @return 抽中的奖品项。
     */
    public static PrizeItem drawWithPity(
            Map<Double, PrizeCategory> prizePool,
            Map<PrizeCategory, Integer> pityCounters) {

        if (prizePool == null || prizePool.isEmpty()) {
            throw new IllegalArgumentException("奖品池不能为空");
        }
        if (pityCounters == null) {
            throw new IllegalArgumentException("保底计数器不能为空");
        }

        // 1. 检查是否有任何类别触发了保底
        PrizeCategory pityTriggeredCategory = null;
        for (Map.Entry<PrizeCategory, Integer> counterEntry : pityCounters.entrySet()) {
            PrizeCategory category = counterEntry.getKey();
            int count = counterEntry.getValue();
            int threshold = category.getPityThreshold();

            // 如果保底阈值有效，并且当前计数已达到或超过阈值
            if (threshold > 0 && count >= threshold) {
                pityTriggeredCategory = category;
                break; // 找到第一个触发保底的就停止（可以根据业务规则调整优先级）
            }
        }

        PrizeCategory chosenCategory;

        if (pityTriggeredCategory != null) {
            // 2. 如果触发了保底，直接选定保底的奖品类别
            chosenCategory = pityTriggeredCategory;
            System.out.println("--- 触发保底！必出 " + chosenCategory.getCategoryId() + " 类别奖品 ---");
        } else {
            // 3. 如果未触发保底，进行常规概率抽奖
            chosenCategory = drawCategory(prizePool);
        }

        // 如果因某种原因没有抽中任何类别（例如，所有概率都为0），则返回null
        if (chosenCategory == null) {
            return null;
        }

        // 4. 更新所有类别的保底计数器
        updatePityCounters(prizePool.values(), pityCounters, chosenCategory);

        // 5. 从选定的奖品类别中随机抽取一个奖品
        List<PrizeItem> chosenItems = chosenCategory.getItems();
        int randomIndex = ThreadLocalRandom.current().nextInt(chosenItems.size());
        return chosenItems.get(randomIndex);
    }

    /**
     * 进行抽奖
     * @param prizeMap 奖品概率映射表。
     *                 Key: 抽中对应列表的概率 (e.g., 0.1 for 10%)
     *                 Value: 奖品列表。列表中的每个奖品在此次抽奖中概率均等。
     * @return 抽中的奖品对象。如果奖品池为空或所有奖品列表都为空，则返回 null。
     * @throws IllegalArgumentException 如果概率为负数或奖品Map为空。
     */
    public static Object draw(Map<Double, List<Object>> prizeMap) {
        if (prizeMap == null || prizeMap.isEmpty()) {
            throw new IllegalArgumentException("奖品池不能为空 (Prize map cannot be null or empty)");
        }

        // 1. 过滤掉空的奖品列表和无效的概率
        TreeMap<Double, List<Object>> filteredPrizeMap = new TreeMap<>();
        double totalProbability = 0;
        for (Map.Entry<Double, List<Object>> entry : prizeMap.entrySet()) {
            Double probability = entry.getKey();
            List<Object> prizes = entry.getValue();

            if (probability == null || probability < 0) {
                throw new IllegalArgumentException("概率不能为负数 (Probability cannot be negative): " + probability);
            }

            // 如果概率大于0且奖品列表不为空，则加入有效奖品池
            if (probability > 0 && prizes != null && !prizes.isEmpty()) {
                totalProbability += probability;
                filteredPrizeMap.put(totalProbability, prizes); // 使用累加概率作为Key
            }
        }

        // 如果没有有效的奖品，直接返回null
        if (filteredPrizeMap.isEmpty()) {
            System.err.println("警告: 奖品池中没有有效的奖品或概率。");
            return null;
        }

        // 2. 生成随机数并确定抽中的奖品列表
        // ThreadLocalRandom在多线程环境下性能更好
        double randomValue = ThreadLocalRandom.current().nextDouble() * totalProbability;

        // 使用TreeMap的ceilingEntry方法可以高效地找到第一个大于等于randomValue的key
        Map.Entry<Double, List<Object>> chosenEntry = filteredPrizeMap.ceilingEntry(randomValue);

        // 如果因为浮点数精度问题没找到（理论上很少发生），可以默认给第一个
        if (chosenEntry == null) {
            chosenEntry = filteredPrizeMap.firstEntry();
        }

        List<Object> chosenPrizeList = chosenEntry.getValue();

        // 3. 从选中的奖品列表中随机抽取一个奖品
        // 由于列表内每个物品概率相等，直接随机取一个索引即可
        int randomIndex = ThreadLocalRandom.current().nextInt(chosenPrizeList.size());
        return chosenPrizeList.get(randomIndex);
    }

    /**
     * 内部方法：根据概率抽取一个奖品类别
     */
    private static PrizeCategory drawCategory(Map<Double, PrizeCategory> prizePool) {
        TreeMap<Double, PrizeCategory> cumulativeMap = new TreeMap<>();
        double totalProbability = 0;

        for (Map.Entry<Double, PrizeCategory> entry : prizePool.entrySet()) {
            double probability = entry.getKey();
            if (probability > 0) {
                totalProbability += probability;
                cumulativeMap.put(totalProbability, entry.getValue());
            }
        }

        if (totalProbability == 0) return null;

        double randomValue = ThreadLocalRandom.current().nextDouble() * totalProbability;
        Map.Entry<Double, PrizeCategory> chosenEntry = cumulativeMap.ceilingEntry(randomValue);

        return chosenEntry != null ? chosenEntry.getValue() : cumulativeMap.firstEntry().getValue();
    }

    /**
     * 内部方法：更新保底计数器
     *
     * @param allCategories    所有参与抽奖的类别
     * @param pityCounters     当前的计数器
     * @param chosenCategory   本次抽中的类别
     */
    private static void updatePityCounters(
            Collection<PrizeCategory> allCategories,
            Map<PrizeCategory, Integer> pityCounters,
            PrizeCategory chosenCategory) {

        for (PrizeCategory category : allCategories) {
            if (category.equals(chosenCategory)) {
                // 抽中了这个类别，计数器清零
                pityCounters.put(category, 0);
            } else {
                // 未抽中这个类别，计数器加1
                pityCounters.put(category, pityCounters.getOrDefault(category, 0) + 1);
            }
        }
    }


    /**
     * 主函数，用于演示和测试
     */
    public static void main(String[] args) {
        // --- 定义奖品 ---
        PrizeItem ssrPrize1 = new PrizeItem("ssr001", "限定角色A", null);
        PrizeItem srPrize1 = new PrizeItem("sr001", "SR角色X", null);
        PrizeItem srPrize2 = new PrizeItem("sr002", "SR角色Y", null);
        PrizeItem rPrize = new PrizeItem("r001", "普通物品", null);

        // --- 定义奖品类别（带保底规则）---
        PrizeCategory ssrCategory = new PrizeCategory("SSR", Collections.singletonList(ssrPrize1), 90); // 90抽必出
        PrizeCategory srCategory = new PrizeCategory("SR", Arrays.asList(srPrize1, srPrize2), 10);      // 10抽必出
        PrizeCategory rCategory = new PrizeCategory("R", Collections.singletonList(rPrize), 0);         // 无保底

        // --- 构建奖品池（概率 -> 类别） ---
        Map<Double, PrizeCategory> prizePool = new LinkedHashMap<>(); // 使用LinkedHashMap保持插入顺序
        prizePool.put(0.02, ssrCategory); // SSR 概率 2%
        prizePool.put(0.18, srCategory);  // SR 概率 18%
        prizePool.put(0.80, rCategory);   // R 概率 80%

        // --- 用户的保底计数器 ---
        // 在实际应用中，这个Map需要从数据库或用户会话中加载
        Map<PrizeCategory, Integer> userPityCounters = new HashMap<>();
        // 初始化计数器（如果用户是第一次抽）
        userPityCounters.put(ssrCategory, 0);
        userPityCounters.put(srCategory, 0);
        userPityCounters.put(rCategory, 0);

        // --- 模拟连续抽奖 ---
        System.out.println("开始模拟抽奖，SSR保底90抽，SR保底10抽...");
        int totalDraws = 100;
        for (int i = 1; i <= totalDraws; i++) {
            System.out.print("第 " + i + " 抽: ");

            // 调用带保底的抽奖方法
            PrizeItem result = drawWithPity(prizePool, userPityCounters);

            System.out.print("抽中了 -> " + result.getName() + " (" + result.getId().toUpperCase().substring(0,2) + ") ");
            System.out.println(String.format("| 当前计数: [SSR: %d/%d, SR: %d/%d]",
                    userPityCounters.get(ssrCategory), ssrCategory.getPityThreshold(),
                    userPityCounters.get(srCategory), srCategory.getPityThreshold()
            ));

            // 在实际应用中，这里需要将 userPityCounters 保存回数据库
        }
    }
}
