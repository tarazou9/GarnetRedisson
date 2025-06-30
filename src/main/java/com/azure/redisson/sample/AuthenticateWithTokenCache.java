// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.redisson.sample;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.redisson.Redisson;
import org.redisson.api.RBucket;
import org.redisson.api.RBuckets;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisAuthRequiredException;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisWrongPasswordException;
import org.redisson.config.Config;
import org.redisson.config.SslVerificationMode;
import org.redisson.config.TransportMode;


import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadLocalRandom;


/**
 * A sample with authenticating with a token cache.
 */
public class AuthenticateWithTokenCache {

    /**
     * The runnable sample.
     *
     * @param args Ignored.
     */
    public static void main(String[] args) {
        // TODO: make sure to run "az login --use-device-code" before running this.
        // Construct a Token Credential from Identity library, e.g. DefaultAzureCredential / ClientSecretCredential / Client CertificateCredential / ManagedIdentityCredential etc.
        // DefaultAzureCredential defaultAzureCredential = new DefaultAzureCredentialBuilder().build();
                
        // Replace with your MI's client ID
        String managedIdentityClientId = "b7ac4f18-2ebc-4c19-b708-f89974514eda";
        String entraScope = "https://management.azure.com/.default";

        // Create a credential for a specific user-assigned managed identity
        ManagedIdentityCredential defaultAzureCredential = new ManagedIdentityCredentialBuilder()
            .clientId(managedIdentityClientId)
            .build();

        // Fetch a Microsoft Entra token to be used for authentication. This token will be used as the password.
        TokenRequestContext trc = new TokenRequestContext().addScopes(entraScope);

        // Instantiate the Token Refresh Cache, this cache will proactively refresh the access token 2 - 5 minutes before expiry.
        TokenRefreshCache tokenRefreshCache = new TokenRefreshCache(defaultAzureCredential, trc);
        AccessToken accessToken = tokenRefreshCache.getAccessToken();
        String username = extractUsernameFromToken(accessToken.getToken());
        System.out.println("Username: " + username);

        // Create Redisson Client. Host Name, Port, and Microsoft Entra token are required here.
        String address = "rediss://" + NodeAliasMapper.HOST_ALIAS_MAP.get("dc1000000") + ":6379"; // any node, Redisson will do auto discovery as a cluster for other nodes
        RedissonClient redisson = createRedissonClient(address, username, accessToken);
        System.out.println("RedissonClient created.");
        int maxTries = 3;
        int i = 0;

        while (i < maxTries) {
            try {
                // perform operations
                System.out.println("Trying to access Garnet...");
                RBuckets rBuckets = redisson.getBuckets();
                System.out.println("Trying to set key");
                RBucket<String> bucket = redisson.getBucket("TestKey");
                bucket.set("This is object value");

                String objectValue = bucket.get().toString();
                System.out.println("Stored object value: " + objectValue);
                break;
            } catch (RedisConnectionException exception) {          
                Throwable rootCause = exception;
                while (rootCause.getCause() != null) {
                    rootCause = rootCause.getCause();
                }
                if (rootCause instanceof RedisWrongPasswordException || rootCause instanceof RedisAuthRequiredException) {
                    System.out.println("Caught Redis auth exception: " + rootCause.getMessage());
                    rootCause.printStackTrace();

                    System.out.println("Redisson is shutting down due to Redis auth exception.");
                    if (redisson != null) {
                        redisson.shutdown();
                    }
                    AccessToken token = tokenRefreshCache.getAccessToken();
                    // Recreate the client with a fresh token non-expired token as password for authentication.
                    redisson = createRedissonClient(address, username, token);
                } else {
                    exception.printStackTrace();
                } 
            } catch (Exception e) {
                // Handle Exception as required
                e.printStackTrace();
            }
            i++;
        }
        redisson.shutdown();
        System.out.println("Redisson shutdown complete.");
    }


     /**
     * Create Redisson Client with username and token.
     */
    private static RedissonClient createRedissonClient(String address, String username, AccessToken accessToken) {
        Config config = new Config();
        config.setTransportMode(TransportMode.NIO);

        System.out.println("Creating RedissonClient...");
        System.out.println("Username " + username);

        config.useClusterServers()
            .setDnsMonitoringInterval(-1) // prevent use of alias like dc1000005
            .addNodeAddress(address)
            .setUsername(username)
            .setPassword(accessToken.getToken())
            .setSslVerificationMode(SslVerificationMode.NONE);
        
        // Override DnsAddressResolverGroupFactory to prevent DNS lookups
        config.setAddressResolverGroupFactory(new NoopDnsAddressResolverFactory());

        return Redisson.create(config);
    }


     /**
     * The token cache to store and proactively refresh the access token.
     */
    public static class TokenRefreshCache {
        private final TokenCredential tokenCredential;
        private final TokenRequestContext tokenRequestContext;
        private final Timer timer;
        private volatile AccessToken accessToken;
        private final Duration maxRefreshOffset = Duration.ofMinutes(5);
        private final Duration baseRefreshOffset = Duration.ofMinutes(2);

        /**
         * Creates an instance of TokenRefreshCache
         * @param tokenCredential the token credential to be used for authentication.
         * @param tokenRequestContext the token request context to be used for authentication.
         */
        public TokenRefreshCache(TokenCredential tokenCredential, TokenRequestContext tokenRequestContext) {
            this.tokenCredential = tokenCredential;
            this.tokenRequestContext = tokenRequestContext;
            this.timer = new Timer();
        }

        /**
         * Gets the cached access token.
         * @return the {@link AccessToken}
         */
        public AccessToken getAccessToken() {
            if (accessToken != null) {
                return  accessToken;
            } else {
                TokenRefreshTask tokenRefreshTask = new TokenRefreshTask();
                accessToken = tokenCredential.getToken(tokenRequestContext).block();
                timer.schedule(tokenRefreshTask, getTokenRefreshDelay());
                return accessToken;
            }
        }

        private class TokenRefreshTask extends TimerTask {
            // Add your task here
            public void run() {
                accessToken = tokenCredential.getToken(tokenRequestContext).block();
                System.out.println("Refreshed Token with Expiry: " + accessToken.getExpiresAt().toEpochSecond());
                timer.schedule(new TokenRefreshTask(), getTokenRefreshDelay());
            }
        }

        private long getTokenRefreshDelay() {
            return ((accessToken.getExpiresAt()
                .minusSeconds(ThreadLocalRandom.current().nextLong(baseRefreshOffset.getSeconds(), maxRefreshOffset.getSeconds()))
                .toEpochSecond() - OffsetDateTime.now().toEpochSecond()) * 1000);
        }
    }

    private static String extractUsernameFromToken(String token) {
        String[] parts = token.split("\\.");
        String base64 = parts[1];

        int modulo = base64.length() % 4;
        if (modulo == 2) {
            base64 += "==";
        } else if (modulo == 3) {
            base64 += "=";
        }

        byte[] jsonBytes = Base64.getDecoder().decode(base64);
        String json = new String(jsonBytes, StandardCharsets.UTF_8);
        JsonObject jwt = JsonParser.parseString(json).getAsJsonObject();

        return jwt.get("oid").getAsString();
    }
}