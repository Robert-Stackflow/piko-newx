"""Reproducibly build the localized NewX bundle without publishing a release."""

import json
import hashlib
import os
import shutil
import subprocess
import zipfile
from pathlib import Path

from build_piko import pre_build_cleanup, set_project_version
from localization.apply_localization import apply
from fixes.apply_fixes import apply_fixes
from features.apply_features import apply_features

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
    test_env = {**os.environ, "PIKO_TEST_SOURCE": str(source)}
    subprocess.run([os.sys.executable, "-m", "unittest", "discover", "-s", "tests", "-v"],
                   cwd=ROOT, env=test_env, check=True)
    report = apply(source)
    report.update(apply_fixes(source))
    report["translated_resources"] += report["fix_resources"]
    if config.get("media_tools_preview", False):
        report.update(apply_features(source))
        report["translated_resources"] += report["feature_resources"]
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
    # Include corresponding source and upstream notices with every binary artifact.
    tracked = subprocess.check_output(["git", "ls-files"], cwd=source, text=True).splitlines()
    added_resources = list((source / "patches/src/main/resources/addresources/values-zh-rCN").rglob("*.xml"))
    source_files = {source / path for path in tracked} | set(added_resources)
    source_files.update(source / path for path in report["fix_files"])
    source_files.update(source / path for path in report.get("feature_files", []))
    with zipfile.ZipFile(output / "localized-source.zip", "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(source_files):
            if path.is_file():
                archive.write(path, path.relative_to(source).as_posix())
        for path in sorted((ROOT / "localization").rglob("*")):
            if path.is_file() and "__pycache__" not in path.parts:
                archive.write(path, "localization-overlay/" + path.relative_to(ROOT / "localization").as_posix())
        for path in sorted((ROOT / "fixes").rglob("*")):
            if path.is_file() and "__pycache__" not in path.parts:
                archive.write(path, "list-fix-overlay/" + path.relative_to(ROOT / "fixes").as_posix())
        if config.get("media_tools_preview", False):
            for path in sorted((ROOT / "features").rglob("*")):
                if path.is_file() and "__pycache__" not in path.parts:
                    archive.write(path, "media-tools-overlay/" + path.relative_to(ROOT / "features").as_posix())
    for name in ("LICENSE", "NOTICE"):
        shutil.copy2(source / name, output / name)
    report["mpp_sha256"] = hashlib.sha256(artifact.read_bytes()).hexdigest()
    (output / "localization-report.json").write_text(
        json.dumps({**config, **report}, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
