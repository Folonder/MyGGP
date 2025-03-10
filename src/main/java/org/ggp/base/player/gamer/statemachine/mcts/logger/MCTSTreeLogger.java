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
 * Класс для логирования процесса построения дерева MCTS в формате JSON и JSON-patch
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
     * Создает логгер для построения дерева MCTS
     * @param matchId идентификатор матча
     * @param turnNumber номер хода
     * @param logDirectory директория для записи логов
     * @param loggingFrequency частота логирования (каждую N-ную итерацию)
     */
    public MCTSTreeLogger(String matchId, int turnNumber, String logDirectory, int loggingFrequency) {
        this.matchId = matchId;
        this.turnNumber = turnNumber;
        this.logDirectory = logDirectory;
        this.loggingFrequency = loggingFrequency;

        // Создаем директорию для логов, если она еще не существует
        File dir = new File(logDirectory);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Логирует текущее состояние дерева MCTS
     * @param treeJson JSON-представление дерева
     */
    public void logTreeState(JsonNode treeJson) {
        iterationCounter++;

        // Пропускаем логирование, если не достигнута частота
        if (iterationCounter % loggingFrequency != 0) {
            return;
        }

        try {
            // Сохраняем снапшот текущего состояния дерева
            treeSnapshots.put(iterationCounter, treeJson);

            // Для первой итерации сохраняем полное дерево
            if (previousTree == null) {
                File initialTreeFile = new File(logDirectory, getInitialFileName());
                mapper.writeValue(initialTreeFile, treeJson);
            } else {
                // Для последующих итераций вычисляем и сохраняем патч
                JsonPatch patch = JsonDiff.asJsonPatch(previousTree, treeJson);
                File patchFile = new File(logDirectory, getPatchFileName(iterationCounter));
                mapper.writeValue(patchFile, patch);
            }

            previousTree = treeJson;
        } catch (IOException e) {
            System.err.println("Ошибка при логировании дерева MCTS: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Возвращает имя файла для исходного состояния дерева
     */
    private String getInitialFileName() {
        return String.format("%s_turn%d_initial.json", matchId, turnNumber);
    }

    /**
     * Возвращает имя файла для патча
     * @param iteration номер итерации
     */
    private String getPatchFileName(int iteration) {
        return String.format("%s_turn%d_patch_%06d.json", matchId, turnNumber, iteration);
    }

    /**
     * Сохраняет все накопленные снапшоты в виде полных JSON файлов
     * Может быть полезно для отладки
     */
    public void saveFullSnapshots() {
        try {
            for (Map.Entry<Integer, JsonNode> entry : treeSnapshots.entrySet()) {
                File snapshotFile = new File(logDirectory,
                        String.format("%s_turn%d_snapshot_%06d.json", matchId, turnNumber, entry.getKey()));
                mapper.writeValue(snapshotFile, entry.getValue());
            }
        } catch (IOException e) {
            System.err.println("Ошибка при сохранении полных снапшотов: " + e.getMessage());
            e.printStackTrace();
        }
    }
}