package org.ggp.base.player.gamer.statemachine.mcts.observer;

import com.google.gson.*;
import org.ggp.base.player.gamer.statemachine.mcts.event.TreeEvent;
import org.ggp.base.player.gamer.statemachine.mcts.event.TreeStartEvent;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.observer.Event;
import org.ggp.base.util.observer.Observer;
import org.ggp.base.util.statemachine.MachineState;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TreeObserver implements Observer {

    private final Gson gson = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(MachineState.class, new MachineStateSerializer())
            .create();
    private String folder;
    private int growthLogCounter = 0;
    private boolean loggingEnabled = true;

    @Override
    public void observe(Event event) {
        if (!loggingEnabled) return;

        try {
            if (event instanceof TreeStartEvent) {
                // Create a folder for the trees with timestamp for uniqueness
                folder = "all_trees/trees_" + new SimpleDateFormat("dd_MM_yyyy_HH_mm_ss").format(new Date());
                File f = new File(folder);
                f.mkdirs();

                // Reset counters
                growthLogCounter = 0;
                System.out.println("Tree logging initialized. Output directory: " + folder);
            } else if (event instanceof TreeEvent) {
                TreeEvent treeEvent = ((TreeEvent) event);

                // Format the filename with padded numbers for proper sorting
                String fileName;

                if (treeEvent.isGrowthEvent()) {
                    // Extract iteration count from event if available, or use counter
                    // Format: tree_001_growth_00000.json (for turn 1, growth log 0)
                    String growthLogId = String.format("%05d", growthLogCounter++);
                    String turnId = String.format("%03d", treeEvent.getTurnNumber());
                    fileName = folder + "/tree_" + turnId + "_growth_" + growthLogId + ".json";
                } else if (treeEvent.isFinalTree()) {
                    // This is the final tree after a move
                    String turnId = String.format("%03d", treeEvent.getTurnNumber());
                    fileName = folder + "/tree_" + turnId + "_FINAL.json";

                    // Reset growth counter for next turn
                    growthLogCounter = 0;
                    System.out.println("Completed logging for turn " + treeEvent.getTurnNumber());
                } else {
                    // Fallback for legacy events
                    String turnId = String.format("%03d", treeEvent.getTurnNumber());
                    fileName = folder + "/tree_" + turnId +
                            (treeEvent.getOnStartMove() ? "_BEGIN" : "_END") + ".json";
                }

                // Write the tree to file
                File f = new File(fileName);
                f.createNewFile();
                BufferedWriter bw = new BufferedWriter(new FileWriter(f));
                bw.write(gson.toJson(treeEvent.getTree()));
                bw.flush();
                bw.close();
            }
        } catch (IOException e) {
            System.err.println("Error writing tree log: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Enable or disable logging
     * @param enabled Whether logging should be enabled
     */
    public void setLoggingEnabled(boolean enabled) {
        this.loggingEnabled = enabled;
    }

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
}