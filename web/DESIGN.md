---
name: Fuzzy Dynamics Lab workbench
description: A paper-and-ink research instrument for finite fuzzy dynamics.
colors:
  primary: "#193f46"
  certified: "#1a665e"
  reference: "#a36e18"
  surface: "#f5f5f0"
  paper: "#ffffff"
  ink: "#233638"
  muted: "#596963"
  rule: "#dce2de"
  danger: "#9c3b28"
typography:
  heading:
    fontFamily: "IBM Plex Sans, sans-serif"
    fontSize: "30px"
    fontWeight: 500
    lineHeight: 1.15
    letterSpacing: "-0.03em"
  body:
    fontFamily: "IBM Plex Sans, sans-serif"
    fontSize: "14px"
    fontWeight: 400
    lineHeight: 1.6
  measurement:
    fontFamily: "IBM Plex Mono, monospace"
    fontSize: "11px"
    fontWeight: 400
    lineHeight: 1.6
rounded:
  control: "3px"
  panel: "0px"
spacing:
  compact: "8px"
  control: "16px"
  panel: "24px"
  canvas: "38px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.paper}"
    rounded: "{rounded.control}"
    height: "43px"
    padding: "0 15px"
---

# Workbench visual system

## Overview

**Creative North Star: "The inspectable research instrument"**

Mode: Operate. Direction: a paper-and-ink research instrument with precise ruled plots, a stable experiment rail, and a wide observation canvas. The first viewport contains the experiment controls and initial profile; no promotional hero.

## Colors

**The Evidence Color Rule.** Teal denotes numerical representatives and retained certificates. Ochre marks interval enclosures and tolerance. These colors supplement visible text and geometry; color alone never communicates whether tolerance was reached.

## Typography

Self-hosted IBM Plex Sans carries interface text; IBM Plex Mono is reserved for measurements and exact data. Text uses high-contrast ink. Focus uses an ochre outline. Small plot labels scale up on mobile so the SVG viewBox does not make them illegible.

## Layout

The shell uses a 284px control rail and fluid canvas; below 1000px the rail becomes a compact top form, and below 640px all plots stack. Main plot and bound plot have different visual weight. Labels stay adjacent to controls. Tables scroll within their own region.

**The Domain Rule.** Spatial and membership axes retain the declared unit domain. Two-dimensional spatial plots use equal axis scaling. Finite-state points render above the enclosure layer. No viewport or data fit changes the scientific domain.

## Elevation & Depth

Panels use a single thin rule and no shadow. Tonal differences separate the experiment rail, canvas, and plotting paper. Active-job motion is a small pulse, disabled for reduced motion.

## Shapes

Controls use a small corner radius; scientific panels remain square. Small circular points represent atoms, and rectangular boxes represent interval products. Shapes carry mathematical meaning rather than decorative illustration.

## Components

Signature interaction: a time cursor changes the finite-state geometry while the complete-horizon bound stays visible. Best/last selection exposes nonmonotone refinement without concealing the retained incumbent.

Run has a strong petrol fill; Cancel is secondary and visibly named. Inputs have adjacent descriptions, visible validation errors, and high-contrast focus outlines. Exact details use disclosure sections, a cell selector, and string-preserving values. Saved evidence has explicit loading, unavailable, and populated states.

## Do's and Don'ts

- Do plot finite representatives as points or stems.
- Do retain exact API strings in the numerical inspector and export.
- Do explain scope and unavailable reference quantities beside the evidence.
- Don't interpolate finite representatives into a continuous membership curve.
- Don't use neon, purple, gradients, or decorative scientific imagery.
- Don't turn a resource exit into a success label.
