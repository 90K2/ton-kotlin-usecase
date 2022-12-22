package org.ton.tonkotlinusecase.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.ton.api.liteclient.config.LiteClientConfigGlobal
import org.ton.api.liteserver.LiteServerDesc
import org.ton.api.pub.PublicKeyEd25519
import org.ton.api.validator.config.ValidatorConfigGlobal
import org.ton.crypto.base64.base64
import org.ton.lite.client.LiteClient
import org.ton.tonkotlinusecase.contracts.LiteContract

@Configuration
class TonBeansConfig {

    @Bean("tonlibra-config-reader")
    fun tonConfig() = TonChainConfigReader()

    @Bean
    fun liteClient(tonChainConfigReader: TonChainConfigReader): LiteClient {
        return LiteClient(
            LiteClientConfigGlobal(
                liteservers = tonChainConfigReader.load().liteservers.map {
                    LiteServerDesc(id = PublicKeyEd25519(base64(it.id.key)), ip = it.ip, port = it.port)
                },
                validator = ValidatorConfigGlobal()
            )
        )
    }

    @Bean
    fun liteContract(): LiteContract = LiteContract(liteClient(tonConfig()))
}
