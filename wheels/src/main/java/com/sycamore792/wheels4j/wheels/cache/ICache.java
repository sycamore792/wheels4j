package com.sycamore792.wheels4j.wheels.cache;

/**
 * @author: Sycamore
 * @date: 2024/11/7 1:53
 * @description: A generic cache interface that defines core operations for a cache implementation.
 * This interface provides the basic contract for storing and retrieving objects in a cache,
 * serving as the foundation for various cache implementations (e.g., LRU, LFU).
 * The cache stores key-value pairs.
 * <p>
 * 通用缓存接口，定义了缓存实现的核心操作。
 * 该接口提供了在缓存中存储和获取对象的基本约定，
 * 作为各种缓存实现（如LRU、LFU等）的基础接口。
 * 缓存以键值对形式存储数据。
 */
public interface ICache<K, V> {
    V get(K key);

    void put(K key, V value);

    void remove(K key);

    int size();

    void clear();
}
