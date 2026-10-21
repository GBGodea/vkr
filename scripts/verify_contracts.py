#!/usr/bin/env python3
"""Run generated JBMC harnesses. Uses only Python's standard library."""
import argparse
import json
import os
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def run_jbmc(jbmc, harness, entry, classpath, unwind, timeout, raw_dir):
    command = [jbmc, harness, "--classpath", classpath, "--function", harness + "." + entry,
               "--unwind", str(unwind), "--unwinding-assertions", "--json-ui", "--trace"]
    started = time.perf_counter()
    raw_path = raw_dir / (harness + "." + entry + ".json")
    try:
        process = subprocess.run(command, cwd=ROOT, capture_output=True, timeout=timeout)
        output = process.stdout.decode("utf-8", errors="replace")
        raw_path.write_text(output, encoding="utf-8")
        stderr = process.stderr.decode("utf-8", errors="replace")
        if stderr:
            raw_path.with_suffix(".stderr.txt").write_text(stderr, encoding="utf-8")
        document = json.loads(output)
        properties = [prop for item in document for prop in item.get("result", [])]
        statuses = [item["cProverStatus"] for item in document if "cProverStatus" in item]
        result = {"exit_code": process.returncode, "tool_status": statuses[-1] if statuses else None,
                  "properties": properties, "command": command, "raw_output": str(raw_path)}
    except subprocess.TimeoutExpired:
        result = {"error": "TIMEOUT", "command": command}
    except (OSError, ValueError, TypeError) as error:
        result = {"error": str(error), "command": command}
    result["seconds"] = round(time.perf_counter() - started, 6)
    return result


def consistency_status(run, harness):
    if "error" in run or not run["properties"]:
        return "INCONCLUSIVE"
    marker = "java::" + harness + ".preconditions:()V.assertion.1"
    properties = run["properties"]
    probe = next((p for p in properties if p.get("property") == marker), None)
    if probe is None or any(p.get("status") != "SUCCESS" for p in properties if p.get("property") != marker):
        return "INCONCLUSIVE"
    if probe.get("status") == "FAILURE" and run["exit_code"] == 10 and run["tool_status"] == "failure":
        return "SATISFIABLE"
    if probe.get("status") == "SUCCESS" and run["exit_code"] == 0 and run["tool_status"] == "success":
        return "NO_ADMISSIBLE_INPUTS"
    return "INCONCLUSIVE"


def proof_status(run, harness, entry="verify"):
    if "error" in run or not run["properties"]:
        return "INCONCLUSIVE"
    properties = run["properties"]
    # A run that did not load the target assertions is never accepted as a proof.
    prefix = "java::" + harness + "." + entry + ":()V.assertion."
    if not any(p.get("property", "").startswith(prefix) for p in properties):
        return "INCONCLUSIVE"
    failed = [p for p in properties if p.get("status") == "FAILURE"]
    if any("unwind" in p.get("property", "") for p in failed):
        return "INCONCLUSIVE"
    if failed and run["exit_code"] == 10 and run["tool_status"] == "failure":
        return "COUNTEREXAMPLE"
    if all(p.get("status") == "SUCCESS" for p in properties) and run["exit_code"] == 0 and run["tool_status"] == "success":
        return "PROVED_WITHIN_BOUNDS"
    return "INCONCLUSIVE"


def assignments(run, harness, parameters):
    witnesses = {}
    for prop in run.get("properties", []):
        if prop.get("status") != "FAILURE":
            continue
        for step in prop.get("trace", []):
            if step.get("stepType") != "assignment" or step.get("hidden") or step.get("internal"):
                continue
            name = step.get("lhs", "")
            value = step.get("value", {}).get("data")
            function = step.get("sourceLocation", {}).get("function")
            allowed = name in parameters or name == "result" or name.startswith("__fv_old")
            if value is not None and allowed and function == "java::" + harness + ".verify:()V":
                witnesses[name] = value
    return witnesses


