package org.ggp.base.player.gamer.statemachine.mcts.model.strategy;

import org.ggp.base.player.gamer.statemachine.mcts.model.tree.JointActions;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTreeNode;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;

import java.util.List;

public class ExpansionStrategy {

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

    private static boolean isNodeNeedExpanded(SearchTreeNode node) {
        // Узел уже симулировали и он НЕ терминальный, или он корневой
        return node.isRoot() || !node.isTerminal() && node.isPlayout();
    }
}
