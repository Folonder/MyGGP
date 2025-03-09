package org.ggp.base.player.gamer.statemachine.mcts.event;

import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTree;
import org.ggp.base.util.observer.Event;

public class TreeEvent extends Event {

    private final SearchTree tree;
    private final int turnNumber;
    private final boolean onStartMove;

    public TreeEvent(SearchTree tree, int turnNumber, boolean onStartMove) {
        this.tree = tree;
        this.turnNumber = turnNumber;
        this.onStartMove = onStartMove;
    }

    public SearchTree getTree() {
        return tree;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public boolean getOnStartMove() { return onStartMove; }
}
