package org.ggp.base.player.gamer.statemachine.mcts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ggp.base.player.gamer.statemachine.mcts.event.IterationEvent;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTree;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTreeNode;
import org.ggp.base.util.observer.Event;
import org.ggp.base.util.observer.Observer;
import org.ggp.base.util.statemachine.Role;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Класс для непосредственного отслеживания и логирования итераций MCTS
 * Может работать как с Redis, так и с записью в файл
 */
public class MCTSIterationTracer implements Observer {

    private int iterationCounter = 0;
    private File logDirectory;
    private boolean fileLoggingEnabled = true;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Creates a log directory for storing iteration data
     */
    public MCTSIterationTracer() {
        // Create directory for logs
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String directoryName = "mcts_logs_" + dateFormat.format(new Date());

        logDirectory = new File(directoryName);
        if (!logDirectory.exists()) {
            if (logDirectory.mkdirs()) {
                System.out.println("Created log directory: " + logDirectory.getAbsolutePath());
            } else {
                System.err.println("Failed to create log directory: " + directoryName);
                fileLoggingEnabled = false;
            }
        }
    }

    @Override
    public void observe(Event event) {
        if (event instanceof IterationEvent) {
            IterationEvent iterEvent = (IterationEvent) event;

            // Save to file
            if (fileLoggingEnabled) {
                try {
                    String filename = String.format("iteration_%05d_turn_%03d.json",
                            iterEvent.getIterationNumber(),
                            iterEvent.getTurnNumber());

                    File outFile = new File(logDirectory, filename);
                    try (FileWriter writer = new FileWriter(outFile)) {
                        writer.write(mapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(iterEvent.getIterationData()));
                    }

                    System.out.println("Iteration #" + iterEvent.getIterationNumber() +
                            " saved to file: " + outFile.getAbsolutePath());

                } catch (IOException e) {
                    System.err.println("Error writing iteration log to file: " + e.getMessage());
                }
            }

            iterationCounter++;
        }
    }

    /**
     * Traces the Selection stage in MCTS
     */
    public void traceSelection(SearchTree tree, List<SearchTreeNode> path, SearchTreeNode selectedNode) {
        System.out.println("TRACE: Selection - selected path of length " + path.size() +
                ", terminal node: " + (selectedNode != null ? "present" : "null"));
    }

    /**
     * Traces the Expansion stage in MCTS
     */
    public void traceExpansion(SearchTreeNode parentNode, List<SearchTreeNode> expandedNodes, SearchTreeNode selectedNode) {
        System.out.println("TRACE: Expansion - created " + expandedNodes.size() + " nodes, " +
                "selected node: " + (selectedNode != null ? "present" : "null"));
    }

    /**
     * Traces the Playout/Simulation stage in MCTS
     */
    public void tracePlayout(SearchTreeNode startNode, int depth, Map<Role, Double> scores) {
        System.out.println("TRACE: Playout - simulation depth: " + depth +
                ", number of roles with scores: " + (scores != null ? scores.size() : 0));
    }

    /**
     * Traces the Backpropagation stage in MCTS
     */
    public void traceBackpropagation(List<SearchTreeNode> path, Map<Role, Double> scores) {
        System.out.println("TRACE: Backpropagation - path length: " + path.size() +
                ", number of propagated scores: " + (scores != null ? scores.size() : 0));
    }
}