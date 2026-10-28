package com.aiworkflow.designer.runtime.simulation;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Honest, limited condition evaluation for simulation walkthroughs. Supports
 * simple comparisons (==, !=, &gt;, &gt;=, &lt;, &lt;=), boolean literals,
 * bare variable truthiness, negation, and {@code &&}/{@code ||} (or UEL-style
 * {@code and}/{@code or}) conjunctions. Anything more complex — method calls,
 * bean references, arithmetic — returns {@link Outcome#UNEVALUATED} so callers
 * never pretend a branch was chosen on purpose.
 */
public final class ConditionEvaluator {

    public enum Outcome { TRUE, FALSE, UNEVALUATED }

    private static final Pattern COMPARISON = Pattern.compile(
            "^([a-zA-Z_][a-zA-Z0-9_.]*)\\s*(==|!=|>=|<=|>|<)\\s*(.+)$");
    private static final Pattern BARE_VARIABLE = Pattern.compile(
            "^!?\\s*[a-zA-Z_][a-zA-Z0-9_.]*$");

    private ConditionEvaluator() {
    }

    public static Outcome evaluate(String condition, Map<String, Object> variables) {
        if (condition == null) {
            return Outcome.UNEVALUATED;
        }
        String expr = unwrap(condition);
        if (expr.isEmpty()) {
            return Outcome.UNEVALUATED;
        }
        return evalOr(expr, variables);
    }

    /** Strips the ${...} UEL wrapper if present. */
    private static String unwrap(String condition) {
        String c = condition.trim();
        if (c.startsWith("${") && c.endsWith("}")) {
            return c.substring(2, c.length() - 1).trim();
        }
        return c;
    }

    private static Outcome evalOr(String expr, Map<String, Object> vars) {
        String[] parts = expr.split("\\s*(?:\\|\\||\\bor\\b)\\s*");
        boolean sawUnevaluated = false;
        for (String part : parts) {
            Outcome o = evalAnd(part, vars);
            if (o == Outcome.TRUE) {
                return Outcome.TRUE;
            }
            if (o == Outcome.UNEVALUATED) {
                sawUnevaluated = true;
            }
        }
        return sawUnevaluated ? Outcome.UNEVALUATED : Outcome.FALSE;
    }

    private static Outcome evalAnd(String expr, Map<String, Object> vars) {
        String[] parts = expr.split("\\s*(?:&&|\\band\\b)\\s*");
        boolean sawUnevaluated = false;
        for (String part : parts) {
            Outcome o = evalTerm(part.trim(), vars);
            if (o == Outcome.FALSE) {
                return Outcome.FALSE;
            }
            if (o == Outcome.UNEVALUATED) {
                sawUnevaluated = true;
            }
        }
        return sawUnevaluated ? Outcome.UNEVALUATED : Outcome.TRUE;
    }

    private static Outcome evalTerm(String term, Map<String, Object> vars) {
        if (term.isEmpty()) {
            return Outcome.UNEVALUATED;
        }

        // Parenthesised groups are not supported — bail honestly
        if (term.contains("(") || term.contains(")")) {
            return Outcome.UNEVALUATED;
        }

        if (term.startsWith("!")) {
            Outcome inner = evalTerm(term.substring(1).trim(), vars);
            return switch (inner) {
                case TRUE -> Outcome.FALSE;
                case FALSE -> Outcome.TRUE;
                case UNEVALUATED -> Outcome.UNEVALUATED;
            };
        }

        if (term.equalsIgnoreCase("true")) {
            return Outcome.TRUE;
        }
        if (term.equalsIgnoreCase("false")) {
            return Outcome.FALSE;
        }

        Matcher comparison = COMPARISON.matcher(term);
        if (comparison.matches()) {
            return evalComparison(comparison.group(1), comparison.group(2),
                    comparison.group(3).trim(), vars);
        }

        if (BARE_VARIABLE.matcher(term).matches()) {
            Object value = resolve(term, vars);
            return truthy(value) ? Outcome.TRUE : Outcome.FALSE;
        }

        return Outcome.UNEVALUATED;
    }

    private static Outcome evalComparison(String varName, String op, String literalText,
                                          Map<String, Object> vars) {
        Object left = resolve(varName, vars);
        Object right = parseLiteral(literalText, vars);
        if (right == UNKNOWN) {
            return Outcome.UNEVALUATED;
        }

        // Numeric comparison when both sides are numbers
        Double leftNum = toNumber(left);
        Double rightNum = toNumber(right);
        if (leftNum != null && rightNum != null) {
            int cmp = Double.compare(leftNum, rightNum);
            return switch (op) {
                case "==" -> bool(cmp == 0);
                case "!=" -> bool(cmp != 0);
                case ">" -> bool(cmp > 0);
                case ">=" -> bool(cmp >= 0);
                case "<" -> bool(cmp < 0);
                case "<=" -> bool(cmp <= 0);
                default -> Outcome.UNEVALUATED;
            };
        }

        // Ordering operators on non-numbers are not evaluable
        if (op.equals(">") || op.equals(">=") || op.equals("<") || op.equals("<=")) {
            return Outcome.UNEVALUATED;
        }

        boolean equal = left == null ? right == null : left.equals(right)
                || String.valueOf(left).equals(String.valueOf(right));
        return switch (op) {
            case "==" -> bool(equal);
            case "!=" -> bool(!equal);
            default -> Outcome.UNEVALUATED;
        };
    }

    private static final Object UNKNOWN = new Object();

    private static Object parseLiteral(String text, Map<String, Object> vars) {
        if (text.equalsIgnoreCase("true")) {
            return Boolean.TRUE;
        }
        if (text.equalsIgnoreCase("false")) {
            return Boolean.FALSE;
        }
        if (text.equalsIgnoreCase("null")) {
            return null;
        }
        if ((text.startsWith("'") && text.endsWith("'"))
                || (text.startsWith("\"") && text.endsWith("\""))) {
            return text.substring(1, text.length() - 1);
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ignored) {
        }
        // A second variable on the right-hand side is supported
        if (text.matches("[a-zA-Z_][a-zA-Z0-9_.]*")) {
            return resolve(text, vars);
        }
        return UNKNOWN;
    }

    private static Object resolve(String name, Map<String, Object> vars) {
        return vars != null ? vars.get(name) : null;
    }

    private static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.doubleValue() != 0;
        }
        String s = value.toString().trim();
        return !s.isEmpty() && !s.equalsIgnoreCase("false");
    }

    private static Double toNumber(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private static Outcome bool(boolean value) {
        return value ? Outcome.TRUE : Outcome.FALSE;
    }
}
