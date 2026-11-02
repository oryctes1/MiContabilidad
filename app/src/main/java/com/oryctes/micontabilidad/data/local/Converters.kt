package com.oryctes.micontabilidad.data.local

import androidx.room.TypeConverter
import com.oryctes.micontabilidad.domain.model.AccountType
import com.oryctes.micontabilidad.domain.model.ReviewStatus
import java.math.BigDecimal
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let { LocalDate.parse(it) }

    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toPlainString()

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? = value?.let { BigDecimal(it) }

    @TypeConverter
    fun fromAccountType(value: AccountType): String = value.name

    @TypeConverter
    fun toAccountType(value: String): AccountType = runCatching {
        AccountType.valueOf(value)
    }.getOrDefault(AccountType.CHECKING)

    @TypeConverter
    fun fromReviewStatus(value: ReviewStatus): String = value.name

    @TypeConverter
    fun toReviewStatus(value: String): ReviewStatus = runCatching {
        ReviewStatus.valueOf(value)
    }.getOrDefault(ReviewStatus.CONFIRMED)
}