def markdown(report):
    text = ["# Проверка контрактов — этап 3 НИР", "", "Время UTC: " + report["created_at"],
            "JBMC: " + report["jbmc_version"], "",
            "Проверяются методы арифметического ядра, используемого приложением. "
            "Результаты относятся к указанным границам входных данных; Spring, БД и транзакции здесь не моделируются.", "",
            "| Метод | Допустимые входы | Проверка свойств | Время, с |", "|---|---|---|---:|"]
    for row in report["generated"]:
        text.append(f'| {row["id"]} | {row["consistency"]} | {row["status"]} | {row["seconds"]:.3f} |')
    text += ["", "## Границы и спецификации", ""]
    for row in report["generated"]:
        text += ["### " + row["id"], "", "Исходный метод: `" + row["method"] + "`.", "",
                 "Границы: " + "; ".join(f'{r["parameter"]} ∈ [{r["min"]}, {r["max"]}]' for r in row["ranges"]) + ".", "",
                 "Предусловия: " + "; ".join("`" + p + "`" for p in row["preconditions"]) + ".", "",
                 "Постусловия и инварианты результата: " + "; ".join("`" + p + "`" for p in row["postconditions"] + row["invariants"]) + ".", ""]
        if row.get("counterexample"):
            text += ["Контрпример (присваивания из трассы):", "", "```json", json.dumps(row["counterexample"], ensure_ascii=False, indent=2), "```", ""]
    if report["manual"]:
        text += ["## Сопоставление с ручными проверками прошлого семестра", "",
                 "| Проверка | Результат | Время, с |", "|---|---|---:|"]
        for row in report["manual"]:
            text.append(f'| {row["entry"]} | {row["status"]} | {row["seconds"]:.3f} |')
        text += ["", "Это контрольное сопоставление трёх операций. Один прогон не оценивает эффективность методов и не заменяет эксперимент этапа 5.", ""]
    text += ["## Значение статусов", "",
             "- SATISFIABLE: найдено хотя бы одно допустимое сочетание входов.",
             "- NO_ADMISSIBLE_INPUTS: в заданных границах нет входов, удовлетворяющих предусловиям. Корректность метода не подтверждается.",
             "- PROVED_WITHIN_BOUNDS: утверждения и служебные проверки JBMC выполнены в заданных границах.",
             "- COUNTEREXAMPLE: найдены входные данные, нарушающие свойство или безопасность выполнения.",
             "- INCONCLUSIVE: ошибка инструмента, таймаут, незавершённое разворачивание циклов или неполный результат.", ""]
    return "\n".join(text)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jbmc", default="jbmc")
    parser.add_argument("--manifest", type=Path, default=ROOT / "bank-application/target/classes/META-INF/formal-verification/manifest.json")
    parser.add_argument("--output", type=Path, default=ROOT / "bank-application/target/verification-results")
    parser.add_argument("--classpath", default=os.pathsep.join(["bank-application/target/classes", "formal-verification-core/target/classes"]))
    parser.add_argument("--unwind", type=int, default=8)
    parser.add_argument("--timeout", type=float, default=120)
    parser.add_argument("--compare-manual", action="store_true")
    args = parser.parse_args(argv)
    if args.unwind < 1 or args.timeout <= 0:
        parser.error("unwind and timeout must be positive")
    try:
        methods = json.loads(args.manifest.read_text(encoding="utf-8"))
        if not methods:
            raise ValueError("Empty manifest: no methods were generated")
        version = subprocess.run([args.jbmc, "--version"], capture_output=True, timeout=10, check=True).stdout.decode().strip()
    except (OSError, ValueError, subprocess.SubprocessError) as error:
        print(str(error), file=sys.stderr)
        return 2
    args.output.mkdir(parents=True, exist_ok=True)
    raw_dir = args.output / "raw"
    raw_dir.mkdir(exist_ok=True)
    report = {"created_at": datetime.now(timezone.utc).isoformat(), "jbmc_version": version,
              "unwind": args.unwind, "timeout_seconds": args.timeout, "generated": [], "manual": []}
    for spec in methods:
        harness = spec["harness"]
        feasibility = run_jbmc(args.jbmc, harness, "preconditions", args.classpath, args.unwind, args.timeout, raw_dir)
        consistency = consistency_status(feasibility, harness)
        row = dict(spec, consistency=consistency, consistency_run=feasibility)
        if consistency == "SATISFIABLE":
            proof = run_jbmc(args.jbmc, harness, "verify", args.classpath, args.unwind, args.timeout, raw_dir)
            row.update(status=proof_status(proof, harness), proof_run=proof)
            if row["status"] == "COUNTEREXAMPLE":
                row["counterexample"] = assignments(proof, harness, spec.get("parameters", [r["parameter"] for r in spec["ranges"]]))
            row["seconds"] = round(feasibility["seconds"] + proof["seconds"], 6)
        else:
            row.update(status=consistency, seconds=feasibility["seconds"])
        report["generated"].append(row)
        print(spec["id"] + ": " + row["status"], flush=True)
    if args.compare_manual:
        harness = "com.example.bank.verification.BankOperationVerificationHarness"
        classpath = os.pathsep.join([args.classpath, "bank-application/target/test-classes"])
        for entry in ("verifyDeposit", "verifyWithdraw", "verifyTransfer"):
            run = run_jbmc(args.jbmc, harness, entry, classpath, args.unwind, args.timeout, raw_dir)
            row = {"entry": entry, "status": proof_status(run, harness, entry), "seconds": run["seconds"], "run": run}
            report["manual"].append(row)
            print("manual " + entry + ": " + row["status"], flush=True)
    (args.output / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    (args.output / "report.md").write_text(markdown(report), encoding="utf-8")
    return 0 if all(row["status"] == "PROVED_WITHIN_BOUNDS" for row in report["generated"] + report["manual"]) else 1


if __name__ == "__main__":
    sys.exit(main())
