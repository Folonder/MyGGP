package org.ggp.base.apps.server.visualization;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import org.ggp.base.server.event.ServerCompletedMatchEvent;
import org.ggp.base.server.event.ServerNewGameStateEvent;
import org.ggp.base.server.event.ServerNewMatchEvent;
import org.ggp.base.server.event.ServerTimeEvent;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.game.GameRepository;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.match.Match;
import org.ggp.base.util.observer.Event;
import org.ggp.base.util.observer.Observer;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;
import org.ggp.base.util.ui.GameStateRenderer;
import org.ggp.base.util.ui.timer.JTimerBar;

@SuppressWarnings("serial")
public final class VisualizationPanel extends JPanel implements Observer
{
    private final Game theGame;
    private final VisualizationPanel myThis;
    private JTabbedPane tabs = new JTabbedPane();
    private final JTimerBar timerBar;
    private final RenderThread rt;

    public VisualizationPanel(Game theGame)
    {
        super(new GridBagLayout());
        this.theGame = theGame;
        this.myThis = this;
        this.timerBar = new JTimerBar();
        this.rt = new RenderThread();
        this.rt.start();
        this.add(tabs, new GridBagConstraints(0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(5, 5, 5, 5), 5, 5));
        this.add(timerBar, new GridBagConstraints(0, 1, 1, 1, 1.0, 0.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(5, 5, 5, 5), 5, 5));
    }

    private int stepCount = 1;
    @Override
    public void observe(Event event)
    {
        if (event instanceof ServerNewGameStateEvent) {
            MachineState s = ((ServerNewGameStateEvent)event).getState();
            rt.submit(s, stepCount++);
        } else if (event instanceof ServerTimeEvent) {
            timerBar.time(((ServerTimeEvent) event).getTime(), 500);
        } else if (event instanceof ServerCompletedMatchEvent) {
            rt.finish();
            timerBar.stop();
        } else if (event instanceof ServerNewMatchEvent) {
            MachineState s = ((ServerNewMatchEvent) event).getInitialState();
            rt.submit(s, stepCount);
        }
    }

    private class RenderThread extends Thread {
        private final LinkedBlockingQueue<VizJob> queue;

        public RenderThread() {
            this.queue = new LinkedBlockingQueue<>();
        }

        private abstract class VizJob{
            public abstract boolean stop();
            public void render() {}
        };

        private final class StopJob extends VizJob {
            @Override
            public boolean stop() {
                return true;
            }
        }

        private final class RenderJob extends VizJob {
            private MachineState state;
            private int stepNum;

            public RenderJob(MachineState state, int stepNum) {
                this.state = state;
                this.stepNum = stepNum;
            }

            @Override
            public boolean stop() {
                return false;
            }

            @Override
            public void render() {
                JPanel newPanel = null;
                try {
                    String XML = Match.renderStateXML(state.getContents());
                    String XSL = theGame.getStylesheet();
                    if (XSL != null) {
                        newPanel = new VizContainerPanel(XML, XSL, myThis);
                    }
                } catch(Exception ex) {
                    ex.printStackTrace();
                }

                if(newPanel != null) {
                    boolean atEnd = (tabs.getSelectedIndex() == tabs.getTabCount()-1);
                    try {
                        for(int i = tabs.getTabCount(); i < stepNum; i++)
                            tabs.add(new Integer(i+1).toString(), new JPanel());
                        tabs.setComponentAt(stepNum-1, newPanel);
                        tabs.setTitleAt(stepNum-1, new Integer(stepNum).toString());

                        if(atEnd) {
                            tabs.setSelectedIndex(tabs.getTabCount()-1);
                        }
                    } catch(Exception ex) {
                        System.err.println("Adding rendered visualization panel failed for: " + theGame.getKey());
                    }
                }
            }
        }

        public void submit(MachineState state, int stepNum) {
            queue.add(new RenderJob(state, stepNum));
        }

        public void finish() {
            queue.add(new StopJob());
        }

        @Override
        public void run() {
            boolean running = true;
            int interrupted = 0;
            while (running) {
                try {
                    VizJob job = queue.take();
                    interrupted = 0;
                    if (!job.stop()) {
                        job.render();
                    } else {
                        GameStateRenderer.shrinkCache();
                        running = false;
                    }
                } catch (InterruptedException e) {
                    interrupted += 1;
                    if ((interrupted % 10) == 0) {
                        System.err.println("Render thread interrupted "+interrupted+" times in a row");
                    }
                }
            }
        }
    }

    /** Folders:
     * match_<special_name>_<current_date_and_time>/round_<>/bot_<special_name>_<bot_type>/step_<step_index>.dot
     * match_<special_name>_<current_date_and_time>/round_<>/bot_<special_name>_<bot_type>/step_<step_index>.dot
     * match_<special_name>_<current_date_and_time>/images/
     */

