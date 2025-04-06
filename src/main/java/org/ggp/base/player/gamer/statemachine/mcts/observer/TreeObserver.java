package org.ggp.base.player.gamer.statemachine.mcts.observer;

import com.google.gson.*;
import org.ggp.base.player.gamer.statemachine.mcts.event.IterationEvent;
import org.ggp.base.player.gamer.statemachine.mcts.event.TreeEvent;
import org.ggp.base.player.gamer.statemachine.mcts.event.TreeStartEvent;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTree;
import org.ggp.base.player.gamer.statemachine.mcts.utils.MCTSStagesTracker;
import org.ggp.base.player.gamer.statemachine.mcts.utils.RedisHelper;
import org.ggp.base.player.gamer.statemachine.mcts.utils.TreeSerializer;
import org.ggp.base.util.observer.Event;
import org.ggp.base.util.observer.Observer;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Map;

public class TreeObserver implements Observer {

    private final Gson gson;

    private String sessionIdentifier;
    private int growthLogCounter = 0;
    private int iterationLogCounter = 0;
    private boolean loggingEnabled = true;

    // Redis connection configuration
    private final JedisPool jedisPool;
    private final String redisHost = "localhost";
    private final int redisPort = 5003;
    private final String redisPassword = "password";

    /**
     * Create a TreeObserver with a generated session ID
     */
    public TreeObserver() {
        this(null);
    }

