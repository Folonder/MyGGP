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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MCTSGamer extends SampleGamer {
    private final long SAFETY_MARGIN = 2000;

    // Progressive logging configuration
    private final boolean ENABLE_GROWTH_LOGGING = true;

    // Logging progression settings
    private final int[] ITERATIONS_FIRST_10 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
    private final int[] ITERATIONS_FIRST_100 = {15, 20, 25, 30, 40, 50, 60, 70, 80, 90, 100};
    private final double PROGRESSION_FACTOR = 1.3;
    private final int MAX_LOG_INTERVAL = 5000;
    private final int MAX_LOG_POINTS = 100;

    private SearchTree tree = null;
    private int turnCount = 0;
    private int growthLogCount = 0;
    private String sessionIdentifier;

    // Observer for tree events
    private TreeObserver treeObserver;

    // Set for storing iterations to log
    private Set<Integer> iterationsToLog = new HashSet<>();

    @Override
    public void stateMachineMetaGame(long xiTimeout)
            throws TransitionDefinitionException, MoveDefinitionException,
            GoalDefinitionException {
        tree = new SearchTree(getStateMachine());
        turnCount = 0;
        growthLogCount = 0;

        // Create a unique session identifier in the format "yyyyMMdd_HHmmss_UUID"
        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss");
        sessionIdentifier = formatter.format(new java.util.Date()) + "_" +
                Math.abs(java.util.UUID.randomUUID().getMostSignificantBits());

        System.out.println("Created session ID: " + sessionIdentifier);

        // Add observer for tree events - this handles Redis storage
        treeObserver = new TreeObserver(sessionIdentifier);
        this.addObserver(treeObserver);
        notifyObservers(new TreeStartEvent());
    }

    @Override
    public void stateMachineStop() {
        super.stateMachineStop();
        // Log final state of the tree with game over flag
        notifyObservers(new TreeEvent(tree, turnCount, false, true, true));

        // Clean up
        treeObserver.shutdown();
    }

    @Override
    public void stateMachineAbort() {
        super.stateMachineAbort();
        // Log final state of the tree on abort with game over flag
        notifyObservers(new TreeEvent(tree, turnCount, false, true, true));

        // Clean up
        treeObserver.shutdown();
    }

    /**
     * Generate set of iteration numbers to log using a progressive scheme
     * @param estimatedTotalIterations Estimated total number of iterations
     */
    private void generateProgressiveLoggingPoints(int estimatedTotalIterations) {
        iterationsToLog.clear();

        // Always log iteration 0 (initial state)
        iterationsToLog.add(0);

        // Add all early iterations (1-10)
        for (int early : ITERATIONS_FIRST_10) {
            iterationsToLog.add(early);
        }

        // Add intermediate iterations (up to 100)
        for (int mid : ITERATIONS_FIRST_100) {
            iterationsToLog.add(mid);
        }

        // Generate progressive logging points
        double nextPoint = 100; // Start from 100
        List<Integer> generatedPoints = new ArrayList<>();

        while (nextPoint < estimatedTotalIterations) {
            nextPoint = Math.ceil(nextPoint * PROGRESSION_FACTOR);

            // Ensure maximum interval is not exceeded
            int lastPoint = iterationsToLog.stream().mapToInt(i -> i).max().orElse(0);
            if (nextPoint - lastPoint > MAX_LOG_INTERVAL) {
                nextPoint = lastPoint + MAX_LOG_INTERVAL;
            }

            generatedPoints.add((int)nextPoint);

            // Safety check - if approaching very large numbers, stop
            if (nextPoint > Integer.MAX_VALUE / 2) break;

            // If generated too many points, stop
            if (generatedPoints.size() >= MAX_LOG_POINTS -
                    ITERATIONS_FIRST_10.length - ITERATIONS_FIRST_100.length - 1) { // -1 for iteration 0
                break;
            }
        }

        // If too many points, filter them evenly
        if (generatedPoints.size() > MAX_LOG_POINTS - ITERATIONS_FIRST_10.length - ITERATIONS_FIRST_100.length - 1) {
            // Select subset of points evenly
            int available = MAX_LOG_POINTS - ITERATIONS_FIRST_10.length - ITERATIONS_FIRST_100.length - 1;
            int step = generatedPoints.size() / available;

            for (int i = 0; i < generatedPoints.size(); i += step) {
                if (i < generatedPoints.size()) {
                    iterationsToLog.add(generatedPoints.get(i));
                }
            }

            // Always add the last point
            if (!generatedPoints.isEmpty()) {
                iterationsToLog.add(generatedPoints.get(generatedPoints.size() - 1));
            }
        } else {
            // If not too many points, add them all
            iterationsToLog.addAll(generatedPoints);
        }

        // Also add approximately estimated last iteration
        iterationsToLog.add(estimatedTotalIterations);

        // Sort and output for debugging
        Integer[] logPoints = iterationsToLog.toArray(new Integer[0]);
        Arrays.sort(logPoints);
        System.out.println("Generated " + logPoints.length + " logging points: " + Arrays.toString(logPoints));
    }

    public Move stateMachineSelectMove(long xiTimeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        long start = System.currentTimeMillis();

        SearchTreeNode startRootNode = tree.findNode(getCurrentState());
        // Perform tree cutting
        tree.cut(startRootNode);

        long finishBy = xiTimeout - SAFETY_MARGIN;
        long availableTime = finishBy - start;

        // Roughly estimate number of iterations we can perform
        // based on previous experience (~20000 iterations per second)
        int estimatedIterations = (int)(availableTime * 20000 / 1000);
        System.out.println("Estimated iterations: " + estimatedIterations);

        // Generate progressive logging points
        generateProgressiveLoggingPoints(estimatedIterations);

        int iterations = 0;
        growthLogCount = 0;

        // Log initial tree state (iteration 0) before any growth
        if (ENABLE_GROWTH_LOGGING) {
            notifyObservers(new TreeEvent(tree, turnCount, true, false));
        }

        while (System.currentTimeMillis() < finishBy) {
            iterations++;
            tree.grow();

            // Log tree growth according to progressive scheme
            if (ENABLE_GROWTH_LOGGING && iterationsToLog.contains(iterations)) {
                growthLogCount++;

                // Notify observers about tree growth
                notifyObservers(new TreeEvent(tree, turnCount, true, false));

                // Every 10 logs, output progress
                if (growthLogCount % 10 == 0) {
                    System.out.println("Progress: logged " + growthLogCount + " states, iteration " + iterations);
                }
            }
        }

        System.out.println("Completed " + iterations + " iterations, logged " + growthLogCount + " tree states");
        Move bestMove = tree.getBestAction(getRole());

        // Notify observers after completing move selection with final tree
        notifyObservers(new TreeEvent(tree, turnCount, false, true));
        turnCount++;

        return bestMove;
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