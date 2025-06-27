// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.example;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.DefaultAzureCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.dns.DnsServerAddressStreamProvider;

import org.redisson.Redisson;
import org.redisson.api.RBucket;
import org.redisson.api.RBuckets;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisAuthRequiredException;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.RedisException;
import org.redisson.client.RedisWrongPasswordException;
import org.redisson.config.Config;
import org.redisson.config.SslVerificationMode;
import org.redisson.config.TransportMode;
import org.redisson.connection.DnsAddressResolverGroupFactory;


import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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

        Logger logger = LoggerFactory.getLogger(AuthenticateWithTokenCache.class);
        logger.debug("DEBUG test message");
        logger.info("INFO test message");

        // TODO: make sure to run "az login --use-device-code" before running this.
        //Construct a Token Credential from Identity library, e.g. DefaultAzureCredential / ClientSecretCredential / Client CertificateCredential / ManagedIdentityCredential etc.
        DefaultAzureCredential defaultAzureCredential = new DefaultAzureCredentialBuilder().build();

        // Fetch a Microsoft Entra token to be used for authentication. This token will be used as the password.
        // TODO: the scope will be switching to "https://cosmos.azure.com/.default"
        TokenRequestContext trc = new TokenRequestContext().addScopes("https://management.azure.com/.default");

        // Instantiate the Token Refresh Cache, this cache will proactively refresh the access token 2 - 5 minutes before expiry.
        GarnetClientTokenProvider tokenProvider = new GarnetClientTokenProvider(defaultAzureCredential, trc);
        AccessToken accessToken = tokenProvider.getAccessToken();
        String username = extractUsernameFromToken(accessToken.getToken());

        // Create Redisson Client
        // Host Name, Port, and Microsoft Entra token are required here.
        // TODO: Replace <HOST_NAME> with Garnet data node private IP.
        RedissonClient redisson = createRedissonClient("rediss://10.41.0.54:6379", username, accessToken);
        System.out.println("RedissonClient created.");
        int maxTries = 3;
        int i = 0;

        while (i < maxTries) {
            try {
                // perform operations
                System.out.println("Trying to access Redis...");
                RBuckets rBuckets = redisson.getBuckets();
                System.out.println("Trying to set key");
                RBucket<String> bucket = redisson.getBucket("Az:key");
                bucket.set("This is object value");

                String objectValue = bucket.get().toString();
                System.out.println("stored object value: " + objectValue);
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
                    AccessToken token = tokenProvider.getAccessToken();
                    // Recreate the client with a fresh token non-expired token as password for
                    // authentication.
                    redisson = createRedissonClient("rediss://10.41.0.54:6379", username, token);
                } else {
                    exception.printStackTrace();
                    System.out.println("Im here");
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


    // Helper Code
    private static RedissonClient createRedissonClient(String address, String username, AccessToken accessToken) {
        Config config = new Config();
        config.setTransportMode(TransportMode.NIO);

        System.out.println("Creating RedissonClient...");
        System.out.println("username " + username);

        config.useClusterServers()
            .setDnsMonitoringInterval(-1) // prevent use of alias like dc1000005
            .addNodeAddress(
                "rediss://10.41.0.54:6379" // any node, Redisson will do auto discovery as a cluster for other nodes
            )
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
    public static class GarnetClientTokenProvider {
        private final TokenCredential tokenCredential;
        private final TokenRequestContext tokenRequestContext;
        private volatile AccessToken accessToken;

        /**
         * Creates an instance of TokenRefreshCache
         * @param tokenCredential the token credential to be used for authentication.
         * @param tokenRequestContext the token request context to be used for authentication.
         */
        public GarnetClientTokenProvider(TokenCredential tokenCredential, TokenRequestContext tokenRequestContext) {
            this.tokenCredential = tokenCredential;
            this.tokenRequestContext = tokenRequestContext;
        }

        /**
         * Gets the cached access token.
         * @return the {@link AccessToken}
         */
        public AccessToken getAccessToken() {
            if (accessToken != null) {
                return  accessToken;
            } else {
                accessToken = tokenCredential.getToken(tokenRequestContext).block();
                return accessToken;
            }
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