package org.ggp.base.player.gamer.statemachine.mcts.model.strategy;

import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTreeNode;
import org.ggp.base.util.statemachine.Role;

import java.util.Map;

public class PropagationStrategy {
    public static void execute(SearchTreeNode node, Map<Role, Double> playoutScore) {
        if (!node.isRoot()) {
            node.getParent().getStatistics().updateUsedActions(node.getPrecedingJointMove(), playoutScore);
            node.getParent().getStatistics().incNumVisits();
            execute(node.getParent(), playoutScore);
        }
    }
}
