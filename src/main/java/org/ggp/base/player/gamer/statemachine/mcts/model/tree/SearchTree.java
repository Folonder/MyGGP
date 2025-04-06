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

    public SearchTree(StateMachine gameModel) {
        this.gameModel = gameModel;
        MachineState rootState = gameModel.getInitialState();
        root = new SearchTreeNode(this, rootState, null);
        currentStagesTracker = new MCTSStagesTracker(0);
    }

    /**
     * Добавляет наблюдателя событий дерева
     */
    public void addObserver(Observer observer) {
        observers.add(observer);
    }

    /**
     * Оповещает наблюдателей о событии
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
            // Create iteration tracer for debugging
            MCTSIterationTracer tracer = new MCTSIterationTracer();

            // Create a stages tracker for this iteration
            currentStagesTracker = new MCTSStagesTracker(iterationCounter);

            // Initialize iteration event
            IterationEvent iterationEvent = new IterationEvent(iterationCounter++, turnNumber);

            // List for collecting selection path
            List<SearchTreeNode> selectionPath = new LinkedList<>();
            selectionPath.add(root);

            // 1. SELECTION: Choose node with highest score
            System.out.println("\n=== MCTS Iteration #" + (iterationCounter-1) + " - SELECTION ===");
            SearchTreeNode selectedNode = getStrategies().getSelectionStrategy().executeWithPath(root, selectionPath);
            iterationEvent.addSelectionStage(selectionPath, selectedNode);
            tracer.traceSelection(this, selectionPath, selectedNode);

            // Record selection data
            currentStagesTracker.recordSelection(selectionPath, selectedNode);

            // 2. EXPANSION: Expand selected node and choose one of the children
            System.out.println("\n=== MCTS Iteration #" + (iterationCounter-1) + " - EXPANSION ===");
            List<SearchTreeNode> expandedNodes = new ArrayList<>();
            SearchTreeNode nodeForPlayout = getStrategies().getExpansionStrategy().executeWithTracking(selectedNode, expandedNodes);
            iterationEvent.addExpansionStage(selectedNode, expandedNodes, nodeForPlayout);
            tracer.traceExpansion(selectedNode, expandedNodes, nodeForPlayout);

            // Record expansion data
            currentStagesTracker.recordExpansion(selectedNode, expandedNodes, nodeForPlayout);

            // 3. PLAYOUT/SIMULATION: Game simulation from selected node
            System.out.println("\n=== MCTS Iteration #" + (iterationCounter-1) + " - PLAYOUT ===");
            int[] depth = new int[1]; // For tracking playout depth
            Map<Role, Double> playoutScore = getStrategies().getPlayoutStrategy().executeWithDepth(nodeForPlayout, depth);
            iterationEvent.addPlayoutStage(nodeForPlayout, depth[0], playoutScore);
            tracer.tracePlayout(nodeForPlayout, depth[0], playoutScore);

            // Record playout data
            currentStagesTracker.recordPlayout(nodeForPlayout, depth[0], playoutScore);

            // 4. BACKPROPAGATION: Propagate obtained scores back up the path
            System.out.println("\n=== MCTS Iteration #" + (iterationCounter-1) + " - BACKPROPAGATION ===");
            List<SearchTreeNode> backpropPath = new LinkedList<>();
            getStrategies().getPropagationStrategy().executeWithTracking(nodeForPlayout, playoutScore, backpropPath);
            iterationEvent.addBackpropagationStage(backpropPath, playoutScore);
            tracer.traceBackpropagation(backpropPath, playoutScore);

            // Record backpropagation data
            currentStagesTracker.recordBackpropagation(backpropPath, playoutScore);

            // Send iteration event to observers
            notifyObservers(iterationEvent);

            // Send it also to new tracer
            tracer.observe(iterationEvent);

            // Print debug information
            System.out.println("\n=== MCTS iteration #" + (iterationCounter-1) + " completed and sent to observers ===\n");
        } catch (Exception e) {
            System.err.println("Error during MCTS iteration: " + e.getMessage());
            e.printStackTrace();
            throw e; // Rethrow exception
        }
    }

    /**
     * Gets the current stages tracker
     */
    public MCTSStagesTracker getCurrentStagesTracker() {
        return currentStagesTracker;
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
     * Устанавливает номер текущего хода
     */
    public void setTurnNumber(int turnNumber) {
        this.turnNumber = turnNumber;
        // Сбрасываем счетчик итераций при смене хода
        this.iterationCounter = 0;
    }

    /**
     * Возвращает номер текущего хода
     */
    public int getTurnNumber() {
        return this.turnNumber;
    }

    public ObjectNode toJSONbyJackson() {
        return root.toJSONbyJackson(new ObjectMapper());
    }
}