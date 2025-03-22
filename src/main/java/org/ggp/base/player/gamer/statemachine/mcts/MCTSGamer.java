package org.ggp.base.player.gamer.statemachine.mcts;

import org.ggp.base.player.gamer.statemachine.mcts.event.TreeEvent;
import org.ggp.base.player.gamer.statemachine.mcts.event.TreeStartEvent;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MCTSGamer extends SampleGamer {
    private final long SAFETY_MARGIN = 2000;

    // Progressive logging configuration
    private final boolean ENABLE_GROWTH_LOGGING = true; // ��������/��������� ����������� �����

    // ��������� ���������� �����������
    private final int[] ITERATIONS_FIRST_10 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10}; // ���������� ������ �� ������ 10 ��������
    private final int[] ITERATIONS_FIRST_100 = {15, 20, 25, 30, 40, 50, 60, 70, 80, 90, 100}; // ����� ������ ����� �� 100
    private final double PROGRESSION_FACTOR = 1.3; // ������ ��������� ����� ����������� � ~1.3 ���� ������ ����������
    private final int MAX_LOG_INTERVAL = 5000; // ������������ �������� ����� ������
    private final int MAX_LOG_POINTS = 100; // ������������ ����� ����� ����������� ��� ������ ����

    private SearchTree tree = null;
    private int turnCount = 0;
    private int growthLogCount = 0;

    // ��������� ��� �������� ��������, ������� ����� ����������
    private Set<Integer> iterationsToLog = new HashSet<>();

    @Override
    public void stateMachineMetaGame(long xiTimeout)
            throws TransitionDefinitionException, MoveDefinitionException,
            GoalDefinitionException {
        tree = new SearchTree(getStateMachine());
        turnCount = 0;
        growthLogCount = 0;
        this.addObserver(new TreeObserver());
        notifyObservers(new TreeStartEvent());
    }

    @Override
    public void stateMachineStop() {
        super.stateMachineStop();
        // ���������� ��������� ��������� ������
        notifyObservers(new TreeEvent(tree, turnCount, false, true));
    }

    @Override
    public void stateMachineAbort() {
        super.stateMachineAbort();
        // ���������� ��������� ��������� ������ ��� ����������
        notifyObservers(new TreeEvent(tree, turnCount, false, true));
    }

    /**
     * ���������� ����� ������� �������� ��� ����������� � �������������� ������������� �����
     * @param estimatedTotalIterations ��������� ����� ����� ��������
     */
    private void generateProgressiveLoggingPoints(int estimatedTotalIterations) {
        iterationsToLog.clear();

        // ������ ���������� �������� 0 (��������� ���������)
        iterationsToLog.add(0);

        // �������� ��� ������ �������� (1-10)
        for (int early : ITERATIONS_FIRST_10) {
            iterationsToLog.add(early);
        }

        // �������� ������������� �������� (�� 100)
        for (int mid : ITERATIONS_FIRST_100) {
            iterationsToLog.add(mid);
        }

        // ������������ ������������� ����� �����������
        double nextPoint = 100; // �������� �� 100
        List<Integer> generatedPoints = new ArrayList<>();

        while (nextPoint < estimatedTotalIterations) {
            nextPoint = Math.ceil(nextPoint * PROGRESSION_FACTOR);

            // ����������, ����� �� ���������� ������������ ��������
            int lastPoint = iterationsToLog.stream().mapToInt(i -> i).max().orElse(0);
            if (nextPoint - lastPoint > MAX_LOG_INTERVAL) {
                nextPoint = lastPoint + MAX_LOG_INTERVAL;
            }

            generatedPoints.add((int)nextPoint);

            // �������� ������������ - ���� �� ������������ � ����� ������� ������, ������������
            if (nextPoint > Integer.MAX_VALUE / 2) break;

            // ���� ������������� ������� ����� �����, ������������
            if (generatedPoints.size() >= MAX_LOG_POINTS -
                    ITERATIONS_FIRST_10.length - ITERATIONS_FIRST_100.length - 1) { // -1 ��� �������� 0
                break;
            }
        }

        // ���� � ��� ������� ����� �����, ������������� �� ����������
        if (generatedPoints.size() > MAX_LOG_POINTS - ITERATIONS_FIRST_10.length - ITERATIONS_FIRST_100.length - 1) {
            // ������� ������������ ����� ����������
            int available = MAX_LOG_POINTS - ITERATIONS_FIRST_10.length - ITERATIONS_FIRST_100.length - 1;
            int step = generatedPoints.size() / available;

            for (int i = 0; i < generatedPoints.size(); i += step) {
                if (i < generatedPoints.size()) {
                    iterationsToLog.add(generatedPoints.get(i));
                }
            }

            // ������ ��������� ��������� �����
            if (!generatedPoints.isEmpty()) {
                iterationsToLog.add(generatedPoints.get(generatedPoints.size() - 1));
            }
        } else {
            // ���� ����� �������, �������� �� ���
            iterationsToLog.addAll(generatedPoints);
        }

        // ����� �������� �������� �������������� ��������� ��������
        iterationsToLog.add(estimatedTotalIterations);

        // ���������� � ����� ��� �������
        Integer[] logPoints = iterationsToLog.toArray(new Integer[0]);
        Arrays.sort(logPoints);
        System.out.println("������������� " + logPoints.length + " ����� �����������: " + Arrays.toString(logPoints));
    }

    public Move stateMachineSelectMove(long xiTimeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        long start = System.currentTimeMillis();

        SearchTreeNode startRootNode = tree.findNode(getCurrentState());
        // Perform tree cutting
        tree.cut(startRootNode);

        long finishBy = xiTimeout - SAFETY_MARGIN;
        long availableTime = finishBy - start;

        // ����� ������� ���������� ��������, ������� �� ������ ���������
        // �� ������ ����������� ����� (~20000 �������� � �������)
        int estimatedIterations = (int)(availableTime * 20000 / 1000);
        System.out.println("��������� ���������� ��������: " + estimatedIterations);

        // ������������ ������������� ����� �����������
        generateProgressiveLoggingPoints(estimatedIterations);

        int iterations = 0;
        growthLogCount = 0;

        // ���������� ��������� ��������� ������ (�������� 0) �� ������ �����
        if (ENABLE_GROWTH_LOGGING) {
            logTreeGrowth(0);
            notifyObservers(new TreeEvent(tree, turnCount, true, false));
        }

        while (System.currentTimeMillis() < finishBy) {
            iterations++;
            tree.grow();

            // ���������� ���� ������ �� ������������� �����
            if (ENABLE_GROWTH_LOGGING && iterationsToLog.contains(iterations)) {
                growthLogCount++;
                // ���������� ������� ��������� ������
                logTreeGrowth(iterations);

                // ��������� ������������ � ����� ������
                notifyObservers(new TreeEvent(tree, turnCount, true, false));

                // ������ 10 �����, �������� ��������
                if (growthLogCount % 10 == 0) {
                    System.out.println("��������: ������������ " + growthLogCount + " ���������, �������� " + iterations);
                }
            }
        }

        System.out.println("��������� " + iterations + " ��������, ������������ " + growthLogCount + " ��������� ������");
        Move bestMove = tree.getBestAction(getRole());

        // ��������� JSON-���� ��� ����� �����
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
            throw new RuntimeException(e);
        }

        // ��������� ������������ ����� ���������� ������ ���� � ��������� �������
        notifyObservers(new TreeEvent(tree, turnCount, false, true));
        turnCount++;

        return bestMove;
    }

    /**
     * �������� ���� ������ � ���� �� ����� ���������� ��������� MCTS
     * @param iterations ������� ���������� ��������
     */
    private void logTreeGrowth(int iterations) {
        String growthLogPath = getMatchFolderString() + "/" + this.getName() + "__" + this.getRoleName().toString()
                + "/growth_" + turnCount + "_iter" + iterations + ".json";
        File growthFile = new File(growthLogPath);

        try {
            growthFile.getParentFile().mkdirs();
            if (growthFile.exists()) growthFile.delete();
            growthFile.createNewFile();

            BufferedWriter writer = new BufferedWriter(new FileWriter(growthFile));
            writer.write(tree.toJSONbyJackson().toString());
            writer.flush();
            writer.close();
        } catch (IOException e) {
            // ���������� ������, �� �� ������������� ��������
            System.err.println("������ ��� ����������� ����� ������: " + e.getMessage());
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