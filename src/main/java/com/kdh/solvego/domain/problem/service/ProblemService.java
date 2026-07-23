package com.kdh.solvego.domain.problem.service;

import com.kdh.solvego.domain.attempt.repository.AttemptRepository;
import com.kdh.solvego.domain.problem.exception.ProblemOwnershipException;
import com.kdh.solvego.domain.problem.repository.ProblemRepository;
import com.kdh.solvego.domain.problem.dto.*;
import com.kdh.solvego.domain.problem.entity.Problem;
import com.kdh.solvego.domain.problem.exception.ProblemNotFoundException;
import com.kdh.solvego.domain.problem.mapper.ProblemMapper;
import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.repository.UserRepository;
import com.kdh.solvego.domain.user.exception.UserNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
    private final AttemptRepository attemptRepository;

    public ProblemService(ProblemRepository problemRepository, UserRepository userRepository, ProblemMapper problemMapper, AttemptRepository attemptRepository) {
        this.problemRepository = problemRepository;
        this.userRepository = userRepository;
        this.problemMapper = problemMapper;
        this.attemptRepository = attemptRepository;
    }

    @Transactional(readOnly = true)
    public ProblemListResponse getProblems(){
        List<Problem> problems = problemRepository.findAllWithCreatorOrderByIdDesc();
        return problemMapper.toListResponse(problems);
    }

    @Cacheable(
            cacheNames = "problemPages",
            key = "'page:' + #page + ':size:' + #size",
            condition = "#page >= 0 && #page <= 2 && #size == 20"
    )
    @Transactional(readOnly = true)
    public ProblemPageResponse getProblems(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        Page<Problem> problemPage =
                problemRepository.findAllWithCreatorOrderByIdDesc(pageable);

        return problemMapper.toPageResponse(problemPage);
    }

    @CacheEvict(
            cacheNames = "problemPages",
            allEntries = true
    )
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

    @CacheEvict(
            cacheNames = "problemPages",
            allEntries = true
    )
    @Transactional
    public void updateProblem(
            Long userId,
            Long problemId,
            ProblemUpdateRequest request
    ) {
        Problem problem = problemRepository.findByIdWithCreator(problemId)
                .orElseThrow(ProblemNotFoundException::new);

        if (!problem.getCreator().getId().equals(userId)) {
            throw new ProblemOwnershipException();
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

    @CacheEvict(
            cacheNames = "problemPages",
            allEntries = true
    )
    @Transactional
    public void deleteProblem(Long userId, Long problemId) {
        Problem problem = problemRepository.findByIdWithCreator(problemId)
                .orElseThrow(ProblemNotFoundException::new);

        if (!problem.getCreator().getId().equals(userId)) {
            throw new ProblemOwnershipException();
        }

        attemptRepository.deleteAllByProblemId(problemId);
        problemRepository.deleteById(problemId);
    }

}
