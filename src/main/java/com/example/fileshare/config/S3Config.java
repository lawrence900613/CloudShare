package com.example.fileshare.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(
            @Value("${app.aws.region}") String region,
            @Value("${app.aws.access-key}") String accessKey,
            @Value("${app.aws.secret-key}") String secretKey
    ) {
        S3ClientBuilder builder = S3Client.builder().region(Region.of(region));
        builder.credentialsProvider(resolveCredentialsProvider(accessKey, secretKey));
        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner(
            @Value("${app.aws.region}") String region,
            @Value("${app.aws.access-key}") String accessKey,
            @Value("${app.aws.secret-key}") String secretKey
    ) {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(resolveCredentialsProvider(accessKey, secretKey))
                .build();
    }

    private AwsCredentialsProvider resolveCredentialsProvider(String accessKey, String secretKey) {
        if (!accessKey.isBlank() && !secretKey.isBlank()) {
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            return StaticCredentialsProvider.create(credentials);
        }
        return DefaultCredentialsProvider.create();
    }
}
