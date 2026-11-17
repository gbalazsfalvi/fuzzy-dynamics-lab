package io.github.gbalazsfalvi.fuzzy.geometry;

import static org.junit.jupiter.api.Assertions.*;

import io.github.gbalazsfalvi.fuzzy.arithmetic.DecimalInterval;
import io.github.gbalazsfalvi.fuzzy.cae.CellPath;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaeGeometryTest {
  @Test
  void constructsExactBallsAndHullsWithoutClippingOrDecimalRounding() {
    Box ball = Box.ball(DecimalPoint.of("0.01234567890123456789", "1"), new BigDecimal("0.1"));
    assertEquals(
        DecimalInterval.of("-0.08765432109876543211", "0.11234567890123456789"), ball.axis(0));
    assertEquals(DecimalInterval.of("0.9", "1.1"), ball.axis(1));
    assertEquals(
        Box.point(DecimalPoint.of("0.2")), Box.ball(DecimalPoint.of("0.2"), BigDecimal.ZERO));
    Box hull =
        Box.of(DecimalInterval.of("0", "0.2"), DecimalInterval.of("0.8", "1"))
            .hull(Box.point(DecimalPoint.of("0.4", "0.5")));
    assertEquals(Box.of(DecimalInterval.of("0", "0.4"), DecimalInterval.of("0.5", "1")), hull);
    assertThrows(
        IllegalArgumentException.class,
        () -> Box.ball(DecimalPoint.of("0.2"), new BigDecimal("-0.1")));
    assertThrows(IllegalArgumentException.class, () -> ball.hull(Box.unit(1)));
  }

  @Test
  void rotatesTieBreakingButAlwaysBisectsALongestSide() {
    List<Box> vertical = Box.unit(2).bisectLongest(1);
    assertEquals(DecimalInterval.of("0", "1"), vertical.getFirst().axis(0));
    assertEquals(DecimalInterval.of("0", "0.5"), vertical.getFirst().axis(1));
    assertEquals(DecimalInterval.of("0.5", "1"), vertical.getLast().axis(1));
    Box unequal = Box.of(DecimalInterval.of("0", "1"), DecimalInterval.of("0", "0.25"));
    assertEquals(DecimalInterval.of("0", "0.5"), unequal.bisectLongest(1).getFirst().axis(0));
    assertThrows(IllegalArgumentException.class, () -> Box.unit(2).bisectLongest(-1));
    assertThrows(IllegalArgumentException.class, () -> Box.unit(2).bisectLongest(2));
    Box depthTwo = new CellPath("01").box(2);
    assertEquals(Box.of(DecimalInterval.of("0", "0.5"), DecimalInterval.of("0.5", "1")), depthTwo);
    Box depthThree = new CellPath("010").box(2);
    assertEquals(DecimalInterval.of("0", "0.25"), depthThree.axis(0));
    assertThrows(IllegalArgumentException.class, () -> CellPath.root().box(0));
  }
}
