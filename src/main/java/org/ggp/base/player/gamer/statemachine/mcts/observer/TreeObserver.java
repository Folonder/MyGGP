package org.ggp.base.player.gamer.statemachine.mcts.observer;

import com.google.gson.*;
import org.ggp.base.player.gamer.statemachine.mcts.event.TreeEvent;
import org.ggp.base.player.gamer.statemachine.mcts.event.TreeStartEvent;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.observer.Event;
import org.ggp.base.util.observer.Observer;
import org.ggp.base.util.statemachine.MachineState;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.lang.reflect.Type;

public class TreeObserver implements Observer {

    private final Gson gson;

    private String sessionIdentifier;
    private int growthLogCounter = 0;
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

        // Configure gson for pretty printing
        gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(MachineState.class, new MachineStateSerializer())
                .create();

        this.sessionIdentifier = sessionId;

        // Test Redis connection
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.ping();
            System.out.println("Successfully connected to Redis");
        } catch (Exception e) {
            System.err.println("Failed to connect to Redis: " + e.getMessage());
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
                System.out.println("Tree logging initialized with Redis. Session ID: " + sessionIdentifier);
            } else if (event instanceof TreeEvent) {
                TreeEvent treeEvent = ((TreeEvent) event);

                // Create Redis key
                String redisKey;

                if (treeEvent.isGrowthEvent()) {
                    // Format for growth events
                    String growthLogId = String.format("%05d", growthLogCounter++);
                    String turnId = String.format("%03d", treeEvent.getTurnNumber());
                    redisKey = String.format("mcts:%s:%s:growth_%s",
                            sessionIdentifier,
                            turnId,
                            growthLogId);
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

    static class MachineStateSerializer implements JsonSerializer<MachineState> {
        @Override
        public JsonElement serialize(MachineState src, Type typeOfSrc, JsonSerializationContext context) {
            JsonArray result = new JsonArray();
            if (src != null && src.getContents() != null) {
                for (GdlSentence sentence : src.getContents()) {
                    result.add(sentence.toString());
                }
            }
            return result;
        }
    }
}