package io.github.gbalazsfalvi.fuzzy.cae;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.TreeMap;

/** Mutable run-local indices; public snapshots never expose these collections. */
final class ActivePartition {
  private final CaeContext context;
  private final LinkedHashMap<CellPath, Entry> leaves = new LinkedHashMap<>();
  private final PriorityQueue<Entry> maximum =
      new PriorityQueue<>(
          Comparator.<Entry, BigDecimal>comparing(Entry::score)
              .reversed()
              .thenComparingLong(Entry::born));
  private final List<NavigableMap<BigDecimal, Integer>> envelope = new ArrayList<>();
  private long born;
  private final CaeSettings settings;
  private final CellRanker ranker;

  ActivePartition(CaeContext context, CaeCell root, CaeSettings settings) {
    this(context, root, settings, null);
  }

  ActivePartition(CaeContext context, CaeCell root, CaeSettings settings, CellRanker ranker) {
    this.context = context;
    this.settings = settings;
    this.ranker = ranker;
    for (int time = 0; time < root.bounds().size(); time++) {
      envelope.add(new TreeMap<>());
    }
    addPrepared(entry(root, born++));
  }

  int size() {
    return leaves.size();
  }

  Optional<Selected> select(int nextRefinement) {
    if (settings.strategy() == CaeSettings.Strategy.UNIFORM
        || nextRefinement % settings.fairnessPeriod() == 0) {
      RefinementEvent.Selection reason =
          settings.strategy() == CaeSettings.Strategy.UNIFORM
              ? RefinementEvent.Selection.UNIFORM
              : RefinementEvent.Selection.FAIRNESS;
      return leaves.values().stream()
          .filter(entry -> entry.cell().path().depth() < settings.maxDepth())
          .findFirst()
          .map(entry -> new Selected(entry.cell(), reason));
    }
    while (!maximum.isEmpty()) {
      Entry entry = maximum.peek();
      if (leaves.get(entry.cell().path()) != entry
          || entry.cell().path().depth() >= settings.maxDepth()) {
        maximum.remove();
      } else {
        RefinementEvent.Selection reason =
            switch (settings.strategy()) {
              case MAXIMUM_BOUND, UNIFORM -> RefinementEvent.Selection.MAXIMUM_BOUND;
              case NEXT_STEP_BOUND -> RefinementEvent.Selection.NEXT_STEP_BOUND;
              case PREDICTED_GAIN_PER_WORK -> RefinementEvent.Selection.PREDICTED_GAIN_PER_WORK;
              case RANKED -> RefinementEvent.Selection.RANKED;
            };
        return Optional.of(new Selected(entry.cell(), reason));
      }
    }
    return Optional.empty();
  }

  void replace(CaeCell parent, CaeCell left, CaeCell right) {
    if (!leaves.containsKey(parent.path())
        || !left.path().equals(parent.path().child(0))
        || !right.path().equals(parent.path().child(1))) {
      throw new IllegalStateException(
          "A refinement must replace one active leaf with both children.");
    }
    // A ranker may fail. Evaluate both scores before mutating any leaf or bound index.
    long nextBorn = Math.addExact(born, 2);
    Entry preparedLeft = entry(left, born);
    Entry preparedRight = entry(right, born + 1);
    leaves.remove(parent.path());
    for (int time = 0; time < envelope.size(); time++) {
      NavigableMap<BigDecimal, Integer> counts = envelope.get(time);
      BigDecimal bound = parent.bounds().get(time);
      int count = counts.get(bound);
      if (count == 1) {
        counts.remove(bound);
      } else {
        counts.put(bound, count - 1);
      }
    }
    addPrepared(preparedLeft);
    addPrepared(preparedRight);
    born = nextBorn;
  }

  List<BigDecimal> bounds() {
    return envelope.stream().map(NavigableMap::lastKey).toList();
  }

  CaeSnapshot snapshot(int refinement) {
    return new CaeSnapshot(
        context, refinement, leaves.values().stream().map(Entry::cell).toList(), bounds());
  }

  private Entry entry(CaeCell cell, long age) {
    BigDecimal score = ranker == null ? RefinementScores.score(cell, settings) : ranker.score(cell);
    if (score == null) {
      throw new ArithmeticException("The external ranker did not return a finite decimal score.");
    }
    return new Entry(cell, age, score);
  }

  private void addPrepared(Entry entry) {
    CaeCell cell = entry.cell();
    leaves.put(cell.path(), entry);
    maximum.add(entry);
    for (int time = 0; time < envelope.size(); time++) {
      envelope.get(time).merge(cell.bounds().get(time), 1, Integer::sum);
    }
  }

  /** Cached score and monotone age make priority queue ties deterministic. */
  private record Entry(CaeCell cell, long born, BigDecimal score) {}

  /** The chosen active cell and the rule that selected it. */
  record Selected(CaeCell cell, RefinementEvent.Selection reason) {}
}
