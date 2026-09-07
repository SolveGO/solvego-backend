package com.kdh.solvego.domain.ai.client;

import com.kdh.solvego.domain.ai.dto.AiAnalyzeRequest;
import com.kdh.solvego.domain.ai.dto.AiAnalyzeResponse;
import com.kdh.solvego.domain.ai.dto.AiGameNextMoveAiResponse;
import com.kdh.solvego.domain.ai.dto.AiGameNextMoveRequest;
import com.kdh.solvego.domain.ai.dto.AiRecommendRequest;
import com.kdh.solvego.domain.ai.dto.AiRecommendResponse;
import com.kdh.solvego.domain.ai.dto.AiStatusResponse;
import com.kdh.solvego.domain.ai.exception.AiServerException;
import com.kdh.solvego.domain.ai.exception.AiTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AiClient {

    private final RestClient restClient;

    public AiClient(
            RestClient.Builder restClientBuilder,
            @Value("${ai.base-url}") String aiBaseUrl
    ) {
        this.restClient = restClientBuilder
                .baseUrl(aiBaseUrl)
                .build();
    }

    public AiRecommendResponse recommend(AiRecommendRequest request) {
        try {
            return restClient.post()
                    .uri("/recommend")
                    .body(request)
                    .retrieve()
                    .body(AiRecommendResponse.class);

        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.GATEWAY_TIMEOUT) {
                throw new AiTimeoutException();
            }

            throw new AiServerException();

        } catch (ResourceAccessException e) {
            throw new AiServerException();
        }
    }

    public AiAnalyzeResponse analyze(AiAnalyzeRequest request) {
        try {
            return restClient.post()
                    .uri("/analyze")
                    .body(request)
                    .retrieve()
                    .body(AiAnalyzeResponse.class);

        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.GATEWAY_TIMEOUT) {
                throw new AiTimeoutException();
            }

            throw new AiServerException();

        } catch (ResourceAccessException e) {
            throw new AiServerException();
        }
    }

    public AiGameNextMoveAiResponse gameNextMove(
            AiGameNextMoveRequest request
    ) {
        try {
            return restClient.post()
                    .uri("/game/next-move")
                    .body(request)
                    .retrieve()
                    .body(AiGameNextMoveAiResponse.class);

        } catch (RestClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.GATEWAY_TIMEOUT) {
                throw new AiTimeoutException();
            }

            throw new AiServerException();

        } catch (ResourceAccessException e) {
            throw new AiServerException();
        }
    }

    public AiStatusResponse status() {
        try {
            restClient.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity();

            return new AiStatusResponse("ONLINE");

        } catch (RestClientResponseException | ResourceAccessException e) {
            return new AiStatusResponse("OFFLINE");
        }
    }
}