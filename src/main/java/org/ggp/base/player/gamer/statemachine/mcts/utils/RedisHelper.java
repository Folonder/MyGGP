package org.ggp.base.player.gamer.statemachine.mcts.utils;

import com.google.gson.Gson;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTree;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Map;
import java.util.Set;

/**
 * Helper class for Redis operations with direct access methods
 */
public class RedisHelper {
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 5003;
    private static final String REDIS_PASSWORD = "password";
    private static JedisPool jedisPool;

    static {
        // Initialize Jedis pool
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);
        jedisPool = new JedisPool(poolConfig, REDIS_HOST, REDIS_PORT, 2000, REDIS_PASSWORD);

        // Test connection on class load
        testConnection();
    }


    /**
     * Directly writes tree data to Redis
     *
     * @param sessionId Session identifier
     * @param turnId Turn identifier
     * @param growthId Growth identifier
     * @param tree SearchTree to store
     * @return true if successful, false otherwise
     */
    public static boolean saveTreeData(String sessionId, int turnId, int growthId, SearchTree tree) {
        String turnIdStr = String.format("%03d", turnId);
        String growthIdStr = String.format("%05d", growthId);

        // Form the key
        String redisKey = String.format("mcts:%s:%s:growth_%s:tree",
                sessionId, turnIdStr, growthIdStr);

        System.out.println("Attempting to save tree directly to Redis key: " + redisKey);

        try {
            // Use custom Gson to avoid circular references
            Gson gson = TreeSerializer.createGson();
            String jsonTree = gson.toJson(tree);

            System.out.println("Tree JSON size: " + jsonTree.length() + " bytes");

            // Save to Redis
            try (Jedis jedis = jedisPool.getResource()) {
                // Set with no expiration
                String result = jedis.set(redisKey, jsonTree);
                boolean success = "OK".equals(result);

                System.out.println("Redis set result: " + result);

                return success;
            }
        } catch (Exception e) {
            System.err.println("ERROR saving tree to Redis: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }    /**
     * Saves MCTS tree growth data in a hierarchical structure with stages
     *
     * @param sessionId Session identifier
     * @param turnId Turn number
     * @param growthId Growth snapshot number
     * @param treeJson Tree snapshot JSON data
     * @param stagesData Map of stage names to their JSON data
     * @return true if all data was saved successfully
     */
    public static boolean saveGrowthWithStages(String sessionId, int turnId, int growthId,
                                               String treeJson,
                                               Map<String, String> stagesData) {
        String turnIdStr = String.format("%03d", turnId);
        String growthIdStr = String.format("%05d", growthId);

        // Base key prefix for all related data
        String baseKeyPrefix = String.format("mcts:%s:%s:growth_%s",
                sessionId, turnIdStr, growthIdStr);

        System.out.println("Saving growth data with stages to Redis prefix: " + baseKeyPrefix);

        boolean allSuccessful = true;

        try (Jedis jedis = jedisPool.getResource()) {
            // Save tree snapshot
            String treeKey = baseKeyPrefix + ":tree";
            String treeResult = jedis.set(treeKey, treeJson);
            boolean treeSuccess = "OK".equals(treeResult);

            if (treeSuccess) {
                System.out.println("Saved tree snapshot to: " + treeKey);
            } else {
                System.err.println("Failed to save tree snapshot to: " + treeKey);
                allSuccessful = false;
            }

            // Save each stage
            for (Map.Entry<String, String> entry : stagesData.entrySet()) {
                String stageName = entry.getKey();
                String stageData = entry.getValue();

                String stageKey = baseKeyPrefix + ":" + stageName;
                String stageResult = jedis.set(stageKey, stageData);
                boolean stageSuccess = "OK".equals(stageResult);

                if (stageSuccess) {
                    System.out.println("Saved " + stageName + " stage to: " + stageKey);
                } else {
                    System.err.println("Failed to save " + stageName + " stage to: " + stageKey);
                    allSuccessful = false;
                }
            }

            // List all keys under this growth prefix
            Set<String> keys = jedis.keys(baseKeyPrefix + ":*");
            System.out.println("Keys saved under growth " + growthIdStr + ":");
            for (String key : keys) {
                System.out.println("  - " + key);
            }

            return allSuccessful;
        } catch (Exception e) {
            System.err.println("ERROR saving growth data: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Tests Redis connection and prints diagnostic information
     */
    public static void testConnection() {
        try (Jedis jedis = jedisPool.getResource()) {
            String pingResponse = jedis.ping();
            System.out.println("\n==== REDIS CONNECTION TEST ====");
            System.out.println("Host: " + REDIS_HOST);
            System.out.println("Port: " + REDIS_PORT);
            System.out.println("PING response: " + pingResponse);

            // Test writing a value
            String testKey = "mcts:direct_test:" + System.currentTimeMillis();
            String testValue = "direct_test_" + System.currentTimeMillis();
            jedis.setex(testKey, 60, testValue);

            String retrievedValue = jedis.get(testKey);
            boolean testSuccess = testValue.equals(retrievedValue);

            System.out.println("Write test key: " + testKey);
            System.out.println("Write test value: " + testValue);
            System.out.println("Read test result: " + retrievedValue);
            System.out.println("Test " + (testSuccess ? "PASSED" : "FAILED"));

            System.out.println("==== END OF REDIS TEST ====\n");
        } catch (Exception e) {
            System.err.println("REDIS CONNECTION ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Directly writes iteration data to Redis
     *
     * @param sessionId Session identifier
     * @param turnId Turn identifier
     * @param iterationId Iteration identifier
     * @param jsonData JSON data to store
     * @return true if successful, false otherwise
     */
    public static boolean saveIterationData(String sessionId, int turnId, int iterationId, String jsonData) {
        String turnIdStr = String.format("%03d", turnId);
        String iterationIdStr = String.format("%05d", iterationId);

        // Form the key
        String redisKey = String.format("mcts:%s:%s:iteration_%s",
                sessionId, turnIdStr, iterationIdStr);

        System.out.println("Attempting to save iteration directly to Redis key: " + redisKey);
        System.out.println("Data length: " + jsonData.length() + " bytes");

        try (Jedis jedis = jedisPool.getResource()) {
            // Set with no expiration
            String result = jedis.set(redisKey, jsonData);
            boolean success = "OK".equals(result);

            // Verify
            String retrievedValue = jedis.get(redisKey);
            boolean verifySuccess = retrievedValue != null && !retrievedValue.isEmpty();

            System.out.println("Redis set result: " + result);
            System.out.println("Verification " + (verifySuccess ? "PASSED" : "FAILED"));

            // List keys matching pattern
            System.out.println("Existing iteration keys in Redis:");
            Set<String> keys = jedis.keys("mcts:" + sessionId + ":*:iteration_*");
            if (keys.isEmpty()) {
                System.out.println("  (none found)");
            } else {
                for (String key : keys) {
                    System.out.println("  - " + key);
                }
            }

            return success && verifySuccess;
        } catch (Exception e) {
            System.err.println("ERROR saving iteration to Redis: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Lists all keys in Redis matching a pattern
     *
     * @param pattern Key pattern to match
     */
    public static void listRedisKeys(String pattern) {
        try (Jedis jedis = jedisPool.getResource()) {
            System.out.println("Redis keys matching pattern: " + pattern);
            Set<String> keys = jedis.keys(pattern);

            if (keys.isEmpty()) {
                System.out.println("  (none found)");
            } else {
                for (String key : keys) {
                    System.out.println("  - " + key);
                }
            }
        } catch (Exception e) {
            System.err.println("ERROR listing Redis keys: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Cleans up Redis resources when done
     */
    public static void shutdown() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }
}