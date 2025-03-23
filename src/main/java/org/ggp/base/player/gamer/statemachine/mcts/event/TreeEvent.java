package org.ggp.base.player.gamer.statemachine.mcts.event;

import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTree;
import org.ggp.base.util.observer.Event;

public class TreeEvent extends Event {

    private final SearchTree tree;
    private final int turnNumber;
    private final boolean isGrowthEvent;   // Indicates this is a tree growth event
    private final boolean isFinalTree;     // Indicates this is the final tree for a move
    private final boolean isGameOver;      // Indicates the game is over (stateMachineStop/Abort)

    /**
     * Creates a new TreeEvent
     *
     * @param tree The search tree
     * @param turnNumber The current turn number
     * @param isGrowthEvent Whether this is a tree growth event
     * @param isFinalTree Whether this is the final tree for a move
     * @param isGameOver Whether the game is over
     */
    public TreeEvent(SearchTree tree, int turnNumber, boolean isGrowthEvent, boolean isFinalTree, boolean isGameOver) {
        this.tree = tree;
        this.turnNumber = turnNumber;
        this.isGrowthEvent = isGrowthEvent;
        this.isFinalTree = isFinalTree;
        this.isGameOver = isGameOver;
    }

    /**
     * Creates a new TreeEvent with isGameOver set to false
     *
     * @param tree The search tree
     * @param turnNumber The current turn number
     * @param isGrowthEvent Whether this is a tree growth event
     * @param isFinalTree Whether this is the final tree for a move
     */
    public TreeEvent(SearchTree tree, int turnNumber, boolean isGrowthEvent, boolean isFinalTree) {
        this(tree, turnNumber, isGrowthEvent, isFinalTree, false);
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
        this.isGameOver = false;
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

    public boolean isGameOver() {
        return isGameOver;
    }

    /**
     * For backward compatibility
     */
    public boolean getOnStartMove() {
        return isGrowthEvent;
    }
}