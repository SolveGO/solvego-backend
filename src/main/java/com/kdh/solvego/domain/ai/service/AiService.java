package com.kdh.solvego.domain.ai.service;

import com.kdh.solvego.domain.ai.client.AiClient;
import com.kdh.solvego.domain.ai.dto.AiAnalyzeRequest;
import com.kdh.solvego.domain.ai.dto.AiAnalyzeResponse;
import com.kdh.solvego.domain.ai.dto.AiRecommendRequest;
import com.kdh.solvego.domain.ai.dto.AiRecommendResponse;
import com.kdh.solvego.domain.ai.dto.AiStatusResponse;
import org.springframework.stereotype.Service;

@Service
public class AiService {

    private final AiClient aiClient;

    public AiService(AiClient aiClient) {
        this.aiClient = aiClient;
    }

    public AiRecommendResponse recommend(AiRecommendRequest request) {
        return aiClient.recommend(request);
    }

    public AiAnalyzeResponse analyze(AiAnalyzeRequest request) {
        return aiClient.analyze(request);
    }

    public AiStatusResponse status() {
        return aiClient.status();
    }
}