"""Independent decode and baseline comparison for generated slide PDFs and MP4."""
import argparse
import csv
import json
import re
import subprocess
from pathlib import Path

import pypdfium2 as pdfium
from pypdf import PdfReader


def verify(root, baseline, ffmpeg):
    report = {"pdfs": [], "video": {}}
    for file in sorted(root.glob("slides-final-*.pdf")):
        count = int(file.stem.rsplit("-", 1)[1])
        reader = PdfReader(file)
        assert len(reader.pages) == count
        for index in range(count):
            page = PdfReader(file).pages[index]
            assert tuple(map(int, page.mediabox[2:])) == (960, 540)
            text = "".join(page.extract_text().split())
            assert f"Slide{index+1}" in text and f"Vectortext{index+1}" in text
            image, = list(page.images)
            assert image.image.size == (1024, 768)
            assert image.image.convert("RGB").getpixel((512, 384)) == (20+index, 80, 180)
        item = {"file": file.name, "pages": count, "bytes": file.stat().st_size, "textAndImages": True}
        old = baseline / f"slides-baseline-{count}.pdf" if baseline else None
        if old and old.exists():
            a, b = pdfium.PdfDocument(old), pdfium.PdfDocument(file)
            try:
                for index in range(count):
                    p, q = a[index], b[index]
                    x, y = p.render(scale=1), q.render(scale=1)
                    try:
                        assert x.to_pil().tobytes() == y.to_pil().tobytes(), (index, "Rendered page differs")
                    finally:
                        x.close(); y.close(); p.close(); q.close()
            finally:
                a.close(); b.close()
            # Fixture-level regression bound, not a size promise for arbitrary fonts/slides.
            assert file.stat().st_size < old.stat().st_size * 3
            item.update(baselineBytes=old.stat().st_size, renderedPagesIdentical=count)
        report["pdfs"].append(item)
    assert report["pdfs"]
    video = root / "slideshow-final-1920.mp4"
    result = subprocess.run([str(ffmpeg), "-hide_banner", "-xerror", "-i", str(video), "-f", "null", "-"],
                            capture_output=True, text=True, check=True)
    assert re.search(r"Video: h264[^\n]*1920x1080", result.stderr)
    assert "Audio:" not in result.stderr and "30 fps" in result.stderr
    assert "00:00:10.00" in result.stderr
    report["video"] = {"width": 1920, "height": 1080, "durationMs": 10000, "fps": 30, "audio": False, "fullDecode": True}
    report["samples"] = {}
    for file in sorted(root.glob("*.csv")):
        rows = list(csv.DictReader(file.open(encoding="utf-8")))
        report["samples"][file.name] = {key: max(int(row[key]) for row in rows) for key in ("nativeBytes", "javaBytes")}
    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    parser.add_argument("--baseline", type=Path)
    parser.add_argument("--ffmpeg", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(verify(args.folder, args.baseline, args.ffmpeg), indent=2))
