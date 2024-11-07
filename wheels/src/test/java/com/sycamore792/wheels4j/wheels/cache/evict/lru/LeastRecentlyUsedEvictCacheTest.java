package com.sycamore792.wheels4j.wheels.cache.evict.lru;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class LeastRecentlyUsedEvictCacheTest {
    
    private LeastRecentlyUsedEvictCache<String, String> cache;
    private static final int DEFAULT_CAPACITY = 3;

    @BeforeEach
    void setUp() {
        cache = new LeastRecentlyUsedEvictCache<>(DEFAULT_CAPACITY);
    }

    @Test
    @DisplayName("构造函数 - 容量参数非法时应抛出IllegalArgumentException异常")
    void testConstructorWithInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new LeastRecentlyUsedEvictCache<>(0));
        assertThrows(IllegalArgumentException.class, () -> new LeastRecentlyUsedEvictCache<>(-1));
    }

    @Test
    @DisplayName("基础功能 - 验证缓存的存取操作")
    void testPutAndGet() {
        cache.put("key1", "value1");
        assertEquals("value1", cache.get("key1"));
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("空值校验 - put方法不应接受null键或值")
    void testPutNull() {
        assertThrows(NullPointerException.class, () -> cache.put(null, "value"));
        assertThrows(NullPointerException.class, () -> cache.put("key", null));
    }

    @Test
    @DisplayName("空值校验 - get方法不应接受null键")
    void testGetNull() {
        assertThrows(NullPointerException.class, () -> cache.get(null));
    }

    @Test
    @DisplayName("淘汰机制 - 超出容量时应淘汰最久未使用的元素")
    void testEviction() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");
        assertEquals(3, cache.size());

        cache.put("key4", "value4");
        assertEquals(3, cache.size());
        assertNull(cache.get("key1"));
        assertEquals("value4", cache.get("key4"));
    }

    @Test
    @DisplayName("LRU顺序 - 访问元素后应更新其使用时间")
    void testLRUOrder() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");

        cache.get("key1");
        cache.put("key4", "value4");

        assertNotNull(cache.get("key1"));
        assertNull(cache.get("key2"));
        assertNotNull(cache.get("key3"));
        assertNotNull(cache.get("key4"));
    }

    @Test
    @DisplayName("命中率 - 验证缓存的命中率统计功能")
    void testHitRate() {
        cache.put("key1", "value1");

        cache.get("key1");
        cache.get("key1");
        cache.get("nonexistent");

        assertEquals(2, cache.getHitCount());
        assertEquals(1, cache.getMissCount());
        assertEquals(2.0/3.0, cache.getHitRate(), 0.0001);
    }

    @Test
    @DisplayName("清空功能 - 验证清空缓存操作")
    void testClear() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        cache.clear();
        assertEquals(0, cache.size());
        assertNull(cache.get("key1"));
        assertNull(cache.get("key2"));
    }

    @Test
    @DisplayName("删除功能 - 验证删除指定键值对")
    void testRemove() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");

        cache.remove("key1");
        assertEquals(1, cache.size());
        assertNull(cache.get("key1"));
        assertEquals("value2", cache.get("key2"));
    }

    @Test
    @DisplayName("更新操作 - 验证更新已存在键的值")
    void testUpdateExistingKey() {
        cache.put("key1", "value1");
        cache.put("key1", "updatedValue");

        assertEquals("updatedValue", cache.get("key1"));
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("并发场景 - 验证多线程并发访问的正确性")
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testConcurrentAccess() throws InterruptedException {
        int threadCount = 100;
        int operationsPerThread = 1000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "key" + ((threadId * operationsPerThread + j) % 100);
                        String value = "value" + j;

                        switch (j % 4) {
                            case 0:
                                cache.put(key, value);
                                break;
                            case 1:
                                cache.get(key);
                                break;
                            case 2:
                                cache.remove(key);
                                break;
                            case 3:
                                cache.put(key, value + "_updated");
                                break;
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        assertTrue(cache.size() <= DEFAULT_CAPACITY);
    }

    @Test
    @DisplayName("压力测试 - 验证大量操作下的缓存表现")
    void testStressTest() {
        int iterations = 10000;
        IntStream.range(0, iterations).forEach(i -> {
            String key = "key" + (i % 100);
            String value = "value" + i;
            cache.put(key, value);
            if (i % 2 == 0) {
                cache.get(key);
            }
        });

        assertTrue(cache.size() <= DEFAULT_CAPACITY);
        assertTrue(cache.getHitRate() >= 0 && cache.getHitRate() <= 1.0);
    }

    @Test
    @DisplayName("边界场景 - 验证容量为1的缓存行为")
    void testSingleCapacityCache() {
        LeastRecentlyUsedEvictCache<String, String> singleCache = new LeastRecentlyUsedEvictCache<>(1);
        singleCache.put("key1", "value1");
        assertEquals("value1", singleCache.get("key1"));

        singleCache.put("key2", "value2");
        assertNull(singleCache.get("key1"));
        assertEquals("value2", singleCache.get("key2"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 100, 1000})
    @DisplayName("参数化测试 - 验证不同容量下的缓存行为")
    void testDifferentCapacities(int capacity) {
        LeastRecentlyUsedEvictCache<String, String> varCache = new LeastRecentlyUsedEvictCache<>(capacity);

        for (int i = 0; i < capacity; i++) {
            varCache.put("key" + i, "value" + i);
        }
        assertEquals(capacity, varCache.size());

        varCache.put("newKey", "newValue");
        assertEquals(capacity, varCache.size());
        assertNull(varCache.get("key0"));
    }

    @Test
    @DisplayName("重复更新 - 验证大量重复键的更新操作")
    void testMassiveKeyUpdates() {
        String singleKey = "testKey";
        for (int i = 0; i < 10000; i++) {
            cache.put(singleKey, "value" + i);
            assertEquals("value" + i, cache.get(singleKey));
        }
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("并发读写 - 验证同一键的并发读写操作")
    void testConcurrentAccessSameKey() throws InterruptedException {
        int threadCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        String testKey = "testKey";
        cache.put(testKey, "initialValue");

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            Thread thread = new Thread(() -> {
                try {
                    startLatch.await();
                    if (index % 2 == 0) {
                        String value = cache.get(testKey);
                        assertNotNull(value);
                    } else {
                        cache.put(testKey, "value" + index);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
            threads.add(thread);
            thread.start();
        }

        startLatch.countDown();
        assertTrue(endLatch.await(5, TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("并发LRU - 验证高并发下LRU淘汰顺序的正确性")
    void testConcurrentLRUOrdering() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < DEFAULT_CAPACITY; i++) {
            cache.put("key" + i, "value" + i);
        }

        String keyToKeep = "key0";
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 1000; j++) {
                        cache.get(keyToKeep);
                        cache.put("newKey" + j, "newValue");
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean await = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();
        boolean b = executor.awaitTermination(5, TimeUnit.SECONDS);

        assertNotNull(cache.get(keyToKeep));
    }

    @Test
    @Tag("performance")
    @DisplayName("性能测试 - 读操作性能")
    void testReadPerformance() {
        int operationCount = 1_000_000;
        String testKey = "testKey";
        String testValue = "testValue";
        cache.put(testKey, testValue);

        long startTime = System.nanoTime();
        for (int i = 0; i < operationCount; i++) {
            cache.get(testKey);
        }
        long endTime = System.nanoTime();

        long avgTimeNanos = (endTime - startTime) / operationCount;
        assertTrue(avgTimeNanos < 1000,
                "平均读操作耗时 " + avgTimeNanos + " ns, 期望小于1000 ns");
    }

    @Test
    @Tag("performance")
    @DisplayName("性能测试 - 写操作性能")
    void testWritePerformance() {
        int operationCount = 100_000;

        long startTime = System.nanoTime();
        for (int i = 0; i < operationCount; i++) {
            cache.put("key" + (i % 100), "value" + i);
        }
        long endTime = System.nanoTime();

        long avgTimeNanos = (endTime - startTime) / operationCount;
        assertTrue(avgTimeNanos < 10000,
                "平均写操作耗时 " + avgTimeNanos + " ns, 期望小于10000 ns");
    }

    @Test
    @Tag("performance")
    @DisplayName("性能测试 - 混合负载下的性能表现")
    void testMixedLoadPerformance() throws InterruptedException {
        int threadCount = Runtime.getRuntime().availableProcessors();
        int operationsPerThread = 100_000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long startTime = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int j = 0; j < operationsPerThread; j++) {
                        int op = random.nextInt(100);
                        String key = "key" + (j % 100);

                        if (op < 80) {
                            cache.get(key);
                        } else if (op < 95) {
                            cache.put(key, "value" + j);
                        } else {
                            cache.remove(key);
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        long endTime = System.nanoTime();

        long totalOperations = (long) threadCount * operationsPerThread;
        long avgTimeNanos = (endTime - startTime) / totalOperations;

        assertTrue(avgTimeNanos < 5000,
                "混合负载下平均操作耗时 " + avgTimeNanos + " ns, 期望小于5000 ns");
    }

    @Test
    @DisplayName("内存压力测试 - 验证内存压力下缓存的表现")
    void testMemoryPressure() {
        int largeSize = 1000;
        LeastRecentlyUsedEvictCache<String, byte[]> largeCache =
                new LeastRecentlyUsedEvictCache<>(largeSize);

        // 创建一个较大的值
        byte[] largeValue = new byte[1024 * 1024]; // 1MB
        Arrays.fill(largeValue, (byte) 1);

        // 填充缓存直到接近堆内存限制
        try {
            for (int i = 0; i < largeSize; i++) {
                largeCache.put("key" + i, Arrays.copyOf(largeValue, largeValue.length));
            }
        } catch (OutOfMemoryError e) {
            fail("Cache should handle memory pressure gracefully");
        }

        assertTrue(largeCache.size() <= largeSize);
    }
}