package org.ggp.base.player.gamer.statemachine.mcts;

import org.ggp.base.player.gamer.statemachine.mcts.event.TreeEvent;
import org.ggp.base.player.gamer.statemachine.mcts.event.TreeStartEvent;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTree;
import org.ggp.base.player.gamer.statemachine.mcts.observer.TreeObserver;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;

/**
 * ���������������� ����� ��� ������������ ����������� �������� MCTS
 */
public class MCTSIterationLoggerDemo {

    /**
     * ������������� ����������� �������� MCTS
     *
     * @param stateMachine ������� ������
     * @param iterations ���������� �������� ��� ����������
     * @param turnNumber ����� �������� ����
     */
    public static void demonstrateIterationLogging(StateMachine stateMachine, int iterations, int turnNumber) {
        try {
            // Create search tree
            SearchTree tree = new SearchTree(stateMachine);
            tree.setTurnNumber(turnNumber);

            // Create and connect observer
            TreeObserver observer = new TreeObserver("direct_demo_" + System.currentTimeMillis());
            tree.addObserver(observer);

            // Send event about tree start
            observer.observe(new TreeStartEvent());

            // Send event about initial tree state
            observer.observe(new TreeEvent(tree, turnNumber, false, false));

            // Run direct Redis test first
            MCTSIterationTest.testDirectRedisSaving();

            // Perform specified number of iterations
            for (int i = 0; i < iterations; i++) {
                // Perform one MCTS iteration
                tree.grow();

                // Every 10 iterations send tree growth event
                if ((i + 1) % 10 == 0) {
                    observer.observe(new TreeEvent(tree, turnNumber, true, false));
                }
            }

            // ���������� ������� � ��������� ��������� ������
            observer.observe(new TreeEvent(tree, turnNumber, false, true));

            System.out.println("������� ��������� ����������� " + iterations + " �������� MCTS ��� ���� " + turnNumber);

        } catch (MoveDefinitionException e) {
            System.err.println("������ ��� ���������� �������� MCTS: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // ����������� �������
            System.out.println("���������� ������������ �����������");
        }
    }
}