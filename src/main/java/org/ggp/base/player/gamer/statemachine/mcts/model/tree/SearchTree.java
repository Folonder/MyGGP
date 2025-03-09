package org.ggp.base.player.gamer.statemachine.mcts.model.tree;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ggp.base.player.gamer.statemachine.mcts.model.strategy.PoolOfStrategies;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;

import java.util.Map;

public class SearchTree {

    private final transient StateMachine gameModel;
    private final transient PoolOfStrategies strategies = new PoolOfStrategies();
    private SearchTreeNode root;

    public SearchTree(StateMachine gameModel) {
        this.gameModel = gameModel;
        MachineState rootState = gameModel.getInitialState();
        root = new SearchTreeNode(this, rootState, null);
    }

    public SearchTreeNode findNode(MachineState state) {
        return root.findNodeInSubTree(state);
    }

    public void cut(SearchTreeNode startRootNode) {
        getStrategies().getCuttingStrategy().execute(this, startRootNode);
    }

    public void grow() throws MoveDefinitionException {

        // Выбрать листовой “незавершенный” узел с наибольшей оценкой
        SearchTreeNode selectedNode = getStrategies().getSelectionStrategy().execute(root);

        // Расширить выбранный узел и выбрать один из дочерних узлов для симуляции игры;
        // если это невозможно, то использовать не расширенный узел
        selectedNode = getStrategies().getExpansionStrategy().execute(selectedNode);

        // Провести симуляцию игры, начиная с выбранного узла
        Map<Role, Double> playoutScore = getStrategies().getPlayoutStrategy().execute(selectedNode);

        // Распространить полученные выигрыши
        getStrategies().getPropagationStrategy().execute(selectedNode, playoutScore);
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

    public ObjectNode toJSONbyJackson() {
        return root.toJSONbyJackson( new ObjectMapper() );
    }
}

