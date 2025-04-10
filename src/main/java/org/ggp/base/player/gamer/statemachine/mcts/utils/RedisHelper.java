package org.ggp.base.player.gamer.statemachine.mcts.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTree;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Map;
import java.util.Set;

/**
 * Helper class for Redis operations
 */
public class RedisHelper {

    // Type constants for keys
    public static final String TYPE_INIT = "init";
    public static final String TYPE_GROWTH = "growth";
    public static final String TYPE_FINAL = "final";
    public static final String TYPE_GAMEOVER = "gameover";

    // Redis connection details
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 5003;
    private static final String REDIS_PASSWORD = "password";
    private static final String REDIS_PREFIX = "mcts:";

    // Redis connection pool
    private static JedisPool jedisPool;

    // Gson instance for JSON serialization
    private static final Gson gson = TreeSerializer.createGson();

    /**
     * Get the Redis connection pool
     */
    private static JedisPool getJedisPool() {
        if (jedisPool == null) {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(10);
            poolConfig.setMaxIdle(5);
            poolConfig.setMinIdle(1);
            jedisPool = new JedisPool(poolConfig, REDIS_HOST, REDIS_PORT, 2000, REDIS_PASSWORD);
        }
        return jedisPool;
    }

    /**
     * Test Redis connection
     */
    public static boolean testConnection() {
        try (Jedis jedis = getJedisPool().getResource()) {
            String response = jedis.ping();
            System.out.println("Redis connection test: " + response);
            return "PONG".equalsIgnoreCase(response);
        } catch (Exception e) {
            System.err.println("Redis connection error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * List Redis keys matching a pattern
     */
    public static void listRedisKeys(String pattern) {
        try (Jedis jedis = getJedisPool().getResource()) {
            Set<String> keys = jedis.keys(pattern);
            System.out.println("Found " + keys.size() + " keys matching pattern: " + pattern);
            for (String key : keys) {
                System.out.println("  " + key);
            }
        } catch (Exception e) {
            System.err.println("Error listing Redis keys: " + e.getMessage());
        }
    }

    /**
     * Save tree data to Redis
     */
    public static boolean saveTreeData(String sessionId, int turnNumber, String type, SearchTree tree) {
        String turnId = String.format("%03d", turnNumber);
        String key = REDIS_PREFIX + sessionId + ":" + turnId + ":" + type;

        try (Jedis jedis = getJedisPool().getResource()) {
            // Create a wrapper object with "root" as the root node
            JsonObject wrapper = new JsonObject();
            wrapper.add("root", gson.toJsonTree(tree.getRoot()));

            // Convert to JSON and save
            String json = gson.toJson(wrapper);
            jedis.set(key, json);

            System.out.println("Saved tree to Redis: " + key);
            return true;
        } catch (Exception e) {
            System.err.println("Error saving tree to Redis: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Save iteration data to Redis
     */
    public static boolean saveIterationData(String sessionId, int turnNumber, int iterationNumber, String jsonData) {
        String turnId = String.format("%03d", turnNumber);
        String iterationId = String.format("%05d", iterationNumber);
        String key = REDIS_PREFIX + sessionId + ":" + turnId + ":iteration:" + iterationId;

        try (Jedis jedis = getJedisPool().getResource()) {
            jedis.set(key, jsonData);
            System.out.println("Saved iteration data to Redis: " + key);
            return true;
        } catch (Exception e) {
            System.err.println("Error saving iteration data to Redis: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Save growth tree with stages data
     */
    public static boolean saveGrowthWithStages(
            String sessionId,
            int turnNumber,
            String growthId,
            SearchTree tree,
            Map<String, String> stagesData) {

        String turnId = String.format("%03d", turnNumber);
        String baseKey = REDIS_PREFIX + sessionId + ":" + turnId + ":growth_" + growthId;

        try (Jedis jedis = getJedisPool().getResource()) {
            // Save the tree
            JsonObject wrapper = new JsonObject();
            wrapper.add("root", gson.toJsonTree(tree.getRoot()));
            String json = gson.toJson(wrapper);
            jedis.set(baseKey + ":tree", json);

            // Save each stage data
            for (Map.Entry<String, String> stage : stagesData.entrySet()) {
                String stageKey = baseKey + ":" + stage.getKey();
                jedis.set(stageKey, stage.getValue());
            }

            System.out.println("Saved tree growth with stages to Redis: " + baseKey);
            return true;
        } catch (Exception e) {
            System.err.println("Error saving tree growth with stages to Redis: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Shutdown Redis connection pool
     */
    public static void shutdown() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }
}