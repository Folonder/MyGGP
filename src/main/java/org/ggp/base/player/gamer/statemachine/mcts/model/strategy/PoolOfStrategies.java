package org.ggp.base.player.gamer.statemachine.mcts.model.strategy;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

public class PoolOfStrategies {

    static final double MIN_SCORE = 0;
    static final double MAX_SCORE = 100;

    private final SelectionStrategy selectionStrategy;
    private final SelectionStrategyForMatch selectionStrategyForMatch;
    private final ExpansionStrategy expansionStrategy;
    private final PlayoutStrategy playoutStrategy;
    private final CuttingStrategy cuttingStrategy;
    private final PropagationStrategy propagationStrategy;

    public PoolOfStrategies() {
        selectionStrategy = new SelectionStrategy();
        selectionStrategyForMatch = new SelectionStrategyForMatch();
        expansionStrategy = new ExpansionStrategy();
        playoutStrategy = new PlayoutStrategy();
        cuttingStrategy = new CuttingStrategy();
        propagationStrategy = new PropagationStrategy();
    }

    static double normalize(double score) {
        return (score - MIN_SCORE) / (MAX_SCORE - MIN_SCORE);
    }

    static <T> T randomElement(Collection<T> collection) {
        int pos = ThreadLocalRandom.current().nextInt(collection.size());

        int i = 0;
        for (T element : collection) {
            if (i == pos) {
                return element;
            }
            i++;
        }

        return null;
    }

    public SelectionStrategy getSelectionStrategy() {
        return selectionStrategy;
    }
    public SelectionStrategyForMatch getSelectionStrategyForMatch() {
        return selectionStrategyForMatch;
    }
    public ExpansionStrategy getExpansionStrategy() {
        return expansionStrategy;
    }
    public PlayoutStrategy getPlayoutStrategy() {
        return playoutStrategy;
    }
    public CuttingStrategy getCuttingStrategy() {
        return cuttingStrategy;
    }
    public PropagationStrategy getPropagationStrategy() {
        return propagationStrategy;
    }
}