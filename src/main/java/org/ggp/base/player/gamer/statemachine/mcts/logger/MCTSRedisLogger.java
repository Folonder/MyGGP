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
 * ������ ��� ����������� ������ MCTS � Redis
 */
public class MCTSRedisLogger implements AutoCloseable {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String REDIS_HOST = "localhost";  // ����� ������� � ������������
    private static final int REDIS_PORT = 5003;            // ����� ������� � ������������

    private final JedisPool jedisPool;
    private final String treeId;
    private final int turnNumber;
    private final AtomicInteger iterationCounter = new AtomicInteger(0);
    private final int loggingFrequency;
    private JsonNode previousTree = null;

    // ��������� ��� ������ Redis
    private static final String KEY_PREFIX = "mcts:";
    private static final String INITIAL_SUFFIX = ":initial";
    private static final String PATCH_SUFFIX = ":patch:";
    private static final String META_SUFFIX = ":meta";
    private static final int EXPIRATION_SECONDS = 3600 * 24; // 24 ����

    // ��� �������� ������
    private final Queue<PatchEntry> batchQueue = new ConcurrentLinkedQueue<>();
    private final int batchSize = 10; // ������ ������ ��� ������
    private final AtomicBoolean processingBatch = new AtomicBoolean(false);

    /**
     * ����� ��� �������� ���������� � �����
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
     * ������� ����� ��������� �������
     * @param matchId ������������� �����
     * @param turnNumber ����� ����
     * @param loggingFrequency ������� �����������
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

        // ���������� ���������� ID ��� ������
        this.treeId = matchId + ":" + turnNumber + ":" + UUID.randomUUID().toString().substring(0, 8);
        this.turnNumber = turnNumber;
        this.loggingFrequency = loggingFrequency;

        // ��������� ����������
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "matchId", matchId);
            jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "turnNumber", String.valueOf(turnNumber));
            jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "createdAt", String.valueOf(System.currentTimeMillis()));
            jedis.expire(KEY_PREFIX + treeId + META_SUFFIX, EXPIRATION_SECONDS);
        }
    }

    /**
     * �������� ��������� ������ MCTS
     * @param treeJson ������� ��������� ������
     * @return true ���� ��� ��� ��������, false ���� ��� �������� ��-�� �������
     */
    public boolean logTreeState(JsonNode treeJson) {
        int iteration = iterationCounter.incrementAndGet();

        // ���������� �����������, ���� �� ������������� �������
        if (iteration % loggingFrequency != 0) {
            return false;
        }

        // ������ ��������� ���������� �����
        if (previousTree == null) {
            try (Jedis jedis = jedisPool.getResource()) {
                String initialJson = treeJson.toString();
                jedis.set(KEY_PREFIX + treeId + INITIAL_SUFFIX, initialJson);
                jedis.expire(KEY_PREFIX + treeId + INITIAL_SUFFIX, EXPIRATION_SECONDS);
                jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "totalPatches", "0");
            } catch (Exception e) {
                System.err.println("������ ��� ������ ���������� ���������: " + e.getMessage());
                e.printStackTrace();
                return false;
            }

            previousTree = treeJson;
            return true;
        }

        // ��������� � ������� �������� ���������
        JsonPatch patch = JsonDiff.asJsonPatch(previousTree, treeJson);
// ���������� ������������� JsonPatch � JsonNode
        JsonNode patchNode = mapper.valueToTree(patch);
        batchQueue.add(new PatchEntry(iteration, patchNode));
        previousTree = treeJson;

        // ���� ������� �������� ������� ������, ��������� ���������
        if (batchQueue.size() >= batchSize && !processingBatch.getAndSet(true)) {
            CompletableFuture.runAsync(() -> {
                processBatch();
                processingBatch.set(false);
            });
        }

        return true;
    }

    /**
     * ��������� ������ ������ ��� Redis
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

            // �������������� ������� ������
            pipeline.hincrBy(KEY_PREFIX + treeId + META_SUFFIX, "totalPatches", count);

            pipeline.sync();
        } catch (Exception e) {
            System.err.println("������ ��� �������� ���������: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ���������� ID ������ ��� ������� � ������
     */
    public String getTreeId() {
        return treeId;
    }

    /**
     * ���������� ���������� ��������
     */
    public int getIterationCount() {
        return iterationCounter.get();
    }

    /**
     * ��������� ���������� � Redis � ���������� ���������� ������
     */
    @Override
    public void close() {
        // ������������ ���������� �������� � �������
        if (!batchQueue.isEmpty()) {
            processBatch();
        }

        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }

    /**
     * �������� ������ ���� ��������� �������� �� Redis
     */
    public static Set<String> getAvailableTrees() {
        try (JedisPool pool = new JedisPool(REDIS_HOST, REDIS_PORT);
             Jedis jedis = pool.getResource()) {
            return jedis.keys(KEY_PREFIX + "*" + META_SUFFIX);
        }
    }
}