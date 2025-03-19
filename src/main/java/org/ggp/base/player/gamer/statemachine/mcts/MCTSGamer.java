package org.ggp.base.player.gamer.statemachine.mcts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ggp.base.player.gamer.statemachine.mcts.logger.MCTSRedisLogger;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTree;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTreeNode;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class MCTSGamer extends SampleGamer {
    private final long SAFETY_MARGIN = 2000;
    private static final int DEFAULT_LOGGING_FREQUENCY = 100;
    private static final boolean ENABLE_REDIS_LOGGING = true;

    private final ObjectMapper mapper = new ObjectMapper();
    private SearchTree tree = null;
    private int turnCount = 0;
    private String currentTreeId = null;

    @Override
    public void stateMachineMetaGame(long timeout)
            throws TransitionDefinitionException, MoveDefinitionException,
            GoalDefinitionException {
        tree = new SearchTree(getStateMachine());
        turnCount = 0;
    }

    @Override
    public Move stateMachineSelectMove(long timeout)
            throws TransitionDefinitionException, MoveDefinitionException,
            GoalDefinitionException {
        // Find node for current state and make it the root
        SearchTreeNode startRootNode = tree.findNode(getCurrentState());
        tree.cut(startRootNode);

        // Initialize Redis logger
        MCTSRedisLogger redisLogger = null;
        try {
            if (ENABLE_REDIS_LOGGING) {
                // Создаем логгер
                redisLogger = new MCTSRedisLogger(
                        getMatch().getMatchId(),
                        turnCount
                );
                currentTreeId = redisLogger.getTreeId();

                // Сохраняем начальное состояние с проверкой
                JsonNode initialTree = tree.toJSONbyJackson();
                boolean saved = redisLogger.saveInitialState(initialTree);
                if (!saved) {
                    System.err.println("ERROR: Failed to save initial state for turn " + turnCount);

                    // Повторные попытки сохранения
                    for (int attempt = 1; attempt <= 3; attempt++) {
                        System.out.println("Retry #" + attempt + " to save initial state");
                        saved = redisLogger.saveInitialState(initialTree);
                        if (saved) {
                            System.out.println("Initial state saved successfully on retry #" + attempt);
                            break;
                        }
                    }

                    if (!saved) {
                        System.err.println("All retries failed for turn " + turnCount);
                    }
                }

                // Дополнительная проверка после сохранения
                if (!redisLogger.hasInitialState()) {
                    System.err.println("Initial state verification failed for turn " + turnCount);
                }
            }
        } catch (Exception e) {
            System.err.println("Error initializing Redis logger: " + e.getMessage());
            e.printStackTrace();
        }

        long finishBy = timeout - SAFETY_MARGIN;
        int iterations = 0;

        while (System.currentTimeMillis() < finishBy) {
            iterations++;
            tree.grow();

            // Логируем изменения каждые X итераций
            if (ENABLE_REDIS_LOGGING && redisLogger != null && iterations % 100 == 0) {
                try {
                    JsonNode currentTree = tree.toJSONbyJackson();
                    redisLogger.logTreeState(currentTree);
                } catch (Exception e) {
                    System.err.println("Error logging tree state: " + e.getMessage());
                }
            }
        }

        // Select best move
        Move bestMove = tree.getBestAction(getRole());

        // Логируем финальное состояние
        if (ENABLE_REDIS_LOGGING && redisLogger != null) {
            try {
                JsonNode finalTree = tree.toJSONbyJackson();
                redisLogger.logTreeState(finalTree);
            } catch (Exception e) {
                System.err.println("Error logging final tree state: " + e.getMessage());
            }
        }

        // Сохраняем ID дерева
        saveTreeIdToFile();

        // Финальная проверка и закрытие логгера
        if (redisLogger != null) {
            try {
                if (!redisLogger.hasInitialState()) {
                    System.err.println("CRITICAL: Initial state missing before closing for turn " + turnCount);

                    // Последняя попытка сохранения
                    JsonNode finalState = tree.toJSONbyJackson();
                    boolean saved = redisLogger.saveInitialState(finalState);
                    if (saved) {
                        System.out.println("Initial state saved in emergency mode for turn " + turnCount);
                    }
                }

                redisLogger.close();
            } catch (Exception e) {
                System.err.println("Error closing Redis logger: " + e.getMessage());
            }
        }

        turnCount++;
        return bestMove;
    }

    private void saveTreeIdToFile() {
        if (currentTreeId == null) return;

        try {
            String dirPath = getMatchFolderString() + "/" + this.getName() + "__" + this.getRoleName().toString();
            File dir = new File(dirPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File treeIdFile = new File(dirPath + "/tree_id.txt");
            FileWriter writer = new FileWriter(treeIdFile);
            writer.write(currentTreeId);
            writer.close();
        } catch (IOException e) {
            System.err.println("Error saving tree ID: " + e.getMessage());
        }
    }
}