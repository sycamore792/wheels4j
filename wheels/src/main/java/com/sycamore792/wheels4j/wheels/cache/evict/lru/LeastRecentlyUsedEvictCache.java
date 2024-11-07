package com.sycamore792.wheels4j.wheels.cache.evict.lru;

import com.sycamore792.wheels4j.wheels.cache.evict.IEvictionCache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 线程安全的 LRU 缓存实现
 * 使用双向链表 + HashMap 实现 O(1) 的访问和更新
 * 通过分离锁实现细粒度的并发控制
 * @author 桑运昌
 */
public class LeastRecentlyUsedEvictCache<K, V> implements IEvictionCache<K, V> {

    private static class Node<K, V> {
        final K key;
        volatile V value;
        volatile Node<K, V> prev;
        volatile Node<K, V> next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    private static class DoubleLinkedList<K, V> {
        private final Node<K, V> head;
        private final Node<K, V> tail;
        private final Lock listLock = new ReentrantLock();

        DoubleLinkedList() {
            head = new Node<>(null, null);
            tail = new Node<>(null, null);
            head.next = tail;
            tail.prev = head;
        }

        void moveToFirst(Node<K, V> node) {
            listLock.lock();
            try {
                // 先从当前位置移除
                removeNode(node);
                // 再添加到头部
                addFirst(node);
            } finally {
                listLock.unlock();
            }
        }

        void addFirst(Node<K, V> node) {
            listLock.lock();
            try {
                node.next = head.next;
                node.prev = head;
                head.next.prev = node;
                head.next = node;
            } finally {
                listLock.unlock();
            }
        }

        void removeNode(Node<K, V> node) {
            listLock.lock();
            try {
                if (node != null && node.prev != null) {
                    node.prev.next = node.next;
                    node.next.prev = node.prev;
                }
            } finally {
                listLock.unlock();
            }
        }

        Node<K, V> removeLast() {
            listLock.lock();
            try {
                Node<K, V> last = tail.prev;
                if (last != head) {
                    removeNode(last);
                    return last;
                }
                return null;
            } finally {
                listLock.unlock();
            }
        }

        void clear() {
            listLock.lock();
            try {
                head.next = tail;
                tail.prev = head;
            } finally {
                listLock.unlock();
            }
        }
    }

    private final int capacity;
    private final Map<K, Node<K, V>> cache;
    private final DoubleLinkedList<K, V> linkedList;
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);



    private final ReentrantReadWriteLock.ReadLock readLock = new ReentrantReadWriteLock().readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = new ReentrantReadWriteLock().writeLock();


    public LeastRecentlyUsedEvictCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Cache capacity must be positive");
        }
        this.capacity = capacity;
        this.cache = new HashMap<>(capacity);
        this.linkedList = new DoubleLinkedList<>();
    }

    @Override
    public void evict() {

        Node<K, V> lastNode = linkedList.removeLast();
        if (lastNode != null && lastNode.key != null) {
            cache.remove(lastNode.key);
        }

    }

    @Override
    public void onAccess(K key) {
        Node<K, V> node = cache.get(key);
        if (node != null) {
            linkedList.moveToFirst(node);
        }
    }

    @Override
    public void onUpdate(K key) {
        onAccess(key);
    }

    @Override
    public boolean shouldEvict() {
        return size() >= capacity;
    }

    @Override
    public V get(K key) {
        if (key == null) {
            throw new NullPointerException("Null key is not allowed");
        }

        readLock.lock();
        try {
            Node<K, V> node = cache.get(key);
            if (node == null) {
                missCount.incrementAndGet();
                return null;
            }
        } finally {
            readLock.unlock();
        }

        // 申请写锁
        writeLock.lock();
        try {
            // double check
            Node<K, V> node = cache.get(key);
            if (node == null) {
                missCount.incrementAndGet();
                return null;
            }
            // 命中
            hitCount.incrementAndGet();
            onAccess(key);
            return node.value;
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void put(K key, V value) {
        if (value == null || key == null) {
            throw new NullPointerException("Null key or value is not allowed");
        }
        writeLock.lock();
        try {
            Node<K, V> node = cache.get(key);
            if (node != null) {
                // 更新已存在节点的值
                node.value = value;
                onUpdate(key);
                return;
            }
            // 检查是否需要驱逐
            if (shouldEvict()) {
                evict();
            }
            // 创建新节点并插入
            node = new Node<>(key, value);
            cache.put(key, node);
            linkedList.addFirst(node);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public void remove(K key) {
        writeLock.lock();
        try {
            Node<K, V> node = cache.remove(key);
            if (node != null) {
                linkedList.removeNode(node);
            }
        }finally {
            writeLock.unlock();
        }
    }

    @Override
    public int size() {
        return cache.size();
    }

    @Override
    public void clear() {
        writeLock.lock();
        try {
            cache.clear();
            linkedList.clear();
        } finally {
            writeLock.unlock();
        }
    }

    public double getHitRate() {
        long total = hitCount.get() + missCount.get();
        return total == 0 ? 0 : hitCount.get() / (double) total;

    }

    public long getHitCount() {
        return hitCount.get();
    }

    public long getMissCount() {
        return missCount.get();
    }
}