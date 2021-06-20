package net.ultragrav.asyncworld;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Stringify {
    public static String stringify(Object obj) {
        return stringify(obj, new ArrayList<>());
    }

    public static String stringify(Object obj, List<Object> antirec) {
        if (antirec.contains(obj)) {
            return "<recursion>";
        }
        antirec.add(obj);
        if (obj == null) {
            return "null";
        }
        if (obj.getClass().isArray()) {
            if (obj instanceof int[]) {
                return Arrays.toString((int[]) obj);
            }
            if (obj instanceof long[]) {
                return Arrays.toString((long[]) obj);
            }
            return stringifyArr((Object[]) obj, antirec);
        }
        if (obj instanceof List) {
            StringBuilder builder = new StringBuilder("List[");
            for (Object ob : (List) obj) {
                builder.append(stringify(ob, antirec) + ", ");
            }
            builder.append("]");
            return builder.toString();
        }
        Class<?> clazz = obj.getClass();
        Field[] fields = clazz.getFields();
        if (fields.length == 0) {
            return "{}";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        for (Field field : fields) {
            try {
                builder.append(field.getName() + "=" + stringify(field.get(obj), antirec) + ",\n");
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        builder.append("}");
        return builder.toString();
    }

    public static String stringifyArr(Object[] arr, List<Object> antirec) {
        StringBuilder builder = new StringBuilder();
        builder.append("[");
        String str = Arrays.stream(arr)
                .map(ob -> stringify(ob, antirec))
                .collect(Collectors.joining(", "));
        builder.append(str);
        builder.append("]");
        return builder.toString();
    }
}
