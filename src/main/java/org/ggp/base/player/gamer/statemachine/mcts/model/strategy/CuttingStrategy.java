package org.ggp.base.player.gamer.statemachine.mcts.model.strategy;

import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTree;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTreeNode;

public class CuttingStrategy {
    public static void execute(SearchTree tree, SearchTreeNode startRootNode) {
        tree.setRoot(startRootNode);
    }
}
