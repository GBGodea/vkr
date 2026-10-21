package ru.vkr.verification.processor;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VerificationProcessorTest {
    @TempDir Path directory;

    private Compilation compile(String body) throws IOException {
        Path source = directory.resolve("Example.java");
        Files.writeString(source, "package sample; import ru.vkr.verification.annotation.*; public class Example { " + body + " }");
        Path classes = Files.createDirectories(directory.resolve("classes"));
        Path generated = Files.createDirectories(directory.resolve("generated"));
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            var options = List.of("--release", "17", "-classpath", System.getProperty("java.class.path"),
                    "-d", classes.toString(), "-s", generated.toString());
            var task = compiler.getTask(null, manager, diagnostics, options, null, manager.getJavaFileObjects(source));
            task.setProcessors(List.of(new VerificationProcessor()));
            return new Compilation(task.call(), diagnostics.getDiagnostics().toString(), classes, generated);
        }
    }

    @Test void compilesGeneratedHarnessAndOldSnapshot() throws Exception {
        Compilation result = compile("""
                @GenerateVerification @Precondition("balance >= amount && amount > 0")
                @Postcondition("result == old(balance) - amount") @Invariant("result >= 0")
                public static long withdraw(long balance, long amount) { return balance - amount; }
                """);
        assertTrue(result.success, result.diagnostics);
        String source = Files.readString(result.generated.resolve("sample/Example_withdraw_long_long_Verification.java"));
        assertTrue(source.contains("var __fv_old0 = balance;"));
        assertTrue(source.contains("sample.Example.withdraw(balance, amount)"));
        assertTrue(source.contains("public static void preconditions()"));
        assertTrue(Files.exists(result.classes.resolve("sample/Example_withdraw_long_long_Verification.class")));
        assertTrue(Files.readString(result.classes.resolve("META-INF/formal-verification/manifest.json")).contains("\"preconditions\""));
    }

    @Test void supportsArrayResultAndDistinctOverloads() throws Exception {
        Compilation result = compile("""
                @GenerateVerification @Postcondition("result != null && result.length == 2 && result[0] == value")
                static long[] pair(long value) { return new long[] {value, value}; }
                @GenerateVerification @Postcondition("result == value")
                static int pair(@SymbolicRange(min=-4,max=4) int value) { return value; }
                """);
        assertTrue(result.success, result.diagnostics);
        assertTrue(Files.exists(result.classes.resolve("sample/Example_pair_long_Verification.class")));
        assertTrue(Files.exists(result.classes.resolve("sample/Example_pair_int_Verification.class")));
        assertTrue(Files.readString(result.generated.resolve("sample/Example_pair_int_Verification.java")).contains("value >= -4L && value <= 4L"));
    }

    @Test void rejectsUnsupportedInstanceMethod() throws Exception {
        Compilation result = compile("@GenerateVerification @Postcondition(\"result >= 0\") long f(long x) { return x; }");
        assertFalse(result.success);
        assertTrue(result.diagnostics.contains("static methods"));
    }

    @Test void rejectsJavaStatementInjection() throws Exception {
        Compilation result = compile("@GenerateVerification @Postcondition(\"result >= 0; System.exit(0)\") static long f(long x) { return x; }");
        assertFalse(result.success);
        assertTrue(result.diagnostics.contains("Unsupported token"));
    }

    @Test void rejectsOldInPreconditionAndInvalidRange() throws Exception {
        Compilation result = compile("@GenerateVerification @Precondition(\"old(x) >= 0\") @Postcondition(\"result == x\") static long f(long x) { return x; }");
        assertFalse(result.success);
        assertTrue(result.diagnostics.contains("old(...)"));
    }

    @Test void rejectsInvalidNumericRange() throws Exception {
        Compilation result = compile("@GenerateVerification(min=100,max=50) @Postcondition(\"result == x\") static long f(long x) { return x; }");
        assertFalse(result.success);
        assertTrue(result.diagnostics.contains("Invalid range"));
    }

    @Test void rejectsHiddenGlobalStateThroughHelper() throws Exception {
        Compilation result = compile("""
                static long shared;
                static long helper(long x) { return x + shared; }
                @GenerateVerification @Postcondition("result == x")
                static long f(long x) { return helper(x); }
                """);
        assertFalse(result.success);
        assertTrue(result.diagnostics.contains("Implicit field state"));
    }

    @Test void rejectsUnmodelledLibraryDependency() throws Exception {
        Compilation result = compile("@GenerateVerification @Postcondition(\"result >= 0\") static long f(long x) { return Math.abs(x); }");
        assertFalse(result.success);
        assertTrue(result.diagnostics.contains("current compilation"));
    }

    @Test void unmarkedTextAnnotationsAreNotProcessed() throws Exception {
        Compilation result = compile("@Precondition(\"human readable description\") @Postcondition(\"old narrative\") static long f(long x) { return x; }");
        assertTrue(result.success, result.diagnostics);
        assertFalse(Files.exists(result.classes.resolve("META-INF/formal-verification/manifest.json")));
    }

    @Test void preservesPrecedenceAndSnapshotsExpressionOnce() {
        var snapshots = new LinkedHashMap<String, String>();
        String expression = ContractExpression.compile("result == old(x + y) && old(x + y) > 0", Set.of("x", "y"), true, snapshots);
        assertEquals("((result == __fv_old0) && (__fv_old0 > 0))", expression);
        assertEquals(1, snapshots.size());
        assertEquals("(x + y)", snapshots.keySet().iterator().next());
    }

    @Test void acceptsParameterBeginningWithOldAndRejectsUnknownNames() {
        var snapshots = new LinkedHashMap<String, String>();
        assertEquals("(result == oldBalance)", ContractExpression.compile("result == oldBalance", Set.of("oldBalance"), true, snapshots));
        assertThrows(IllegalArgumentException.class, () -> ContractExpression.compile("result == typo", Set.of("x"), true, snapshots));
        assertThrows(IllegalArgumentException.class, () -> ContractExpression.compile("result == old(result)", Set.of("x"), true, snapshots));
        assertThrows(IllegalArgumentException.class, () -> ContractExpression.compile("result == Math.abs(x)", Set.of("x"), true, snapshots));
    }

    private record Compilation(boolean success, String diagnostics, Path classes, Path generated) { }
}
