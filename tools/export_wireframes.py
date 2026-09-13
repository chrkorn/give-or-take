#!/usr/bin/env python3
"""Render each wireframe artboard of a Claude Design canvas to its own PNG.

The canvas is a single .dc.html file holding every screen. Exporting by hand means
screenshotting thirteen times and getting slightly different crops each time; this
walks the document instead, so a re-run after any edit regenerates the whole set
consistently.

Two sets are written:

  <out>/            the artboard alone — clean screens, for report figures
  <out>/annotated/  the artboard with its numbered design notes, for the repository

Requires Playwright (development tooling only; not a dependency of the app):

    pip install playwright && playwright install chromium

Usage:
    python3 tools/export_wireframes.py \
        --input docs/wireframes/give-or-take-wireframes.dc.html \
        --output docs/wireframes
"""

import argparse
import pathlib
import re
import sys

from playwright.sync_api import sync_playwright

# Each screen is a `.col` containing a `.t` slug title, an optional `.sub` subtitle,
# the frame itself, and a trailing notes block. Phone screens mark the frame `.ab`;
# the screen-flow diagram is wider and carries no class, so it is found by size.
COLUMN = ".col"
TITLE = ".t"
ARTBOARD = ".ab"

READ_SLUG = """
(el) => {
  // The title may be prefixed by a badge element such as "4a". Badges are elements;
  // the slug is a bare text node, so dropping element children leaves the slug.
  const clone = el.cloneNode(true);
  [...clone.children].forEach((child) => child.remove());
  return clone.textContent.trim();
}
"""

FRAME_INDEX = """
(col) => {
  const artboard = col.querySelector('.ab');
  const children = [...col.children];
  if (artboard) return children.indexOf(artboard);
  // No .ab: take the tallest child that is not the title, subtitle or notes block.
  let best = -1, bestHeight = 0;
  children.forEach((child, index) => {
    if (child.classList.contains('t') || child.classList.contains('sub')) return;
    const height = child.getBoundingClientRect().height;
    if (height > bestHeight) { bestHeight = height; best = index; }
  });
  return best;
}
"""


def safe_name(slug: str) -> str:
    """Reduce a slug to something safe for a filename, keeping its leading number."""
    cleaned = re.sub(r"[^A-Za-z0-9._-]+", "-", slug).strip("-").lower()
    return cleaned or "artboard"


def export(input_path: pathlib.Path, output_dir: pathlib.Path, scale: int) -> int:
    annotated_dir = output_dir / "annotated"
    output_dir.mkdir(parents=True, exist_ok=True)
    annotated_dir.mkdir(parents=True, exist_ok=True)

    written = 0
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch()
        page = browser.new_page(
            viewport={"width": 2000, "height": 1400}, device_scale_factor=scale
        )
        page.goto(input_path.resolve().as_uri(), wait_until="networkidle")
        page.wait_for_timeout(600)

        columns = page.query_selector_all(COLUMN)
        if not columns:
            print(f"no '{COLUMN}' elements found in {input_path}", file=sys.stderr)
            return 0

        for position, column in enumerate(columns, start=1):
            title = column.query_selector(TITLE)
            slug = page.evaluate(READ_SLUG, title) if title else ""
            name = safe_name(slug or f"{position:02d}-artboard")

            frame_index = page.evaluate(FRAME_INDEX, column)
            frames = column.query_selector_all(":scope > *")
            if frame_index < 0 or frame_index >= len(frames):
                print(f"  skipped {name}: no frame found", file=sys.stderr)
                continue

            frames[frame_index].screenshot(path=str(output_dir / f"{name}.png"))
            column.screenshot(path=str(annotated_dir / f"{name}.png"))
            written += 1
            print(f"  {name}.png")

        browser.close()
    return written


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=pathlib.Path, required=True)
    parser.add_argument("--output", type=pathlib.Path, required=True)
    parser.add_argument(
        "--scale", type=int, default=2, help="device scale factor; 2 keeps print crisp"
    )
    args = parser.parse_args(argv)

    if not args.input.exists():
        print(f"input not found: {args.input}", file=sys.stderr)
        return 1

    print(f"Exporting {args.input} at {args.scale}x")
    written = export(args.input, args.output, args.scale)
    print(f"Wrote {written} artboards to {args.output} and {args.output / 'annotated'}")
    return 0 if written else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
