package org.ggp.base.player.gamer.statemachine.mcts.model.statistics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.JointActions;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StatisticsForActions {

    private final Map<Role, Map<Move, ActionStatistics>> map = new HashMap<>(); // Выигрыши, привязанные к действиям, и их количества использований
    private List<Role> roles;  // используем только для генерации JSON - так быстрее

    public boolean isEmpty() {
        return map.isEmpty();
    }

    void addActions(JointActions actions) {
        roles = actions.getRoles();
        for (Role r : actions.getRoles()) {
            put(r, actions.get(r), new ActionStatistics());
        }
    }

    void updateActions(JointActions actions, Map<Role, Double> playoutScore) {
        for (Role r : actions.getRoles()) {
            update(r, actions.get(r), playoutScore.get(r));
        }
    }

    private void put(Role role, Move action, ActionStatistics actionStatistics) {

        Map<Move, ActionStatistics> actionStatisticsMap = map.get(role);

        if (actionStatisticsMap == null) {
            Map<Move, ActionStatistics> item = new HashMap<>();
            item.put(action, actionStatistics);
            map.put(role, item);

        } else {
            actionStatisticsMap.put(action, actionStatistics);
        }
    }

    void update(Role role, Move action, double actionScore) {
        ActionStatistics item = get(role, action);

        if (item == null) {
            item = new ActionStatistics();
            put(role, action, item);
        }

        item.actionScore += actionScore;
        item.actionNumUsed++;
    }

    ActionStatistics get(Role role, Move action) {
        Map<Move, ActionStatistics> actionStatisticsMap;
        actionStatisticsMap = map.get(role);

        if (actionStatisticsMap == null) {
            return null;
        }

        return actionStatisticsMap.get(action);
    }

    Set<Move> getUsedActions(Role role) {
        return map.get(role).keySet();
    }

    public Set<Role> getRoles() {
        return map.keySet();
    }

    public ArrayNode toJSONbyJackson(ObjectMapper mapper) {
        ArrayNode statisticsJSON = mapper.createArrayNode();

        for(Role r : roles) {
            ObjectNode roleJSON = mapper.createObjectNode();
            roleJSON.put("role", r.toString());

            ArrayNode actionsStatistics = mapper.createArrayNode();
            for(Move a : getUsedActions(r)) {
                ObjectNode actionJSON = mapper.createObjectNode();

                ActionStatistics as = get(r, a);
                actionJSON.put("action", a.toString());
                actionJSON.put("averageActionScore", as.actionScore /as.actionNumUsed);
                actionJSON.put("actionNumUsed", as.actionNumUsed);

                actionsStatistics.add( actionJSON );
            }
            roleJSON.putIfAbsent("actions", actionsStatistics);

            statisticsJSON.add(roleJSON);
        }

        return statisticsJSON;
    }

    public static class ActionStatistics {
        private double actionScore;
        private int actionNumUsed;

        public ActionStatistics() {
            actionScore = 0;
            actionNumUsed = 0;
        }

        public double getScore() {
            return actionScore;
        }

        public int getNumUsed() { return actionNumUsed; }

        public ObjectNode toJSONbyJackson(ObjectMapper mapper) {
            ObjectNode statistics = mapper.createObjectNode();
            statistics.put("averageActionScore", actionScore /actionNumUsed);
            statistics.put("actionNumUsed", actionNumUsed);
            return statistics;
        }
    }
}
