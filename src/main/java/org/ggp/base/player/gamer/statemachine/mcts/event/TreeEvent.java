package org.ggp.base.player.gamer.statemachine.mcts.event;

import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTree;
import org.ggp.base.util.observer.Event;

public class TreeEvent extends Event {

    private final SearchTree tree;
    private final int turnNumber;
    private final boolean isGrowthEvent;   // Indicates this is a tree growth event
    private final boolean isFinalTree;     // Indicates this is the final tree for a move

    /**
     * Creates a new TreeEvent
     *
     * @param tree The search tree
     * @param turnNumber The current turn number
     * @param isGrowthEvent Whether this is a tree growth event
     * @param isFinalTree Whether this is the final tree for a move
     */
    public TreeEvent(SearchTree tree, int turnNumber, boolean isGrowthEvent, boolean isFinalTree) {
        this.tree = tree;
        this.turnNumber = turnNumber;
        this.isGrowthEvent = isGrowthEvent;
        this.isFinalTree = isFinalTree;
    }

    /**
     * Backwards compatibility constructor
     *
     * @param tree The search tree
     * @param turnNumber The current turn number
     * @param onStartMove Whether this is at the start of a move
     */
    public TreeEvent(SearchTree tree, int turnNumber, boolean onStartMove) {
        this.tree = tree;
        this.turnNumber = turnNumber;
        this.isGrowthEvent = false;
        this.isFinalTree = true;
    }

    public SearchTree getTree() {
        return tree;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public boolean isGrowthEvent() {
        return isGrowthEvent;
    }

    public boolean isFinalTree() {
        return isFinalTree;
    }

    /**
     * For backward compatibility
     */
    public boolean getOnStartMove() {
        return isGrowthEvent;
    }
}