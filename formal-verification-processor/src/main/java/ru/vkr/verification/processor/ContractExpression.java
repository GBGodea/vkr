package ru.vkr.verification.processor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small expression language: no method calls, assignment, or arbitrary Java statements. */
final class ContractExpression {
    private static final Pattern TOKEN = Pattern.compile(
            "\\s*([A-Za-z_$][A-Za-z0-9_$]*|[0-9]+[lL]?|&&|\\|\\||==|!=|<=|>=|[()+*/%<>=!.,\\[\\]\\-])");
    private static final Map<String, Integer> PRECEDENCE = Map.ofEntries(
            Map.entry("||", 1), Map.entry("&&", 2), Map.entry("==", 3), Map.entry("!=", 3),
            Map.entry("<", 4), Map.entry("<=", 4), Map.entry(">", 4), Map.entry(">=", 4),
            Map.entry("+", 5), Map.entry("-", 5), Map.entry("*", 6), Map.entry("/", 6), Map.entry("%", 6));
    private final List<String> tokens = new ArrayList<>();
    private final Set<String> parameters;
    private final boolean allowResult;
    private final Map<String, String> snapshots;
    private int position;
    private boolean insideOld;

    private ContractExpression(String source, Set<String> parameters, boolean allowResult,
                               Map<String, String> snapshots) {
        this.parameters = parameters;
        this.allowResult = allowResult;
        this.snapshots = snapshots;
        int offset = 0;
        while (offset < source.length()) {
            if (source.substring(offset).isBlank()) break;
            Matcher matcher = TOKEN.matcher(source);
            matcher.region(offset, source.length());
            if (!matcher.lookingAt()) throw new IllegalArgumentException("Unsupported token at: " + source.substring(offset));
            tokens.add(matcher.group(1));
            offset = matcher.end();
        }
    }

    static String compile(String source, Set<String> parameters, boolean allowResult,
                          Map<String, String> snapshots) {
        ContractExpression parser = new ContractExpression(source, parameters, allowResult, snapshots);
        String expression = parser.expression(1);
        if (parser.position != parser.tokens.size()) throw new IllegalArgumentException("Unexpected token: " + parser.peek());
        return expression;
    }

    private String expression(int minimum) {
        String left = atom();
        while (PRECEDENCE.getOrDefault(peek(), 0) >= minimum) {
            String operator = take();
            String right = expression(PRECEDENCE.get(operator) + 1);
            left = "(" + left + " " + operator + " " + right + ")";
        }
        return left;
    }

    private String atom() {
        String token = take();
        if (Set.of("!", "-", "+").contains(token)) return "(" + token + atom() + ")";
        if (token.equals("(")) {
            String value = expression(1);
            expect(")");
            return "(" + value + ")";
        }
        if (token.equals("old")) {
            if (!allowResult || insideOld) throw new IllegalArgumentException("old(...) is allowed only in output properties; nested old is unsupported");
            expect("(");
            insideOld = true;
            String original = expression(1);
            insideOld = false;
            expect(")");
            return snapshots.computeIfAbsent(original, ignored -> "__fv_old" + snapshots.size());
        }
        if (token.equals("Long") || token.equals("Integer")) {
            expect(".");
            String constant = take();
            if (!Set.of("MIN_VALUE", "MAX_VALUE").contains(constant)) throw new IllegalArgumentException("Unsupported constant");
            return token + "." + constant;
        }
        if (token.matches("[0-9]+[lL]?") || Set.of("true", "false", "null").contains(token)) return token;
        if (!parameters.contains(token) && !(allowResult && !insideOld && token.equals("result"))) {
            throw new IllegalArgumentException("Unknown name: " + token);
        }
        String value = token;
        if (peek().equals("[")) {
            take();
            String index = expression(1);
            expect("]");
            value += "[" + index + "]";
        }
        if (peek().equals(".")) {
            take();
            expect("length");
            value += ".length";
        }
        return value;
    }

    private String peek() { return position < tokens.size() ? tokens.get(position) : ""; }
    private String take() {
        if (position >= tokens.size()) throw new IllegalArgumentException("Incomplete expression");
        return tokens.get(position++);
    }
    private void expect(String token) {
        if (!take().equals(token)) throw new IllegalArgumentException("Expected " + token);
    }
}
