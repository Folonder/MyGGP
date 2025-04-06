package org.ggp.base.player.gamer.statemachine.mcts.model.strategy;

import org.ggp.base.player.gamer.statemachine.mcts.model.tree.JointActions;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTreeNode;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;

import java.util.ArrayList;
import java.util.List;

public class ExpansionStrategy {

    /**
     * Расширяет узел дерева и возвращает случайный дочерний узел
     */
    public static SearchTreeNode execute(SearchTreeNode node) throws MoveDefinitionException {
        if (!isNodeNeedExpanded(node)) {
            return node;
        }

        List<List<Move>> jointMoves = node.getGameModel().getLegalJointMoves(node.getState());
        for (List<Move> jointMove : jointMoves) {
            node.createChild(new JointActions(node.getGameModel().getRoles(), jointMove));
            node.getStatistics().addUsedActions(new JointActions(node.getGameModel().getRoles(), jointMove));
        }

        return PoolOfStrategies.randomElement(node.getChildren());
    }

    /**
     * Expands a tree node and returns a random child node with tracking of created nodes
     *
     * @param node Node to expand
     * @param expandedNodes List to store created nodes
     * @return Selected node for simulation
     */
    public SearchTreeNode executeWithTracking(SearchTreeNode node, List<SearchTreeNode> expandedNodes) throws MoveDefinitionException {
        try {
            System.out.println("Expansion: processing node " + (node != null ? node.getState().toString().substring(0, 20) + "..." : "null"));

            if (node == null) {
                System.err.println("ERROR: Node is null in executeWithTracking!");
                return null;
            }

            if (!isNodeNeedExpanded(node)) {
                System.out.println("Expansion: node doesn't need expansion, returning it");
                return node;
            }

            // List to store created nodes
            List<SearchTreeNode> newNodes = new ArrayList<>();

            List<List<Move>> jointMoves = node.getGameModel().getLegalJointMoves(node.getState());
            System.out.println("Expansion: found " + jointMoves.size() + " possible moves");

            for (List<Move> jointMove : jointMoves) {
                JointActions actions = new JointActions(node.getGameModel().getRoles(), jointMove);
                SearchTreeNode childNode = node.createChild(actions);
                node.getStatistics().addUsedActions(actions);
                newNodes.add(childNode);
            }

            // Add created nodes to tracking list
            expandedNodes.addAll(newNodes);
            System.out.println("Expansion: created " + newNodes.size() + " new nodes");

            // If no new nodes were created, return the original node
            if (newNodes.isEmpty()) {
                System.out.println("Expansion: no new nodes created, returning original");
                return node;
            }

            // Return random node from new ones
            SearchTreeNode selectedNode = PoolOfStrategies.randomElement(newNodes);
            System.out.println("Expansion: selected random child node for playout");
            return selectedNode;
        } catch (Exception e) {
            System.err.println("ERROR in executeWithTracking: " + e.getMessage());
            e.printStackTrace();
            return node;
        }
    }

    private static boolean isNodeNeedExpanded(SearchTreeNode node) {
        // Узел уже симулировали и он НЕ терминальный, или он корневой
        return node.isRoot() || !node.isTerminal() && node.isPlayout();
    }
}