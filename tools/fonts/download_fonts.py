#!/usr/bin/env python3
"""Downloads the fonts bundled with the app and their licences.

Run from anywhere (stdlib only, isolated mode recommended):

    python3 -I tools/fonts/download_fonts.py            # download missing files
    python3 -I tools/fonts/download_fonts.py --force    # re-download everything
    python3 -I tools/fonts/download_fonts.py --check    # no network: verify + regenerate

What it does
------------
1. For every family in FAMILIES it asks the Google Fonts CSS2 API for static TTF instances
   (the "Wget/1.21" user agent makes the API answer with plain truetype URLs on
   fonts.gstatic.com, one file per weight, no unicode-range subsetting).
   Weights the API does not offer are dropped and reported.
2. Saves each file as core/designsystem/src/main/res/font/<family>_<weight>.ttf
   (Android resource names: lowercase a-z, 0-9 and "_").
3. Downloads the family's licence from github.com/google/fonts
   (ofl/<dir>/OFL.txt, apache/<dir>/LICENSE.txt or ufl/<dir>/UFL.txt) into
   core/designsystem/src/main/assets/licenses/fonts/<family>.txt.
4. Parses the 'cmap' table of every TTF to find out whether the font really contains Thai
   (consonants, vowels, tone marks and Thai digits) and prints a report.
5. Writes tools/fonts/fonts_manifest.json and the generated Kotlin resource table
   core/designsystem/src/main/kotlin/.../font/BundledFontFiles.kt.

The script is idempotent: existing valid files are kept unless --force is given.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import struct
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

USER_AGENT = "Wget/1.21"
CSS_URL = "https://fonts.googleapis.com/css2?{query}"
LICENSE_URLS = (
    ("OFL-1.1", "https://raw.githubusercontent.com/google/fonts/main/ofl/{dir}/OFL.txt"),
    ("Apache-2.0", "https://raw.githubusercontent.com/google/fonts/main/apache/{dir}/LICENSE.txt"),
    ("UFL-1.0", "https://raw.githubusercontent.com/google/fonts/main/ufl/{dir}/UFL.txt"),
)

ROOT = Path(__file__).resolve().parents[2]
DS = ROOT / "core" / "designsystem" / "src" / "main"
FONT_DIR = DS / "res" / "font"
LICENSE_DIR = DS / "assets" / "licenses" / "fonts"
MANIFEST = Path(__file__).resolve().parent / "fonts_manifest.json"
KOTLIN_OUT = (
    DS / "kotlin" / "io" / "github" / "tweeter042" / "alarmclock" / "core" / "designsystem"
    / "font" / "BundledFontFiles.kt"
)

# (Google Fonts family name, weights). The order here is the order of the generated table.
FAMILIES: list[tuple[str, list[int]]] = [
    # --- Thai-capable families -------------------------------------------------------------
    ("Noto Sans Thai", [400, 700]),
    ("IBM Plex Sans Thai", [300, 400, 700]),
    ("IBM Plex Sans Thai Looped", [400, 700]),
    ("Anuphan", [300, 400, 700]),
    ("Bai Jamjuree", [400, 700]),
    ("Prompt", [200, 300, 400, 700]),
    ("Kanit", [300, 400, 700]),
    ("Sarabun", [400, 700]),
    ("Mitr", [300, 400, 600]),
    ("Chakra Petch", [400, 700]),
    ("K2D", [400, 700]),
    ("Krub", [400, 700]),
    ("Niramit", [400, 700]),
    ("Athiti", [400, 600]),
    ("Mali", [400, 700]),
    ("Itim", [400]),
    ("Sriracha", [400]),
    ("Charm", [400, 700]),
    ("Pridi", [300, 400, 700]),
    ("Taviraj", [400, 700]),
    ("Trirong", [400, 700]),
    ("Noto Serif Thai", [400, 700]),
    ("Chonburi", [400]),
    ("Pattaya", [400]),
    # --- Latin display / mono (clock digits, headings) ---------------------------------------
    ("Inter", [100, 300, 400, 700]),
    ("Space Grotesk", [400, 700]),
    ("Space Mono", [400, 700]),
    ("JetBrains Mono", [400, 700]),
    ("IBM Plex Mono", [400, 700]),
    ("Share Tech Mono", [400]),
    ("VT323", [400]),
    ("Press Start 2P", [400]),
    ("Silkscreen", [400, 700]),
    ("Orbitron", [400, 700]),
    ("Bebas Neue", [400]),
    ("Archivo Black", [400]),
    ("Playfair Display", [400, 700]),
    ("DM Serif Display", [400]),
    ("Major Mono Display", [400]),
    ("Monoton", [400]),
    ("Audiowide", [400]),
    ("Oswald", [300, 500, 700]),
]

# Code points a font must contain to count as "supports Thai": consonants, vowels, tone marks,
# signs and Thai digits (U+0E01-U+0E3A, U+0E3F-U+0E4E, U+0E50-U+0E59).
THAI_REQUIRED = (
    list(range(0x0E01, 0x0E3B)) + list(range(0x0E3F, 0x0E4F)) + list(range(0x0E50, 0x0E5A))
)
LATIN_REQUIRED = list(range(0x30, 0x3A)) + list(range(0x41, 0x5B)) + list(range(0x61, 0x7B)) + [0x3A]


def family_key(family: str) -> str:
    """'IBM Plex Sans Thai Looped' -> 'ibm_plex_sans_thai_looped'."""
    key = re.sub(r"[^a-z0-9]+", "_", family.lower()).strip("_")
    if not key or not key[0].isalpha():
        raise ValueError(f"family {family!r} does not give a valid resource name: {key!r}")
    return key


def repo_dir(family: str) -> str:
    """Directory name used by github.com/google/fonts ('Press Start 2P' -> 'pressstart2p')."""
    return re.sub(r"[^a-z0-9]", "", family.lower())


def http_get(url: str, attempts: int = 4) -> bytes:
    last: Exception | None = None
    for attempt in range(attempts):
        req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                return resp.read()
        except urllib.error.HTTPError as e:
            # 4xx (except 429) are definitive answers, retrying will not help.
            if e.code != 429 and 400 <= e.code < 500:
                raise
            last = e
        except (urllib.error.URLError, TimeoutError, ConnectionError) as e:
            last = e
        time.sleep(2 + attempt * 5)
    assert last is not None
    raise last


FACE_RE = re.compile(r"@font-face\s*{(.*?)}", re.S)
WEIGHT_RE = re.compile(r"font-weight:\s*(\d+)\s*;")
STYLE_RE = re.compile(r"font-style:\s*(\w+)\s*;")
SRC_RE = re.compile(r"src:\s*url\((https://fonts\.gstatic\.com/[^)]+)\)\s*format\('truetype'\)")


def css_urls(family: str, weights: list[int]) -> dict[int, str]:
    """Returns {weight: ttf_url} for the weights the API offers."""
    query = urllib.parse.urlencode(
        {"family": f"{family}:wght@{';'.join(str(w) for w in weights)}"}, safe=":@;"
    )
    try:
        css = http_get(CSS_URL.format(query=query)).decode("utf-8")
    except urllib.error.HTTPError as e:
        if e.code == 400 and len(weights) > 1:
            # One of the weights does not exist: ask for each weight on its own.
            found: dict[int, str] = {}
            for w in weights:
                found.update(css_urls(family, [w]))
            return found
        if e.code == 400:
            return {}
        raise
    result: dict[int, str] = {}
    for block in FACE_RE.findall(css):
        style = STYLE_RE.search(block)
        if style and style.group(1) != "normal":
            continue
        weight = WEIGHT_RE.search(block)
        src = SRC_RE.search(block)
        if weight and src:
            result[int(weight.group(1))] = src.group(1)
    return result


def is_font_file(data: bytes) -> bool:
    return len(data) > 12 and data[:4] in (b"\x00\x01\x00\x00", b"true", b"OTTO")


def cmap_codepoints(data: bytes) -> set[int]:
    """All code points mapped by the font's Unicode cmap subtables (formats 4 and 12)."""
    num_tables = struct.unpack(">H", data[4:6])[0]
    cmap_offset = None
    for i in range(num_tables):
        tag, _checksum, offset, _length = struct.unpack(">4sIII", data[12 + 16 * i: 28 + 16 * i])
        if tag == b"cmap":
            cmap_offset = offset
            break
    if cmap_offset is None:
        return set()
    _version, count = struct.unpack(">HH", data[cmap_offset: cmap_offset + 4])
    points: set[int] = set()
    for i in range(count):
        platform, encoding, sub_offset = struct.unpack(
            ">HHI", data[cmap_offset + 4 + 8 * i: cmap_offset + 12 + 8 * i]
        )
        if not ((platform == 3 and encoding in (1, 10)) or platform == 0):
            continue
        base = cmap_offset + sub_offset
        fmt = struct.unpack(">H", data[base: base + 2])[0]
        if fmt == 4:
            seg_x2 = struct.unpack(">H", data[base + 6: base + 8])[0]
            seg = seg_x2 // 2
            ends = struct.unpack(f">{seg}H", data[base + 14: base + 14 + seg_x2])
            starts_at = base + 16 + seg_x2
            starts = struct.unpack(f">{seg}H", data[starts_at: starts_at + seg_x2])
            deltas_at = starts_at + seg_x2
            deltas = struct.unpack(f">{seg}h", data[deltas_at: deltas_at + seg_x2])
            ranges_at = deltas_at + seg_x2
            range_offsets = struct.unpack(f">{seg}H", data[ranges_at: ranges_at + seg_x2])
            for s in range(seg):
                for cp in range(starts[s], ends[s] + 1):
                    if cp == 0xFFFF:
                        continue
                    if range_offsets[s] == 0:
                        glyph = (cp + deltas[s]) & 0xFFFF
                    else:
                        addr = ranges_at + 2 * s + range_offsets[s] + 2 * (cp - starts[s])
                        glyph = struct.unpack(">H", data[addr: addr + 2])[0]
                        if glyph:
                            glyph = (glyph + deltas[s]) & 0xFFFF
                    if glyph:
                        points.add(cp)
        elif fmt == 12:
            n_groups = struct.unpack(">I", data[base + 12: base + 16])[0]
            for g in range(n_groups):
                start, end, start_glyph = struct.unpack(
                    ">III", data[base + 16 + 12 * g: base + 28 + 12 * g]
                )
                if start_glyph or start != end:
                    points.update(range(start, end + 1))
    return points


