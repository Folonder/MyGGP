package org.ggp.base.player.gamer.statemachine.mcts.logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.diff.JsonDiff;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

/**
 * Логгер для записи состояний MCTS дерева в Redis в формате JSON Patch
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
     * Создаёт новый логгер для указанного хода
     */
    public MCTSRedisLogger(String matchId, int turnNumber) {
        this.treeId = matchId + ":" + turnNumber;
        this.turnNumber = turnNumber;

        // Инициализация происходит в конструкторе
        try (Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT)) {
            jedis.auth(REDIS_PASSWORD);

            // Сохраняем метаданные
            jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "matchId", matchId);
            jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "turnNumber", String.valueOf(turnNumber));
            jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "createdAt", String.valueOf(System.currentTimeMillis()));
            jedis.expire(KEY_PREFIX + treeId + META_SUFFIX, 86400);

            // Сбрасываем счетчик патчей
            jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "totalPatches", "0");

            System.out.println("MCTSRedisLogger initialized for turn " + turnNumber);
        } catch (JedisException e) {
            System.err.println("Redis connection error during initialization: " + e.getMessage());
            throw e; // Повторное возбуждение исключения для обработки на уровне выше
        }
    }

    /**
     * Сохраняет начальное состояние дерева
     * @return true если сохранение успешно, false в противном случае
     */
    public boolean saveInitialState(JsonNode treeJson) {
        try (Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT)) {
            jedis.auth(REDIS_PASSWORD);

            // Конвертируем в строку и сохраняем полное дерево (начальное состояние)
            String initialJson = treeJson.toString();
            jedis.set(KEY_PREFIX + treeId + INITIAL_SUFFIX, initialJson);
            jedis.expire(KEY_PREFIX + treeId + INITIAL_SUFFIX, 86400);

            // Проверяем, что начальное состояние сохранилось
            String savedJson = jedis.get(KEY_PREFIX + treeId + INITIAL_SUFFIX);
            if (savedJson == null || savedJson.isEmpty()) {
                System.err.println("Failed to save initial state for turn " + turnNumber +
                        " - verification failed");
                return false;
            }

            // Сохраняем копию для патчей
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
     * Сохраняет патч изменений с текущим состоянием дерева
     */
    public boolean logTreeState(JsonNode treeJson) {
        // Если начальное состояние не сохранено, сохраняем его сейчас
        if (!initialSaved) {
            return saveInitialState(treeJson);
        }

        try (Jedis jedis = new Jedis(REDIS_HOST, REDIS_PORT)) {
            jedis.auth(REDIS_PASSWORD);

            // Увеличиваем счетчик патчей
            patchCounter++;

            // Вычисляем разницу между предыдущим и текущим состоянием в формате JSON Patch
            JsonNode patchNode = JsonDiff.asJson(previousTree, treeJson);

            // Проверяем, есть ли изменения
            if (patchNode.isArray() && patchNode.size() > 0) {
                // Сохраняем патч
                String patchKey = KEY_PREFIX + treeId + PATCH_SUFFIX + String.format("%06d", patchCounter);
                jedis.set(patchKey, patchNode.toString());
                jedis.expire(patchKey, 86400);

                // Обновляем счетчик в метаданных
                jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "totalPatches", String.valueOf(patchCounter));

                // Обновляем метку времени последнего патча
                jedis.hset(KEY_PREFIX + treeId + META_SUFFIX, "lastPatchTime", String.valueOf(System.currentTimeMillis()));

                // Логируем размер патча для анализа
                System.out.println("Patch #" + patchCounter + " saved: " + patchNode.size() + " operations, " +
                        patchNode.toString().length() + " bytes");
            } else {
                System.out.println("No changes detected, skipping patch #" + patchCounter);
                // Уменьшаем счетчик обратно, так как патч не был сохранен
                patchCounter--;
            }

            // Обновляем предыдущее состояние
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
     * Проверяет, существует ли начальное состояние в Redis
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
     * Возвращает идентификатор дерева
     */
    public String getTreeId() {
        return treeId;
    }

    @Override
    public void close() {
        // Проверка наличия начального состояния перед закрытием
        if (!hasInitialState() && previousTree != null) {
            System.err.println("WARNING: Initial state missing for turn " + turnNumber +
                    " - attempting emergency save");
            saveInitialState(previousTree);
        }

        System.out.println("Closing MCTSRedisLogger for turn " + turnNumber + ", saved " + patchCounter + " patches");
    }
}