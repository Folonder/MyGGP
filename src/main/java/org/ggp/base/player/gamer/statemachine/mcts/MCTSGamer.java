package org.ggp.base.player.gamer.statemachine.mcts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ggp.base.player.gamer.statemachine.mcts.event.TreeEvent;
import org.ggp.base.player.gamer.statemachine.mcts.event.TreeStartEvent;
import org.ggp.base.player.gamer.statemachine.mcts.logger.MCTSRedisLogger;
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
    private static final int DEFAULT_LOGGING_FREQUENCY = 1;
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
        // ��������� ����������� ������ �� ������ ������ �����
        this.addObserver(new TreeObserver());
        notifyObservers(new TreeStartEvent());
    }

    @Override
    public void stateMachineStop() {
        super.stateMachineStop();
        // ����� �����������������, ���� ����� ���������� dot-����� ��� ���������
        // this.writeLogInfoToDotFile();
    }

    @Override
    public void stateMachineAbort() {
        super.stateMachineAbort();
        // ����� �����������������, ���� ����� ���������� dot-����� ��� ����������
        // this.writeLogInfoToDotFile();
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
                // ������� ������
                redisLogger = new MCTSRedisLogger(
                        getMatch().getMatchId(),
                        turnCount
                );
                currentTreeId = redisLogger.getTreeId();

                // ��������� ��������� ��������� � ���������
                JsonNode initialTree = tree.toJSONbyJackson();
                boolean saved = redisLogger.saveInitialState(initialTree);
                if (!saved) {
                    System.err.println("ERROR: Failed to save initial state for turn " + turnCount);

                    // ��������� ������� ����������
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

                // �������������� �������� ����� ����������
                if (!redisLogger.hasInitialState()) {
                    System.err.println("Initial state verification failed for turn " + turnCount);
                }
            }
        } catch (Exception e) {
            System.err.println("Error initializing Redis logger: " + e.getMessage());
            e.printStackTrace();
        }

        // �������� ����������� � ������ ���� (�� ������� �����)
        notifyObservers(new TreeEvent(tree, turnCount, true));

        long finishBy = timeout - SAFETY_MARGIN;
        int iterations = 0;

        while (System.currentTimeMillis() < finishBy) {
            iterations++;
            tree.grow();

            // �������� ��������� ������ X ��������
            if (ENABLE_REDIS_LOGGING && redisLogger != null && iterations % DEFAULT_LOGGING_FREQUENCY == 0) {
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

        // �������� ��������� ���������
        if (ENABLE_REDIS_LOGGING && redisLogger != null) {
            try {
                JsonNode finalTree = tree.toJSONbyJackson();
                redisLogger.logTreeState(finalTree);
            } catch (Exception e) {
                System.err.println("Error logging final tree state: " + e.getMessage());
            }
        }

        // ��������� ������ ������ � JSON-���� (�� ������� �����)
        saveTreeToJsonFile();

        // ��������� ID ������ ��� ����� � Redis
        saveTreeIdToFile();

        // �������� ����������� � ���������� ���� (�� ������� �����)
        notifyObservers(new TreeEvent(tree, turnCount, false));

        // ��������� �������� � �������� �������
        if (redisLogger != null) {
            try {
                if (!redisLogger.hasInitialState()) {
                    System.err.println("CRITICAL: Initial state missing before closing for turn " + turnCount);

                    // ��������� ������� ����������
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

    // ����� ��� ���������� ������ � JSON-���� (�� ������� �����)
    private void saveTreeToJsonFile() {
        String filePath = getMatchFolderString() + "/" + this.getName() + "__" + this.getRoleName().toString() + "/step_" + turnCount + ".json";
        File f = new File(filePath);
        if (f.exists()) f.delete();
        BufferedWriter bw = null;
        try {
            f.getParentFile().mkdirs();
            f.createNewFile();
            bw = new BufferedWriter(new FileWriter(f));
            bw.write(tree.toJSONbyJackson().toString());
            bw.flush();
            bw.close();
        } catch (IOException e) {
            System.err.println("Error saving tree to JSON file: " + e.getMessage());
        }
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

    // ����� �������� ���-������ �� ������� �����
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

    // ��������������� ����� ��� �������� ���-������ �� ������� �����
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