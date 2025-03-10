package org.ggp.base.player.gamer.statemachine.mcts.logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.diff.JsonDiff;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * ����� ��� ����������� �������� ���������� ������ MCTS � ������� JSON � JSON-patch
 */
public class MCTSTreeLogger {
    private final ObjectMapper mapper = new ObjectMapper();
    private JsonNode previousTree = null;
    private final Map<Integer, JsonNode> treeSnapshots = new HashMap<>();
    private final String logDirectory;
    private final String matchId;
    private final int turnNumber;
    private int iterationCounter = 0;
    private final int loggingFrequency;

    /**
     * ������� ������ ��� ���������� ������ MCTS
     * @param matchId ������������� �����
     * @param turnNumber ����� ����
     * @param logDirectory ���������� ��� ������ �����
     * @param loggingFrequency ������� ����������� (������ N-��� ��������)
     */
    public MCTSTreeLogger(String matchId, int turnNumber, String logDirectory, int loggingFrequency) {
        this.matchId = matchId;
        this.turnNumber = turnNumber;
        this.logDirectory = logDirectory;
        this.loggingFrequency = loggingFrequency;

        // ������� ���������� ��� �����, ���� ��� ��� �� ����������
        File dir = new File(logDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * �������� ������� ��������� ������ MCTS
     * @param treeJson JSON-������������� ������
     */
    public void logTreeState(JsonNode treeJson) {
        iterationCounter++;

        // ���������� �����������, ���� �� ���������� �������
        if (iterationCounter % loggingFrequency != 0) {
            return;
        }

        try {
            // ��������� ������� �������� ��������� ������
            treeSnapshots.put(iterationCounter, treeJson);

            // ��� ������ �������� ��������� ������ ������
            if (previousTree == null) {
                File initialTreeFile = new File(logDirectory, getInitialFileName());
                mapper.writeValue(initialTreeFile, treeJson);
            } else {
                // ��� ����������� �������� ��������� � ��������� ����
                JsonPatch patch = JsonDiff.asJsonPatch(previousTree, treeJson);
                File patchFile = new File(logDirectory, getPatchFileName(iterationCounter));
                mapper.writeValue(patchFile, patch);
            }

            previousTree = treeJson;
        } catch (IOException e) {
            System.err.println("������ ��� ����������� ������ MCTS: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ���������� ��� ����� ��� ��������� ��������� ������
     */
    private String getInitialFileName() {
        return String.format("%s_turn%d_initial.json", matchId, turnNumber);
    }

    /**
     * ���������� ��� ����� ��� �����
     * @param iteration ����� ��������
     */
    private String getPatchFileName(int iteration) {
        return String.format("%s_turn%d_patch_%06d.json", matchId, turnNumber, iteration);
    }

    /**
     * ��������� ��� ����������� �������� � ���� ������ JSON ������
     * ����� ���� ������� ��� �������
     */
    public void saveFullSnapshots() {
        try {
            for (Map.Entry<Integer, JsonNode> entry : treeSnapshots.entrySet()) {
                File snapshotFile = new File(logDirectory,
                        String.format("%s_turn%d_snapshot_%06d.json", matchId, turnNumber, entry.getKey()));
                mapper.writeValue(snapshotFile, entry.getValue());
            }
        } catch (IOException e) {
            System.err.println("������ ��� ���������� ������ ���������: " + e.getMessage());
            e.printStackTrace();
        }
    }
}