"""Independent host PDF decode: all pages, image pixels/resolution, text, order and rotation."""
import argparse
import csv
import json
from pathlib import Path

from pypdf import PdfReader


def verify(root: Path) -> dict:
    evidence = {}
    for file in sorted(root.glob("photo-final-*.pdf")):
        count = int(file.stem.rsplit("-", 1)[1])
        reader = PdfReader(file)
        assert len(reader.pages) == count, file
        config = json.loads(file.with_suffix(".json").read_text()) if file.with_suffix(".json").exists() else {"imageWidth": 1536, "imageHeight": 1024}
        for index in range(count):
            # Use a fresh reader per image to avoid retaining all decoded image streams on the host.
            page = PdfReader(file).pages[index]
            assert tuple(map(int, page.mediabox[2:])) == (842, 595)
            image, = list(page.images)
            assert image.image.size == (config["imageWidth"], config["imageHeight"])
            assert image.image.convert("RGB").getpixel((config["imageWidth"] // 2, config["imageHeight"] // 2)) == (20 + index, 80, 180)
        evidence[file.name] = {"pages": count, "decoded_images": count}
    reader = PdfReader(root / "photo-4096.pdf")
    assert len(reader.pages) == 2
    image, = list(reader.pages[0].images)
    assert image.image.size == (4096, 2048)
    assert image.image.convert("RGB").getpixel((100, 100)) == (255, 0, 0)
    assert image.image.convert("RGB").getpixel((3996, 100)) == (0, 0, 255)
    evidence["photo-4096.pdf"] = {"pages": 2, "image_size": [4096, 2048]}
    reader = PdfReader(root / "vector-2000.pdf")
    assert len(reader.pages) == 2000
    for index, page in enumerate(reader.pages):
        assert "".join(page.extract_text().split()) == f"Vectorpage{2000-index}"
        assert page.rotation == 90
        assert not page.get("/Resources", {}).get("/XObject")
    evidence["vector-2000.pdf"] = {"pages": 2000, "text_pages": 2000, "rotation": 90}
    for file in sorted(root.glob("photo-final-*.csv")):
        rows = list(csv.DictReader(file.open(encoding="utf-8")))
        evidence[file.name] = {key: max(int(row[key]) for row in rows) for key in ("nativeBytes", "javaBytes", "pssKiB", "elapsedMs")}
    assert any(name.startswith("photo-final-") and name.endswith(".pdf") for name in evidence)
    return evidence


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    args = parser.parse_args()
    print(json.dumps(verify(args.folder), indent=2))
