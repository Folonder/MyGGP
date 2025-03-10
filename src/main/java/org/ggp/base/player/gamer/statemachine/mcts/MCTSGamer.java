package org.ggp.base.player.gamer.statemachine.mcts;

import org.ggp.base.player.gamer.statemachine.mcts.event.TreeEvent;
import org.ggp.base.player.gamer.statemachine.mcts.event.TreeStartEvent;
import org.ggp.base.player.gamer.statemachine.mcts.logger.MCTSTreeLogger;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTree;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTreeNode;
import org.ggp.base.player.gamer.statemachine.mcts.model.statistics.CumulativeStatistics;
import org.ggp.base.player.gamer.statemachine.mcts.model.statistics.StatisticsForActions;
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
    private final long SAFETY_MARGIN = 2000;

    // Конфигурация логирования
    private static final int DEFAULT_LOGGING_FREQUENCY = 1000; // Логировать каждые 1000 итераций
    private static final boolean ENABLE_MCTS_LOGGING = true;  // Флаг для включения/выключения логирования

    private SearchTree tree = null;
    private int turnCount = 0;
    private MCTSTreeLogger treeLogger;

    @Override
    public void stateMachineMetaGame(long xiTimeout)
            throws TransitionDefinitionException, MoveDefinitionException,
            GoalDefinitionException {
        tree = new SearchTree(getStateMachine());
        turnCount = 0;
        this.addObserver(new TreeObserver());
        notifyObservers(new TreeStartEvent());
    }

    @Override
    public void stateMachineStop() {
        super.stateMachineStop();
    }

    @Override
    public void stateMachineAbort() {
        super.stateMachineAbort();
    }

    @Override
    public Move stateMachineSelectMove(long xiTimeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        SearchTreeNode startRootNode = tree.findNode(getCurrentState());
        // Выполняем обрезку дерева
        tree.cut(startRootNode);

        // Инициализируем логгер для текущего хода
        if (ENABLE_MCTS_LOGGING) {
            String logDir = getMatchFolderString() + "/" + this.getName() + "__" + this.getRoleName().toString() + "/mcts_logs";
            treeLogger = new MCTSTreeLogger(getMatch().getMatchId(), turnCount, logDir, DEFAULT_LOGGING_FREQUENCY);

            // Логируем начальное состояние дерева
            treeLogger.logTreeState(tree.toJSONbyJackson());
        }

        long finishBy = xiTimeout - SAFETY_MARGIN;
        int iterations = 0;
        while (System.currentTimeMillis() < finishBy) {
            iterations++;
            tree.grow();

            // Логируем текущее состояние дерева после итерации
            if (ENABLE_MCTS_LOGGING) {
                treeLogger.logTreeState(tree.toJSONbyJackson());
            }
        }

        Move bestMove = tree.getBestAction(getRole());

        // Сохраняем JSON файл с итоговым деревом для этого хода
        saveTreeToJson(tree);

        notifyObservers(new TreeEvent(tree, turnCount, false));
        turnCount++;

        return bestMove;
    }

    /**
     * Сохраняет дерево в JSON файл
     */
    private void saveTreeToJson(SearchTree tree) {
        String filePath = getMatchFolderString() + "/" + this.getName() + "__" + this.getRoleName().toString() + "/step_" + turnCount + ".json";
        File f = new File(filePath);
        if (f.exists()) f.delete();
        try {
            f.getParentFile().mkdirs();
            f.createNewFile();

            // Используем ObjectMapper из Jackson для записи дерева в JSON файл
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            mapper.writeValue(f, tree.toJSONbyJackson());
        } catch (IOException e) {
            System.err.println("Ошибка при сохранении дерева в JSON: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public LogInfoNode createLogInfoTree(Move selectedMove) {
        MachineState selectedNextState = null;
        try {
            selectedNextState = getStateMachine().getRandomNextState(this.tree.getRoot().getState(), this.getRole(), selectedMove);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return makeLogTreeFromSearchTree(this.tree.getRoot(), selectedNextState);
    }

    public LogInfoNode makeLogTreeFromSearchTree(SearchTreeNode node, MachineState selectedNextState) {
        LogInfoNode result = this.createLogInfoNode(node.getState());

        result.tableData.add(Collections.singletonList("NUM VISITS"));
        result.tableData.add(Collections.singletonList(String.valueOf(node.getStatistics().getNumVisits())));

        // Insert additional statistic data
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
            }
            else {
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
