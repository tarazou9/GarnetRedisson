# Garnet Redisson Quick Start Guide

## Prerequisites
- Have Garnet cluster created and running
- Familiarity with the [Redisson](https://github.com/redisson/redisson) and [Azure Identity for Java](https://learn.microsoft.com/azure/developer/java/sdk/identity) client libraries is required.
- **Dependency Requirements:**
   ```xml
    <dependency>
        <groupId>com.azure</groupId>
        <artifactId>azure-identity</artifactId>
        <version>1.11.2</version> <!-- {x-version-update;com.azure:azure-identity;dependency} -->
    </dependency>
    
    <dependency>
        <groupId>org.redisson</groupId>
        <artifactId>redisson</artifactId>
        <version>3.41.0</version> <!-- {x-version-update;org.redisson:redisson;external_dependency} -->
    </dependency>
   ```
## Authenticate with Microsoft Entra ID

### Authenticate for a Human
Run below command before running the sample code. 
```bash
# Using Azure CLI from a Bash Shell
az login --use-device-code
```

In `AuthenticateWithTokenCache.java` file, uncomment out Line 47 and comment out Line 54-56.
```java
DefaultAzureCredential defaultAzureCredential = new DefaultAzureCredentialBuilder().build();
```

### Authenticate for a Managed Identity.
Set up an MI for client machine. 
1. Provision a VM and have the client app running in VM.
2. Assign MI to the VM. User can do that through the "Security" --> "Identity" blade from the VM portal.
3. When creating Garnet cluster, make sure to choose the MI.
4. Get the client id of the MI. 
5. In `AuthenticateWithTokenCache.java` file, replace `managedIdentityClientId` value in Line 50 with the client id of above MI.

```java
    String managedIdentityClientId = "<MI client ID>";

    // Create a credential for a specific user-assigned managed identity
    ManagedIdentityCredential defaultAzureCredential = new ManagedIdentityCredentialBuilder()
        .clientId(managedIdentityClientId)
        .build();
```

### Entra Scope will be changed from "https://management.azure.com/.default" to "https://cosmos.azure.com/.default" in Upcoming June releases.

## Configure Node IP addresses
- In `NodeAliasMapper.java` file, replace all the IP addresses with Garnet cluster data node IP addresses.
- When creating Redisson client with cluster mode, use any data node IP is okay since Redisson will do auto discovery as a cluster for other nodes.

## Token Fresh Cache
This sample is intended to assist in authenticating with Microsoft Entra ID via the Redisson client library. It focuses on displaying the logic required to fetch a Microsoft Entra access token using a token cache and to use it as password when setting up the Redisson client instance. It also shows how to recreate and authenticate the Redisson client instance using the cached access token when its connection is broken in error/exception scenarios. The token cache stores and proactively refreshes the Microsoft Entra access token 2 minutes before expiry and ensures a non-expired token is available for use when the cache is accessed.

## To run with logging
Set the debug level, ex. debug in the "simplelogger.properties" file.