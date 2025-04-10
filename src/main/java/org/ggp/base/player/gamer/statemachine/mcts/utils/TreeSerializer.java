package org.ggp.base.player.gamer.statemachine.mcts.utils;

import com.google.gson.*;
import org.ggp.base.player.gamer.statemachine.mcts.model.statistics.CumulativeStatistics;
import org.ggp.base.player.gamer.statemachine.mcts.model.statistics.StatisticsForActions;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.JointActions;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTreeNode;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;

import java.lang.reflect.Type;

/**
 * Utility class for serializing tree structures to different formats
 */
public class TreeSerializer {

    /**
     * Creates a configured Gson instance for tree serialization
     */
    public static Gson createGson() {
        return new GsonBuilder()
                .setPrettyPrinting()
                // Custom serializer for tree nodes
                .registerTypeAdapter(SearchTreeNode.class, new SearchTreeNodeSerializer())
                // Custom serializer for machine states
                .registerTypeAdapter(MachineState.class, new MachineStateSerializer())
                // Custom serializer for joint actions
                .registerTypeAdapter(JointActions.class, new JointActionsSerializer())
                // Custom serializer for statistics
                .registerTypeAdapter(CumulativeStatistics.class, new CumulativeStatisticsSerializer())
                .create();
    }

    /**
     * Custom serializer for SearchTreeNode
     */
    static class SearchTreeNodeSerializer implements JsonSerializer<SearchTreeNode> {
        @Override
        public JsonElement serialize(SearchTreeNode src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject node = new JsonObject();

            // Add state information
            node.add("state", context.serialize(src.getState()));

            // Add statistics (only if not empty)
            if (!src.getStatistics().isEmpty()) {
                node.add("statistics", context.serialize(src.getStatistics()));
            }

            // Add preceding joint move (if exists)
            if (src.getPrecedingJointMove() != null) {
                node.add("precedingJointMove", context.serialize(src.getPrecedingJointMove()));
            }

            // Mark if node has had a playout
            if (src.isPlayout()) {
                node.addProperty("isPlayout", true);
            }

            // Add children nodes
            if (!src.isLeaf()) {
                JsonArray children = new JsonArray();
                for (SearchTreeNode child : src.getChildren()) {
                    children.add(context.serialize(child));
                }
                node.add("children", children);
            }

            return node;
        }
    }

    /**
     * Custom serializer for MachineState
     */
    static class MachineStateSerializer implements JsonSerializer<MachineState> {
        @Override
        public JsonElement serialize(MachineState src, Type typeOfSrc, JsonSerializationContext context) {
            JsonArray result = new JsonArray();
            if (src != null && src.getContents() != null) {
                for (GdlSentence sentence : src.getContents()) {
                    result.add(sentence.toString());
                }
            }
            return result;
        }
    }

    /**
     * Custom serializer for JointActions - formats it in a way compatible with C# models
     */
    static class JointActionsSerializer implements JsonSerializer<JointActions> {
        @Override
        public JsonElement serialize(JointActions src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jointMoveJSON = new JsonObject();

            // Add roles array
            JsonArray rolesArray = new JsonArray();
            for (Role role : src.getRoles()) {
                JsonObject roleObject = new JsonObject();
                JsonObject nameObject = new JsonObject();
                nameObject.addProperty("value", role.toString());
                roleObject.add("name", nameObject);
                rolesArray.add(roleObject);
            }
            jointMoveJSON.add("roles", rolesArray);

            // Add actionsMap object - this format matches what C# expects
            JsonObject actionsMapObject = new JsonObject();
            for (Role role : src.getRoles()) {
                Move move = src.get(role);
                if (move != null) {
                    JsonObject actionObject = new JsonObject();
                    JsonObject contentsObject = new JsonObject();

                    // Format the action/move
                    String moveStr = move.toString();

                    if (moveStr.equals("noop")) {
                        // Simple noop case
                        contentsObject.addProperty("value", "noop");
                    } else {
                        // Format complex moves like "( mark 1 )"
                        String[] moveParts = moveStr.replaceAll("\\(|\\)", "").trim().split("\\s+");
                        if (moveParts.length > 0) {
                            JsonObject nameObject = new JsonObject();
                            nameObject.addProperty("value", moveParts[0]); // The action name
                            contentsObject.add("name", nameObject);

                            // Add parameters as body array
                            if (moveParts.length > 1) {
                                JsonArray bodyArray = new JsonArray();
                                for (int i = 1; i < moveParts.length; i++) {
                                    JsonObject paramObject = new JsonObject();
                                    paramObject.addProperty("value", moveParts[i]);
                                    bodyArray.add(paramObject);
                                }
                                contentsObject.add("body", bodyArray);
                            }
                        } else {
                            // Fallback for unexpected formats
                            contentsObject.addProperty("value", moveStr);
                        }
                    }

                    actionObject.add("contents", contentsObject);
                    actionsMapObject.add(role.toString(), actionObject);
                }
            }
            jointMoveJSON.add("actionsMap", actionsMapObject);

            return jointMoveJSON;
        }
    }

    /**
     * Custom serializer for CumulativeStatistics
     */
    static class CumulativeStatisticsSerializer implements JsonSerializer<CumulativeStatistics> {
        @Override
        public JsonElement serialize(CumulativeStatistics src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject statsJson = new JsonObject();

            // Add basic statistics
            statsJson.addProperty("numVisits", src.getNumVisits());

            // Format statistics for actions
            if (!src.isEmpty()) {
                JsonObject statsForActionsJson = new JsonObject();

                // Create map structure
                JsonObject mapJson = new JsonObject();

                // Build the map for each role and its actions
                for (Role role : src.getRoles()) {
                    JsonObject actionsForRoleJson = new JsonObject();

                    for (Move action : src.getUsedActions(role)) {
                        StatisticsForActions.ActionStatistics actionStats = src.get(role, action);
                        JsonObject actionStatsJson = new JsonObject();
                        actionStatsJson.addProperty("actionScore", actionStats.getScore());
                        actionStatsJson.addProperty("actionNumUsed", actionStats.getNumUsed());

                        actionsForRoleJson.add(action.toString(), actionStatsJson);
                    }

                    if (actionsForRoleJson.size() > 0) {
                        mapJson.add(role.toString(), actionsForRoleJson);
                    }
                }

                statsForActionsJson.add("map", mapJson);

                // Add roles array - this is needed by C# models
                JsonArray rolesArray = new JsonArray();
                for (Role role : src.getRoles()) {
                    JsonObject roleObject = new JsonObject();
                    JsonObject nameObject = new JsonObject();
                    nameObject.addProperty("value", role.toString());
                    roleObject.add("name", nameObject);
                    rolesArray.add(roleObject);
                }
                statsForActionsJson.add("roles", rolesArray);

                statsJson.add("statisticsForActions", statsForActionsJson);
            }

            return statsJson;
        }
    }
}