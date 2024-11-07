package com.sycamore792.wheels4j.wheels.cache.evict.lfu;

import com.sycamore792.wheels4j.wheels.cache.evict.IEvictionCache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 线程安全的 LFU 缓存实现
 * @author 桑运昌
 */
public class LeastFrequentlyUsedEvictCache<K, V>{

}