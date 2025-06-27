To log in as a individual human, 

run "az login --use-device-code" before running this
Then "AuthenticateWithTokenCache.java', see below code block

// Run "az login --use-device-code" before running this.
//Construct a Token Credential from Identity library, e.g. DefaultAzureCredential / ClientSecretCredential / Client CertificateCredential / ManagedIdentityCredential etc.
DefaultAzureCredential defaultAzureCredential = new DefaultAzureCredentialBuilder().build();