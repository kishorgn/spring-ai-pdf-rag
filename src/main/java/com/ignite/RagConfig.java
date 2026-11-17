package com.ignite;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {
    @Bean
    public SimpleVectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                VectorStore vectorStore,
                                RagProperties props) {
        SearchRequest searchRequest = SearchRequest.builder()
                .topK(props.topK())
                .similarityThreshold(props.similarityThreshold())
                .build();

        QuestionAnswerAdvisor ragAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchRequest)
                .build();

        return builder
                .defaultSystem("""
                        You answer questions about a single document.
                        Use only the information in the provided context.
                        If the answer is not in the context, reply exactly: 
                        "I could not find that in the document."
                        """)
                .defaultAdvisors(ragAdvisor)
                .build();
    }
}
