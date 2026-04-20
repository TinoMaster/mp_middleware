package it.ariaspa.mypay.mypaycore.api.util;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class LogHelper {

    public static String methodToShortString(Method method) {
        return methodToString(method, false, false, false, false, false, false);
    }

    public static String methodToFullString(Method method) {
        return methodToString(method, true, true, true, true, true, true);
    }

    public static String methodToLongString(Method method) {
        return methodToString(method, true, true, true, true, false, false);
    }

    public static String methodToString(Method method) {
        return methodToString(method, false, false, true, false, false, false);
    }

    private static String methodToString(Method method, boolean includeModifier, boolean includeDeclaringClass, boolean includeArgs, boolean includeReturnType,
                                         boolean useLongReturnAndArgumentTypeName, boolean useLongTypeName) {

        StringBuilder sb = new StringBuilder();
        if (includeModifier) {
            sb.append(Modifier.toString(method.getModifiers()));
            sb.append(" ");
        }
        if (includeReturnType) {
            appendType(sb, method.getReturnType(), useLongReturnAndArgumentTypeName);
            sb.append(" ");
        }
        if (includeDeclaringClass) {
            appendType(sb, method.getDeclaringClass(), useLongTypeName);
            sb.append(".");
        }
        sb.append(method.getName());
        sb.append("(");
        Class<?>[] parametersTypes = method.getParameterTypes();
        appendTypes(sb, parametersTypes, includeArgs, useLongReturnAndArgumentTypeName);
        sb.append(")");
        return sb.toString();
    }

    private static void appendTypes(StringBuilder sb, Class<?>[] types, boolean includeArgs,
                                    boolean useLongReturnAndArgumentTypeName) {

        if (includeArgs) {
            for (int size = types.length, i = 0; i < size; i++) {
                appendType(sb, types[i], useLongReturnAndArgumentTypeName);
                if (i < size - 1) {
                    sb.append(",");
                }
            }
        } else {
            if (types.length != 0) {
                sb.append("..");
            }
        }
    }

    private static void appendType(StringBuilder sb, Class<?> type, boolean useLongTypeName) {
        if (type.isArray()) {
            appendType(sb, type.getComponentType(), useLongTypeName);
            sb.append("[]");
        } else {
            sb.append(useLongTypeName ? type.getName() : type.getSimpleName());
        }
    }
}
