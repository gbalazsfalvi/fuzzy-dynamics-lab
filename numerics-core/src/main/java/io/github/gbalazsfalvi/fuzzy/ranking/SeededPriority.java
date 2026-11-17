package io.github.gbalazsfalvi.fuzzy.ranking;

import io.github.gbalazsfalvi.fuzzy.cae.CaeCell;
import io.github.gbalazsfalvi.fuzzy.cae.CellRanker;
import java.math.BigDecimal;
import java.util.SplittableRandom;

/**
 * Seeded random-priority baseline with reproducible per-path keys and the ordinary FIFO fairness.
 *
 * <p>This is a random priority ranking, not a claim of independent uniform selection among active
 * leaves. Path bytes are mixed with the seed before drawing one Java 21 pseudorandom unsigned key.
 * Scores never enter numerical bounds.
 *
 * @param seed the frozen random priority seed
 */
public record SeededPriority(long seed) implements CellRanker {
  /** {@inheritDoc} */
  @Override
  public BigDecimal score(CaeCell cell) {
    long pathHash = seed ^ 0xcbf29ce484222325L;
    for (int index = 0; index < cell.path().bits().length(); index++) {
      pathHash ^= cell.path().bits().charAt(index);
      pathHash *= 0x100000001b3L;
    }
    long key = new SplittableRandom(pathHash).nextLong();
    return new BigDecimal(Long.toUnsignedString(key));
  }
}
