package org.ggp.base.player.gamer.statemachine.mcts.model.tree;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;

import java.util.*;

public class JointActions {

    private final List<Role> roles;
    private final SortedMap<Role, Move> actionsMap;

    public JointActions(List<Role> roles) {
        this.roles = roles;
        actionsMap = new TreeMap<>(new RoleComparator());
    }

    public JointActions(List<Role> roles, List<Move> actionsList) {
        this(roles);
        for (int i = 0; i < actionsList.size(); i++) {
            actionsMap.put(roles.get(i), actionsList.get(i));
        }
    }

    public List<Move> toList() {
        return new ArrayList<>(actionsMap.values());
    }

    public List<Role> getRoles() {
        return roles;
    }

    public Move get(Role role) {
        return actionsMap.get(role);
    }

    public void put(Role role, Move action) {
        actionsMap.put(role, action);
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof JointActions) {
            JointActions otherAction = (JointActions) other;
            return this.actionsMap.equals(otherAction.actionsMap);
        }
        return false;
    }

    class RoleComparator implements Comparator<Role> {
        public int compare(Role roleA, Role roleB) {
            int roleA_index = roles.indexOf(roleA);
            int roleB_index = roles.indexOf(roleB);
            return Integer.compare(roleA_index, roleB_index);
        }
    }

    ArrayNode toJSONbyJackson(ObjectMapper mapper) {
        ArrayNode actionsJSON = mapper.createArrayNode();
        for(Role r : getRoles()) {
            ObjectNode actionJSON = mapper.createObjectNode();
            actionJSON.put("role", r.toString());
            actionJSON.put("action", get(r).toString());
            actionsJSON.add(actionJSON);
        }
        return actionsJSON;
    }
}
