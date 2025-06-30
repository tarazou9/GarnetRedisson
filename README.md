To log in as a individual human, 

run "az login --use-device-code" before running this
Then "AuthenticateWithTokenCache.java', see below code block

// Run "az login --use-device-code" before running this.
//Construct a Token Credential from Identity library, e.g. DefaultAzureCredential / ClientSecretCredential / Client CertificateCredential / ManagedIdentityCredential etc.
DefaultAzureCredential defaultAzureCredential = new DefaultAzureCredentialBuilder().build();



Use MI/Service Principle to login 

1. Create your client app in VM, and assign MI to the VM.
2. Get the client id of the MI. 
3. When creating Garnet cluster, make sure to choose the MI.
4.         String managedIdentityClientId = "b7ac4f18-2ebc-4c19-b708-f89974514eda";

        // Create a credential for a specific user-assigned managed identity
        ManagedIdentityCredential defaultAzureCredential = new ManagedIdentityCredentialBuilder()
            .clientId(managedIdentityClientId)
            .build();


Entra Scope will change from "https://management.azure.com/.default" to "https://cosmos.azure.com/.default"


// To run with logging 
Set the debug level, ex. debug in the "simplelogger.properties" file.

// To turn off logging
mvn compile exec:java \
  -Dexec.mainClass="com.example.AuthenticateWithTokenCache" \
  -Dexec.args="-Dorg.slf4j.simpleLogger.log.org.redisson=off -Dorg.slf4j.simpleLogger.log.io.netty=off"
