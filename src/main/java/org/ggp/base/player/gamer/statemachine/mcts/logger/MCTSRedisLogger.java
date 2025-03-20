package org.ggp.base.player.gamer.statemachine.mcts.logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.diff.JsonDiff;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

/**
 * ������ ��� ������ ��������� MCTS ������ � Redis � ������� JSON Patch
 */
public class MCTSRedisLogger implements AutoCloseable {
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 5003;
    private static final String REDIS_PASSWORD = "password";

    private final String treeId;
    private final int turnNumber;
    private final ObjectMapper mapper = new ObjectMapper();

    // Redis key prefixes
    private static final String KEY_PREFIX = "mcts:";
    private static final String INITIAL_SUFFIX = ":initial";
    private static final String PATCH_SUFFIX = ":patch:";
    private static final String META_SUFFIX = ":meta";

    private int patchCounter = 0;
    private JsonNode previousTree = null;
    private boolean initialSaved = false;

    /**
     * ������ ����� ������ ��� ���������� ����
     */
    public MCTSRedisLogger(String matchId, int turnNumber) {
        this.treeId = matchId + ":" + turnNumber;
        this.turnNumber = turnNumber;

        // ������������� ���������� � ������������
        try (Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT)) {
            jedis.auth(REDIS_PASSWORD);

            // ��������� ����������
            jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "matchId", matchId);
            jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "turnNumber", String.valueOf(turnNumber));
            jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "createdAt", String.valueOf(System.currentTimeMillis()));
            jedis.expire(KEY_PREFIX + treeId + META_SUFFIX, 86400);

            // ���������� ������� ������
            jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "totalPatches", "0");

            System.out.println("MCTSRedisLogger initialized for turn " + turnNumber);
        } catch (JedisException e) {
            System.err.println("Redis connection error during initialization: " + e.getMessage());
            throw e; // ��������� ����������� ���������� ��� ��������� �� ������ ����
        }
    }

    /**
     * ��������� ��������� ��������� ������
     * @return true ���� ���������� �������, false � ��������� ������
     */
    public boolean saveInitialState(JsonNode treeJson) {
        try (Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT)) {
            jedis.auth(REDIS_PASSWORD);

            // ������������ � ������ � ��������� ������ ������ (��������� ���������)
            String initialJson = treeJson.toString();
            jedis.set(KEY_PREFIX + treeId + INITIAL_SUFFIX, initialJson);
            jedis.expire(KEY_PREFIX + treeId + INITIAL_SUFFIX, 86400);

            // ���������, ��� ��������� ��������� �����������
            String savedJson = jedis.get(KEY_PREFIX + treeId + INITIAL_SUFFIX);
            if (savedJson == null || savedJson.isEmpty()) {
                System.err.println("Failed to save initial state for turn " + turnNumber +
                        " - verification failed");
                return false;
            }

            // ��������� ����� ��� ������
            previousTree = treeJson.deepCopy();
            initialSaved = true;

            System.out.println("Initial state saved for turn " + turnNumber);
            return true;
        } catch (JedisException e) {
            System.err.println("Redis error saving initial state: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Error saving initial state: " + e.getMessage());
            return false;
        }
    }

    /**
     * ��������� ���� ��������� � ������� ���������� ������
     */
    public boolean logTreeState(JsonNode treeJson) {
        // ���� ��������� ��������� �� ���������, ��������� ��� ������
        if (!initialSaved) {
            return saveInitialState(treeJson);
        }

        try (Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT)) {
            jedis.auth(REDIS_PASSWORD);

            // ����������� ������� ������
            patchCounter++;

            // ��������� ������� ����� ���������� � ������� ���������� � ������� JSON Patch
            JsonNode patchNode = JsonDiff.asJson(previousTree, treeJson);

            // ���������, ���� �� ���������
            if (patchNode.isArray() && patchNode.size() > 0) {
                // ��������� ����
                String patchKey = KEY_PREFIX + treeId + PATCH_SUFFIX + String.format("%06d", patchCounter);
                jedis.set(patchKey, patchNode.toString());
                jedis.expire(patchKey, 86400);

                // ��������� ������� � ����������
                jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "totalPatches", String.valueOf(patchCounter));

                // ��������� ����� ������� ���������� �����
                jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "lastPatchTime", String.valueOf(System.currentTimeMillis()));

                // �������� ������ ����� ��� �������
                System.out.println("Patch #" + patchCounter + " saved: " + patchNode.size() + " operations, " +
                        patchNode.toString().length() + " bytes");
            } else {
                System.out.println("No changes detected, skipping patch #" + patchCounter);
                // ��������� ������� �������, ��� ��� ���� �� ��� ��������
                patchCounter--;
            }

            // ��������� ���������� ���������
            previousTree = treeJson.deepCopy();

            return true;
        } catch (JedisException e) {
            System.err.println("Redis error saving patch: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Error saving patch: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * ���������, ���������� �� ��������� ��������� � Redis
     */
    public boolean hasInitialState() {
        try (Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT)) {
            jedis.auth(REDIS_PASSWORD);
            return jedis.exists(KEY_PREFIX + treeId + INITIAL_SUFFIX);
        } catch (JedisException e) {
            System.err.println("Redis error checking initial state: " + e.getMessage());
            return false;
        }
    }

    /**
     * ���������� ������������� ������
     */
    public String getTreeId() {
        return treeId;
    }

    @Override
    public void close() {
        // �������� ������� ���������� ��������� ����� ���������
        if (!hasInitialState() && previousTree != null) {
            System.err.println("WARNING: Initial state missing for turn " + turnNumber +
                    " - attempting emergency save");
            saveInitialState(previousTree);
        }

        System.out.println("Closing MCTSRedisLogger for turn " + turnNumber + ", saved " + patchCounter + " patches");
    }
}