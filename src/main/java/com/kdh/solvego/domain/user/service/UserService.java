package com.kdh.solvego.domain.user.service;

import com.kdh.solvego.domain.attempt.repository.AttemptRepository;
import com.kdh.solvego.domain.problem.repository.ProblemRepository;
import com.kdh.solvego.domain.subscription.entity.Subscription;
import com.kdh.solvego.domain.subscription.repository.SubscriptionRepository;
import com.kdh.solvego.domain.subscription.service.SubscriptionEntitlementService;
import com.kdh.solvego.domain.user.dto.MyPageProblemResponse;
import com.kdh.solvego.domain.user.dto.MyPageResponse;
import com.kdh.solvego.domain.user.dto.PasswordChangeRequest;
import com.kdh.solvego.domain.user.dto.SignupRequest;
import com.kdh.solvego.domain.user.dto.SignupResponse;
import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.exception.CurrentPasswordMismatchException;
import com.kdh.solvego.domain.user.exception.DuplicateUsernameException;
import com.kdh.solvego.domain.user.exception.UserNotFoundException;
import com.kdh.solvego.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ProblemRepository problemRepository;
    private final AttemptRepository attemptRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionEntitlementService entitlementService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ProblemRepository problemRepository,
            AttemptRepository attemptRepository,
            SubscriptionRepository subscriptionRepository,
            SubscriptionEntitlementService entitlementService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.problemRepository = problemRepository;
        this.attemptRepository = attemptRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.entitlementService = entitlementService;
    }

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if(userRepository.existsByUsername(request.username())){
            throw new DuplicateUsernameException();
        }
        String encodedPassword = passwordEncoder.encode(request.password());

        User user = new User(
                request.username(),
                encodedPassword
        );

        User savedUser;
        try {
            savedUser = userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateUsernameException();
        }
        subscriptionRepository.save(Subscription.free(savedUser));
        return new SignupResponse(savedUser.getId());
    }

    @Transactional
    public MyPageResponse getMyPage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        List<MyPageProblemResponse> problems = problemRepository
                .findAllByCreatorIdOrderByIdDesc(userId)
                .stream()
                .map(problem -> new MyPageProblemResponse(
                        problem.getId(),
                        problem.getTitle()
                ))
                .toList();

        return new MyPageResponse(
                user.getUsername(),
                user.getCreatedAt(),
                problemRepository.countByCreatorId(userId),
                attemptRepository.countDistinctProblemsByUserId(userId),
                attemptRepository.countDistinctWrongProblemsByUserId(userId),
                entitlementService.currentPlan(userId),
                problems
        );
    }

    @Transactional
    public void changePassword(Long userId, PasswordChangeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        if (!user.matchesPassword(request.currentPassword(), passwordEncoder)) {
            throw new CurrentPasswordMismatchException();
        }
        user.changePassword(passwordEncoder.encode(request.newPassword()));
    }

    @CacheEvict(cacheNames = "problemPages", allEntries = true)
    @Transactional
    public void deleteAccount(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new UserNotFoundException();
        }
        attemptRepository.deleteAllByProblemCreatorId(userId);
        attemptRepository.deleteAllByUserId(userId);
        problemRepository.deleteAllByCreatorId(userId);
        userRepository.deleteById(userId);
    }
}
