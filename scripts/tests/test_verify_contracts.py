import importlib.util
from pathlib import Path
import unittest

MODULE_PATH = Path(__file__).resolve().parents[1] / "verify_contracts.py"
SPEC = importlib.util.spec_from_file_location("verify_contracts", MODULE_PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class VerdictTests(unittest.TestCase):
    def run_data(self, properties, exit_code, tool_status):
        return {"properties": properties, "exit_code": exit_code, "tool_status": tool_status}

    def test_reachable_false_probe_is_not_a_bug(self):
        marker = {"property": "java::sample.H.preconditions:()V.assertion.1", "status": "FAILURE"}
        run = self.run_data([marker], 10, "failure")
        self.assertEqual("SATISFIABLE", MODULE.consistency_status(run, "sample.H"))

    def test_unreachable_probe_is_not_a_proof(self):
        marker = {"property": "java::sample.H.preconditions:()V.assertion.1", "status": "SUCCESS"}
        run = self.run_data([marker], 0, "success")
        self.assertEqual("NO_ADMISSIBLE_INPUTS", MODULE.consistency_status(run, "sample.H"))

    def test_other_failures_are_not_mistaken_for_reachability(self):
        marker = {"property": "java::sample.H.preconditions:()V.assertion.1", "status": "FAILURE"}
        bounds = {"property": "array-index-out-of-bounds", "status": "FAILURE"}
        run = self.run_data([marker, bounds], 10, "failure")
        self.assertEqual("INCONCLUSIVE", MODULE.consistency_status(run, "sample.H"))

    def test_unwinding_failure_and_missing_assertions_are_inconclusive(self):
        target = {"property": "java::sample.H.verify:()V.assertion.1", "status": "SUCCESS"}
        unwind = {"property": "loop.unwind.0", "status": "FAILURE"}
        self.assertEqual("INCONCLUSIVE", MODULE.proof_status(self.run_data([target, unwind], 10, "failure"), "sample.H"))
        self.assertEqual("INCONCLUSIVE", MODULE.proof_status(self.run_data([], 0, "success"), "sample.H"))
        self.assertEqual("INCONCLUSIVE", MODULE.proof_status({"error": "TIMEOUT"}, "sample.H"))

    def test_proof_requires_consistent_tool_status(self):
        target = {"property": "java::sample.H.verify:()V.assertion.1", "status": "SUCCESS"}
        self.assertEqual("PROVED_WITHIN_BOUNDS", MODULE.proof_status(self.run_data([target], 0, "success"), "sample.H"))
        self.assertEqual("INCONCLUSIVE", MODULE.proof_status(self.run_data([target], 0, "failure"), "sample.H"))

    def test_counterexample_keeps_harness_values_and_discards_callee_locals(self):
        def step(name, value, function):
            return {"stepType": "assignment", "lhs": name, "value": {"data": value},
                    "sourceLocation": {"function": function}}
        run = {"properties": [{"status": "FAILURE", "trace": [
            step("balance", "5L", "java::sample.H.verify:()V"),
            step("balance", "100L", "java::sample.Example.f:(J)J"),
            step("__CPROVER_rounding_mode", "0", "java::sample.H.verify:()V"),
            step("result", "10L", "java::sample.H.verify:()V")]}]}
        self.assertEqual({"balance": "5L", "result": "10L"}, MODULE.assignments(run, "sample.H", ["balance"]))


if __name__ == "__main__":
    unittest.main()
