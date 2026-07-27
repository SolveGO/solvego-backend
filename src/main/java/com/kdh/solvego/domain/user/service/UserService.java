package com.kdh.solvego.domain.user.service;

import com.kdh.solvego.domain.user.dto.SignupRequest;
import com.kdh.solvego.domain.user.dto.SignupResponse;
import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.exception.DuplicateUsernameException;
import com.kdh.solvego.domain.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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

        try {
            User savedUser = userRepository.save(user);
            return new SignupResponse(savedUser.getId());
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateUsernameException();
        }
    }
}
