package org.ggp.base.player.gamer.statemachine.mcts.utils;

import com.google.gson.Gson;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTree;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.io.File;
import java.io.FileWriter;
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

    // Types of tree records
    public static final String TYPE_GROWTH = "growth";
    public static final String TYPE_FINAL = "final";
    public static final String TYPE_GAMEOVER = "gameover";
    public static final String TYPE_INIT = "init";

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

            // Also save to file system in matches directory
            try {
                String directoryName = "matches/" + sessionId;

                // Ensure parent directory exists
                File matchesDir = new File("matches");
                if (!matchesDir.exists()) {
                    matchesDir.mkdir();
                }

                // Ensure session directory exists
                File dir = new File(directoryName);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                File outFile = new File(dir, String.format("iteration_%s_turn_%s.json",
                        iterationIdStr, turnIdStr));
                try (FileWriter writer = new FileWriter(outFile)) {
                    writer.write(jsonData);
                }
                System.out.println("Saved iteration to file: " + outFile.getAbsolutePath());
            } catch (Exception e) {
                System.err.println("ERROR saving iteration to file: " + e.getMessage());
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

    /**
     * Saves MCTS tree growth data in a hierarchical structure with stages
     *
     * @param sessionId Session identifier
     * @param turnId Turn number
     * @param growthId Growth snapshot identifier
     * @param tree SearchTree to store
     * @param stagesData Map of stage names to their JSON data
     * @return true if all data was saved successfully
     */
    public static boolean saveGrowthWithStages(String sessionId, int turnId, String growthId,
                                               SearchTree tree,
                                               Map<String, String> stagesData) {
        String turnIdStr = String.format("%03d", turnId);

        // Base key prefix for all related data
        String baseKeyPrefix = String.format("mcts:%s:%s:growth_%s",
                sessionId, turnIdStr, growthId);

        System.out.println("Saving growth data with stages to Redis prefix: " + baseKeyPrefix);

        boolean allSuccessful = true;

        try {
            // Create Gson instance for tree serialization
            Gson gson = TreeSerializer.createGson();
            String treeJson = gson.toJson(tree);
            System.out.println("Tree JSON size: " + treeJson.length() + " bytes");

            // Save to Redis
            try (Jedis jedis = jedisPool.getResource()) {
                // Save tree
                String treeKey = baseKeyPrefix;
                String treeResult = jedis.set(treeKey, treeJson);
                boolean treeSuccess = "OK".equals(treeResult);

                if (treeSuccess) {
                    System.out.println("Saved tree to Redis: " + treeKey);
                } else {
                    System.err.println("Failed to save tree to Redis: " + treeKey);
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
                        System.out.println("Saved " + stageName + " stage to Redis: " + stageKey);
                    } else {
                        System.err.println("Failed to save " + stageName + " stage to Redis: " + stageKey);
                        allSuccessful = false;
                    }
                }
            }

            // Also save to file system in matches directory
            try {
                String directoryName = "matches/" + sessionId;

                // Ensure parent directory exists
                File matchesDir = new File("matches");
                if (!matchesDir.exists()) {
                    matchesDir.mkdir();
                }

                // Ensure session directory exists
                File dir = new File(directoryName);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                // Save tree
                File treeFile = new File(dir, "growth_" + growthId + ".json");
                try (FileWriter writer = new FileWriter(treeFile)) {
                    writer.write(treeJson);
                }
                System.out.println("Saved tree to file: " + treeFile.getAbsolutePath());

                // Save stages
                for (Map.Entry<String, String> entry : stagesData.entrySet()) {
                    String stageName = entry.getKey();
                    String stageData = entry.getValue();

                    File stageFile = new File(dir, "growth_" + growthId + "_" + stageName + ".json");
                    try (FileWriter writer = new FileWriter(stageFile)) {
                        writer.write(stageData);
                    }
                    System.out.println("Saved " + stageName + " stage to file: " + stageFile.getAbsolutePath());
                }
            } catch (Exception e) {
                System.err.println("ERROR saving to file system: " + e.getMessage());
                e.printStackTrace();
                allSuccessful = false;
            }

            return allSuccessful;
        } catch (Exception e) {
            System.err.println("ERROR in saveGrowthWithStages: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Directly writes tree data to Redis and file system
     *
     * @param sessionId Session identifier
     * @param turnId Turn identifier
     * @param type Type of tree record (growth_X, final, gameover, init)
     * @param tree SearchTree to store
     * @return true if successful, false otherwise
     */
    public static boolean saveTreeData(String sessionId, int turnId, String type, SearchTree tree) {
        String turnIdStr = String.format("%03d", turnId);

        // Form the key based on type
        String redisKey;
        if (TYPE_FINAL.equals(type)) {
            redisKey = String.format("mcts:%s:%s:final", sessionId, turnIdStr);
        } else if (TYPE_GAMEOVER.equals(type)) {
            redisKey = String.format("mcts:%s:gameover", sessionId);
        } else if (TYPE_INIT.equals(type)) {
            redisKey = String.format("mcts:%s:%s:init", sessionId, turnIdStr);
        } else {
            // Growth type with numeric id
            redisKey = String.format("mcts:%s:%s:growth_%s", sessionId, turnIdStr, type);
        }

        System.out.println("Attempting to save tree directly to Redis key: " + redisKey);

        try {
            // Use TreeSerializer for proper serialization
            Gson gson = TreeSerializer.createGson();
            String jsonTree = gson.toJson(tree);

            System.out.println("Tree JSON size: " + jsonTree.length() + " bytes");

            // Save to Redis
            boolean redisSuccess = false;
            try (Jedis jedis = jedisPool.getResource()) {
                // Set with no expiration
                String result = jedis.set(redisKey, jsonTree);
                redisSuccess = "OK".equals(result);
                System.out.println("Redis save result: " + (redisSuccess ? "SUCCESS" : "FAILED"));
            } catch (Exception e) {
                System.err.println("ERROR saving to Redis: " + e.getMessage());
            }

            // Also save to file system in matches directory
            boolean fileSuccess = false;
            try {
                String directoryName = "matches/" + sessionId;

                // Ensure parent directory exists
                File matchesDir = new File("matches");
                if (!matchesDir.exists()) {
                    matchesDir.mkdir();
                }

                // Ensure session directory exists
                File dir = new File(directoryName);
                if (!dir.exists()) {
                    dir.mkdirs();
                }

                // Determine file name based on type
                File outFile;
                if (TYPE_FINAL.equals(type)) {
                    outFile = new File(dir, "final.json");
                } else if (TYPE_GAMEOVER.equals(type)) {
                    outFile = new File(dir, "gameover.json");
                } else if (TYPE_INIT.equals(type)) {
                    outFile = new File(dir, "init.json");
                } else {
                    // Growth type with numeric id
                    outFile = new File(dir, "growth_" + type + ".json");
                }

                try (FileWriter writer = new FileWriter(outFile)) {
                    writer.write(jsonTree);
                }
                System.out.println("Saved tree to file: " + outFile.getAbsolutePath());
                fileSuccess = true;
            } catch (Exception e) {
                System.err.println("ERROR saving to file system: " + e.getMessage());
                e.printStackTrace();
            }

            return redisSuccess || fileSuccess; // Consider success if either worked
        } catch (Exception e) {
            System.err.println("ERROR in saveTreeData: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}