package com.kdh.solvego.domain.problem.mapper;

import com.kdh.solvego.domain.problem.dto.*;
import com.kdh.solvego.domain.problem.entity.Problem;
import com.kdh.solvego.domain.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProblemMapper {

    public Problem toEntity(ProblemCreateRequest request, User creator) {
        return new Problem(
                request.title(),
                request.description(),
                request.blackStones(),
                request.whiteStones(),
                request.nextPlayer(),
                request.answerPosition(),
                creator
        );
    }

    public ProblemPageResponse toPageResponse(Page<Problem> problemPage) {
        Page<ProblemSummaryResponse> responsePage =
                problemPage.map(this::toSummaryResponse);

        return new ProblemPageResponse(
                responsePage.getContent(),
                responsePage.getNumber(),
                responsePage.getSize(),
                responsePage.getTotalElements(),
                responsePage.getTotalPages()
        );
    }

    public ProblemListResponse toListResponse(List<Problem> problems) {
        List<ProblemSummaryResponse> problemResponses = problems.stream()
                .map(this::toSummaryResponse)
                .toList();

        return new ProblemListResponse(problemResponses);
    }

    public ProblemSummaryResponse toSummaryResponse(Problem problem) {
        return new ProblemSummaryResponse(
                problem.getId(),
                problem.getTitle(),
                problem.getCreator().getUsername()
        );
    }

    public ProblemDetailResponse toDetailResponse(Problem problem) {
        return new ProblemDetailResponse(
                problem.getId(),
                problem.getTitle(),
                problem.getDescription(),
                problem.getBlackStones(),
                problem.getWhiteStones(),
                problem.getNextPlayer(),
                problem.getCreator().getUsername()
        );
    }

    public List<WrongProblemResponse> toWrongProblemResponses(List<Problem> problems) {
        return problems.stream()
                .map(this::toWrongProblemResponse)
                .toList();
    }

    private WrongProblemResponse toWrongProblemResponse(Problem problem) {
        return new WrongProblemResponse(
                problem.getId(),
                problem.getTitle()
        );
    }
}