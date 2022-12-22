package org.ton.tonkotlinusecase.dto


data class TonTxMsg (
    val value: Long,
    val fwdFee: Long,
    val ihrFee: Long,
    val createdLt: Long,
    val source: String?,
    val destination: String?,
    val op: Long?,
    val msgAction: TonMsgAction?,
    val msgType: TonMsgType,
    val init: Boolean = false,
    val transferAmount: Long? = null,
    val comment: String? = null
)

enum class TonMsgType {
    IN, OUT ;

    fun inMsg(): Boolean = this == IN
    fun outMsg(): Boolean = this == OUT
}

enum class TonMsgAction {

    /**
     * in_msg.body isNullOrEmpty == false
     */
    INVOCATION,

    /**
     *
     * .The payload of a message may
        happen to be an empty cell slice, having no data bits and no references. By
        convention, such messages are used for simple value transfers

        init.value is null
        body isNullOrEmpty == true OR contains special transfer tag
     */
    TRANSFER,

    /**
     * in_msg.init is not null ; orig_status (!= active) -> end_status=active
     */
    INIT
}
