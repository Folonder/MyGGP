package org.ggp.base.player.gamer.statemachine.mcts.logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.diff.JsonDiff;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Асинхронный логгер для MCTS дерева с использованием Redis
 */
public class AsyncMCTSRedisLogger implements AutoCloseable {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 5003;

    private final JedisPool jedisPool;
    private final String treeId;
    private final int turnNumber;
    private final AtomicInteger iterationCounter = new AtomicInteger(0);
    private final int loggingFrequency;
    private volatile JsonNode previousTree = null;

    // Константы для Redis
    private static final String KEY_PREFIX = "mcts:";
    private static final String INITIAL_SUFFIX = ":initial";
    private static final String PATCH_SUFFIX = ":patch:";
    private static final String META_SUFFIX = ":meta";
    private static final int EXPIRATION_SECONDS = 3600 * 24; // 24 часа

    // Очередь для асинхронной обработки
    private final BlockingQueue<PatchEntry> batchQueue = new LinkedBlockingQueue<>(1000);
    private final ExecutorService batchProcessor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);

    // Вспомогательный класс для хранения патчей
    private static class PatchEntry {
        final int iteration;
        final JsonNode patch;

        PatchEntry(int iteration, JsonNode patch) {
            this.iteration = iteration;
            this.patch = patch;
        }
    }

    public AsyncMCTSRedisLogger(String matchId, int turnNumber, int loggingFrequency) {
        JedisPoolConfig poolConfig = createJedisPoolConfig();

        jedisPool = new JedisPool(poolConfig, REDIS_HOST, REDIS_PORT, 2000, "password");

        this.treeId = matchId + ":" + turnNumber + ":" + UUID.randomUUID().toString().substring(0, 8);
        this.turnNumber = turnNumber;
        this.loggingFrequency = loggingFrequency;

        // Сохраняем метаданные
        saveTreeMetadata(matchId);

        // Запускаем асинхронный процессор пакетов
        startBatchProcessor();
    }

    private JedisPoolConfig createJedisPoolConfig() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(128);
        poolConfig.setMinIdle(16);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setBlockWhenExhausted(true);
        return poolConfig;
    }

    private void saveTreeMetadata(String matchId) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "matchId", matchId);
            jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "turnNumber", String.valueOf(turnNumber));
            jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "createdAt", String.valueOf(System.currentTimeMillis()));
            jedis.expire(KEY_PREFIX + treeId + META_SUFFIX, EXPIRATION_SECONDS);
        } catch (Exception e) {
            System.err.println("Ошибка при сохранении метаданных: " + e.getMessage());
        }
    }

    private void startBatchProcessor() {
        batchProcessor.submit(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    PatchEntry entry = batchQueue.poll(100, TimeUnit.MILLISECONDS);
                    if (entry != null) {
                        processBatch();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Ошибка при обработке пакета: " + e.getMessage());
                }
            }
        });
    }

    public boolean logTreeState(JsonNode treeJson) {
        int iteration = iterationCounter.incrementAndGet();

        // Пропускаем логирование по частоте
        if (iteration % loggingFrequency != 0) {
            return false;
        }

        // Первое состояние - сохраняем полностью
        if (previousTree == null) {
            saveInitialState(treeJson);
            previousTree = treeJson;
            return true;
        }

        // Создаем и сохраняем патч
        try {
            JsonPatch patch = JsonDiff.asJsonPatch(previousTree, treeJson);
            JsonNode patchNode = mapper.valueToTree(patch);

            batchQueue.offer(new PatchEntry(iteration, patchNode), 50, TimeUnit.MILLISECONDS);
            previousTree = treeJson;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            System.err.println("Ошибка при создании патча: " + e.getMessage());
            return false;
        }

        return true;
    }

    private void saveInitialState(JsonNode treeJson) {
        try (Jedis jedis = jedisPool.getResource()) {
            String initialJson = treeJson.toString();
            jedis.set(KEY_PREFIX + treeId + INITIAL_SUFFIX, initialJson);
            jedis.expire(KEY_PREFIX + treeId + INITIAL_SUFFIX, EXPIRATION_SECONDS);
            jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "totalPatches", "0");
        } catch (Exception e) {
            System.err.println("Ошибка при сохранении начального состояния: " + e.getMessage());
        }
    }

    private void processBatch() {
        try (Jedis jedis = jedisPool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            int count = 0;
            int maxPatchesToProcess = 100; // Увеличиваем максимальное количество патчей за итерацию

            // Пытаемся обработать все патчи в очереди
            while (!batchQueue.isEmpty() && count < maxPatchesToProcess) {
                PatchEntry entry = batchQueue.poll();
                if (entry != null) {
                    String patchKey = KEY_PREFIX + treeId + PATCH_SUFFIX + entry.iteration;
                    pipeline.set(patchKey, entry.patch.toString());
                    pipeline.expire(patchKey, EXPIRATION_SECONDS);
                    count++;

                    // Добавляем отладочный вывод
                    System.out.println("Saving patch: " + patchKey);
                }
            }

            // Всегда обновляем счетчик патчей, даже если их 0
            pipeline.hincrBy(KEY_PREFIX + treeId + META_SUFFIX, "totalPatches", count);

            // Принудительная синхронизация
            pipeline.sync();

            // Отладочный вывод
            System.out.println("Processed " + count + " patches");
        } catch (Exception e) {
            System.err.println("Ошибка при пакетной обработке: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String getTreeId() {
        return treeId;
    }

    public int getIterationCount() {
        return iterationCounter.get();
    }

    @Override
    public void close() {
        // Обрабатываем оставшиеся элементы
        while (!batchQueue.isEmpty()) {
            processBatch();
        }

        // Завершаем пул потоков
        batchProcessor.shutdown();
        try {
            if (!batchProcessor.awaitTermination(5, TimeUnit.SECONDS)) {
                batchProcessor.shutdownNow();
            }
        } catch (InterruptedException e) {
            batchProcessor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Закрываем пул Jedis
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }

    public static Set<String> getAvailableTrees() {
        try (JedisPool pool = new JedisPool(REDIS_HOST, REDIS_PORT);
             Jedis jedis = pool.getResource()) {
            return jedis.keys(KEY_PREFIX + "*" + META_SUFFIX);
        }
    }
}