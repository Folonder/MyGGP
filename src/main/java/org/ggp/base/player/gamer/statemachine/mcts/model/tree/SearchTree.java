package org.ggp.base.player.gamer.statemachine.mcts.model.tree;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ggp.base.player.gamer.statemachine.mcts.MCTSIterationTracer;
import org.ggp.base.player.gamer.statemachine.mcts.event.IterationEvent;
import org.ggp.base.player.gamer.statemachine.mcts.model.strategy.PoolOfStrategies;
import org.ggp.base.player.gamer.statemachine.mcts.utils.MCTSStagesTracker;
import org.ggp.base.util.observer.Event;
import org.ggp.base.util.observer.Observer;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class SearchTree {
    private final transient StateMachine gameModel;
    private final transient PoolOfStrategies strategies = new PoolOfStrategies();
    private SearchTreeNode root;
    private List<Observer> observers = new ArrayList<>();
    private int iterationCounter = 0;
    private int turnNumber = 0;
    private MCTSStagesTracker currentStagesTracker;
    private String sessionId;
    private MCTSIterationTracer tracer;

    public SearchTree(StateMachine gameModel) {
        this.gameModel = gameModel;
        MachineState rootState = gameModel.getInitialState();
        root = new SearchTreeNode(this, rootState, null);

        // Generate a session ID for this tree
        java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss");
        sessionId = "tree_" + formatter.format(new java.util.Date()) + "_" +
                Math.abs(java.util.UUID.randomUUID().getMostSignificantBits());

        // Create a tracer that will be used for debug logs
        tracer = new MCTSIterationTracer(sessionId);

        // Initialize the stages tracker for the first iteration
        resetStagesTracker();
    }

    /**
     * Gets the session ID for this tree
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Sets a specific session ID for this tree
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
        // Update tracer with new session ID
        tracer = new MCTSIterationTracer(sessionId);
    }

    /**
     * Reset the stages tracker for a new iteration
     */
    private void resetStagesTracker() {
        currentStagesTracker = new MCTSStagesTracker(iterationCounter);
    }

    /**
     * Adds an observer of tree events
     */
    public void addObserver(Observer observer) {
        observers.add(observer);
    }

    /**
     * Notifies observers of an event
     */
    private void notifyObservers(Event event) {
        for (Observer observer : observers) {
            observer.observe(event);
        }
    }

    public SearchTreeNode findNode(MachineState state) {
        return root.findNodeInSubTree(state);
    }

    public void cut(SearchTreeNode startRootNode) {
        getStrategies().getCuttingStrategy().execute(this, startRootNode);
    }

    /**
     * Performs one iteration of MCTS tree growth with logging of all stages
     */
    public void grow() throws MoveDefinitionException {
        try {
            // IMPORTANT: We're creating a fresh stages tracker with the current iteration number
            // before any algorithm steps
            resetStagesTracker();

            // Initialize iteration event with the exact same number
            IterationEvent iterationEvent = new IterationEvent(iterationCounter, turnNumber);

            // List for collecting selection path
            List<SearchTreeNode> selectionPath = new LinkedList<>();
            selectionPath.add(root);

            // 1. SELECTION: Choose node with highest score
            System.out.println("\n=== MCTS Iteration #" + iterationCounter + " - SELECTION ===");
            SearchTreeNode selectedNode = getStrategies().getSelectionStrategy().executeWithPath(root, selectionPath);
            iterationEvent.addSelectionStage(selectionPath, selectedNode);

            // Record selection data
            currentStagesTracker.recordSelection(selectionPath, selectedNode);
            tracer.traceSelection(this, selectionPath, selectedNode);

            // 2. EXPANSION: Expand selected node and choose one of the children
            System.out.println("\n=== MCTS Iteration #" + iterationCounter + " - EXPANSION ===");
            List<SearchTreeNode> expandedNodes = new ArrayList<>();
            SearchTreeNode nodeForPlayout = getStrategies().getExpansionStrategy().executeWithTracking(selectedNode, expandedNodes);
            iterationEvent.addExpansionStage(selectedNode, expandedNodes, nodeForPlayout);

            // Record expansion data
            currentStagesTracker.recordExpansion(selectedNode, expandedNodes, nodeForPlayout);
            tracer.traceExpansion(selectedNode, expandedNodes, nodeForPlayout);

            // 3. PLAYOUT/SIMULATION: Game simulation from selected node
            System.out.println("\n=== MCTS Iteration #" + iterationCounter + " - PLAYOUT ===");
            int[] depth = new int[1]; // For tracking playout depth
            Map<Role, Double> playoutScore = getStrategies().getPlayoutStrategy().executeWithDepth(nodeForPlayout, depth);
            iterationEvent.addPlayoutStage(nodeForPlayout, depth[0], playoutScore);

            // Record playout data
            currentStagesTracker.recordPlayout(nodeForPlayout, depth[0], playoutScore);
            tracer.tracePlayout(nodeForPlayout, depth[0], playoutScore);

            // 4. BACKPROPAGATION: Propagate obtained scores back up the path
            System.out.println("\n=== MCTS Iteration #" + iterationCounter + " - BACKPROPAGATION ===");
            List<SearchTreeNode> backpropPath = new LinkedList<>();
            getStrategies().getPropagationStrategy().executeWithTracking(nodeForPlayout, playoutScore, backpropPath);
            iterationEvent.addBackpropagationStage(backpropPath, playoutScore);

            // Record backpropagation data
            currentStagesTracker.recordBackpropagation(backpropPath, playoutScore);
            tracer.traceBackpropagation(backpropPath, playoutScore);

            // Send iteration event to observers
            notifyObservers(iterationEvent);

            // Print debug information
            System.out.println("\n=== MCTS iteration #" + iterationCounter + " completed and sent to observers ===\n");

            // Increment counter AFTER all processing is done
            iterationCounter++;
        } catch (Exception e) {
            System.err.println("Error during MCTS iteration: " + e.getMessage());
            e.printStackTrace();
            throw e; // Rethrow exception
        }
    }

    public Move getBestAction(Role choosingRole) {
        return root.getBestAction(choosingRole);
    }

    StateMachine getGameModel() {
        return gameModel;
    }

    public SearchTreeNode getRoot() {
        return this.root;
    }

    public void setRoot(SearchTreeNode newRoot) {
        root = newRoot;
        root.becomeRoot();
    }

    PoolOfStrategies getStrategies() {
        return strategies;
    }

    /**
     * Sets the turn number for the current game
     */
    public void setTurnNumber(int turnNumber) {
        this.turnNumber = turnNumber;
        // Reset the iteration counter when changing turns
        this.iterationCounter = 0;
        // Reset the stages tracker
        resetStagesTracker();
    }

    /**
     * Gets the current turn number
     */
    public int getTurnNumber() {
        return this.turnNumber;
    }

    /**
     * Gets the current stages tracker
     */
    public MCTSStagesTracker getCurrentStagesTracker() {
        return currentStagesTracker;
    }

    public ObjectNode toJSONbyJackson() {
        return root.toJSONbyJackson(new ObjectMapper());
    }
}