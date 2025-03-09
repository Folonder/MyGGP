package org.ggp.base.player.gamer.statemachine.mcts.model.tree;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ggp.base.player.gamer.statemachine.mcts.model.statistics.CumulativeStatistics;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;

import java.util.HashSet;
import java.util.Set;

public class SearchTreeNode {
    private final transient SearchTree treeOwner;
    private transient SearchTreeNode parent;
    private final Set<SearchTreeNode> children;

    private final JointActions precedingJointMove;

    private final MachineState state;
    private final CumulativeStatistics statistics;

    private boolean isPlayout;

    public SearchTreeNode(SearchTree treeOwner, MachineState state, JointActions precedingJointMove) {
        this.treeOwner = treeOwner;
        this.state = state;
        this.precedingJointMove = precedingJointMove;
        children = new HashSet<>();
        statistics = new CumulativeStatistics();
        isPlayout = false;
    }

    public StateMachine getGameModel() {
        return treeOwner.getGameModel();
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }

    public boolean isRoot() {
        return parent == null;
    }

    void becomeRoot() {
        this.parent = null;
    }

    public boolean isTerminal() {
        return getGameModel().isTerminal(getState()); //TODO пока так, в mctsref определение посложнее
    }

    public boolean isPlayout() {
        return isPlayout;
    }

    public void markPlayout() {
        isPlayout = true;
    }

    public MachineState getState() {
        return state;
    }

    public CumulativeStatistics getStatistics() {
        return statistics;
    }

    public JointActions getPrecedingJointMove() {
        return precedingJointMove;
    }

    public SearchTreeNode getParent() { return parent; }

    public SearchTreeNode getChild(final JointActions jointMove) {
        return children.stream().filter(c -> c.precedingJointMove.equals(jointMove)).findFirst().orElse(null);
    }

    public Set<SearchTreeNode> getChildren() {
        return new HashSet<>(this.children);
    }

    public SearchTreeNode createChild(JointActions usedJointMove) {
        MachineState nextState = getGameModel().getNextState(this.getState(), usedJointMove.toList());
        SearchTreeNode childNode = new SearchTreeNode(treeOwner, nextState, usedJointMove);
        linkChildToParent(this, childNode);
        return childNode;
    }

    private static void linkChildToParent(SearchTreeNode parentNode, SearchTreeNode childNode) {
        childNode.parent = parentNode;
        parentNode.children.add(childNode);
    }

    SearchTreeNode findNodeInSubTree(MachineState state) {
        if (state.equals(getState())) {
            return this;
        } else {
            for (SearchTreeNode child : children) {
                SearchTreeNode finding = child.findNodeInSubTree(state);
                if (finding != null) {
                    return finding;
                }
            }
            return null;
        }
    }

    public Move getBestAction(Role choosingRole) {
        return treeOwner.getStrategies().getSelectionStrategyForMatch().execute(this, choosingRole);
    }

    ObjectNode toJSONbyJackson(ObjectMapper mapper) {
        ObjectNode nodeJSON = mapper.createObjectNode();

        if(precedingJointMove != null) {
            nodeJSON.putIfAbsent("precedingJointMove", precedingJointMove.toJSONbyJackson(mapper));
        }
        nodeJSON.put("state", state.toString());
        if(!isLeaf()) {
            nodeJSON.putIfAbsent("statistics", statistics.toJSONbyJackson(mapper));

            if (!children.isEmpty()) {
                ArrayNode childrenJSON = mapper.createArrayNode();
                for (SearchTreeNode child : children) {
                    childrenJSON.add(child.toJSONbyJackson(mapper));
                }
                nodeJSON.putIfAbsent("children", childrenJSON);
            }
        }

        return nodeJSON;
    }
}
