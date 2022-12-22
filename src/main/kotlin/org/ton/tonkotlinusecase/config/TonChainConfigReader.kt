package org.ton.tonkotlinusecase.config

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import org.springframework.util.StreamUtils
import java.nio.charset.Charset

@Component
class TonChainConfigReader {

    @Value("\${ton.net-config}")
    private lateinit var configFile: Resource

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class TonNetConfig (
        @JsonProperty("liteservers")
        val liteservers: List<LiteServerParams>
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class LiteServerParams (
            @JsonProperty("ip")
            val ip: Int,
            @JsonProperty("port")
            val port: Int,
            @JsonProperty("id")
            val id: LiteServerId
        )
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class LiteServerId (
            @JsonProperty("@type")
            val type: String,
            @JsonProperty("key")
            val key: String
        )
    }

    fun load(): TonNetConfig {
        return ObjectMapper().readValue(
            StreamUtils.copyToString(configFile.inputStream, Charset.defaultCharset()),
            TonNetConfig::class.java
        )
    }
}
