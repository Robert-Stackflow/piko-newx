"""Reproducibly build the localized NewX bundle without publishing a release."""

import json
import os
import shutil
import subprocess
from pathlib import Path

from build_piko import pre_build_cleanup, set_project_version
from localization.apply_localization import apply

ROOT = Path(__file__).resolve().parent


def main():
    config = json.loads((ROOT / "localization/source.json").read_text(encoding="utf-8"))
    source = ROOT / ".localized-source"
    if source.exists():
        raise FileExistsError(".localized-source already exists; use a fresh checkout for a build")
    subprocess.run(["git", "init", str(source)], check=True)
    subprocess.run(["git", "remote", "add", "origin",
                    f"https://github.com/{config['repository']}.git"], cwd=source, check=True)
    subprocess.run(["git", "fetch", "--depth=1", "origin", config["commit"]], cwd=source, check=True)
    subprocess.run(["git", "checkout", "--detach", "FETCH_HEAD"], cwd=source, check=True)
    report = apply(source)
    print(json.dumps(report, indent=2), flush=True)
    # Cleanup is confined to the new generated source checkout above.
    pre_build_cleanup(source)
    set_project_version(source, config["version"])
    gradlew = "gradlew.bat" if os.name == "nt" else "./gradlew"
    subprocess.run([gradlew, "buildAndroid", "--no-daemon", "--max-workers=2",
                    "-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8"], cwd=source, check=True)
    output = ROOT / "bins"
    output.mkdir(exist_ok=True)
    artifact = source / f"patches/build/libs/patches-{config['version']}.mpp"
    if not artifact.is_file():
        raise FileNotFoundError(artifact)
    shutil.copy2(artifact, output / "patches.mpp")
    (output / "localization-report.json").write_text(
        json.dumps({**config, **report}, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
