package com.sycamore792.wheels4j.wheels.cache.evict;

import com.sycamore792.wheels4j.wheels.cache.ICache;

/**
 * @author: Sycamore
 * @date: 2024/11/7 2:03
 * @description: 淘汰缓存接口
 */
public interface IEvictionCache<K, V>  extends ICache<K, V> {
    // 触发淘汰
    void evict();
    // 数据访问通知
    void onAccess(K key);
    // 数据更新通知
    void onUpdate(K key);
    // 是否需要淘汰
    boolean shouldEvict();
}
