package org.ggp.base.player.gamer.statemachine.mcts.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTreeNode;
import org.ggp.base.util.observer.Event;
import org.ggp.base.util.statemachine.Role;

import java.util.List;
import java.util.Map;

/**
 * �������, �������������� ���� �������� ��������� MCTS, ������� ��� ������ ������:
 * Selection, Expansion, Playout (Simulation), � Backpropagation
 */
public class IterationEvent extends Event {
    private final int iterationNumber;
    private final int turnNumber;
    private final ObjectNode iterationData;
    private final ObjectMapper mapper;

    public IterationEvent(int iterationNumber, int turnNumber) {
        this.iterationNumber = iterationNumber;
        this.turnNumber = turnNumber;
        this.mapper = new ObjectMapper();
        this.iterationData = mapper.createObjectNode();

        // ������������� ������� ��������� �������
        iterationData.put("iteration", iterationNumber);
        iterationData.putObject("stages");
    }

    /**
     * ��������� ���������� � ������ Selection (����� ����)
     *
     * @param path ���� ��������� �����
     * @param selectedNode ����, ��������� ��� ���������� ���������
     */
    public void addSelectionStage(List<SearchTreeNode> path, SearchTreeNode selectedNode) {
        ObjectNode stagesNode = (ObjectNode) iterationData.get("stages");
        ObjectNode selectionNode = stagesNode.putObject("selection");

        // ��������� ���� ������
        ArrayNode pathNode = selectionNode.putArray("path");
        for (SearchTreeNode node : path) {
            // ���������� ��������� ����� toJSON() ������ toJSONbyJackson
            pathNode.add(node.toJSON());
        }

        // ��������� ��������� ����
        if (selectedNode != null) {
            selectionNode.set("selectedNode", selectedNode.toJSON());
        }
    }

    /**
     * ��������� ���������� � ������ Expansion (���������� ������)
     *
     * @param parentNode ������������ ����, ������� ��� ��������
     * @param expandedNodes ������ ��������� �������� �����
     * @param selectedNode ����, ��������� ��� ���������
     */
    public void addExpansionStage(SearchTreeNode parentNode, List<SearchTreeNode> expandedNodes, SearchTreeNode selectedNode) {
        ObjectNode stagesNode = (ObjectNode) iterationData.get("stages");
        ObjectNode expansionNode = stagesNode.putObject("expansion");

        // ��������� ������������ ����
        if (parentNode != null) {
            expansionNode.set("parentNode", parentNode.toJSON());
        }

        // ��������� ����������� ����
        ArrayNode expandedNodesArray = expansionNode.putArray("expandedNodes");
        for (SearchTreeNode node : expandedNodes) {
            expandedNodesArray.add(node.toJSON());
        }

        // ��������� ��������� ����
        if (selectedNode != null) {
            expansionNode.set("selectedNode", selectedNode.toJSON());
        }
    }

    /**
     * ��������� ���������� � ������ Playout (���������)
     *
     * @param startNode ��������� ���� ���������
     * @param depth ������� ���������
     * @param scores ����, ���������� ������ �����
     */
    public void addPlayoutStage(SearchTreeNode startNode, int depth, Map<Role, Double> scores) {
        ObjectNode stagesNode = (ObjectNode) iterationData.get("stages");
        ObjectNode playoutNode = stagesNode.putObject("playout");

        // ��������� ��������� ����
        if (startNode != null) {
            playoutNode.set("startNode", startNode.toJSON());
        }

        // ��������� ������� ���������
        playoutNode.put("depth", depth);

        // ��������� ����
        ObjectNode scoresNode = playoutNode.putObject("scores");
        if (scores != null) {
            for (Map.Entry<Role, Double> entry : scores.entrySet()) {
                scoresNode.put(entry.getKey().toString(), entry.getValue());
            }
        }
    }

    /**
     * ��������� ���������� � ������ Backpropagation (��������������� �������)
     *
     * @param path ���� ��������������� (�� ����� � �����)
     * @param scores ���������������� ����
     */
    public void addBackpropagationStage(List<SearchTreeNode> path, Map<Role, Double> scores) {
        ObjectNode stagesNode = (ObjectNode) iterationData.get("stages");
        ObjectNode backpropNode = stagesNode.putObject("backpropagation");

        // ��������� ���� ���������������
        ArrayNode pathNode = backpropNode.putArray("path");
        for (SearchTreeNode node : path) {
            pathNode.add(node.toJSON());
        }

        // ��������� ����
        ObjectNode scoresNode = backpropNode.putObject("scores");
        if (scores != null) {
            for (Map.Entry<Role, Double> entry : scores.entrySet()) {
                scoresNode.put(entry.getKey().toString(), entry.getValue());
            }
        }
    }

    public int getIterationNumber() {
        return iterationNumber;
    }

    public int getTurnNumber() {
        return turnNumber;
    }

    public ObjectNode getIterationData() {
        return iterationData;
    }
}