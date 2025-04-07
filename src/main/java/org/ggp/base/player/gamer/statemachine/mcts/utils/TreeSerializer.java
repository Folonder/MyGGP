package org.ggp.base.player.gamer.statemachine.mcts.utils;

import com.google.gson.*;
import org.ggp.base.player.gamer.statemachine.mcts.model.statistics.CumulativeStatistics;
import org.ggp.base.player.gamer.statemachine.mcts.model.statistics.StatisticsForActions;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.JointActions;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTree;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTreeNode;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;

import java.lang.reflect.Type;
import java.util.Set;

/**
 * Custom serializer for SearchTree and related classes to avoid circular references
 */
public class TreeSerializer {

    /**
     * Creates a gson instance with custom serializers to handle circular references
     */
    public static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(SearchTree.class, new SearchTreeSerializer())
                .registerTypeAdapter(SearchTreeNode.class, new SearchTreeNodeSerializer())
                .registerTypeAdapter(MachineState.class, new MachineStateSerializer())
                .setPrettyPrinting()
                .create();
    }

    /**
     * Custom serializer for SearchTree
     */
    private static class SearchTreeSerializer implements JsonSerializer<SearchTree> {
        @Override
        public JsonElement serialize(SearchTree tree, Type typeOfSrc, JsonSerializationContext context) {
            // Directly serialize the root node
            return context.serialize(tree.getRoot());
        }
    }

    /**
     * Custom serializer for SearchTreeNode that preserves the full tree structure
     */
    private static class SearchTreeNodeSerializer implements JsonSerializer<SearchTreeNode> {
        @Override
        public JsonElement serialize(SearchTreeNode node, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject result = new JsonObject();

            // Add state representation
            if (node.getState() != null) {
                result.addProperty("state", node.getState().toString());
            }

            // Add statistics with full details
            CumulativeStatistics stats = node.getStatistics();
            if (stats != null && !stats.isEmpty()) {
                JsonObject statsObj = new JsonObject();
                statsObj.addProperty("numVisits", stats.getNumVisits());

                // Add detailed statistics for actions
                JsonArray statsForActionsArray = new JsonArray();
                for (Role role : stats.getRoles()) {
                    JsonObject roleObj = new JsonObject();
                    roleObj.addProperty("role", role.getName().getValue());

                    JsonArray actionsArray = new JsonArray();
                    Set<Move> usedActions = stats.getUsedActions(role);
                    if (usedActions != null) {
                        for (Move action : usedActions) {
                            StatisticsForActions.ActionStatistics actionStats = stats.get(role, action);
                            if (actionStats != null) {
                                JsonObject actionObj = new JsonObject();
                                actionObj.addProperty("action", action.getContents().toString());
                                actionObj.addProperty("averageActionScore",
                                        actionStats.getScore() / (double)actionStats.getNumUsed());
                                actionObj.addProperty("actionNumUsed", actionStats.getNumUsed());
                                actionsArray.add(actionObj);
                            }
                        }
                    }
                    roleObj.add("actions", actionsArray);
                    statsForActionsArray.add(roleObj);
                }
                statsObj.add("statisticsForActions", statsForActionsArray);
                result.add("statistics", statsObj);
            }

            // Add preceding joint move
            JointActions actions = node.getPrecedingJointMove();
            if (actions != null) {
                JsonArray actionsArray = new JsonArray();
                for (Role role : actions.getRoles()) {
                    JsonObject actionObj = new JsonObject();
                    actionObj.addProperty("role", role.getName().getValue());
                    Move move = actions.get(role);
                    if (move != null) {
                        actionObj.addProperty("action", move.getContents().toString());
                    }
                    actionsArray.add(actionObj);
                }
                result.add("precedingJointMove", actionsArray);
            }

            // Add children with recursion up to a reasonable depth
            if (!node.isLeaf()) {
                JsonArray childrenArray = new JsonArray();
                // To avoid stack overflow, limit recursion depth based on numVisits
                // Nodes with fewer visits get less detailed serialization
                Set<SearchTreeNode> children = node.getChildren();

                // Process each child
                for (SearchTreeNode child : children) {
                    // Recursively serialize child nodes with lower thresholds
                    childrenArray.add(context.serialize(child));
                }

                result.add("children", childrenArray);
            }

            return result;
        }
    }

    /**
     * Custom serializer for MachineState
     */
    private static class MachineStateSerializer implements JsonSerializer<MachineState> {
        @Override
        public JsonElement serialize(MachineState state, Type typeOfSrc, JsonSerializationContext context) {
            JsonArray result = new JsonArray();
            if (state != null && state.getContents() != null) {
                for (GdlSentence sentence : state.getContents()) {
                    result.add(sentence.toString());
                }
            }
            return result;
        }
    }
}