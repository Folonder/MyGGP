package org.ggp.base.player.gamer.statemachine.mcts.model.strategy;

import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTreeNode;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;

import java.util.HashMap;
import java.util.Map;

public class PlayoutStrategy {

    /**
     * Выполняет симуляцию игры от заданного узла
     *
     * @param startNode Узел, от которого начинается симуляция
     * @return Вознаграждения для каждой роли
     */
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

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Performs a game simulation from the given node with depth tracking
     *
     * @param startNode Node from which to start simulation
     * @param depth Array to store simulation depth (depth will be written to depth[0])
     * @return Rewards for each role
     */
    public Map<Role, Double> executeWithDepth(SearchTreeNode startNode, int[] depth) {
        try {
            System.out.println("Playout: starting simulation from node " +
                    (startNode != null ? startNode.getState().toString().substring(0, 20) + "..." : "null"));

            if (startNode == null) {
                System.err.println("ERROR: Start node is null in executeWithDepth!");
                return new HashMap<>();
            }

            Map<Role, Double> scores = new HashMap<>();
            StateMachine gameModel = startNode.getGameModel();

            MachineState finalState = gameModel.performDepthCharge(startNode.getState(), depth);
            System.out.println("Playout: simulation completed, depth: " + depth[0]);

            for (Role r : gameModel.getRoles()) {
                int goal = gameModel.getGoal(finalState, r);
                scores.put(r, (double) goal);
                System.out.println("Playout: for role " + r + " received result: " + goal);
            }

            startNode.markPlayout();
            return scores;

        } catch (Exception e) {
            System.err.println("ERROR in executeWithDepth: " + e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }
}