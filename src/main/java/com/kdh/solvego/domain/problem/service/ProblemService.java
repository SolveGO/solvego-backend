package com.kdh.solvego.domain.problem.service;

import com.kdh.solvego.domain.problem.exception.ProblemAccessDeniedException;
import com.kdh.solvego.domain.problem.repository.ProblemRepository;
import com.kdh.solvego.domain.problem.dto.*;
import com.kdh.solvego.domain.problem.entity.Problem;
import com.kdh.solvego.domain.problem.exception.ProblemNotFoundException;
import com.kdh.solvego.domain.problem.mapper.ProblemMapper;
import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.repository.UserRepository;
import com.kdh.solvego.domain.user.exception.UserNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProblemService {

    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;
    private final ProblemMapper problemMapper;

    public ProblemService(ProblemRepository problemRepository, UserRepository userRepository, ProblemMapper problemMapper) {
        this.problemRepository = problemRepository;
        this.userRepository = userRepository;
        this.problemMapper = problemMapper;
    }

    @Transactional(readOnly = true)
    public ProblemListResponse getProblems(){
        List<Problem> problems = problemRepository.findAllWithCreatorOrderByIdDesc();
        return problemMapper.toListResponse(problems);
    }

    @Transactional(readOnly = true)
    public ProblemPageResponse getProblems(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        Page<Problem> problemPage =
                problemRepository.findAllWithCreatorOrderByIdDesc(pageable);

        return problemMapper.toPageResponse(problemPage);
    }

    @Transactional
    public ProblemCreateResponse createProblem(Long userId, ProblemCreateRequest request) {
        User creator=userRepository.findById(userId).orElseThrow(UserNotFoundException::new);
        Problem problem = problemMapper.toEntity(request, creator);
        Problem savedProblem = problemRepository.save(problem);
        return new ProblemCreateResponse(savedProblem.getId());
    }

    @Transactional(readOnly = true)
    public ProblemDetailResponse getProblem(Long problemId) {
        Problem problem = problemRepository.findByIdWithCreator(problemId)
                .orElseThrow(ProblemNotFoundException::new);
        return problemMapper.toDetailResponse(problem);
    }

    @Transactional
    public void updateProblem(
            Long userId,
            Long problemId,
            ProblemUpdateRequest request
    ) {
        Problem problem = problemRepository.findByIdWithCreator(problemId)
                .orElseThrow(ProblemNotFoundException::new);

        if (!problem.getCreator().getId().equals(userId)) {
            throw new ProblemAccessDeniedException();
        }

        problem.update(
                request.title(),
                request.description(),
                request.blackStones(),
                request.whiteStones(),
                request.nextPlayer(),
                request.answerPosition()
        );
    }

}
