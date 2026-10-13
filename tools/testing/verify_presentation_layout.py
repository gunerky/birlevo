"""Local QA: compare native measurement inventories and pre-change decoded PNG pixels."""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageChops


def verify(baseline: Path, after: Path) -> dict:
    metrics = after / "presentation-layout"
    phone = json.loads((metrics / "measurements-phone.json").read_text(encoding="utf-8"))
    assert len(phone) == 66, "Expected 22 script samples × 3 slide layouts"
    for mode in ("large-text", "tablet"):
        other = json.loads((metrics / f"measurements-{mode}.json").read_text(encoding="utf-8"))
        assert phone == other, f"Output measurement changed with UI mode/language: {mode}"
    compared = []
    for theme in ("CLASSIC", "OCEAN", "WARM"):
        for layout in ("TEXT_IMAGE", "IMAGE_LEFT", "IMAGE_TOP"):
            name = f"design-{theme}-{layout}.png"
            with Image.open(baseline / name) as old, Image.open(after / name) as new:
                assert old.size == new.size == (1280, 720), f"Size changed: {name}"
                difference = ImageChops.difference(old.convert("RGBA"), new.convert("RGBA"))
                assert all(channel.getbbox() is None for channel in difference.split()), f"Pixels changed: {name}"
            compared.append(name)
    return {"measurement_records_per_mode": len(phone), "modes": ["phone", "large-text", "tablet"],
            "all_measurements_equal": True, "baseline_pngs_equal": compared,
            "scope": "Same QA emulator; not a font/Office/physical device guarantee."}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--after", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    result = verify(args.baseline, args.after)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print("66 measurements equal across three UI modes/languages; 9 pre-change PNGs pixel-identical.")
