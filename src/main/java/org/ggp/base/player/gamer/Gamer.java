package org.ggp.base.player.gamer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ggp.base.apps.player.config.ConfigPanel;
import org.ggp.base.apps.player.config.EmptyConfigPanel;
import org.ggp.base.apps.player.detail.DetailPanel;
import org.ggp.base.apps.player.detail.EmptyDetailPanel;
import org.ggp.base.player.gamer.exception.AbortingException;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.exception.MetaGamingException;
import org.ggp.base.player.gamer.exception.MoveSelectionException;
import org.ggp.base.player.gamer.exception.StoppingException;
import org.ggp.base.player.gamer.statemachine.sancho.RuntimeGameCharacteristics;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlTerm;
import org.ggp.base.util.match.Match;
import org.ggp.base.util.observer.Event;
import org.ggp.base.util.observer.Observer;
import org.ggp.base.util.observer.Subject;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.ui.GameStateRenderer;

import javax.imageio.ImageIO;
import javax.swing.*;


/**
 * The Gamer class defines methods for both meta-gaming and move selection in a
 * pre-specified amount of time. The Gamer class is based on the <i>algorithm</i>
 * design pattern.
 */
public abstract class Gamer implements Subject
{
    private Match match;
    private GdlConstant roleName;
    protected RuntimeGameCharacteristics mGameCharacteristics;

    protected String gameVisuals = null;

    public Gamer()
    {
        observers = new ArrayList<Observer>();

        // When not playing a match, the variables 'match'
        // and 'roleName' should be NULL. This indicates that
        // the player is available for starting a new match.
        match = null;
        roleName = null;
    }

    /* The following values are recommendations to the implementations
     * for the minimum length of time to leave between the stated timeout
     * and when you actually return from metaGame and selectMove. They are
     * stored here so they can be shared amongst all Gamers. */
    public static final long PREFERRED_METAGAME_BUFFER = 3900;
    public static final long PREFERRED_PLAY_BUFFER = 1900;

    // ==== The Gaming Algorithms ====
    public abstract void metaGame(long timeout) throws MetaGamingException;

    public abstract GdlTerm selectMove(long timeout) throws MoveSelectionException;

    /* Note that the match's goal values will not necessarily be known when
     * stop() is called, as we only know the final set of moves and haven't
     * interpreted them yet. To get the final goal values, process the final
     * moves of the game.
     */
    public abstract void stop() throws StoppingException;  // Cleanly stop playing the match

    public abstract void abort() throws AbortingException;  // Abruptly stop playing the match

    public abstract void preview(Game g, long timeout) throws GamePreviewException;  // Preview a game

    public void setGameVisuals(String gameVisuals) { this.gameVisuals = gameVisuals; }

    // ==== Gamer Profile and Configuration ====
    public abstract String getName();
    public String getSpecies() { return null; }

    public boolean isComputerPlayer() {
        return true;
    }

    public ConfigPanel getConfigPanel() {
        return new EmptyConfigPanel();
    }

    public DetailPanel getDetailPanel() {
        return new EmptyDetailPanel();
    }

    // ==== Accessors ====
    public final Match getMatch() {
        return match;
    }

    public final void setMatch(Match match) {
        this.match = match;
    }

    public final GdlConstant getRoleName() {
        return roleName;
    }

    public final void setRoleName(GdlConstant roleName) {
        this.roleName = roleName;
    }

    // ==== Observer Stuff ====
    private final List<Observer> observers;
    @Override
    public final void addObserver(Observer observer)
    {
        observers.add(observer);
    }

    @Override
    public final void notifyObservers(Event event) {
        for (Observer observer : observers) {
            observer.observe(event);
        }
    }

    // ==== Logging Stuff ====
    protected List<LogInfoNode> logInfoRoots = new ArrayList<>();

    private int currLogInfoNodeIndex = 0;

    public final void logCurrMove(Move selectedMove) {

        //        LogInfoNode root = createLogInfoTree(selectedMove);
//
//        if (root != null) {
///*            if (selectedMove.toString().equals("noop")) {
//                root.children.clear();
//                root.tableData.add(Arrays.asList("NO OPERATION"));
//            }
//*/
//            this.logInfoRoots.add(root);
//        }
    }

    public LogInfoNode createLogInfoTree(Move selectedMove) { return null; }

    public LogInfoNode createLogInfoNode(MachineState state) {
        LogInfoNode result = new LogInfoNode(this.currLogInfoNodeIndex, state);
        this.currLogInfoNodeIndex++;

        result.gameFieldImagePath = this.getMatchFolderString() + "/images/" + this.getName() + "_state_" + state.hashCode() + ".jpg";
        result.stateAsStr = state.toString(); // TODO(check): string representation of given state must represent board's state, not performed actions.

        return result;
    }

