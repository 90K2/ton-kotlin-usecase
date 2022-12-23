# Getting Started

Step 0: make sure that your IDE is ready to use this project : 

- jetbrains://idea/settings?name=Build%2C+Execution%2C+Deployment--Build+Tools--Gradle : Gradle JVM 18
- Project Settings - Project - SDK - 18

How to connect ton-kotlin in your project: 

```groovy
repositories {
	maven { url = uri("https://jitpack.io") }
}

implementation("org.ton:ton-kotlin:0.2.1")

```

Jitpack repo is needed because of `ton-kotlin` dependency `kotlinio-base64`  

Blockchain config file setup over `application.yml`

```groovy
ton:
  net-config: classpath:global-config.json
```

### Config components

`config.TonChainConfigReader` : config class that serves for reading json config and setup liteClient
`config.TonBeansConfig` : config class that creating main beans, eg. LiteClient that will often be used later 

### Use cases

[LiteClient](src/test/kotlin/org/ton/tonkotlinusecase/LiteClientTests.kt) `org.ton.tonkotlinusecase.LiteClientTests`

Accounts: 

- get account info raw 
- get account info DTO (with suggested object mapping)

Blocks: 

- get last masterchain block 
- decode coin transfer with comment
- decode notification about NFT receiving

[Contract get methods examples](src/test/kotlin/org/ton/tonkotlinusecase/GetMethodsTest.kt) `org.ton.tonkotlinusecase.GetMethodsTest`

- get nft data 
- get collectable nft address by index
- get collection data

[Wallet](src/test/kotlin/org/ton/tonkotlinusecase/WalletTests.kt) `org.ton.tonkotlinusecase.WalletTests`

Before start you need to set wallet seed phrase in env variable `WALLET_MNEMONIC` or directly inside [application.yml](src/main/resources/application.yml)

- get wallet seqno
- send TONs
- deploy nft
- transfer nft

#### Advanced options 

- [Workaround for building LiteClient on nearest and live liteserver only](https://github.com/90K2/ton-kotlin-usecase/blob/master/src/main/kotlin/org/ton/tonkotlinusecase/config/TonlibBeanConfig.kt#L37)
