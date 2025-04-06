/**
 * Tests direct Redis saving capabilities
 */
package org.ggp.base.player.gamer.statemachine.mcts;

import org.ggp.base.player.gamer.statemachine.mcts.event.IterationEvent;
import org.ggp.base.player.gamer.statemachine.mcts.event.TreeEvent;
import org.ggp.base.player.gamer.statemachine.mcts.event.TreeStartEvent;
import org.ggp.base.player.gamer.statemachine.mcts.model.tree.SearchTree;
import org.ggp.base.player.gamer.statemachine.mcts.observer.TreeObserver;
import org.ggp.base.player.gamer.statemachine.mcts.utils.RedisHelper;
import org.ggp.base.util.observer.Event;
import org.ggp.base.util.observer.Observer;
import org.ggp.base.util.statemachine.StateMachine;

/**
 * Простой класс для тестирования логирования итераций MCTS
 */
public class MCTSIterationTest {

    /**
     * Debug observer class for MCTS events
     */
    private static class DebugObserver implements Observer {
        @Override
        public void observe(Event event) {
            System.out.println("DEBUG: Received event: " + event.getClass().getSimpleName());

            if (event instanceof IterationEvent) {
                IterationEvent iterEvent = (IterationEvent) event;
                System.out.println("  Iteration #" + iterEvent.getIterationNumber());
                System.out.println("  Turn #" + iterEvent.getTurnNumber());
                System.out.println("  Has data: " + (iterEvent.getIterationData() != null ? "yes" : "no"));
            }
        }
    }

    /**
     * Performs a single MCTS iteration and logs it
     */
    public static void testSingleIteration(StateMachine stateMachine) {
        try {
            System.out.println("\n=== START OF SINGLE MCTS ITERATION TEST ===\n");

            // Create search tree
            SearchTree tree = new SearchTree(stateMachine);
            tree.setTurnNumber(1);

            // Create main observer
            TreeObserver mainObserver = new TreeObserver("test_session");
            tree.addObserver(mainObserver);

            // Create debug observer
            DebugObserver debugObserver = new DebugObserver();
            tree.addObserver(debugObserver);

            // Send event about tree start
            mainObserver.observe(new TreeStartEvent());

            // Send event about initial tree state
            mainObserver.observe(new TreeEvent(tree, 1, false, false));

            System.out.println("\n--- Performing one MCTS iteration ---\n");

            // Perform one iteration
            tree.grow();

            // Send tree growth event
            mainObserver.observe(new TreeEvent(tree, 1, true, false));

            System.out.println("\n=== END OF SINGLE MCTS ITERATION TEST ===\n");

        } catch (Exception e) {
            System.err.println("ERROR during MCTS iteration test: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void testDirectRedisSaving() {
        try {
            System.out.println("\n=== DIRECT REDIS SAVING TEST ===\n");

            // Create a simple test object
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode testNode = mapper.createObjectNode();
            testNode.put("testField", "testValue");
            testNode.put("timestamp", System.currentTimeMillis());

            String jsonData = mapper.writeValueAsString(testNode);

            // Try saving directly to Redis
            String testSessionId = "direct_test_" + System.currentTimeMillis();
            boolean success = RedisHelper.saveIterationData(testSessionId, 1, 1, jsonData);

            System.out.println("Direct Redis saving test: " + (success ? "SUCCESSFUL" : "FAILED"));

            // List keys to verify
            RedisHelper.listRedisKeys("mcts:" + testSessionId + ":*");

            System.out.println("\n=== END OF DIRECT REDIS SAVING TEST ===\n");
        } catch (Exception e) {
            System.err.println("ERROR during direct Redis test: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Performs multiple MCTS iterations and logs them
     */
    public static void testMultipleIterations(StateMachine stateMachine, int numIterations) {
        try {
            System.out.println("\n=== START OF MULTIPLE MCTS ITERATIONS TEST ===\n");

            // Create search tree
            SearchTree tree = new SearchTree(stateMachine);
            tree.setTurnNumber(1);

            // Create main observer
            TreeObserver mainObserver = new TreeObserver("test_session");
            tree.addObserver(mainObserver);

            // Send event about tree start
            mainObserver.observe(new TreeStartEvent());

            // Send event about initial tree state
            mainObserver.observe(new TreeEvent(tree, 1, false, false));

            System.out.println("\n--- Performing " + numIterations + " MCTS iterations ---\n");

            // Perform multiple iterations
            for (int i = 0; i < numIterations; i++) {
                System.out.println("Performing iteration #" + (i+1) + "...");
                tree.grow();

                // Every 5 iterations send tree growth event
                if ((i + 1) % 5 == 0) {
                    mainObserver.observe(new TreeEvent(tree, 1, true, false));
                    System.out.println("Recorded intermediate tree state");
                }
            }

            // Send event about final tree state
            mainObserver.observe(new TreeEvent(tree, 1, false, true));

            System.out.println("\n=== END OF MULTIPLE MCTS ITERATIONS TEST ===\n");

        } catch (Exception e) {
            System.err.println("ERROR during MCTS iterations test: " + e.getMessage());
            e.printStackTrace();
        }
    }
}