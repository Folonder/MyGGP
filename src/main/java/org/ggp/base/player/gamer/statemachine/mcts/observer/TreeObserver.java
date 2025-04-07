package org.ggp.base.player.gamer.statemachine.mcts.observer;

import com.google.gson.Gson;
import org.ggp.base.player.gamer.statemachine.mcts.MCTSIterationTracer;
import org.ggp.base.player.gamer.statemachine.mcts.event.IterationEvent;
import org.ggp.base.player.gamer.statemachine.mcts.event.TreeEvent;
import org.ggp.base.player.gamer.statemachine.mcts.event.TreeStartEvent;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTree;
import org.ggp.base.player.gamer.statemachine.mcts.utils.MCTSStagesTracker;
import org.ggp.base.player.gamer.statemachine.mcts.utils.RedisHelper;
import org.ggp.base.player.gamer.statemachine.mcts.utils.TreeSerializer;
import org.ggp.base.util.observer.Event;
import org.ggp.base.util.observer.Observer;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.Map;

public class TreeObserver implements Observer {

    private final Gson gson;

    private String sessionIdentifier;
    private int growthLogCounter = 0;
    private int iterationLogCounter = 0;
    private boolean loggingEnabled = true;
    private MCTSIterationTracer fileTracer;

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
            fileTracer = new MCTSIterationTracer(sessionId);
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

                    // Create file tracer with the session ID
                    fileTracer = new MCTSIterationTracer(sessionIdentifier);
                }

                // Reset counters
                growthLogCounter = 0;
                iterationLogCounter = 0;
                System.out.println("Tree logging initialized with Redis. Session ID: " + sessionIdentifier);
            } else if (event instanceof TreeEvent) {
                // Processing tree events
                TreeEvent treeEvent = ((TreeEvent) event);

                if (treeEvent.isGrowthEvent()) {
                    // Format for growth events - generate the ID first but don't increment yet
                    String growthLogId = String.format("%05d", growthLogCounter);
                    String turnId = String.format("%03d", treeEvent.getTurnNumber());

                    System.out.println("\n=== Processing Growth Event ===");
                    System.out.println("Growth ID: " + growthLogId + ", Turn: " + turnId);

                    // Get the tree
                    SearchTree tree = treeEvent.getTree();

                    // Make sure tree is using the same session ID
                    tree.setSessionId(sessionIdentifier);

                    // Check if we have stages data for this iteration
                    MCTSStagesTracker stagesTracker = tree.getCurrentStagesTracker();

                    boolean saved = false;
                    if (stagesTracker != null) {
                        // Get all stage data
                        Map<String, String> stagesData = stagesTracker.getAllStagesJson();

                        // Save growth with stages using hierarchical structure
                        saved = RedisHelper.saveGrowthWithStages(
                                sessionIdentifier,
                                treeEvent.getTurnNumber(),
                                growthLogId,
                                tree,
                                stagesData
                        );
                    } else {
                        // No stages data available, just save the tree
                        saved = RedisHelper.saveTreeData(
                                sessionIdentifier,
                                treeEvent.getTurnNumber(),
                                RedisHelper.TYPE_GROWTH + "_" + growthLogId,
                                tree
                        );
                    }

                    // Increment counter only after successful save
                    if (saved) {
                        growthLogCounter++;
                        System.out.println("Successfully saved growth event " + growthLogId);
                    } else {
                        System.err.println("Failed to save growth event " + growthLogId);
                    }

                } else if (treeEvent.isFinalTree()) {
                    // Final tree state after a move
                    String turnId = String.format("%03d", treeEvent.getTurnNumber());
                    String type;

                    // Determine the type of final record
                    if (treeEvent.isGameOver()) {
                        type = RedisHelper.TYPE_GAMEOVER;
                        System.out.println("\n=== Processing Game Over Tree ===");
                    } else {
                        type = RedisHelper.TYPE_FINAL;
                        System.out.println("\n=== Processing Final Tree for Turn " + turnId + " ===");
                    }

                    // Get the tree
                    SearchTree tree = treeEvent.getTree();

                    // Make sure tree is using the same session ID
                    tree.setSessionId(sessionIdentifier);

                    // Save the tree data
                    boolean saved = RedisHelper.saveTreeData(
                            sessionIdentifier,
                            treeEvent.getTurnNumber(),
                            type,
                            tree
                    );

                    if (saved) {
                        System.out.println("Successfully saved " + type + " tree");
                    } else {
                        System.err.println("Failed to save " + type + " tree");
                    }

                    // Reset growth counter for next turn (but only if not game over)
                    if (!treeEvent.isGameOver()) {
                        growthLogCounter = 0;
                        iterationLogCounter = 0;
                        System.out.println("Completed logging for turn " + treeEvent.getTurnNumber());
                    }
                } else {
                    // For the initial tree
                    String turnId = String.format("%03d", treeEvent.getTurnNumber());
                    System.out.println("\n=== Processing Initial Tree for Turn " + turnId + " ===");

                    // Get the tree
                    SearchTree tree = treeEvent.getTree();

                    // Make sure tree is using the same session ID
                    tree.setSessionId(sessionIdentifier);

                    // Save the initial tree
                    boolean saved = RedisHelper.saveTreeData(
                            sessionIdentifier,
                            treeEvent.getTurnNumber(),
                            RedisHelper.TYPE_INIT,
                            tree
                    );

                    if (saved) {
                        System.out.println("Successfully saved initial tree");
                    } else {
                        System.err.println("Failed to save initial tree");
                    }
                }
            } else if (event instanceof IterationEvent) {
                // Processing MCTS iteration events
                IterationEvent iterationEvent = (IterationEvent) event;

                // Create Redis key for iteration
                String turnId = String.format("%03d", iterationEvent.getTurnNumber());
                String iterationId = String.format("%05d", iterationEvent.getIterationNumber());

                System.out.println("=== Processing Iteration Event ===");
                System.out.println("Iteration: " + iterationId + ", Turn: " + turnId);

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
                    System.out.println("Successfully saved iteration data");

                    // Increment iteration counter only after successful save
                    iterationLogCounter++;
                } else {
                    System.err.println("Failed to save iteration data");
                }

                // Also use the file tracer to save to file system
                if (fileTracer != null) {
                    fileTracer.observe(iterationEvent);
                }
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