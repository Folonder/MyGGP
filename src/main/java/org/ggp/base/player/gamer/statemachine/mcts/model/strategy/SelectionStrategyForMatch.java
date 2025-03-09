package org.ggp.base.player.gamer.statemachine.mcts.model.strategy;

import org.ggp.base.player.gamer.statemachine.mcts.model.statistics.CumulativeStatistics;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTreeNode;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;

import java.util.ArrayList;
import java.util.List;

public class SelectionStrategyForMatch {

    public Move execute(SearchTreeNode node, Role role) {

        CumulativeStatistics statistics = node.getStatistics();

        if (statistics.isEmpty()) {
            return null;
        }

        List<Move> bestActions = new ArrayList<>();

        double bestActionScore = PoolOfStrategies.MIN_SCORE - 1;
        for (Move action : statistics.getUsedActions(role)) {

            double actionScore = evaluateActionScore(statistics, role, action);

            if (actionScore > bestActionScore) {
                bestActionScore = actionScore;
                bestActions.clear();
                bestActions.add(action);

            } else if (actionScore == bestActionScore) {
                bestActions.add(action);
            }
        }
        return PoolOfStrategies.randomElement(bestActions);
    }

    private double evaluateActionScore(CumulativeStatistics statistics, Role role, Move action) {
        return statistics.get(role, action).getNumUsed();
    }
}
