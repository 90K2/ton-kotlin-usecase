# Getting Started

Step 0: make sure that your IDE was ready to use this project :
jetbrains://idea/settings?name=Build%2C+Execution%2C+Deployment--Build+Tools--Gradle : Gradle JVM 18
Project Settings - Project - SDK - 18

How to connect ton-kotlin in your project: 

```groovy
repositories {
	maven { url = uri("https://jitpack.io") }
}

implementation("org.ton:ton-kotlin:0.1.1")

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

LiteClient `org.ton.tonkotlinusecase.LiteClientTests`

Accounts: 

- get account info raw 
- get account info DTO (with suggested object mapping)

Blocks: 

- get last masterchain block 
- decode coin transfer with comment
- decode notification about NFT receiving

Contract get methods examples `org.ton.tonkotlinusecase.GetMethodsTest`

- get nft data 
- get collectable nft address by index
- get collection data

Wallet `org.ton.tonkotlinusecase.WalletTests`

- get wallet seqno
- send TONs
- deploy nft
- transfer nft