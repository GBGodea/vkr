#!/usr/bin/env python3
"""Preserve an actual completed stage-3 run for the NIR report and thesis."""
import hashlib
import json
import shutil
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "docs/results/stage-3"


def main():
    generated_dir = ROOT / "bank-application/target/verification-results"
    examples_dir = ROOT / "target/stage3-examples/results"
    generated = json.loads((generated_dir / "report.json").read_text(encoding="utf-8"))
    examples = json.loads((examples_dir / "report.json").read_text(encoding="utf-8"))
    if len(generated["generated"]) != 3 or len(generated["manual"]) != 3:
        raise ValueError("Expected three generated and three manual checks")
    if any(row["status"] != "PROVED_WITHIN_BOUNDS" for row in generated["generated"] + generated["manual"]):
        raise ValueError("The banking checks did not all pass")
    expected = {"DEMO-BROKEN-WITHDRAW": "COUNTEREXAMPLE", "DEMO-CONTRADICTORY-PRECONDITIONS": "NO_ADMISSIBLE_INPUTS",
                "DEMO-PRECONDITION-OUTSIDE-BOUNDS": "NO_ADMISSIBLE_INPUTS"}
    if {row["id"]: row["status"] for row in examples["generated"]} != expected:
        raise ValueError("The negative examples did not produce their expected outcomes")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(generated_dir / "report.md", OUTPUT / "verification-report.md")
    shutil.copyfile(examples_dir / "report.md", OUTPUT / "negative-examples-report.md")
    java_dir = OUTPUT / "generated"
    java_dir.mkdir(exist_ok=True)
    for source in (ROOT / "bank-application/target/generated-sources/annotations").rglob("*_Verification.java"):
        shutil.copyfile(source, java_dir / source.name)
    raw_dir = OUTPUT / "raw"
    raw_dir.mkdir(exist_ok=True)
    for directory in (generated_dir / "raw", examples_dir / "raw"):
        for source in directory.glob("*.json"):
            shutil.copyfile(source, raw_dir / source.name)
    for name, path in (("bank-manifest.json", ROOT / "bank-application/target/classes/META-INF/formal-verification/manifest.json"),
                       ("examples-manifest.json", ROOT / "target/stage3-examples/classes/META-INF/formal-verification/manifest.json")):
        shutil.copyfile(path, OUTPUT / name)
    sources = [ROOT / "bank-application/src/main/java/com/example/bank/domain/BankArithmetic.java",
               ROOT / "verification-examples/Stage3Examples.java", ROOT / "scripts/verify_contracts.py"]
    sources.extend((ROOT / "formal-verification-processor/src/main/java").rglob("*.java"))
    summary = {"generated_at": generated["created_at"], "examples_at": examples["created_at"],
               "jbmc_version": generated["jbmc_version"], "unwind": generated["unwind"],
               "java_version": subprocess.run(["java", "-version"], capture_output=True, check=True).stderr.decode("utf-8", errors="replace").strip(),
               "source_sha256": {str(p.relative_to(ROOT)).replace("\\", "/"): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},
               "banking": [], "manual": [], "examples": []}
    log = ["JBMC " + generated["jbmc_version"], "Run " + generated["created_at"], ""]
    for row in generated["generated"] + examples["generated"]:
        compact = {key: row[key] for key in ("id", "method", "ranges", "consistency", "status", "seconds")}
        if "counterexample" in row:
            compact["counterexample"] = row["counterexample"]
        category = "banking" if row["id"].startswith("BANK-") else "examples"
        summary[category].append(compact)
        for kind in ("consistency_run", "proof_run"):
            if kind in row:
                run = row[kind]
                log.append(row["id"] + " " + kind + " exit=" + str(run["exit_code"]) + " status=" + str(run["tool_status"]))
                log.append("command: " + subprocess.list2cmdline(run["command"]))
                for prop in run.get("properties", []):
                    log.append(prop.get("property", "?") + " " + prop.get("status", "?"))
                log.append("")
    for row in generated["manual"]:
        summary["manual"].append({key: row[key] for key in ("entry", "status", "seconds")})
    (OUTPUT / "verification-summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    (OUTPUT / "jbmc-results.txt").write_text("\n".join(log) + "\n", encoding="utf-8")
    print("Archived stage-3 evidence in " + str(OUTPUT))


if __name__ == "__main__":
    main()
