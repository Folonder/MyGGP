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
 * Событие, представляющее одну итерацию алгоритма MCTS, включая все четыре стадии:
 * Selection, Expansion, Playout (Simulation), и Backpropagation
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

        // Инициализация базовой структуры события
        iterationData.put("iteration", iterationNumber);
        iterationData.putObject("stages");
    }

    /**
     * Добавляет информацию о стадии Selection (выбор узла)
     *
     * @param path Путь выбранных узлов
     * @param selectedNode Узел, выбранный для дальнейшей обработки
     */
    public void addSelectionStage(List<SearchTreeNode> path, SearchTreeNode selectedNode) {
        ObjectNode stagesNode = (ObjectNode) iterationData.get("stages");
        ObjectNode selectionNode = stagesNode.putObject("selection");

        // Добавляем путь выбора
        ArrayNode pathNode = selectionNode.putArray("path");
        for (SearchTreeNode node : path) {
            // Используем публичный метод toJSON() вместо toJSONbyJackson
            pathNode.add(node.toJSON());
        }

        // Добавляем выбранный узел
        if (selectedNode != null) {
            selectionNode.set("selectedNode", selectedNode.toJSON());
        }
    }

    /**
     * Добавляет информацию о стадии Expansion (расширение дерева)
     *
     * @param parentNode Родительский узел, который был расширен
     * @param expandedNodes Список созданных дочерних узлов
     * @param selectedNode Узел, выбранный для симуляции
     */
    public void addExpansionStage(SearchTreeNode parentNode, List<SearchTreeNode> expandedNodes, SearchTreeNode selectedNode) {
        ObjectNode stagesNode = (ObjectNode) iterationData.get("stages");
        ObjectNode expansionNode = stagesNode.putObject("expansion");

        // Добавляем родительский узел
        if (parentNode != null) {
            expansionNode.set("parentNode", parentNode.toJSON());
        }

        // Добавляем расширенные узлы
        ArrayNode expandedNodesArray = expansionNode.putArray("expandedNodes");
        for (SearchTreeNode node : expandedNodes) {
            expandedNodesArray.add(node.toJSON());
        }

        // Добавляем выбранный узел
        if (selectedNode != null) {
            expansionNode.set("selectedNode", selectedNode.toJSON());
        }
    }

    /**
     * Добавляет информацию о стадии Playout (симуляция)
     *
     * @param startNode Начальный узел симуляции
     * @param depth Глубина симуляции
     * @param scores Очки, полученные каждой ролью
     */
    public void addPlayoutStage(SearchTreeNode startNode, int depth, Map<Role, Double> scores) {
        ObjectNode stagesNode = (ObjectNode) iterationData.get("stages");
        ObjectNode playoutNode = stagesNode.putObject("playout");

        // Добавляем начальный узел
        if (startNode != null) {
            playoutNode.set("startNode", startNode.toJSON());
        }

        // Добавляем глубину симуляции
        playoutNode.put("depth", depth);

        // Добавляем очки
        ObjectNode scoresNode = playoutNode.putObject("scores");
        if (scores != null) {
            for (Map.Entry<Role, Double> entry : scores.entrySet()) {
                scoresNode.put(entry.getKey().toString(), entry.getValue());
            }
        }
    }

    /**
     * Добавляет информацию о стадии Backpropagation (распространение обратно)
     *
     * @param path Путь распространения (от листа к корню)
     * @param scores Распространяемые очки
     */
    public void addBackpropagationStage(List<SearchTreeNode> path, Map<Role, Double> scores) {
        ObjectNode stagesNode = (ObjectNode) iterationData.get("stages");
        ObjectNode backpropNode = stagesNode.putObject("backpropagation");

        // Добавляем путь распространения
        ArrayNode pathNode = backpropNode.putArray("path");
        for (SearchTreeNode node : path) {
            pathNode.add(node.toJSON());
        }

        // Добавляем очки
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