def license_spdx(path: Path) -> str | None:
    if not path.exists():
        return None
    text = path.read_text(encoding="utf-8", errors="replace").upper()
    if "SIL OPEN FONT LICENSE" in text:
        return "OFL-1.1"
    if "APACHE LICENSE" in text:
        return "Apache-2.0"
    if "UBUNTU FONT LICENCE" in text:
        return "UFL-1.0"
    return "unknown"


def coverage(points: set[int], required: list[int]) -> float:
    return sum(1 for cp in required if cp in points) / len(required)


def write_kotlin(entries: list[dict]) -> None:
    lines = [
        "// GENERATED by tools/fonts/download_fonts.py - do not edit by hand.",
        "// Re-run the script after changing the bundled font list.",
        "package io.github.tweeter042.alarmclock.core.designsystem.font",
        "",
        "import io.github.tweeter042.alarmclock.core.designsystem.R",
        "",
        "/** One bundled TTF file: an `R.font` id and the weight it was instanced at. */",
        "internal class BundledFontFile(val resId: Int, val weight: Int, val resourceName: String)",
        "",
        "/** Every font file bundled in `res/font`, grouped by FontCatalog id. */",
        "internal object BundledFontFiles {",
        "    val byFamily: Map<String, List<BundledFontFile>> = mapOf(",
    ]
    for entry in entries:
        lines.append(f'        "{entry["id"]}" to listOf(')
        for f in entry["files"]:
            lines.append(
                f'            BundledFontFile(R.font.{f["resource"]}, {f["weight"]}, "{f["resource"]}"),'
            )
        lines.append("        ),")
    lines += [
        "    )",
        "",
        "    /** Families whose cmap contains the full Thai block (verified by the download script). */",
        "    val thaiCapable: Set<String> = setOf(",
    ]
    for entry in entries:
        if entry["supportsThai"]:
            lines.append(f'        "{entry["id"]}",')
    lines += ["    )", "}", ""]
    KOTLIN_OUT.parent.mkdir(parents=True, exist_ok=True)
    KOTLIN_OUT.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--force", action="store_true", help="re-download files that already exist")
    parser.add_argument("--check", action="store_true", help="offline: only verify files and regenerate outputs")
    args = parser.parse_args()

    FONT_DIR.mkdir(parents=True, exist_ok=True)
    LICENSE_DIR.mkdir(parents=True, exist_ok=True)

    entries: list[dict] = []
    dropped: list[str] = []
    problems: list[str] = []
    downloaded = 0
    total_bytes = 0

    for family, weights in FAMILIES:
        key = family_key(family)
        targets = {w: FONT_DIR / f"{key}_{w}.ttf" for w in weights}
        missing = [
            w for w, p in targets.items()
            if args.force or not p.exists() or not is_font_file(p.read_bytes())
        ]
        urls: dict[int, str] = {}
        if missing and not args.check:
            urls = css_urls(family, missing)
        files = []
        for w in weights:
            path = targets[w]
            if w in missing:
                if w not in urls:
                    dropped.append(f"{family} {w}")
                    if path.exists() and args.force:
                        path.unlink()
                    continue
                data = http_get(urls[w])
                if not is_font_file(data):
                    problems.append(f"{family} {w}: response is not a TrueType file")
                    continue
                path.write_bytes(data)
                downloaded += 1
            elif not path.exists():
                continue
            data = path.read_bytes()
            total_bytes += len(data)
            files.append({
                "resource": path.stem,
                "weight": w,
                "bytes": len(data),
                "sha256": hashlib.sha256(data).hexdigest(),
            })
        if not files:
            problems.append(f"{family}: no files available")
            continue

        # Licence (one per family). The SPDX id is derived from the licence text itself.
        license_path = LICENSE_DIR / f"{key}.txt"
        if (args.force or not license_path.exists()) and not args.check:
            for _spdx, template in LICENSE_URLS:
                try:
                    text = http_get(template.format(dir=repo_dir(family)))
                except urllib.error.HTTPError as e:
                    if e.code == 404:
                        continue
                    raise
                license_path.write_bytes(text)
                break
        spdx = license_spdx(license_path)
        if spdx is None:
            problems.append(f"{family}: licence not found on github.com/google/fonts")

        # Thai support: every downloaded weight must contain the full Thai set.
        cmaps = [cmap_codepoints((FONT_DIR / f"{f['resource']}.ttf").read_bytes()) for f in files]
        thai_cov = min(coverage(points, THAI_REQUIRED) for points in cmaps)
        latin_cov = min(coverage(points, LATIN_REQUIRED) for points in cmaps)
        entries.append({
            "id": key,
            "family": family,
            "requestedWeights": weights,
            "weights": [f["weight"] for f in files],
            "files": files,
            "supportsThai": thai_cov >= 1.0,
            "thaiCoverage": round(thai_cov, 3),
            "latinCoverage": round(latin_cov, 3),
            "license": spdx or "unknown",
            "licenseFile": f"licenses/fonts/{key}.txt",
        })

    MANIFEST.write_text(json.dumps({
        "generatedBy": "tools/fonts/download_fonts.py",
        "totalBytes": total_bytes,
        "families": entries,
        "droppedWeights": dropped,
    }, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    write_kotlin(entries)

    # Report.
    print(f"{'id':28} {'weights':22} {'thai':>6} {'latin':>6} {'size':>9}  licence")
    for e in entries:
        size = sum(f["bytes"] for f in e["files"])
        print(
            f"{e['id']:28} {','.join(map(str, e['weights'])):22} "
            f"{'yes' if e['supportsThai'] else 'no':>6} {e['latinCoverage']:>6.2f} "
            f"{size / 1024:>7.0f}KB  {e['license']}"
        )
    print(f"\nfamilies: {len(entries)}  files: {sum(len(e['files']) for e in entries)}  "
          f"downloaded now: {downloaded}  total payload: {total_bytes / 1024 / 1024:.2f} MB")
    print(f"thai-capable: {sum(1 for e in entries if e['supportsThai'])}")
    if dropped:
        print("dropped (not offered by Google Fonts): " + ", ".join(dropped))
    if problems:
        print("PROBLEMS:\n  " + "\n  ".join(problems))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
