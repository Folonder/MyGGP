package org.ggp.base.player.gamer.statemachine.mcts.model.strategy;

import org.ggp.base.player.gamer.statemachine.mcts.model.tree.JointActions;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTreeNode;
import org.ggp.base.player.gamer.statemachine.mcts.model.statistics.CumulativeStatistics;
import org.ggp.base.player.gamer.statemachine.mcts.model.statistics.StatisticsForActions;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class SelectionStrategy {

    private static final double EXPLORATION_BIAS = 0.4;
    private static final double FIRST_PLAY_URGENCY = 10;

    /**
     * Выполняет выбор узла дерева
     */
    public static SearchTreeNode execute(SearchTreeNode node) {
        if (node.isLeaf()) {
            return node;
        } else {
            JointActions jointBestActions = getJointBestActions(node);
            SearchTreeNode selectedChild = node.getChild(jointBestActions);

            // Продвигаемся в глубину
            return execute(selectedChild);
        }
    }

    /**
     * Performs node selection with path tracking
     *
     * @param node Current node
     * @param path List to store selection path
     * @return Selected node
     */
    public SearchTreeNode executeWithPath(SearchTreeNode node, List<SearchTreeNode> path) {
        try {
            System.out.println("Selection: processing node " + (node != null ? node.getState().toString().substring(0, 20) + "..." : "null"));

            if (node == null) {
                System.err.println("ERROR: Node is null in executeWithPath!");
                return null;
            }

            if (node.isLeaf()) {
                System.out.println("Selection: found leaf node");
                return node;
            } else {
                JointActions jointBestActions = getJointBestActions(node);
                SearchTreeNode selectedChild = node.getChild(jointBestActions);

                if (selectedChild == null) {
                    System.err.println("ERROR: Selected child node is null!");
                    return node;
                }

                // Add node to path
                path.add(selectedChild);
                System.out.println("Selection: added node to path, current path length: " + path.size());

                // Proceed deeper
                return executeWithPath(selectedChild, path);
            }
        } catch (Exception e) {
            System.err.println("ERROR in executeWithPath: " + e.getMessage());
            e.printStackTrace();
            return node;
        }
    }

    private static JointActions getJointBestActions(SearchTreeNode node) {
        List<Role> roles = node.getGameModel().getRoles();
        JointActions jointBestActions = new JointActions(roles);

        for (Role role : roles) {
            jointBestActions.put(role, getBestAction(node.getStatistics(), role) );
        }
        return jointBestActions;
    }

    private static Move getBestAction(CumulativeStatistics statistics, Role role) {

        List<Move> bestActions = new ArrayList<>();

        double bestActionScore = PoolOfStrategies.MIN_SCORE-1;
        for (Move action : statistics.getUsedActions(role)) {

            double actionScore = getExplorationScore(statistics, role, action) +
                    getExploitationScore(statistics, role, action);

            if (actionScore > bestActionScore) {
                bestActionScore = actionScore;
                bestActions.clear();
                bestActions.add(action);
            } else if(actionScore == bestActionScore) {
                bestActions.add(action);
            }
        }

        return PoolOfStrategies.randomElement(bestActions);
    }

    private static double getExplorationScore(CumulativeStatistics statistics, Role role, Move action) {
        StatisticsForActions.ActionStatistics item = statistics.get(role, action);
        if (item.getNumUsed() == 0) {
            return FIRST_PLAY_URGENCY + ThreadLocalRandom.current().nextDouble(0, 1);
        } else {
            int stateNumVisits = statistics.getNumVisits();
            return EXPLORATION_BIAS * Math.sqrt(2 * Math.log(stateNumVisits) / item.getNumUsed());
        }
    }

    private static double getExploitationScore(CumulativeStatistics statistics, Role role, Move action) {
        StatisticsForActions.ActionStatistics item = statistics.get(role, action);
        double actionScore = item.getScore();
        int actionNumUsed = item.getNumUsed();

        if (item.getNumUsed() == 0) {
            return 0;
        }

        return PoolOfStrategies.normalize(actionScore) / actionNumUsed;
    }
}