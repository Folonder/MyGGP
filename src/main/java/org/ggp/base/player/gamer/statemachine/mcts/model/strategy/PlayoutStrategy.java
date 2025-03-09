package org.ggp.base.player.gamer.statemachine.mcts.model.strategy;

import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTreeNode;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;

import java.util.HashMap;
import java.util.Map;

public class PlayoutStrategy {

    // Возвращаемое значение - полученные выигрыши для каждой роли
    public Map<Role, Double> execute(SearchTreeNode startNode) {
        Map<Role, Double> scores = new HashMap<>();
        StateMachine gameModel = startNode.getGameModel();
        int[] depth = new int[1];

        try {
            MachineState finalState = gameModel.performDepthCharge(startNode.getState(), depth);
            for (Role r : gameModel.getRoles()) {
                scores.put(r, (double) gameModel.getGoal(finalState, r));
            }

            startNode.markPlayout();
            return scores;

        } catch (Exception e) { //// ????!!!! подумать, что делать
            throw new RuntimeException(e);
        }
    }
}
