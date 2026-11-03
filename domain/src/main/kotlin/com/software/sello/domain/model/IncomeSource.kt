package com.software.sello.domain.model

sealed interface IncomeSourceError {
    data class UnknownKey(val key: String) : IncomeSourceError

    /** "Otro" needs a name, and it must be a valid one. */
    data class InvalidOtherName(val reason: TextError) : IncomeSourceError

    /** A name was supplied for a source that does not take one. */
    data class UnexpectedName(val key: String) : IncomeSourceError
}

/**
 * Where a manually declared inflow came from. [key] is the stable stored identity;
 * labels are presentation's job. Transfer is a recorded inflow like the others: it
 * never implies a debit from, or a match with, another tracked account.
 */
sealed interface IncomeSource {
    val key: String

    data object Salary : IncomeSource {
        override val key = "salary"
    }

    data object Freelance : IncomeSource {
        override val key = "freelance"
    }

    data object PassiveIncome : IncomeSource {
        override val key = "passive_income"
    }

    data object Transfer : IncomeSource {
        override val key = "transfer"
    }

    data class Other(val name: SourceName) : IncomeSource {
        override val key get() = KEY

        companion object {
            const val KEY = "other"
        }
    }

    companion object {
        private val fixed = listOf(Salary, Freelance, PassiveIncome, Transfer)

        /** Keys in the approved order, for pickers and codecs. */
        val keys: List<String> = fixed.map { it.key } + Other.KEY

        /**
         * What a person picks and types: [of], with an "Otro" name held to
         * [SourceName.fromInput]; [stored] is the name already saved.
         */
        fun fromInput(
            key: String,
            otherName: String?,
            stored: String? = null
        ): Outcome<IncomeSource, IncomeSourceError> {
            if (key == Other.KEY) {
                val typed = SourceName.fromInput(otherName.orEmpty(), stored)
                if (typed is Outcome.Failure) {
                    return Outcome.Failure(IncomeSourceError.InvalidOtherName(typed.error))
                }
            }
            return of(key, otherName)
        }

        fun of(key: String, otherName: String?): Outcome<IncomeSource, IncomeSourceError> {
            if (key == Other.KEY) {
                return when (val name = SourceName.of(otherName.orEmpty())) {
                    is Outcome.Success -> Outcome.Success(Other(name.value))

                    is Outcome.Failure ->
                        Outcome.Failure(IncomeSourceError.InvalidOtherName(name.error))
                }
            }
            val source = fixed.firstOrNull { it.key == key }
            return when {
                source == null -> Outcome.Failure(IncomeSourceError.UnknownKey(key))
                otherName != null -> Outcome.Failure(IncomeSourceError.UnexpectedName(key))
                else -> Outcome.Success(source)
            }
        }
    }
}
