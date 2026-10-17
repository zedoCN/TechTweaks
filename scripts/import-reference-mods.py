#!/usr/bin/env python3
"""Import the five exact compile/test dependencies from a TECH client-pack zip."""
from pathlib import Path
from zipfile import ZipFile
import hashlib, sys
EXPECTED = {
    'curios-neoforge-16.0.0+26.2.jar': '5cb4a8b45301c5c32279ad80c24d4da8ebcd9879b140ffdb3057fc7c331b8500',
    'ntm-next-neoforge-1.0.1+mc26.2.jar': '794364805e5df60be157e214297b1d56dcab6dd64fcd275763acb625abd13e96',
    'sophisticatedbackpacks-26.2-3.26.8.2179.jar': 'c31a54bdfed0b0ae334927585b32bf17f974ebec1f3350e3032e5177b9312fbc',
    'sophisticatedcore-26.2-1.5.2.2359.jar': '64c29a0042d3a3d74a46443756e7c310af5e02e35041b57db291462bb95f12a6',
    'pipez-neoforge-1.2.32+26.2.jar': 'b57183e9e66144a0773e4fd89cce85b0827fc300d76dca3cc4c727fb9532ffc3',
}
root = Path(__file__).resolve().parents[1]
if len(sys.argv) != 2:
    raise SystemExit("usage: python3 scripts/import-reference-mods.py CLIENT_PACK.zip")
with ZipFile(sys.argv[1]) as archive:
    selected = {}
    for filename, expected in EXPECTED.items():
        matches = [name for name in archive.namelist() if Path(name).name == filename]
        if len(matches) != 1:
            raise SystemExit(f"expected exactly one {filename}, found {len(matches)}")
        blob = archive.read(matches[0])
        if hashlib.sha256(blob).hexdigest() != expected:
            raise SystemExit(f"hash mismatch for {filename}; use the matching TECH mod versions")
        selected[filename] = blob
(root / "libs").mkdir(exist_ok=True)
for filename, blob in selected.items():
    (root / "libs" / filename).write_bytes(blob)
print(f"Imported {len(selected)} dependencies (not bundled into the addon jar)")
