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

    @Override
    public void observe(Event event) {
 /*
        try {
            if (event instanceof TreeStartEvent) {
                // Создать папку с деревьями
                folder = "all_trees/trees_" + new SimpleDateFormat("dd_MM_yyyy__hh_mm_ss").format(new Date());
                File f = new File(folder);
                f.mkdir();

            } else if (event instanceof TreeEvent) {
                TreeEvent treeEvent = ((TreeEvent) event);

                // Записать дерево в файл
                String fileName = folder + "all_trees/trees_" + treeEvent.getTurnNumber() + (treeEvent.getOnStartMove() ? "_BEGIN" : "_END") + ".json";
                File f = new File(fileName);
                f.createNewFile();
                BufferedWriter bw = new BufferedWriter(new FileWriter(f));
                bw.write(gson.toJson(treeEvent.getTree()));
                bw.flush();
                bw.close();

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
   */
    }


    static class MachineStateSerializer implements JsonSerializer<MachineState> {
        @Override
        public JsonElement serialize(MachineState src, Type typeOfSrc, JsonSerializationContext context) {
            JsonArray result = new JsonArray();
            for (GdlSentence sentence : src.getContents()) {
                result.add(sentence.toString());
            }
            return result;
        }
    }
}