    /**
     * Save game's state to the JPG image in the given directory returning it's name
     * @param game           input game
     * @param state          current state of the game
     * @param imageFilePath  path to the directory
     * @return name of the created JPG image */
    private static String saveStateToJPGimage(Game game, MachineState state, String imageFilePath) {
        // Get visualization properties of the state
        String gameXMLcontent = Match.renderStateXML(state.getContents());
        String gameXSLcontent = game.getStylesheet();

        if (gameXSLcontent == null) {
            return null;
        }

        Component c = new JPanel();
        // BufferedImage outImage = new BufferedImage(600, 600, BufferedImage.TYPE_INT_RGB);
        BufferedImage outImage = c.getGraphicsConfiguration().createCompatibleImage(600, 600);
        GameStateRenderer.renderImagefromGameXML(gameXMLcontent, gameXSLcontent, outImage);

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

    // Simple test that loads the nineBoardTicTacToe game and visualizes
    // a randomly-played match, to demonstrate that visualization works.
    public static void main(String args[]) {
        JFrame frame = new JFrame("Visualization Test");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Game theGame = GameRepository.getDefaultRepository().getGame("nineBoardTicTacToe");
        Game theGame = GameRepository.getDefaultRepository().getGame("tictactoe2");
        VisualizationPanel theVisual = new VisualizationPanel(theGame);
        frame.setPreferredSize(new Dimension(1200, 900));
        frame.getContentPane().add(theVisual);
        frame.pack();
        frame.setVisible(true);

        StateMachine theMachine = new CachedStateMachine(new ProverStateMachine());
        theMachine.initialize(theGame.getRules());
        try {
            File file = new File("output.dot");
            FileWriter fw = new FileWriter(file, false);
            fw.write("digraph {\n  Node[shape=rect, fontname=\"Courier New\"];\n");

            int stateIndex = 0;

            Set<GdlSentence> prevStateActions = new HashSet<>();
            Set<GdlSentence> currStateActions = new HashSet<>();

            MachineState theCurrentState = theMachine.getInitialState();
            String stateOutFileName = saveStateToJPGimage(theGame, theCurrentState, "output_" + theCurrentState.hashCode() + ".jpg");
            String gameFieldViewStr;
            if (stateOutFileName != null) {
                gameFieldViewStr = "<TD colspan=\"3\" fixedsize=\"true\" width=\"200\" height=\"200\"><IMG SRC=\"" + stateOutFileName + "\"/></TD>\n";
            }
            else {
                gameFieldViewStr = "<TD colspan=\"3\">" + theCurrentState + "</TD>\n";
            }

            fw.write(
                    stateIndex + "[shape=none label=<\n" +
                            "<TABLE border=\"1\">\n" +
                            "<TR>\n" +
                            "     <TD colspan=\"3\">STEP #" + (stateIndex+1) + "</TD>\n" +
                            "</TR>\n" +
                            "<TR>\n" +
                            "     <TD colspan=\"3\">GAME FIELD:</TD>\n" +
                            "</TR>\n" +
                            "<TR>\n" +
                            gameFieldViewStr +
                            "</TR>\n" +
                            "<TR>\n" +
                            "     <TD>Field A</TD>\n" +
                            "     <TD>Field B</TD>\n" +
                            "     <TD>Field C</TD>\n" +
                            "</TR>\n" +
                            "<TR>\n" +
                            "     <TD>1</TD>\n" +
                            "     <TD>2</TD>\n" +
                            "     <TD>3</TD>\n" +
                            "</TR>\n" +
                            "</TABLE>>];\n"
            );

            do {
                stateIndex++;
                theVisual.observe(new ServerNewGameStateEvent(theCurrentState));

                prevStateActions.clear();
                prevStateActions.addAll(theCurrentState.getContents());

                theCurrentState = theMachine.getRandomNextState(theCurrentState);
                stateOutFileName = saveStateToJPGimage(theGame, theCurrentState, "output_" + theCurrentState.hashCode() + ".jpg");

                currStateActions.clear();
                currStateActions.addAll(theCurrentState.getContents());

                if (stateOutFileName != null) {
                    gameFieldViewStr = "<TD colspan=\"3\" fixedsize=\"true\" width=\"200\" height=\"200\"><IMG SRC=\"" + stateOutFileName + "\"/></TD>\n";
                }
                else {
                    currStateActions.removeAll(prevStateActions);
                    gameFieldViewStr = "<TD colspan=\"3\">" + currStateActions.iterator().next() + "</TD>\n";
                }

                fw.write(
                        stateIndex + "[shape=none label=<\n" +
                                "<TABLE border=\"1\">\n" +
                                "<TR>\n" +
                                "     <TD colspan=\"3\">STEP #" + (stateIndex+1) + "</TD>\n" +
                                "</TR>\n" +
                                "<TR>\n" +
                                "     <TD colspan=\"3\">GAME FIELD:</TD>\n" +
                                "</TR>\n" +
                                "<TR>\n" +
                                gameFieldViewStr +
                                "</TR>\n" +
                                "<TR>\n" +
                                "     <TD>Field A</TD>\n" +
                                "     <TD>Field B</TD>\n" +
                                "     <TD>Field C</TD>\n" +
                                "</TR>\n" +
                                "<TR>\n" +
                                "     <TD>1</TD>\n" +
                                "     <TD>2</TD>\n" +
                                "     <TD>3</TD>\n" +
                                "</TR>\n" +
                                "</TABLE>>];\n"
                );
                fw.write(stateIndex-1 + " -> " + stateIndex + ";\n");

                theCurrentState.hashCode();

                Thread.sleep(250);
                System.out.println("State: " + theCurrentState);
            } while(!theMachine.isTerminal(theCurrentState));
            theVisual.observe(new ServerNewGameStateEvent(theCurrentState));

            fw.write("}");

            fw.flush();
            fw.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}