package org.ggp.base.player.gamer.statemachine.mcts.utils;

import com.google.gson.*;
import org.ggp.base.player.gamer.statemachine.mcts.model.statistics.CumulativeStatistics;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.JointActions;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTree;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTreeNode;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Role;

import java.lang.reflect.Type;

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
            JsonObject result = new JsonObject();

            // Only serialize the root node - avoid circular references
            result.add("root", context.serialize(tree.getRoot()));

            return result;
        }
    }

    /**
     * Custom serializer for SearchTreeNode
     */
    private static class SearchTreeNodeSerializer implements JsonSerializer<SearchTreeNode> {
        @Override
        public JsonElement serialize(SearchTreeNode node, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject result = new JsonObject();

            // Include only necessary fields, avoiding circular references
            result.addProperty("isLeaf", node.isLeaf());
            result.addProperty("isRoot", node.isRoot());
            result.addProperty("isTerminal", node.isTerminal());
            result.addProperty("isPlayout", node.isPlayout());

            // Add state representation
            if (node.getState() != null) {
                result.addProperty("state", node.getState().toString());
            }

            // Add statistics
            CumulativeStatistics stats = node.getStatistics();
            if (stats != null) {
                JsonObject statsObj = new JsonObject();
                statsObj.addProperty("numVisits", stats.getNumVisits());
                result.add("statistics", statsObj);
            }

            // Add preceding joint move if available
            JointActions actions = node.getPrecedingJointMove();
            if (actions != null) {
                JsonArray actionsArray = new JsonArray();
                for (Role role : actions.getRoles()) {
                    JsonObject actionObj = new JsonObject();
                    actionObj.addProperty("role", role.getName().getValue());
                    if (actions.get(role) != null) {
                        actionObj.addProperty("move", actions.get(role).getContents().toString());
                    }
                    actionsArray.add(actionObj);
                }
                result.add("precedingJointMove", actionsArray);
            }

            // Add children without recursive serialization to avoid circular references
            if (!node.isLeaf()) {
                JsonArray childrenArray = new JsonArray();
                for (SearchTreeNode child : node.getChildren()) {
                    JsonObject childObj = new JsonObject();
                    if (child.getState() != null) {
                        childObj.addProperty("state", child.getState().toString());
                    }
                    childrenArray.add(childObj);
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