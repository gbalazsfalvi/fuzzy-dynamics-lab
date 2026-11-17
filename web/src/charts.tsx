import { useEffect, useState } from "react";
import {
  integerTicks,
  numeric,
  profileGrade,
  UNIT_TICKS,
  unitPosition,
} from "./data";
import type { Preset, Snapshot, Result } from "./data";

const W = 720,
  H = 330,
  L = 74,
  R = 24,
  T = 22,
  B = 45;
function geometry(dimension: number, availableWidth: number) {
  const width = Math.min(dimension === 2 ? 400 : W, availableWidth),
    // Equal spatial scales matter for 2D geometry; scalar plots retain enough
    // vertical space for readable labels instead of shrinking a desktop SVG.
    height = dimension === 2 ? width - L - R + T + B : width < 480 ? 260 : H;
  return {
    W: width,
    H: height,
    X: (value: number | string) => unitPosition(value, L, width - L - R),
    Y: (value: number | string) =>
      height - B - unitPosition(value, 0, height - B - T),
  };
}

/** Match SVG coordinates to its content width so mobile text keeps its size. */
function useChartGeometry(dimension: number) {
  const [element, setElement] = useState<SVGSVGElement | null>(null);
  const [width, setWidth] = useState(W);
  useEffect(() => {
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => {
      if (entry.contentRect.width > 0) setWidth(entry.contentRect.width);
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, [dimension, element]);
  return { ref: setElement, ...geometry(dimension, width) };
}

function Axes({
  dimension = 1,
  layout,
}: {
  dimension?: number;
  layout: ReturnType<typeof geometry>;
}) {
  const { W, H, X, Y } = layout;
  return (
    <g className="plot-axes">
      {UNIT_TICKS.map((tick) => (
        <g key={tick}>
          <line
            className="grid-line"
            x1={L}
            x2={W - R}
            y1={Y(tick)}
            y2={Y(tick)}
          />
          <text x={L - 13} y={Y(tick) + 4} textAnchor="end">
            {tick}
          </text>
          <line x1={X(tick)} x2={X(tick)} y1={H - B} y2={H - B + 5} />
          <text x={X(tick)} y={H - B + 22} textAnchor="middle">
            {tick}
          </text>
        </g>
      ))}
      <path className="axis-line" d={`M ${L} ${T} V ${H - B} H ${W - R}`} />
      <text x={(L + W - R) / 2} y={H - 3} textAnchor="middle">
        {dimension === 2 ? "x₁ · unit domain" : "x · unit domain"}
      </text>
      <text
        transform={`translate(15 ${(T + H - B) / 2}) rotate(-90)`}
        textAnchor="middle"
      >
        {dimension === 2 ? "x₂" : "Membership grade μ"}
      </text>
    </g>
  );
}

export function InitialProfile({ preset }: { preset: Preset }) {
  const layout = useChartGeometry(preset.dimension);
  const { ref, W, H, X, Y } = layout;
  const points = Array.from({ length: 401 }, (_, i) => [
    i / 400,
    profileGrade(preset.profile.peaks, [i / 400]),
  ]);
  return (
    <svg
      ref={ref}
      className={`scientific-chart initial-chart ${preset.dimension === 2 ? "two-d" : ""}`}
      viewBox={`0 0 ${W} ${H}`}
      role="img"
      aria-label={`Initial ${preset.dimension}-dimensional membership profile. ${preset.dimension === 1 ? "Sampled display preview." : "Peak centers and square support boundaries."}`}
    >
      <Axes dimension={preset.dimension} layout={layout} />
      {preset.dimension === 1 ? (
        <polyline
          className="profile-line"
          points={points.map(([x, y]) => `${X(x)},${Y(y)}`).join(" ")}
        />
      ) : (
        preset.profile.peaks.map((peak, index) => {
          const x = numeric(peak.center[0]),
            y = numeric(peak.center[1]),
            width = numeric(peak.width);
          return (
            <g key={index}>
              <rect
                className="support-box"
                x={X(x - width)}
                y={Y(y + width)}
                width={X(x + width) - X(x - width)}
                height={Y(y - width) - Y(y + width)}
              />
              <circle className="atom" cx={X(x)} cy={Y(y)} r={4}>
                <title>{`Peak center (${peak.center.join(", ")}), height ${peak.height}, width ${peak.width}`}</title>
              </circle>
              <text className="peak-label" x={X(x) + 9} y={Y(y) - 9}>
                μ={peak.height}
              </text>
            </g>
          );
        })
      )}
    </svg>
  );
}

export function StatePlot({
  snapshot,
  time,
  dimension,
  enclosures,
}: {
  snapshot: Snapshot;
  time: number;
  dimension: number;
  enclosures: boolean;
}) {
  const layout = useChartGeometry(dimension);
  const { ref, W, H, X, Y } = layout;
  return (
    <svg
      ref={ref}
      className={`scientific-chart state-chart ${dimension === 2 ? "two-d" : ""}`}
      viewBox={`0 0 ${W} ${H}`}
      role="img"
      aria-label={`Finite representatives at time ${time}. ${enclosures ? "Directed interval reachable enclosures shown." : "Enclosures hidden."}`}
    >
      <Axes dimension={dimension} layout={layout} />
      {snapshot.cells.map((cell) => {
        const step = cell.trajectory.find((item) => item.time === time);
        if (!step) return null;
        const grade = numeric(cell.membership.grade),
          x = X(step.representative[0]);
        const y = Y(dimension === 2 ? step.representative[1] : grade);
        const opacity = Math.max(0.13, grade * 0.8);
        return (
          <g key={cell.path}>
            {enclosures &&
              (dimension === 1 ? (
                <g className="enclosure" opacity={opacity}>
                  <line
                    x1={X(step.reachable[0].lower)}
                    x2={X(step.reachable[0].upper)}
                    y1={y}
                    y2={y}
                  />
                  <line
                    x1={X(step.reachable[0].lower)}
                    x2={X(step.reachable[0].lower)}
                    y1={y - 4}
                    y2={y + 4}
                  />
                  <line
                    x1={X(step.reachable[0].upper)}
                    x2={X(step.reachable[0].upper)}
                    y1={y - 4}
                    y2={y + 4}
                  />
                </g>
              ) : (
                <rect
                  className="reachable-box"
                  opacity={opacity}
                  x={X(step.reachable[0].lower)}
                  y={Y(step.reachable[1].upper)}
                  width={
                    X(step.reachable[0].upper) - X(step.reachable[0].lower)
                  }
                  height={
                    Y(step.reachable[1].lower) - Y(step.reachable[1].upper)
                  }
                />
              ))}
            {dimension === 1 && (
              <line
                className="atom-stem"
                opacity={Math.max(0.2, grade)}
                x1={x}
                x2={x}
                y1={Y(0)}
                y2={y}
              />
            )}
          </g>
        );
      })}
      {snapshot.cells.map((cell) => {
        const step = cell.trajectory.find((item) => item.time === time);
        if (!step) return null;
        const grade = numeric(cell.membership.grade);
        return (
          <circle
            key={cell.path}
            className={grade === 0 ? "zero-atom" : "atom"}
            opacity={dimension === 2 ? Math.max(0.2, grade) : 1}
            cx={X(step.representative[0])}
            cy={Y(dimension === 2 ? step.representative[1] : grade)}
            r={grade === 0 ? 2 : 3}
          >
            <title>{`Cell ${cell.path || "root"}; representative (${step.representative.join(", ")}); grade ${cell.membership.grade}; enclosure ${step.reachable.map((a) => `[${a.lower}, ${a.upper}]`).join(" × ")}; radius ${step.radius}`}</title>
          </circle>
        );
      })}
    </svg>
  );
}

export function BoundPlot({
  result,
  horizon,
  tolerance,
  mode,
}: {
  result: Result;
  horizon: number;
  tolerance: string;
  mode: "horizon" | "refinement";
}) {
  const { ref, W, H, Y } = useChartGeometry(1);
  if (!result.best)
    return (
      <p className="empty-inline">
        No complete certificate is available for this outcome.
      </p>
    );
  const entries = result.refinementCurves;
  const xmax =
    mode === "horizon"
      ? Math.max(1, horizon)
      : Math.max(1, ...entries.map((e) => e.refinement));
  const px = (value: number) => L + (value / xmax) * (W - L - R);
  const boundPoints = result.best.bounds
    .map((bound, i) => `${px(i)},${Y(bound)}`)
    .join(" ");
  return (
    <svg
      ref={ref}
      className="scientific-chart bound-chart"
      viewBox={`0 0 ${W} ${H}`}
      role="img"
      aria-label={
        mode === "horizon"
          ? "Certified upper bound at each integer time."
          : "Current and retained best certificate by committed refinement."
      }
    >
      <g className="plot-axes">
        {UNIT_TICKS.map((tick) => (
          <g key={tick}>
            <line
              className="grid-line"
              x1={L}
              x2={W - R}
              y1={Y(tick)}
              y2={Y(tick)}
            />
            <text x={L - 12} y={Y(tick) + 4} textAnchor="end">
              {tick}
            </text>
          </g>
        ))}
        <path className="axis-line" d={`M ${L} ${T} V ${H - B} H ${W - R}`} />
        {integerTicks(
          mode === "horizon"
            ? horizon
            : Math.max(0, ...entries.map((entry) => entry.refinement)),
        ).map((tick) => (
          <text key={tick} x={px(tick)} y={H - B + 22} textAnchor="middle">
            {tick}
          </text>
        ))}
        <text x={(L + W - R) / 2} y={H - 3} textAnchor="middle">
          {mode === "horizon" ? "Integer time t" : "Committed refinements"}
        </text>
        <text
          transform={`translate(15 ${(T + H - B) / 2}) rotate(-90)`}
          textAnchor="middle"
        >
          Endograph upper bound
        </text>
      </g>
      <line
        className="tolerance-line"
        x1={L}
        x2={W - R}
        y1={Y(tolerance)}
        y2={Y(tolerance)}
      />
      {mode === "horizon" ? (
        <>
          <polyline className="bound-line" points={boundPoints} />
          {result.best.bounds.map((bound, i) => (
            <circle
              key={i}
              className="bound-dot"
              cx={px(i)}
              cy={Y(bound)}
              r={3}
            >
              <title>
                t={i}; bound={bound}
              </title>
            </circle>
          ))}
        </>
      ) : (
        <>
          <polyline
            className="current-line"
            points={entries
              .map((e) => `${px(e.refinement)},${Y(e.currentBound)}`)
              .join(" ")}
          />
          <polyline
            className="bound-line"
            points={entries
              .map((e) => `${px(e.refinement)},${Y(e.bestBound)}`)
              .join(" ")}
          />
          {entries.map((e) => (
            <circle
              className="bound-dot"
              key={e.refinement}
              cx={px(e.refinement)}
              cy={Y(e.bestBound)}
              r={1.7}
            >
              <title>{`Refinement ${e.refinement}; current ${e.currentBound}; best ${e.bestBound}; active cells ${e.activeCells}`}</title>
            </circle>
          ))}
        </>
      )}
    </svg>
  );
}
