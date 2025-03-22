package org.ggp.base.player.gamer.statemachine.mcts;

import org.ggp.base.player.gamer.statemachine.mcts.event.TreeEvent;
import org.ggp.base.player.gamer.statemachine.mcts.event.TreeStartEvent;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTree;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTreeNode;
import org.ggp.base.player.gamer.statemachine.mcts.model.statistics.CumulativeStatistics;
import org.ggp.base.player.gamer.statemachine.mcts.model.statistics.StatisticsForActions;
import org.ggp.base.player.gamer.statemachine.mcts.observer.TreeObserver;
import org.ggp.base.player.gamer.statemachine.sample.SampleGamer;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MCTSGamer extends SampleGamer {
    private final long SAFETY_MARGIN = 2000;

    // Progressive logging configuration
    private final boolean ENABLE_GROWTH_LOGGING = true; // Включить/выключить логирование роста

    // Настройки прогрессии логирования
    private final int[] ITERATIONS_FIRST_10 = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10}; // Логировать каждую из первых 10 итераций
    private final int[] ITERATIONS_FIRST_100 = {15, 20, 25, 30, 40, 50, 60, 70, 80, 90, 100}; // Более редкие точки до 100
    private final double PROGRESSION_FACTOR = 1.3; // Каждая следующая точка логирования в ~1.3 раза больше предыдущей
    private final int MAX_LOG_INTERVAL = 5000; // Максимальный интервал между логами
    private final int MAX_LOG_POINTS = 100; // Максимальное число точек логирования для одного хода

    private SearchTree tree = null;
    private int turnCount = 0;
    private int growthLogCount = 0;

    // Множество для хранения итераций, которые нужно логировать
    private Set<Integer> iterationsToLog = new HashSet<>();

    @Override
    public void stateMachineMetaGame(long xiTimeout)
            throws TransitionDefinitionException, MoveDefinitionException,
            GoalDefinitionException {
        tree = new SearchTree(getStateMachine());
        turnCount = 0;
        growthLogCount = 0;
        this.addObserver(new TreeObserver());
        notifyObservers(new TreeStartEvent());
    }

    @Override
    public void stateMachineStop() {
        super.stateMachineStop();
        // Логировать финальное состояние дерева
        notifyObservers(new TreeEvent(tree, turnCount, false, true));
    }

    @Override
    public void stateMachineAbort() {
        super.stateMachineAbort();
        // Логировать финальное состояние дерева при прерывании
        notifyObservers(new TreeEvent(tree, turnCount, false, true));
    }

    /**
     * Генерирует набор номеров итераций для логирования с использованием прогрессивной схемы
     * @param estimatedTotalIterations Ожидаемое общее число итераций
     */
    private void generateProgressiveLoggingPoints(int estimatedTotalIterations) {
        iterationsToLog.clear();

        // Всегда логировать итерацию 0 (начальное состояние)
        iterationsToLog.add(0);

        // Добавить все ранние итерации (1-10)
        for (int early : ITERATIONS_FIRST_10) {
            iterationsToLog.add(early);
        }

        // Добавить промежуточные итерации (до 100)
        for (int mid : ITERATIONS_FIRST_100) {
            iterationsToLog.add(mid);
        }

        // Генерировать прогрессивные точки логирования
        double nextPoint = 100; // Начинаем от 100
        List<Integer> generatedPoints = new ArrayList<>();

        while (nextPoint < estimatedTotalIterations) {
            nextPoint = Math.ceil(nextPoint * PROGRESSION_FACTOR);

            // Обеспечить, чтобы не превышался максимальный интервал
            int lastPoint = iterationsToLog.stream().mapToInt(i -> i).max().orElse(0);
            if (nextPoint - lastPoint > MAX_LOG_INTERVAL) {
                nextPoint = lastPoint + MAX_LOG_INTERVAL;
            }

            generatedPoints.add((int)nextPoint);

            // Проверка безопасности - если мы приближаемся к очень большим числам, остановиться
            if (nextPoint > Integer.MAX_VALUE / 2) break;

            // Если сгенерировали слишком много точек, остановиться
            if (generatedPoints.size() >= MAX_LOG_POINTS -
                    ITERATIONS_FIRST_10.length - ITERATIONS_FIRST_100.length - 1) { // -1 для итерации 0
                break;
            }
        }

        // Если у нас слишком много точек, отфильтровать их равномерно
        if (generatedPoints.size() > MAX_LOG_POINTS - ITERATIONS_FIRST_10.length - ITERATIONS_FIRST_100.length - 1) {
            // Выбрать подмножество точек равномерно
            int available = MAX_LOG_POINTS - ITERATIONS_FIRST_10.length - ITERATIONS_FIRST_100.length - 1;
            int step = generatedPoints.size() / available;

            for (int i = 0; i < generatedPoints.size(); i += step) {
                if (i < generatedPoints.size()) {
                    iterationsToLog.add(generatedPoints.get(i));
                }
            }

            // Всегда добавлять последнюю точку
            if (!generatedPoints.isEmpty()) {
                iterationsToLog.add(generatedPoints.get(generatedPoints.size() - 1));
            }
        } else {
            // Если точек немного, добавить их все
            iterationsToLog.addAll(generatedPoints);
        }

        // Также добавить примерно предполагаемую последнюю итерацию
        iterationsToLog.add(estimatedTotalIterations);

        // Сортировка и вывод для отладки
        Integer[] logPoints = iterationsToLog.toArray(new Integer[0]);
        Arrays.sort(logPoints);
        System.out.println("Сгенерировано " + logPoints.length + " точек логирования: " + Arrays.toString(logPoints));
    }

    public Move stateMachineSelectMove(long xiTimeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
        long start = System.currentTimeMillis();

        SearchTreeNode startRootNode = tree.findNode(getCurrentState());
        // Perform tree cutting
        tree.cut(startRootNode);

        long finishBy = xiTimeout - SAFETY_MARGIN;
        long availableTime = finishBy - start;

        // Грубо оценить количество итераций, которые мы сможем выполнить
        // на основе предыдущего опыта (~20000 итераций в секунду)
        int estimatedIterations = (int)(availableTime * 20000 / 1000);
        System.out.println("Ожидаемое количество итераций: " + estimatedIterations);

        // Генерировать прогрессивные точки логирования
        generateProgressiveLoggingPoints(estimatedIterations);

        int iterations = 0;
        growthLogCount = 0;

        // Логировать начальное состояние дерева (итерация 0) до любого роста
        if (ENABLE_GROWTH_LOGGING) {
            logTreeGrowth(0);
            notifyObservers(new TreeEvent(tree, turnCount, true, false));
        }

        while (System.currentTimeMillis() < finishBy) {
            iterations++;
            tree.grow();

            // Логировать рост дерева по прогрессивной схеме
            if (ENABLE_GROWTH_LOGGING && iterationsToLog.contains(iterations)) {
                growthLogCount++;
                // Логировать текущее состояние дерева
                logTreeGrowth(iterations);

                // Уведомить наблюдателей о росте дерева
                notifyObservers(new TreeEvent(tree, turnCount, true, false));

                // Каждые 10 логов, выводить прогресс
                if (growthLogCount % 10 == 0) {
                    System.out.println("Прогресс: залогировано " + growthLogCount + " состояний, итерация " + iterations);
                }
            }
        }

        System.out.println("Завершено " + iterations + " итераций, залогировано " + growthLogCount + " состояний дерева");
        Move bestMove = tree.getBestAction(getRole());

        // Сохранить JSON-файл для этого матча
        String filePath = getMatchFolderString() + "/" + this.getName() + "__" + this.getRoleName().toString() + "/step_" + turnCount + ".json";
        File f = new File(filePath);
        if (f.exists()) f.delete();
        BufferedWriter bw = null;
        try {
            f.getParentFile().mkdirs();
            f.createNewFile();
            bw = new BufferedWriter(new FileWriter(f));
            bw.write(tree.toJSONbyJackson().toString());
            bw.flush();
            bw.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Уведомить наблюдателей после завершения выбора хода с финальным деревом
        notifyObservers(new TreeEvent(tree, turnCount, false, true));
        turnCount++;

        return bestMove;
    }

    /**
     * Логирует рост дерева в файл во время выполнения алгоритма MCTS
     * @param iterations Текущее количество итераций
     */
    private void logTreeGrowth(int iterations) {
        String growthLogPath = getMatchFolderString() + "/" + this.getName() + "__" + this.getRoleName().toString()
                + "/growth_" + turnCount + "_iter" + iterations + ".json";
        File growthFile = new File(growthLogPath);

        try {
            growthFile.getParentFile().mkdirs();
            if (growthFile.exists()) growthFile.delete();
            growthFile.createNewFile();

            BufferedWriter writer = new BufferedWriter(new FileWriter(growthFile));
            writer.write(tree.toJSONbyJackson().toString());
            writer.flush();
            writer.close();
        } catch (IOException e) {
            // Логировать ошибку, но не останавливать алгоритм
            System.err.println("Ошибка при логировании роста дерева: " + e.getMessage());
        }
    }

    @Override
    public LogInfoNode createLogInfoTree(Move selectedMove) {
        MachineState selectedNextState = null;
        try {
            selectedNextState = getStateMachine().getRandomNextState(this.tree.getRoot().getState(), this.getRole(), selectedMove);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return makeLogTreeFromSearchTree(this.tree.getRoot(), selectedNextState);
    }

    public LogInfoNode makeLogTreeFromSearchTree(SearchTreeNode node, MachineState selectedNextState) {
        LogInfoNode result = this.createLogInfoNode(node.getState());

        result.tableData.add(Collections.singletonList("NUM VISITS"));
        result.tableData.add(Collections.singletonList(String.valueOf(node.getStatistics().getNumVisits())));

        // Insert additional statistic data
        CumulativeStatistics statistics = node.getStatistics();
        for (Role role : statistics.getRoles()) {
            result.tableData.add(Collections.singletonList("ROLE :: " + role));

            List<String> actions = new ArrayList<>();
            List<String> actions_scores = new ArrayList<>();
            List<String> actions_num_uses = new ArrayList<>();
            actions.add("Actions");
            actions_scores.add("Scores");
            actions_num_uses.add("Uses num");

            for (Move action : statistics.getUsedActions(role)) {
                StatisticsForActions.ActionStatistics actionStatistics = statistics.get(role, action);
                actions.add(action.toString());
                actions_scores.add(String.valueOf(actionStatistics.getScore()));
                actions_num_uses.add(String.valueOf(actionStatistics.getNumUsed()));
            }

            if (actions.size() > 1) {
                result.tableData.add(actions);
                result.tableData.add(actions_scores);
                result.tableData.add(actions_num_uses);
            }
            else {
                result.tableData.add(Collections.singletonList("NO CALCULATION"));
            }
        }

        if (node.getState().equals(selectedNextState)) {
            result.tableData.add(Collections.singletonList("SELECTED STATE"));
        }

        for (SearchTreeNode child : node.getChildren()) {
            LogInfoNode childInfoNode = this.makeLogTreeFromSearchTree(child, selectedNextState);
            result.children.add(childInfoNode);
        }

        return result;
    }
}