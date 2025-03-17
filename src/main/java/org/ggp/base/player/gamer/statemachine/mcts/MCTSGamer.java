package org.ggp.base.player.gamer.statemachine.mcts;

import org.ggp.base.player.gamer.statemachine.mcts.event.TreeEvent;
import org.ggp.base.player.gamer.statemachine.mcts.event.TreeStartEvent;
import org.ggp.base.player.gamer.statemachine.mcts.logger.AsyncMCTSRedisLogger;
import org.ggp.base.player.gamer.statemachine.mcts.model.statistics.CumulativeStatistics;
import org.ggp.base.player.gamer.statemachine.mcts.model.statistics.StatisticsForActions;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTree;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTreeNode;
import org.ggp.base.player.gamer.statemachine.mcts.observer.TreeObserver;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MCTSGamer extends SampleGamer {
    // Время безопасного завершения
    private final long SAFETY_MARGIN = 2000;

    // Конфигурация логгера
    private static final int DEFAULT_LOGGING_FREQUENCY = 1000;
    private static final boolean ENABLE_REDIS_LOGGING = true;

    // Состояние игры
    private SearchTree tree = null;
    private int turnCount = 0;
    private String currentTreeId = null;
    private AsyncMCTSRedisLogger redisLogger = null;

    @Override
    public void stateMachineMetaGame(long xiTimeout)
            throws TransitionDefinitionException, MoveDefinitionException,
            GoalDefinitionException {
        // Инициализируем дерево поиска
        tree = new SearchTree(getStateMachine());
        turnCount = 0;

        // Добавляем наблюдателя за деревом
        this.addObserver(new TreeObserver());

        // Уведомляем о старте дерева
        notifyObservers(new TreeStartEvent());
    }

    @Override
    public void stateMachineStop() {
        super.stateMachineStop();
        saveTreeIdToFile();
        closeRedisLogger();
    }

    @Override
    public void stateMachineAbort() {
        super.stateMachineAbort();
        saveTreeIdToFile();
        closeRedisLogger();
    }

    @Override
    public Move stateMachineSelectMove(long xiTimeout)
            throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        // Находим текущий узел в дереве
        SearchTreeNode startRootNode = tree.findNode(getCurrentState());
        tree.cut(startRootNode);

        // Инициализируем Redis-логгер
        if (ENABLE_REDIS_LOGGING) {
            redisLogger = new AsyncMCTSRedisLogger(
                    getMatch().getMatchId(),
                    turnCount,
                    DEFAULT_LOGGING_FREQUENCY
            );

            // Сохраняем ID для последующего доступа
            currentTreeId = redisLogger.getTreeId();

            // Логируем начальное состояние дерева
            redisLogger.logTreeState(tree.toJSONbyJackson());
        }

        // Вычисляем время завершения с учетом безопасного запаса
        long finishBy = xiTimeout - SAFETY_MARGIN;
        int iterations = 0;

        // Выполняем поиск, пока не истечет время
        while (System.currentTimeMillis() < finishBy) {
            iterations++;
            tree.grow();

            // Логируем состояние дерева в Redis
            if (ENABLE_REDIS_LOGGING && redisLogger != null) {
                redisLogger.logTreeState(tree.toJSONbyJackson());
            }
        }

        // Выбираем лучший ход
        Move bestMove = tree.getBestAction(getRole());

        // Сохраняем ID дерева в файл
        saveTreeIdToFile();

        // Закрываем Redis-логгер
        closeRedisLogger();

        // Сохраняем состояние дерева в файл
        saveTreeToFile();

        // Уведомляем наблюдателей о событии дерева
        notifyObservers(new TreeEvent(tree, turnCount, false));
        turnCount++;

        return bestMove;
    }

    /**
     * Сохраняет ID текущего дерева в файл
     */
    private void saveTreeIdToFile() {
        if (currentTreeId == null) return;

        try {
            String dirPath = getMatchFolderString() + "/" +
                    this.getName() + "__" +
                    this.getRoleName().toString();

            File dir = new File(dirPath);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            File treeIdFile = new File(dirPath + "/tree_id.txt");
            try (FileWriter writer = new FileWriter(treeIdFile)) {
                writer.write(currentTreeId);
            }
        } catch (IOException e) {
            System.err.println("Ошибка при сохранении ID дерева: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Сохраняет состояние дерева в файл
     */
    private void saveTreeToFile() {
        String filePath = getMatchFolderString() + "/" +
                this.getName() + "__" +
                this.getRoleName().toString() +
                "/step_" + turnCount + ".json";

        try {
            File f = new File(filePath);
            if (f.exists()) f.delete();
            f.getParentFile().mkdirs();
            f.createNewFile();

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(f))) {
                bw.write(tree.toJSONbyJackson().toString());
                bw.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException("Ошибка при сохранении дерева", e);
        }
    }

    /**
     * Закрывает Redis-логгер
     */
    private void closeRedisLogger() {
        if (redisLogger != null) {
            redisLogger.close();
            redisLogger = null;
        }
    }

    @Override
    public LogInfoNode createLogInfoTree(Move selectedMove) {
        MachineState selectedNextState = null;
        try {
            selectedNextState = getStateMachine().getRandomNextState(
                    this.tree.getRoot().getState(),
                    this.getRole(),
                    selectedMove
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
        return makeLogTreeFromSearchTree(this.tree.getRoot(), selectedNextState);
    }

    /**
     * Создает узел логирования из узла поиска
     */
    public LogInfoNode makeLogTreeFromSearchTree(SearchTreeNode node, MachineState selectedNextState) {
        LogInfoNode result = this.createLogInfoNode(node.getState());

        result.tableData.add(Collections.singletonList("NUM VISITS"));
        result.tableData.add(Collections.singletonList(String.valueOf(node.getStatistics().getNumVisits())));

        // Добавляем статистику
        CumulativeStatistics statistics = node.getStatistics();
        for (Role role : statistics.getRoles()) {
            result.tableData.add(Collections.singletonList("ROLE :: " + role));

            List<String> actions = new ArrayList<>();
            List<String> actions_scores = new ArrayList<>();
            List<String> actions_num_uses = new ArrayList<>();
            actions.add("Actions");
            actions_scores.add("Scores");
            actions_num_uses.add("Uses num");

            for (Move action : statistics.getUsedActions(role)) {
                StatisticsForActions.ActionStatistics actionStatistics = statistics.get(role, action);
                actions.add(action.toString());
                actions_scores.add(String.valueOf(actionStatistics.getScore()));
                actions_num_uses.add(String.valueOf(actionStatistics.getNumUsed()));
            }

            if (actions.size() > 1) {
                result.tableData.add(actions);
                result.tableData.add(actions_scores);
                result.tableData.add(actions_num_uses);
            } else {
                result.tableData.add(Collections.singletonList("NO CALCULATION"));
            }
        }

        if (node.getState().equals(selectedNextState)) {
            result.tableData.add(Collections.singletonList("SELECTED STATE"));
        }

        for (SearchTreeNode child : node.getChildren()) {
            LogInfoNode childInfoNode = this.makeLogTreeFromSearchTree(child, selectedNextState);
            result.children.add(childInfoNode);
        }

        return result;
    }
}