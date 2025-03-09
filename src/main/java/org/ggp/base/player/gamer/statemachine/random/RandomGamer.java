package org.ggp.base.player.gamer.statemachine.random;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.ggp.base.apps.player.detail.DetailPanel;
import org.ggp.base.apps.player.detail.SimpleDetailPanel;
import org.ggp.base.player.gamer.Gamer;
import org.ggp.base.player.gamer.event.GamerSelectedMoveEvent;
import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

/**
 * RandomGamer is a very simple state-machine-based Gamer that will always
 * pick randomly from the legal moves it finds at any state in the game.
 */
public final class RandomGamer extends StateMachineGamer
{
    @Override
    public String getName() { return "Random"; }

    @Override
    public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        long start = System.currentTimeMillis();

        MachineState currState = getCurrentState();
        List<Move> moves = getStateMachine().getLegalMoves(currState, getRole());
        Move selectedMove = (moves.get(ThreadLocalRandom.current().nextInt(moves.size())));

        long stop = System.currentTimeMillis();

        this.logCurrMove(selectedMove);

        notifyObservers(new GamerSelectedMoveEvent(moves, selectedMove, stop - start));
        return selectedMove;
    }

    @Override
    public StateMachine getInitialStateMachine() {
        return new CachedStateMachine(new ProverStateMachine());
    }

    @Override
    public void preview(Game g, long timeout) throws GamePreviewException {
        // Random gamer does no game previewing.
    }

    @Override
    public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
    {
        // Random gamer does no metagaming at the beginning of the match.
    }

    @Override
    public void stateMachineStop() {
        // Random gamer does no special cleanup when the match ends normally.
        this.writeLogInfoToDotFile();
    }

    @Override
    public void stateMachineAbort() {
        // Random gamer does no special cleanup when the match ends abruptly.
        this.writeLogInfoToDotFile();
    }

    @Override
    public DetailPanel getDetailPanel() {
        return new SimpleDetailPanel();
    }

    @Override
    public LogInfoNode createLogInfoTree(Move selectedMove) {
        MachineState currState = getCurrentState();
        List<MachineState> nextStates = new ArrayList<>();
        MachineState selectedNextState = null;

        if (!selectedMove.toString().equals("noop")) {
            try {
                nextStates = getStateMachine().getNextStates(currState);
                selectedNextState = getStateMachine().getRandomNextState(currState, this.getRole(), selectedMove);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        LogInfoNode root = this.createLogInfoNode(currState);

        for (MachineState ms : nextStates) {
            LogInfoNode child = this.createLogInfoNode(ms);
            if (ms.equals(selectedNextState)) { child.tableData.add(Arrays.asList("SELECTED STATE")); }

            root.children.add(child);
        }

        return root;
    }
}