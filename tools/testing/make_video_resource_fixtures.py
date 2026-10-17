"""Generate original high-resolution/time-varying long-video QA inputs on the host only."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import imageio_ffmpeg


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("folder", type=Path)
    args = parser.parse_args()
    args.folder.mkdir(parents=True, exist_ok=True)
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    manifest = {}
    for name, width, height, seconds, level in [
        ("high.mp4", 3840, 2160, 8, "5.1"),
        ("long.mp4", 1280, 720, 599, "3.1"),
    ]:
        target = args.folder / name
        # Three real content phases, a flashing center, four fixed orientation markers and audio.
        change = seconds / 3
        filters = (f"drawbox=color=lime:t=fill:enable='gte(t,{change})',"
                   f"drawbox=color=blue:t=fill:enable='gte(t,{change * 2})',"
                   "drawbox=x=0:y=0:w=iw/8:h=ih/8:color=yellow:t=fill,"
                   "drawbox=x=iw*7/8:y=0:w=iw/8:h=ih/8:color=cyan:t=fill,"
                   "drawbox=x=0:y=ih*7/8:w=iw/8:h=ih/8:color=magenta:t=fill,"
                   "drawbox=x=iw*7/8:y=ih*7/8:w=iw/8:h=ih/8:color=white:t=fill,"
                   "drawgrid=w=iw/8:h=ih/8:t=2:c=black@0.4,"
                   "drawbox=x=iw/3:y=ih/3:w=iw/3:h=ih/3:color=white:t=fill:enable='lt(mod(t,2),1)'")
        subprocess.run([ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i",
                        f"color=c=red:s={width}x{height}:r=12", "-f", "lavfi", "-i",
                        "sine=frequency=440:sample_rate=48000", "-t", str(seconds), "-vf", filters,
                        "-c:v", "libx264", "-threads", "2", "-preset", "ultrafast", "-profile:v", "baseline",
                        "-level:v", level, "-pix_fmt", "yuv420p", "-crf", "25", "-g", "24",
                        "-colorspace", "bt709", "-color_trc", "bt709", "-color_primaries", "bt709",
                        "-c:a", "aac", "-b:a", "96k", "-ac", "1", "-movflags", "+faststart", str(target)], check=True)
        digest = hashlib.sha256()
        with target.open("rb") as source:
            for chunk in iter(lambda: source.read(65536), b""):
                digest.update(chunk)
        manifest[name] = dict(width=width, height=height, seconds=seconds,
                              bytes=target.stat().st_size, sha256=digest.hexdigest())
    subprocess.run([ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-display_rotation", "90",
                    "-i", str(args.folder / "high.mp4"), "-c", "copy", str(args.folder / "rotated.mp4")], check=True)
    manifest["rotated.mp4"] = dict(width=2160, height=3840, seconds=8, rotation=90)
    subprocess.run([ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-i", str(args.folder / "high.mp4"),
                    "-vf", "scale=1280:720", "-c:v", "libx264", "-threads", "2", "-preset", "ultrafast",
                    "-profile:v", "baseline", "-level:v", "3.1", "-crf", "25", "-c:a", "copy",
                    str(args.folder / "legacy.mp4")], check=True)
    subprocess.run([ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-display_rotation", "90",
                    "-i", str(args.folder / "legacy.mp4"), "-c", "copy", str(args.folder / "legacy-rotated.mp4")], check=True)
    manifest["legacy.mp4"] = dict(width=1280, height=720, seconds=8)
    manifest["legacy-rotated.mp4"] = dict(width=720, height=1280, seconds=8, rotation=90)
    subprocess.run([ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-i", str(args.folder / "high.mp4"),
                    "-vf", "scale=1920:1080", "-c:v", "libx264", "-threads", "2", "-preset", "ultrafast",
                    "-profile:v", "baseline", "-level:v", "4.0", "-crf", "25", "-c:a", "copy",
                    str(args.folder / "medium.mp4")], check=True)
    subprocess.run([ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-display_rotation", "90",
                    "-i", str(args.folder / "medium.mp4"), "-c", "copy", str(args.folder / "medium-rotated.mp4")], check=True)
    manifest["medium.mp4"] = dict(width=1920, height=1080, seconds=8)
    manifest["medium-rotated.mp4"] = dict(width=1080, height=1920, seconds=8, rotation=90)
    (args.folder / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(json.dumps(manifest, indent=2))


if __name__ == "__main__":
    main()