    /**
     * Create a TreeObserver with a specified session ID
     * @param sessionId The session ID to use
     */
    public TreeObserver(String sessionId) {
        // Initialize redis connection pool
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);
        poolConfig.setMaxIdle(5);
        poolConfig.setMinIdle(1);
        jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 2000, redisPassword);

        // Configure gson for pretty printing with custom serializers
        gson = TreeSerializer.createGson();

        this.sessionIdentifier = sessionId;

        // Use RedisHelper to test connection
        RedisHelper.testConnection();

        // List existing keys if session is specified
        if (sessionId != null) {
            RedisHelper.listRedisKeys("mcts:" + sessionId + ":*");
        }
    }

    @Override
    public void observe(Event event) {
        if (!loggingEnabled) return;

        try {
            if (event instanceof TreeStartEvent) {
                // If the session ID wasn't passed in the constructor, generate it now
                if (sessionIdentifier == null) {
                    // Try to get game name, default to "unknown" if not available
                    String gameName = "unknown";
                    // Generate a unique session identifier with game name
                    java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss");
                    sessionIdentifier = gameName + "_" + formatter.format(new java.util.Date()) + "_" +
                            Math.abs(java.util.UUID.randomUUID().getMostSignificantBits());
                }

                // Reset counters
                growthLogCounter = 0;
                iterationLogCounter = 0;
                System.out.println("Tree logging initialized with Redis. Session ID: " + sessionIdentifier);
            } else if (event instanceof TreeEvent) {
                // Обработка событий дерева (как было раньше)
                TreeEvent treeEvent = ((TreeEvent) event);

                // Create Redis key
                String redisKey;

                if (treeEvent.isGrowthEvent()) {
                    // Format for growth events
                    String growthLogId = String.format("%05d", growthLogCounter++);
                    String turnId = String.format("%03d", treeEvent.getTurnNumber());

                    System.out.println("\n=== Processing Growth Event ===");
                    System.out.println("Growth ID: " + growthLogId + ", Turn: " + turnId);

                    // Get the tree as JSON using custom serializer
                    SearchTree tree = treeEvent.getTree();

                    // Check if we have stages data for this iteration
                    MCTSStagesTracker stagesTracker = tree.getCurrentStagesTracker();

                    if (stagesTracker != null) {
                        // Get all stage data
                        Map<String, String> stagesData = stagesTracker.getAllStagesJson();

                        // Save tree separately using specialized method first
                        boolean treeSaved = RedisHelper.saveTreeData(
                                sessionIdentifier,
                                treeEvent.getTurnNumber(),
                                growthLogCounter - 1,
                                tree
                        );

                        // Only save stages if tree was saved successfully
                        if (treeSaved) {
                            // Save each stage separately
                            boolean allStagesSaved = true;
                            for (Map.Entry<String, String> entry : stagesData.entrySet()) {
                                String stageName = entry.getKey();
                                String stageData = entry.getValue();

                                String stageKey = String.format("mcts:%s:%s:growth_%s:%s",
                                        sessionIdentifier, turnId, growthLogId, stageName);

                                try (Jedis jedis = jedisPool.getResource()) {
                                    String result = jedis.set(stageKey, stageData);
                                    boolean success = "OK".equals(result);

                                    if (success) {
                                        System.out.println("Saved " + stageName + " stage to Redis");
                                    } else {
                                        System.err.println("Failed to save " + stageName + " stage to Redis");
                                        allStagesSaved = false;
                                    }
                                } catch (Exception e) {
                                    System.err.println("ERROR saving stage to Redis: " + e.getMessage());
                                    allStagesSaved = false;
                                }
                            }

                            System.out.println("Saved growth with stages: " +
                                    (allStagesSaved ? "all successful" : "some stages failed"));
                        } else {
                            System.err.println("Failed to save tree, skipping stage saving");

                            // Fallback to traditional approach for just the tree
                            String traditionalRedisKey = String.format("mcts:%s:%s:growth_%s",
                                    sessionIdentifier, turnId, growthLogId);

                            try (Jedis jedis = jedisPool.getResource()) {
                                String jsonTree = TreeSerializer.createGson().toJson(tree);
                                jedis.set(traditionalRedisKey, jsonTree);
                                System.out.println("Saved tree using traditional approach: " + traditionalRedisKey);
                            } catch (Exception e) {
                                System.err.println("ERROR using traditional approach: " + e.getMessage());
                            }
                        }
                    } else {
                        // Fallback to traditional approach if no stages data
                        String traditionalRedisKey = String.format("mcts:%s:%s:growth_%s",
                                sessionIdentifier, turnId, growthLogId);

                        try (Jedis jedis = jedisPool.getResource()) {
                            String jsonTree = TreeSerializer.createGson().toJson(tree);
                            jedis.set(traditionalRedisKey, jsonTree);
                            System.out.println("Saved tree (no stages data) to Redis: " + traditionalRedisKey);
                        } catch (Exception e) {
                            System.err.println("ERROR saving to Redis: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    // Set redisKey for other conditions
                    redisKey = String.format("mcts:%s:%s:growth_%s",
                            sessionIdentifier, turnId, growthLogId);
                } else if (treeEvent.isFinalTree()) {
                    // Final tree state after a move
                    String turnId = String.format("%03d", treeEvent.getTurnNumber());

                    // Only store the final tree if we haven't already stored a final tree for this turn
                    // This happens when the game is over (stateMachineStop/Abort is called)
                    if (treeEvent.isGameOver()) {
                        // For the game-over final state (after the last move)
                        redisKey = String.format("mcts:%s:gameover", sessionIdentifier);
                    } else {
                        redisKey = String.format("mcts:%s:%s:final",
                                sessionIdentifier,
                                turnId);
                    }

                    // Reset growth counter for next turn
                    growthLogCounter = 0;

                    // Reset iteration counter for next turn
                    iterationLogCounter = 0;

                    System.out.println("Completed logging for turn " + treeEvent.getTurnNumber());
                } else {
                    // For the initial tree
                    String turnId = String.format("%03d", treeEvent.getTurnNumber());
                    redisKey = String.format("mcts:%s:%s:init",
                            sessionIdentifier,
                            turnId);
                }

                // Store the tree in Redis
                try (Jedis jedis = jedisPool.getResource()) {
                    String jsonTree = gson.toJson(treeEvent.getTree());
                    jedis.set(redisKey, jsonTree);
                    System.out.println("Stored tree in Redis with key: " + redisKey);
                }
            } else if (event instanceof IterationEvent) {
                // Processing MCTS iteration events
                IterationEvent iterationEvent = (IterationEvent) event;

                // Create Redis key for iteration
                String turnId = String.format("%03d", iterationEvent.getTurnNumber());
                String iterationId = String.format("%05d", iterationEvent.getIterationNumber());

                System.out.println("=== Starting to save MCTS iteration ===");
                System.out.println("Iteration: " + iterationEvent.getIterationNumber() + ", Turn: " + iterationEvent.getTurnNumber());

                // Convert iteration data to JSON
                String jsonIteration = gson.toJson(iterationEvent.getIterationData());

                // Try saving using the RedisHelper for direct access
                boolean saved = RedisHelper.saveIterationData(
                        sessionIdentifier,
                        iterationEvent.getTurnNumber(),
                        iterationEvent.getIterationNumber(),
                        jsonIteration
                );

                if (saved) {
                    System.out.println("Successfully saved iteration data to Redis using direct access");
                } else {
                    System.err.println("Failed to save iteration data using direct access");

                    // Fallback to traditional approach
                    System.out.println("Trying traditional Redis approach...");
                    String redisKey = String.format("mcts:%s:%s:iteration_%s",
                            sessionIdentifier, turnId, iterationId);

                    try (Jedis jedis = jedisPool.getResource()) {
                        jedis.set(redisKey, jsonIteration);
                        System.out.println("Saved iteration data to Redis using traditional approach: " + redisKey);
                    } catch (Exception e) {
                        System.err.println("ERROR saving to Redis (traditional): " + e.getMessage());
                    }
                }

                System.out.println("=== End of saving MCTS iteration ===");

                // Increment iteration counter
                iterationLogCounter++;
            }
        } catch (Exception e) {
            System.err.println("Error writing to Redis: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Enable or disable logging
     * @param enabled Whether logging should be enabled
     */
    public void setLoggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
    }

    /**
     * Clean up resources
     */
    public void shutdown() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }
}