package toys.timberix.lexerix.api

import java.math.BigDecimal
import java.math.RoundingMode

fun Float.asCurrency(): BigDecimal = BigDecimal.valueOf(toDouble()).setScale(2, RoundingMode.HALF_UP)