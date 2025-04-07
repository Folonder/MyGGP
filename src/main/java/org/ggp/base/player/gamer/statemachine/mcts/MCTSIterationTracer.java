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
 * Class for directly tracking and logging MCTS iterations
 * Can work with both Redis and file writing
 */
public class MCTSIterationTracer implements Observer {

    private int iterationCounter = 0;
    private File logDirectory;
    private boolean fileLoggingEnabled = true;
    private final ObjectMapper mapper = new ObjectMapper();
    private String sessionId;

    /**
     * Creates a log directory for storing iteration data
     */
    public MCTSIterationTracer() {
        // Default constructor will use timestamp-based session ID
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        this.sessionId = "mcts_" + dateFormat.format(new Date());
        initLogDirectory();
    }

    /**
     * Creates a log directory for storing iteration data with specified session ID
     */
    public MCTSIterationTracer(String sessionId) {
        this.sessionId = sessionId;
        initLogDirectory();
    }

    /**
     * Initialize log directory in matches folder
     */
    private void initLogDirectory() {
        // Create directory for logs in matches folder
        String directoryName = "matches/" + sessionId;

        System.out.println("Initializing log directory: " + directoryName);

        // Ensure parent directory exists
        File matchesDir = new File("matches");
        if (!matchesDir.exists()) {
            if (matchesDir.mkdir()) {
                System.out.println("Created matches directory: " + matchesDir.getAbsolutePath());
            } else {
                System.err.println("Failed to create matches directory");
                fileLoggingEnabled = false;
                return;
            }
        }

        logDirectory = new File(directoryName);
        if (!logDirectory.exists()) {
            if (logDirectory.mkdirs()) {
                System.out.println("Created log directory: " + logDirectory.getAbsolutePath());
            } else {
                System.err.println("Failed to create log directory: " + directoryName);
                fileLoggingEnabled = false;
            }
        } else {
            System.out.println("Log directory already exists: " + logDirectory.getAbsolutePath());
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