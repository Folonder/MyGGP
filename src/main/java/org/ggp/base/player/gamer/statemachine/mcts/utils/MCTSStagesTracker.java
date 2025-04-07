package org.ggp.base.player.gamer.statemachine.mcts.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTreeNode;
import org.ggp.base.util.statemachine.Role;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class for tracking and storing the current MCTS iteration stages
 */
public class MCTSStagesTracker {
    private final ObjectMapper mapper = new ObjectMapper();
    private ObjectNode selectionData;
    private ObjectNode expansionData;
    private ObjectNode playoutData;
    private ObjectNode backpropagationData;
    private int iterationNumber;

    public MCTSStagesTracker(int iterationNumber) {
        this.iterationNumber = iterationNumber;
        reset();

        // Debug log to verify correct iteration number
        System.out.println("Created MCTSStagesTracker for iteration #" + iterationNumber);
    }

    /**
     * Resets all stage data
     */
    public void reset() {
        selectionData = mapper.createObjectNode();
        expansionData = mapper.createObjectNode();
        playoutData = mapper.createObjectNode();
        backpropagationData = mapper.createObjectNode();

        // Initialize each stage data with the iteration number
        selectionData.put("iterationNumber", iterationNumber);
        expansionData.put("iterationNumber", iterationNumber);
        playoutData.put("iterationNumber", iterationNumber);
        backpropagationData.put("iterationNumber", iterationNumber);
    }

    /**
     * Records selection stage data
     */
    public void recordSelection(List<SearchTreeNode> path, SearchTreeNode selectedNode) {
        // Iteration number is already set in reset()
        selectionData.put("pathLength", path.size());

        if (selectedNode != null) {
            selectionData.put("selectedNodeState", selectedNode.getState().toString());
            selectionData.put("isTerminal", selectedNode.isTerminal());
            selectionData.put("isLeaf", selectedNode.isLeaf());
        }

        System.out.println("Recorded selection data for iteration #" + iterationNumber);
    }

    /**
     * Records expansion stage data
     */
    public void recordExpansion(SearchTreeNode parentNode, List<SearchTreeNode> expandedNodes, SearchTreeNode selectedNode) {
        // Iteration number is already set in reset()
        expansionData.put("expandedNodesCount", expandedNodes.size());

        if (parentNode != null) {
            expansionData.put("parentNodeState", parentNode.getState().toString());
        }

        if (selectedNode != null) {
            expansionData.put("selectedNodeState", selectedNode.getState().toString());
        }

        System.out.println("Recorded expansion data for iteration #" + iterationNumber);
    }

    /**
     * Records playout stage data
     */
    public void recordPlayout(SearchTreeNode startNode, int depth, Map<Role, Double> scores) {
        // Iteration number is already set in reset()
        playoutData.put("depth", depth);

        if (startNode != null) {
            playoutData.put("startNodeState", startNode.getState().toString());
        }

        ObjectNode scoresNode = playoutData.putObject("scores");
        if (scores != null) {
            for (Map.Entry<Role, Double> entry : scores.entrySet()) {
                scoresNode.put(entry.getKey().getName().getValue(), entry.getValue());
            }
        }

        System.out.println("Recorded playout data for iteration #" + iterationNumber);
    }

    /**
     * Records backpropagation stage data
     */
    public void recordBackpropagation(List<SearchTreeNode> path, Map<Role, Double> scores) {
        // Iteration number is already set in reset()
        backpropagationData.put("pathLength", path.size());

        ObjectNode scoresNode = backpropagationData.putObject("scores");
        if (scores != null) {
            for (Map.Entry<Role, Double> entry : scores.entrySet()) {
                scoresNode.put(entry.getKey().getName().getValue(), entry.getValue());
            }
        }

        System.out.println("Recorded backpropagation data for iteration #" + iterationNumber);
    }

    /**
     * Gets all stage data as a map of stage names to JSON strings
     */
    public Map<String, String> getAllStagesJson() {
        Map<String, String> result = new HashMap<>();
        try {
            result.put("selection", mapper.writeValueAsString(selectionData));
            result.put("expansion", mapper.writeValueAsString(expansionData));
            result.put("playout", mapper.writeValueAsString(playoutData));
            result.put("backpropagation", mapper.writeValueAsString(backpropagationData));

            System.out.println("Retrieved all stages JSON for iteration #" + iterationNumber);
        } catch (Exception e) {
            System.err.println("Error serializing stage data for iteration #" + iterationNumber + ": " + e.getMessage());
        }
        return result;
    }

    public String getSelectionJson() {
        try {
            return mapper.writeValueAsString(selectionData);
        } catch (Exception e) {
            System.err.println("Error serializing selection data: " + e.getMessage());
            return "{}";
        }
    }

    public String getExpansionJson() {
        try {
            return mapper.writeValueAsString(expansionData);
        } catch (Exception e) {
            System.err.println("Error serializing expansion data: " + e.getMessage());
            return "{}";
        }
    }

    public String getPlayoutJson() {
        try {
            return mapper.writeValueAsString(playoutData);
        } catch (Exception e) {
            System.err.println("Error serializing playout data: " + e.getMessage());
            return "{}";
        }
    }

    public String getBackpropagationJson() {
        try {
            return mapper.writeValueAsString(backpropagationData);
        } catch (Exception e) {
            System.err.println("Error serializing backpropagation data: " + e.getMessage());
            return "{}";
        }
    }

    /**
     * Gets the iteration number for this tracker
     */
    public int getIterationNumber() {
        return iterationNumber;
    }
}