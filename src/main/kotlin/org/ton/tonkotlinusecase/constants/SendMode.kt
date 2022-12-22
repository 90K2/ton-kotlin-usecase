package org.ton.tonkotlinusecase.constants

object SendMode {
    const val CARRY_ALL_REMAINING_BALANCE = 128
    const val CARRY_ALL_REMAINING_INCOMING_VALUE = 64
    const val DESTROY_ACCOUNT_IF_ZERO = 32
    const val PAY_GAS_SEPARATELY = 1
    const val IGNORE_ERRORS = 2
}
