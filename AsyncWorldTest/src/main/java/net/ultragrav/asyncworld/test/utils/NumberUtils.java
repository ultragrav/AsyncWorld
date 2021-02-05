/*
 * Copyright (c) 2020. UltraDev
 */

package net.ultragrav.asyncworld.test.utils;

import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.TreeMap;

public class NumberUtils {
    private final static TreeMap<Integer, String> numeralMap = new TreeMap<>();
    private static String[] multipliers = {"K", "M", "B", "T", "q", "Q", "s", "S", "O", "N", "D", "Ud", "Dd", "Td",
            "qd", "Qd", "sd", "Sd", "Od", "Nd"};

    static {
        numeralMap.put(1000, "M");
        numeralMap.put(900, "CM");
        numeralMap.put(500, "D");
        numeralMap.put(400, "CD");
        numeralMap.put(100, "C");
        numeralMap.put(90, "XC");
        numeralMap.put(50, "L");
        numeralMap.put(40, "XL");
        numeralMap.put(10, "X");
        numeralMap.put(9, "IX");
        numeralMap.put(5, "V");
        numeralMap.put(4, "IV");
        numeralMap.put(1, "I");
    }

    public static String toRoman(int number) {
        if (number == 0) {
            return "0";
        }
        int l = numeralMap.floorKey(number);
        if (number == l) {
            return numeralMap.get(number);
        }
        return numeralMap.get(l) + toRoman(number - l);
    }

    public static String toReadableNumber(long l) {
        return toReadableNumber(BigInteger.valueOf(l));
    }

    public static String toReadableNumber(@NotNull BigInteger number) {
        if (number.compareTo(BigInteger.ZERO) == 0) {
            return "0";
        }
        if (number.compareTo(BigInteger.ZERO) < 0) {
            return "-" + toReadableNumber(number.negate());
        }
        int l = (int) Math.floor(BigMath.logBigInteger(number) / Math.log(10.0) / 3d);
        if (l == 0) {
            return number.toString();
        }
        int magnitude = l * 3;
        BigInteger[] divRem = number.divideAndRemainder(BigInteger.TEN.pow(magnitude));
        int div = divRem[0].intValue();
        BigInteger rem = divRem[1].divide(BigInteger.TEN.pow(magnitude - 1));
        int remI = rem.intValue();
        String remS = remI + "";
        /*if (remS.length() == 1) {
            remS = "0" + remS;
        }*/
        return div + "." + remS + multipliers[l - 1];
    }

    @NotNull
    public static String formatFull(BigInteger number) {
        return NumberFormat.getNumberInstance(Locale.US).format(number);
    }

    @NotNull
    public static String formatFull(int number) {
        return NumberFormat.getNumberInstance(Locale.US).format(number);
    }

    @NotNull
    public static String formatFull(long number) {
        return NumberFormat.getNumberInstance(Locale.US).format(number);
    }

    public static String formatFull(double number) {
        return NumberFormat.getNumberInstance(Locale.US).format(number);
    }
}