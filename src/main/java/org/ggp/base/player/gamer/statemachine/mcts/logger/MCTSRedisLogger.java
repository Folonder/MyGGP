package org.ggp.base.player.gamer.statemachine.mcts.logger;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.diff.JsonDiff;

import java.util.Queue;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сервис для логирования дерева MCTS в Redis
 */
public class MCTSRedisLogger implements AutoCloseable {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String REDIS_HOST = "localhost";  // Можно вынести в конфигурацию
    private static final int REDIS_PORT = 5003;            // Можно вынести в конфигурацию

    private final JedisPool jedisPool;
    private final String treeId;
    private final int turnNumber;
    private final AtomicInteger iterationCounter = new AtomicInteger(0);
    private final int loggingFrequency;
    private JsonNode previousTree = null;

    // Константы для ключей Redis
    private static final String KEY_PREFIX = "mcts:";
    private static final String INITIAL_SUFFIX = ":initial";
    private static final String PATCH_SUFFIX = ":patch:";
    private static final String META_SUFFIX = ":meta";
    private static final int EXPIRATION_SECONDS = 3600 * 24; // 24 часа

    // Для пакетной записи
    private final Queue<PatchEntry> batchQueue = new ConcurrentLinkedQueue<>();
    private final int batchSize = 10; // Размер пакета для записи
    private final AtomicBoolean processingBatch = new AtomicBoolean(false);

    /**
     * Класс для хранения информации о патче
     */
    private static class PatchEntry {
        final int iteration;
        final JsonNode patch;

        PatchEntry(int iteration, JsonNode patch) {
            this.iteration = iteration;
            this.patch = patch;
        }
    }

    /**
     * Создает новый экземпляр логгера
     * @param matchId идентификатор матча
     * @param turnNumber номер хода
     * @param loggingFrequency частота логирования
     */
    public MCTSRedisLogger(String matchId, int turnNumber, int loggingFrequency) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(128);
        poolConfig.setMaxIdle(128);
        poolConfig.setMinIdle(16);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setNumTestsPerEvictionRun(3);
        poolConfig.setBlockWhenExhausted(true);

        jedisPool = new JedisPool(poolConfig, REDIS_HOST, REDIS_PORT);

        // Генерируем уникальный ID для дерева
        this.treeId = matchId + ":" + turnNumber + ":" + UUID.randomUUID().toString().substring(0, 8);
        this.turnNumber = turnNumber;
        this.loggingFrequency = loggingFrequency;

        // Сохраняем метаданные
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "matchId", matchId);
            jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "turnNumber", String.valueOf(turnNumber));
            jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "createdAt", String.valueOf(System.currentTimeMillis()));
            jedis.expire(KEY_PREFIX + treeId + META_SUFFIX, EXPIRATION_SECONDS);
        }
    }

    /**
     * Логирует состояние дерева MCTS
     * @param treeJson текущее состояние дерева
     * @return true если лог был сохранен, false если был пропущен из-за частоты
     */
    public boolean logTreeState(JsonNode treeJson) {
        int iteration = iterationCounter.incrementAndGet();

        // Пропускаем логирование, если не соответствует частоте
        if (iteration % loggingFrequency != 0) {
            return false;
        }

        // Первое состояние записываем сразу
        if (previousTree == null) {
            try (Jedis jedis = jedisPool.getResource()) {
                String initialJson = treeJson.toString();
                jedis.set(KEY_PREFIX + treeId + INITIAL_SUFFIX, initialJson);
                jedis.expire(KEY_PREFIX + treeId + INITIAL_SUFFIX, EXPIRATION_SECONDS);
                jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "totalPatches", "0");
            } catch (Exception e) {
                System.err.println("Ошибка при записи начального состояния: " + e.getMessage());
                e.printStackTrace();
                return false;
            }

            previousTree = treeJson;
            return true;
        }

        // Добавляем в очередь пакетной обработки
        JsonPatch patch = JsonDiff.asJsonPatch(previousTree, treeJson);
// Необходимо преобразовать JsonPatch в JsonNode
        JsonNode patchNode = mapper.valueToTree(patch);
        batchQueue.add(new PatchEntry(iteration, patchNode));
        previousTree = treeJson;

        // Если очередь достигла размера пакета, запускаем обработку
        if (batchQueue.size() >= batchSize && !processingBatch.getAndSet(true)) {
            CompletableFuture.runAsync(() -> {
                processBatch();
                processingBatch.set(false);
            });
        }

        return true;
    }

    /**
     * Обработка пакета патчей для Redis
     */
    private void processBatch() {
        if (batchQueue.isEmpty()) return;

        try (Jedis jedis = jedisPool.getResource()) {
            Pipeline pipeline = jedis.pipelined();
            int count = 0;

            PatchEntry entry;
            while ((entry = batchQueue.poll()) != null && count < batchSize) {
                String patchKey = KEY_PREFIX + treeId + PATCH_SUFFIX + entry.iteration;
                pipeline.set(patchKey, entry.patch.toString());
                pipeline.expire(patchKey, EXPIRATION_SECONDS);
                count++;
            }

            // Инкрементируем счетчик патчей
            pipeline.hincrBy(KEY_PREFIX + treeId + META_SUFFIX, "totalPatches", count);

            pipeline.sync();
        } catch (Exception e) {
            System.err.println("Ошибка при пакетной обработке: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Возвращает ID дерева для доступа к данным
     */
    public String getTreeId() {
        return treeId;
    }

    /**
     * Возвращает количество итераций
     */
    public int getIterationCount() {
        return iterationCounter.get();
    }

    /**
     * Закрывает соединение с Redis и сбрасывает оставшиеся данные
     */
    @Override
    public void close() {
        // Обрабатываем оставшиеся элементы в очереди
        if (!batchQueue.isEmpty()) {
            processBatch();
        }

        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }

    /**
     * Получает список всех доступных деревьев из Redis
     */
    public static Set<String> getAvailableTrees() {
        try (JedisPool pool = new JedisPool(REDIS_HOST, REDIS_PORT);
             Jedis jedis = pool.getResource()) {
            return jedis.keys(KEY_PREFIX + "*" + META_SUFFIX);
        }
    }
}