    public void writeLogInfoToDotFile() {
        for (int i=0; i<this.logInfoRoots.size(); i++) {
            String dotFilePath = getMatchFolderString() + "/" + this.getName() + "__" + this.roleName.toString() + "/step_" + i + ".dot";
            String dotFileContent = "digraph {\n";
            dotFileContent += "Node[fontname=\"courier new\" shape=\"rect\"]\n";

            List<LogInfoNode> currWave = new ArrayList<>();
            currWave.add(this.logInfoRoots.get(i));

            List<LogInfoNode> bufferWave = new ArrayList<>();

            while (!currWave.isEmpty()) {
                for (LogInfoNode node : currWave) {
                    Gamer.saveStateToJPGimage(this.gameVisuals, node.state, node.gameFieldImagePath);

                    bufferWave.addAll(node.children);

                    int maxColumnCount = 1;
                    for (List<String> row : node.tableData) {
                        if (row.size() > maxColumnCount) { maxColumnCount = row.size(); }
                    }

                    dotFileContent += node.id + "[label=<<TABLE BORDER=\"0\" CELLBORDER=\"1\" CELLSPACING=\"0\">\n";
                    dotFileContent += "  <TR>\n";
                    dotFileContent += "  <TD COLSPAN=\"" + maxColumnCount + "\">GAME FIELD</TD>\n";
                    dotFileContent += "  </TR>\n";
                    dotFileContent += "  <TR>\n";
                    if (node.gameFieldImagePath != null) {
                        File imageFile = new File(node.gameFieldImagePath);
                        dotFileContent += "  <TD colspan=\"" + maxColumnCount + "\" fixedsize=\"true\" width=\"200\" height=\"200\"><IMG SRC=\"" +
                                "../images/" + imageFile.getName() +
                                "\"/></TD>\n";
                    }
                    else {
                        dotFileContent += "  <TD COLSPAN=\"" + maxColumnCount + "\">" + node.stateAsStr + "</TD>\n";
                    }
                    dotFileContent += "  </TR>\n";

                    for (List<String> row : node.tableData) {
                        dotFileContent += "  <TR>\n";

                        int currColumnSize = maxColumnCount / row.size();
                        int lastColumnSize = maxColumnCount / row.size() + maxColumnCount % row.size();
                        int remainColumnToPrintedCount = row.size();
                        for (String str : row) {
                            dotFileContent += "    <TD COLSPAN=\"" + (remainColumnToPrintedCount > 1 ? currColumnSize : lastColumnSize) + "\">" +
                                    str +
                                    "</TD>\n";

                            remainColumnToPrintedCount--;
                        }

                        dotFileContent += "  </TR>\n";
                    }
                    dotFileContent += "</TABLE>>];\n";

                    for (LogInfoNode child : node.children) {
                        dotFileContent += node.id + " -> " + child.id + "\n";
                    }
                }

                currWave.clear();
                currWave.addAll(bufferWave);
                bufferWave.clear();
            }

            dotFileContent += "}";

            File outputLogFile = new File(dotFilePath);
            try {
                outputLogFile.getParentFile().mkdirs();
                outputLogFile.createNewFile();
                FileWriter outputLogFileWriter = new FileWriter(outputLogFile, false);
                outputLogFileWriter.write(dotFileContent);
                outputLogFileWriter.flush();
                outputLogFileWriter.close();
            }
            catch(Exception e) {

                e.printStackTrace();
            }
        }
    }

    public static String saveStateToJPGimage(String gameVisuals, MachineState state, String imageFilePath) {
        // Get visualization properties of the state
        String gameXMLcontent = Match.renderStateXML(state.getContents());

        if (gameVisuals == null || gameVisuals.isEmpty() || gameVisuals.equals("null")) {
            return null;
        }

        BufferedImage outImage = new BufferedImage(600, 600, BufferedImage.TYPE_INT_RGB);
        GameStateRenderer.renderImagefromGameXML(gameXMLcontent, gameVisuals, outImage);

        File outImageFile = new File(imageFilePath);
        try {
            outImageFile.mkdirs();
            outImageFile.createNewFile();
            ImageIO.write(outImage, "jpg", outImageFile);
        }
        catch(Exception e) {
            e.printStackTrace();
        }

        return imageFilePath;
    }

    public class LogInfoNode {
        public final int id;
        public List<List<String>> tableData = new ArrayList<>();
        public String gameFieldImagePath = null;
        public String stateAsStr = null;
        public MachineState state = null;

        public List<LogInfoNode> children = new ArrayList<>();

        public LogInfoNode(int id, MachineState state) {
            this.id = id;
            this.state = state;
        }
    }

    public final String getMatchFolderString() { return "matches/" + this.getMatch().getMatchId(); }

    /* EXAMPLE OF DOT FORMAT FOR A NODE
    digraph G {
  Node[fontname="courier new" shape="rect"]

  1[label=<
<TABLE BORDER="0" CELLBORDER="1" CELLSPACING="0">
  <TR><TD COLSPAN="3">GAME FIELD</TD></TR>
  <TR><TD COLSPAN="3">...<BR/>...<BR/>...</TD></TR>

  <TR>
    <TD>actions</TD>
    <TD>scores</TD>
    <TD>actions_num_used</TD>
  </TR>

  <TR><TD COLSPAN="3">X</TD></TR>

  <TR>
    <TD>[X,-,-]<BR/>[-,X,-]<BR/>[-,-,X]</TD>
    <TD>50<BR/>100+100+50<BR/>100</TD>
    <TD>1<BR/>3<BR/>1</TD>
  </TR>

  <TR><TD COLSPAN="3">O</TD></TR>

  <TR>
    <TD>[O,-,-]</TD>
    <TD>50+0+0+0+50</TD>
    <TD>5</TD>
  </TR>
</TABLE>>];
}

preceding_joint_move  = <(-,-,x),(-,-,-)>

num_visits = 2

X:
Actions = <(-,-,-)>
action_scores = <100+50>
action_num_used = <2>

O:
Actions = <(O,-,-),(-,0,-)>
action_scores = <0,50>
action_num_used = <1,1>
     */
}