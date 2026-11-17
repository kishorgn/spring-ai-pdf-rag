package com.ignite;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

@ConfigurationProperties("app.rag")
public record RagProperties(
    Resource pdf,
    String vectorStorePath,
    int topK,
    double similarityThreshold
) {
}
