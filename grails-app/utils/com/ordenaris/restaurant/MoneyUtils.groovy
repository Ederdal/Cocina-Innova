package com.ordenaris.restaurant

class MoneyUtils {
    static Integer amountToCents(def amount) {
        if (amount == null) return null
        BigDecimal decimal = amount as BigDecimal
        return (decimal.multiply(100)).setScale(0, BigDecimal.ROUND_HALF_UP).intValue()
    }

    static BigDecimal centsToAmount(Integer cents) {
        if (cents == null) return null
        return (cents as BigDecimal).divide(100)
    }
}
