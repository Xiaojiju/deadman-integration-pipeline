package com.mtfm.gateway.spi.payload;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 设备值 ↔ 业务值的加减乘除。
 *
 * <p>配置按<strong>入站</strong>方向：设备 238、运算 divide 10 → 业务 23.8。
 * 出站自动取逆运算：业务 23.8 → 设备 238。
 */
public final class ScaleTransform {

    public static final String NONE = "none";
    public static final String ADD = "add";
    public static final String SUBTRACT = "subtract";
    public static final String MULTIPLY = "multiply";
    public static final String DIVIDE = "divide";

    private ScaleTransform() {
    }

    public static boolean configured(String op, String operand) {
        return parseOp(op) != null && parseOperand(operand) != null;
    }

    /** 设备值 → 业务值。 */
    public static Object inbound(Object raw, String op, String operand) {
        return apply(raw, parseOp(op), parseOperand(operand), false);
    }

    /** 业务值 → 设备值（入站运算的逆）。 */
    public static Object outbound(Object raw, String op, String operand) {
        return apply(raw, parseOp(op), parseOperand(operand), true);
    }

    private static Object apply(Object raw, String op, BigDecimal operand, boolean inverse) {
        if (raw == null || op == null || operand == null) {
            return raw;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return raw;
        }
        String direction = inverse ? inverseOp(op) : op;
        if (DIVIDE.equals(direction) && operand.compareTo(BigDecimal.ZERO) == 0) {
            return raw;
        }
        BigDecimal result = switch (direction) {
            case ADD -> value.add(operand);
            case SUBTRACT -> value.subtract(operand);
            case MULTIPLY -> value.multiply(operand);
            case DIVIDE -> value.divide(operand, 8, RoundingMode.HALF_UP);
            default -> value;
        };
        return toNumber(result.stripTrailingZeros());
    }

    private static String inverseOp(String op) {
        return switch (op) {
            case ADD -> SUBTRACT;
            case SUBTRACT -> ADD;
            case MULTIPLY -> DIVIDE;
            case DIVIDE -> MULTIPLY;
            default -> op;
        };
    }

    private static String parseOp(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.trim().toLowerCase();
        return switch (key) {
            case ADD, "+", "plus" -> ADD;
            case SUBTRACT, "-", "minus" -> SUBTRACT;
            case MULTIPLY, "*", "x", "mul" -> MULTIPLY;
            case DIVIDE, "/", "div" -> DIVIDE;
            case NONE, "" -> null;
            default -> null;
        };
    }

    private static BigDecimal parseOperand(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Object toNumber(BigDecimal value) {
        if (value.scale() <= 0) {
            try {
                return value.longValueExact();
            } catch (ArithmeticException ignored) {
                return value;
            }
        }
        return value.doubleValue();
    }
}
