#!/usr/bin/env python3
"""把 Android 版的預錄語音（app/src/main/assets/voice/*.wav）轉成網頁版用的 mp3（web/public/voice/*.mp3）。

  python3 tools/voice/to_web.py            （在 repo 根目錄執行；需要安裝 ffmpeg）

單聲道、64 kbps，63 句約 1.8 MB（wav 原檔約 9 MB）。已經比 wav 新的 mp3 會略過，加 --force 全部重做。
重做語音後，記得把 web/public/sw.js 的 CACHE 版本號加一，已安裝的使用者才會拿到新檔。
"""
import argparse
import pathlib
import subprocess
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "app/src/main/assets/voice"
DST = ROOT / "web/public/voice"


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--force", action="store_true", help="已經有的 mp3 也重做")
    args = ap.parse_args()

    DST.mkdir(parents=True, exist_ok=True)
    wavs = sorted(SRC.glob("*.wav"))
    if not wavs:
        sys.exit(f"找不到語音檔：{SRC}")
    done = 0
    for wav in wavs:
        mp3 = DST / (wav.stem + ".mp3")
        if mp3.exists() and mp3.stat().st_mtime >= wav.stat().st_mtime and not args.force:
            continue
        try:
            subprocess.run(
                ["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-ac", "1",
                 "-codec:a", "libmp3lame", "-b:a", "64k", str(mp3)],
                check=True,
            )
        except FileNotFoundError:
            sys.exit("找不到 ffmpeg，請先安裝（例如 winget install ffmpeg、brew install ffmpeg 或 sudo apt install ffmpeg）")
        done += 1
    print(f"轉了 {done} 個，共 {len(wavs)} 句 → {DST}")


if __name__ == "__main__":
    main()
