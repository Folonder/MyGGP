package org.ggp.base.player.gamer.statemachine.mcts.model.statistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.JointActions;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;

import java.util.Map;
import java.util.Set;

public class CumulativeStatistics {
    private final StatisticsForActions statisticsForActions;
    private int numVisits;

    public CumulativeStatistics() {
        statisticsForActions = new StatisticsForActions();
        numVisits = 0;
    }

    public boolean isEmpty() {
        return statisticsForActions.isEmpty();
    }

    public void addUsedActions(JointActions usedJointMove) {
        statisticsForActions.addActions(usedJointMove);
    }

    public void updateUsedActions(JointActions usedJointMove, Map<Role, Double> playoutScore) {
        statisticsForActions.updateActions(usedJointMove, playoutScore);
    }

    public Set<Role> getRoles() {
        return statisticsForActions.getRoles();
    }

    public Set<Move> getUsedActions(Role role) {
        return statisticsForActions.getUsedActions(role);
    }

    public StatisticsForActions.ActionStatistics get(Role role, Move action) {
        return statisticsForActions.get(role, action);
    }

    public int getNumVisits() {
        return numVisits;
    }

    public void incNumVisits() {
        numVisits++;
    }

    public ObjectNode toJSONbyJackson(ObjectMapper mapper){
        ObjectNode statisticsJSON = mapper.createObjectNode();
        statisticsJSON.put("numVisits", numVisits);
        statisticsJSON.putIfAbsent("statisticsForActions", statisticsForActions.toJSONbyJackson(mapper));
        return statisticsJSON;
    }
}
