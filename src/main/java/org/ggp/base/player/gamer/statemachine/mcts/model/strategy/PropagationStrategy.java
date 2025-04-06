package org.ggp.base.player.gamer.statemachine.mcts.model.strategy;

import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTreeNode;
import org.ggp.base.util.statemachine.Role;

import java.util.List;
import java.util.Map;

public class PropagationStrategy {

    /**
     * ¬ыполн€ет распространение результатов симул€ции вверх по дереву
     *
     * @param node ”зел, от которого начинаетс€ распространение
     * @param playoutScore –езультаты симул€ции
     */
    public static void execute(SearchTreeNode node, Map<Role, Double> playoutScore) {
        if (!node.isRoot()) {
            node.getParent().getStatistics().updateUsedActions(node.getPrecedingJointMove(), playoutScore);
            node.getParent().getStatistics().incNumVisits();
            execute(node.getParent(), playoutScore);
        }
    }

    /**
     * Performs backpropagation of simulation results up the tree with path tracking
     *
     * @param node The node from which to start backpropagation
     * @param playoutScore Simulation results
     * @param path List to store the backpropagation path
     */
    public void executeWithTracking(SearchTreeNode node, Map<Role, Double> playoutScore, List<SearchTreeNode> path) {
        try {
            System.out.println("Backpropagation: processing node " + (node != null ? node.getState().toString().substring(0, 20) + "..." : "null"));

            // Add current node to path
            if (node != null) {
                path.add(node);
            } else {
                System.err.println("ERROR: Node is null in executeWithTracking!");
                return;
            }

            if (!node.isRoot()) {
                // Update parent node statistics
                SearchTreeNode parent = node.getParent();
                if (parent != null) {
                    parent.getStatistics().updateUsedActions(node.getPrecedingJointMove(), playoutScore);
                    parent.getStatistics().incNumVisits();

                    // Recursively propagate results further up the tree
                    executeWithTracking(parent, playoutScore, path);
                } else {
                    System.err.println("ERROR: Parent is null, but node is not root!");
                }
            } else {
                System.out.println("Backpropagation: reached root node");
            }
        } catch (Exception e) {
            System.err.println("ERROR in executeWithTracking: " + e.getMessage());
            e.printStackTrace();
        }
    }
}