package org.ggp.base.player.gamer.statemachine.mcts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ggp.base.player.gamer.statemachine.mcts.utils.RedisHelper;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Set;

/**
 * Standalone test program for Redis MCTS integration
 */
public class RedisMCTSTest {
    // Redis connection details
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 5003;
    private static final String REDIS_PASSWORD = "password";

    public static void main(String[] args) {
        System.out.println("Starting Redis MCTS Test");

        // Test basic Redis connection
        basicRedisTest();

        // Test using RedisHelper
        helperRedisTest();

        // Test serialization and saving
        serializationTest();

        System.out.println("Redis MCTS Test Complete");
    }

    private static void basicRedisTest() {
        System.out.println("\n=== BASIC REDIS CONNECTION TEST ===");

        JedisPool jedisPool = null;
        try {
            // Setup connection
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(5);
            jedisPool = new JedisPool(poolConfig, REDIS_HOST, REDIS_PORT, 2000, REDIS_PASSWORD);

            // Test connection
            try (Jedis jedis = jedisPool.getResource()) {
                String pingResponse = jedis.ping();
                System.out.println("PING Response: " + pingResponse);

                // Test write
                String testKey = "mcts:standalone_test:" + System.currentTimeMillis();
                String testValue = "standalone_test_" + System.currentTimeMillis();
                String setResult = jedis.set(testKey, testValue);
                System.out.println("SET Result: " + setResult);

                // Test read
                String getValue = jedis.get(testKey);
                System.out.println("GET Result: " + getValue);
                System.out.println("Values match: " + testValue.equals(getValue));

                // List keys
                Set<String> keys = jedis.keys("mcts:*");
                System.out.println("Existing MCTS keys in Redis: " + keys.size());
                for (String key : keys) {
                    if (keys.size() <= 10 || key.contains("standalone_test")) {
                        System.out.println("  - " + key);
                    }
                }

                if (keys.size() > 10) {
                    System.out.println("  (showing only first few keys)");
                }
            }
        } catch (Exception e) {
            System.err.println("ERROR in basic Redis test: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (jedisPool != null) {
                jedisPool.close();
            }
        }

        System.out.println("=== END OF BASIC REDIS TEST ===\n");
    }

    private static void helperRedisTest() {
        System.out.println("\n=== REDIS HELPER TEST ===");

        // Test connection
        RedisHelper.testConnection();

        // List keys
        RedisHelper.listRedisKeys("mcts:*");

        System.out.println("=== END OF REDIS HELPER TEST ===\n");
    }

    private static void serializationTest() {
        System.out.println("\n=== SERIALIZATION TEST ===");

        try {
            // Create a test MCTS iteration-like object
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode iterationData = mapper.createObjectNode();

            // Add iteration details
            iterationData.put("iteration", 999);
            iterationData.put("timestamp", System.currentTimeMillis());

            // Add stages
            ObjectNode stages = iterationData.putObject("stages");

            // Selection stage
            ObjectNode selection = stages.putObject("selection");
            selection.put("pathLength", 5);
            selection.put("selectedNodeState", "test_state_data");

            // Convert to JSON
            String jsonData = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(iterationData);
            System.out.println("Generated JSON:");
            System.out.println(jsonData);

            // Try saving with RedisHelper
            String testSessionId = "serialization_test_" + System.currentTimeMillis();
            boolean saved = RedisHelper.saveIterationData(testSessionId, 1, 999, jsonData);
            System.out.println("Save result: " + (saved ? "SUCCESS" : "FAILED"));

            // Verify
            RedisHelper.listRedisKeys("mcts:" + testSessionId + ":*");
        } catch (Exception e) {
            System.err.println("ERROR in serialization test: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("=== END OF SERIALIZATION TEST ===\n");
    }
}