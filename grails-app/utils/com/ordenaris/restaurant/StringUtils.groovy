package com.ordenaris.restaurant

import java.util.regex.Pattern

class StringUtils {
    static boolean onlyNumbers(String str) {
        if (str == null) return false
        return str ==~ /^[0-9]*$/
    }

    static boolean isValidUuid(String str) {
        if (str == null) return false
        return str.size() == 32 && str ==~ /^[a-fA-F0-9]+$/
    }

    static boolean onlyLettersAndSpaces(String str) {
        if (str == null) return false
        return str ==~ /^[A-Za-zÁÉÍÓÚáéíóúÑñ\s]+$/
    }

    static boolean matchesPattern(String str, Pattern pattern) {
        if (str == null) return false
        return pattern.matcher(str).matches()
    }
}
