package ru.vkr.verification.processor;

import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import ru.vkr.verification.annotation.GenerateVerification;
import ru.vkr.verification.annotation.Invariant;
import ru.vkr.verification.annotation.Postcondition;
import ru.vkr.verification.annotation.Precondition;
import ru.vkr.verification.annotation.SymbolicRange;
import ru.vkr.verification.annotation.Verified;

@SupportedAnnotationTypes("ru.vkr.verification.annotation.GenerateVerification")
public final class VerificationProcessor extends AbstractProcessor {
    private final Map<String, String> manifest = new TreeMap<>();
    @Override public SourceVersion getSupportedSourceVersion() { return SourceVersion.latestSupported(); }

    @Override public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        for (Element element : round.getElementsAnnotatedWith(GenerateVerification.class)) {
            try { generate((ExecutableElement) element); }
            catch (IllegalArgumentException | IOException ex) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "FV: " + ex.getMessage(), element);
            }
        }
        if (round.processingOver() && !manifest.isEmpty() && !round.errorRaised()) {
            try (Writer writer = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "",
                    "META-INF/formal-verification/manifest.json").openWriter()) {
                writer.write("[\n" + String.join(",\n", manifest.values()) + "\n]\n");
            } catch (IOException ex) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "FV manifest: " + ex.getMessage());
            }
        }
        return true;
    }

    private void generate(ExecutableElement method) throws IOException {
        TypeElement owner = (TypeElement) method.getEnclosingElement();
        if (owner.getEnclosingElement().getKind() != ElementKind.PACKAGE || !method.getModifiers().contains(Modifier.STATIC)
                || method.getModifiers().contains(Modifier.PRIVATE)) {
            throw new IllegalArgumentException("Only accessible static methods in top-level classes are supported");
        }
        if (!method.getTypeParameters().isEmpty()) throw new IllegalArgumentException("Generic methods are unsupported");
        checkDependencies(method, new HashSet<>());
        String returnType = method.getReturnType().toString();
        if (!Set.of("long", "int", "boolean", "long[]").contains(returnType)) {
            throw new IllegalArgumentException("Return type must be long, int, boolean, or long[]");
        }
        if (owner.getAnnotationsByType(Invariant.class).length != 0) {
            throw new IllegalArgumentException("Class invariants are unsupported for static functions; put output invariants on the method");
        }
        GenerateVerification options = method.getAnnotation(GenerateVerification.class);
        String packageName = processingEnv.getElementUtils().getPackageOf(owner).getQualifiedName().toString();
        String suffix = method.getParameters().stream().map(p -> p.asType().toString()).reduce("", (a,b) -> a + "_" + b);
        String className = owner.getSimpleName() + "_" + method.getSimpleName() + suffix + "_Verification";
        String qualified = packageName.isEmpty() ? className : packageName + "." + className;
        Set<String> names = new HashSet<>();
        List<String> declarations = new ArrayList<>();
        List<String> bounds = new ArrayList<>();
        List<String> ranges = new ArrayList<>();
        for (VariableElement parameter : method.getParameters()) {
            String name = parameter.getSimpleName().toString();
            if (name.equals("result") || name.startsWith("__fv_")) throw new IllegalArgumentException("Reserved parameter name: " + name);
            names.add(name);
            TypeKind kind = parameter.asType().getKind();
            String intrinsic = switch (kind) {
                case LONG -> "nondetLong";
                case INT -> "nondetInt";
                case BOOLEAN -> "nondetBoolean";
                default -> throw new IllegalArgumentException("Unsupported parameter type: " + parameter.asType());
            };
            declarations.add("        " + parameter.asType() + " " + name + " = org.cprover.CProver." + intrinsic + "();\n");
            if (kind != TypeKind.BOOLEAN) {
                SymbolicRange range = parameter.getAnnotation(SymbolicRange.class);
                long min = range == null ? options.min() : range.min();
                long max = range == null ? options.max() : range.max();
                if (range == null && kind == TypeKind.INT) { min = Math.max(min, Integer.MIN_VALUE); max = Math.min(max, Integer.MAX_VALUE); }
                if (min > max || (kind == TypeKind.INT && (min < Integer.MIN_VALUE || max > Integer.MAX_VALUE))) {
                    throw new IllegalArgumentException("Invalid range for " + name);
                }
                bounds.add(name + " >= " + literal(min) + " && " + name + " <= " + literal(max));
                ranges.add("{\"parameter\":" + json(name) + ",\"min\":" + json(Long.toString(min)) + ",\"max\":" + json(Long.toString(max)) + "}");
            }
        }
        List<String> pre = Arrays.stream(method.getAnnotationsByType(Precondition.class)).map(Precondition::value).toList();
        List<String> post = Arrays.stream(method.getAnnotationsByType(Postcondition.class)).map(Postcondition::value).toList();
        List<String> invariants = Arrays.stream(method.getAnnotationsByType(Invariant.class)).map(Invariant::value).toList();
        if (post.isEmpty() && invariants.isEmpty()) throw new IllegalArgumentException("At least one output property is required");
        Map<String, String> snapshots = new LinkedHashMap<>();
        List<String> assumptions = new ArrayList<>(bounds);
        for (String condition : pre) assumptions.add(ContractExpression.compile(condition, names, false, snapshots));
        List<String> properties = new ArrayList<>();
        for (String condition : post) properties.add(ContractExpression.compile(condition, names, true, snapshots));
        for (String condition : invariants) properties.add(ContractExpression.compile(condition, names, true, snapshots));
        String setup = String.join("", declarations);
        for (String condition : assumptions) setup += "        org.cprover.CProver.assume(" + condition + ");\n";
        StringBuilder source = new StringBuilder("// Generated by VerificationProcessor; do not edit.\n");
        if (!packageName.isEmpty()) source.append("package ").append(packageName).append(";\n\n");
        source.append("public final class ").append(className).append(" {\n")
                .append("    public static void preconditions() {\n").append(setup)
                .append("        assert false : \"FV_PRECONDITIONS_REACHABLE\";\n    }\n\n")
                .append("    public static void verify() {\n").append(setup);
        snapshots.forEach((expression, variable) -> source.append("        var ").append(variable).append(" = ").append(expression).append(";\n"));
        String arguments = String.join(", ", method.getParameters().stream().map(p -> p.getSimpleName().toString()).toList());
        source.append("        ").append(returnType).append(" result = ").append(owner.getQualifiedName()).append(".")
                .append(method.getSimpleName()).append("(").append(arguments).append(");\n");
        for (int i = 0; i < properties.size(); i++) {
            source.append("        assert ").append(properties.get(i)).append(" : \"FV_PROPERTY_").append(i + 1).append("\";\n");
        }
        source.append("    }\n}\n");
        try (Writer writer = processingEnv.getFiler().createSourceFile(qualified, method).openWriter()) { writer.write(source.toString()); }
        Verified label = method.getAnnotation(Verified.class);
        String id = label == null || label.id().isBlank() ? owner.getQualifiedName() + "." + method.getSimpleName() : label.id();
        manifest.put(qualified, "  {\"id\":" + json(id) + ",\"harness\":" + json(qualified)
                + ",\"method\":" + json(owner.getQualifiedName() + "." + method.getSimpleName())
                + ",\"parameters\":" + jsonArray(method.getParameters().stream().map(p -> p.getSimpleName().toString()).toList())
                + ",\"ranges\":[" + String.join(",", ranges) + "],\"preconditions\":" + jsonArray(pre)
                + ",\"postconditions\":" + jsonArray(post) + ",\"invariants\":" + jsonArray(invariants) + "}");
    }

    /** Refuse implicit global state and unmodelled library calls rather than initialise them to defaults. */
    private void checkDependencies(ExecutableElement method, Set<ExecutableElement> visited) {
        if (!visited.add(method)) return;
        Trees trees = Trees.instance(processingEnv);
        TreePath path = trees.getPath(method);
        if (path == null || !method.getModifiers().contains(Modifier.STATIC)) {
            throw new IllegalArgumentException("Calls must target static methods available in the current compilation: " + method);
        }
        MethodTree declaration = (MethodTree) path.getLeaf();
        if (declaration.getBody() == null) throw new IllegalArgumentException("Native or abstract method dependency is unsupported");
        new TreePathScanner<Void, Void>() {
            private void checkField() {
                Element element = trees.getElement(getCurrentPath());
                if (element instanceof VariableElement variable && variable.getKind() == ElementKind.FIELD
                        && variable.getConstantValue() == null) {
                    throw new IllegalArgumentException("Implicit field state is unsupported: " + variable);
                }
            }
            @Override public Void visitIdentifier(IdentifierTree node, Void ignored) {
                checkField();
                return super.visitIdentifier(node, ignored);
            }
            @Override public Void visitMemberSelect(MemberSelectTree node, Void ignored) {
                boolean arrayLength = node.getIdentifier().contentEquals("length")
                        && trees.getTypeMirror(new TreePath(getCurrentPath(), node.getExpression())).getKind() == TypeKind.ARRAY;
                if (!arrayLength) checkField();
                return super.visitMemberSelect(node, ignored);
            }
            @Override public Void visitMethodInvocation(MethodInvocationTree node, Void ignored) {
                Element target = trees.getElement(new TreePath(getCurrentPath(), node.getMethodSelect()));
                if (!(target instanceof ExecutableElement called)) throw new IllegalArgumentException("Unresolved method call");
                checkDependencies(called, visited);
                return super.visitMethodInvocation(node, ignored);
            }
            @Override public Void visitNewClass(NewClassTree node, Void ignored) {
                Element type = trees.getElement(new TreePath(getCurrentPath(), node.getIdentifier()));
                if (type == null || !type.toString().equals("java.lang.IllegalArgumentException")) {
                    throw new IllegalArgumentException("Object construction is unsupported except IllegalArgumentException");
                }
                return super.visitNewClass(node, ignored);
            }
        }.scan(new TreePath(path, declaration.getBody()), null);
    }

    private static String literal(long value) { return value == Long.MIN_VALUE ? "Long.MIN_VALUE" : value + "L"; }
    private static String jsonArray(List<String> items) { return "[" + String.join(",", items.stream().map(VerificationProcessor::json).toList()) + "]"; }
    private static String json(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
}
