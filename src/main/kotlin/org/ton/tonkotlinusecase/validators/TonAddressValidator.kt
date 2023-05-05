package org.ton.tonkotlinusecase.validators

import java.util.*
import javax.validation.Constraint
import javax.validation.ConstraintValidator
import javax.validation.ConstraintValidatorContext
import javax.validation.Payload
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FIELD)
@Retention
@Constraint(validatedBy = [TonAddressValidator::class])
annotation class TonAddress(
    val message: String = "Wrong TON address format",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)


class TonAddressValidator: ConstraintValidator<TonAddress, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext?): Boolean {
        return value?.let {
            try {
                parse(it)
                true
            } catch (ex: Exception) {
                false
            }
        } ?: false
    }

    companion object {
        fun parse(address: String) = try {
            if (address.contains(':')) {
                parseRaw(address)
            } else {
                parseUserFriendly(address)
            }
        } catch (e: Exception) {
            throw IllegalArgumentException("Can't parse address: $address", e)
        }

        private fun parseRaw(address: String) {
            require(address.contains(':'))
            // 32 bytes, each represented as 2 characters
            require(address.substringAfter(':').length == 32 * 2)
        }

        private fun parseUserFriendly(address: String) {
            val addressBytes = ByteArray(36)

            try {
                Base64.getUrlDecoder().decode(address).copyInto(addressBytes)
            } catch (e: Exception) {
                try {
                    Base64.getDecoder().decode(address).copyInto(addressBytes)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Can't parse address: $address", e)
                }
            }
            val tag = addressBytes[0]
            val cleanTestOnly = tag and 0x7F.toByte()
            check((cleanTestOnly == 0x11.toByte()) or (cleanTestOnly == 0x51.toByte())) {
                "unknown address tag"
            }
            var workchainId = addressBytes[1].toInt()
            var rawAddress = addressBytes.copyOfRange(fromIndex = 2, toIndex = 2 + 32)
            var expectedChecksum =
                ((addressBytes[2 + 32].toInt() and 0xFF) shl 8) or (addressBytes[2 + 32 + 1].toInt() and 0xFF)

            val actualChecksum = checksum(tag, workchainId, rawAddress)
            check(expectedChecksum == actualChecksum) {
                "CRC check failed"
            }
        }

        private fun checksum(tag: Byte, workchainId: Int, address: ByteArray): Int =
            crc16(byteArrayOf(tag, workchainId.toByte()), address)

        // Get the tag byte based on set flags
        private fun tag(testOnly: Boolean, bounceable: Boolean): Byte =
            (if (testOnly) 0x80.toByte() else 0.toByte()) or
                    (if (bounceable) 0x11.toByte() else 0x51.toByte())
    }
}
