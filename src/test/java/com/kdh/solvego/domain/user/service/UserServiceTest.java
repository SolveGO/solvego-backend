package com.kdh.solvego.domain.user.service;


import com.kdh.solvego.domain.attempt.repository.AttemptRepository;
import com.kdh.solvego.domain.problem.entity.Problem;
import com.kdh.solvego.domain.problem.repository.ProblemRepository;
import com.kdh.solvego.domain.user.dto.MyPageResponse;
import com.kdh.solvego.domain.user.dto.PasswordChangeRequest;
import com.kdh.solvego.domain.user.dto.SignupRequest;
import com.kdh.solvego.domain.user.dto.SignupResponse;
import com.kdh.solvego.domain.user.entity.User;
import com.kdh.solvego.domain.user.exception.CurrentPasswordMismatchException;
import com.kdh.solvego.domain.user.exception.DuplicateUsernameException;
import com.kdh.solvego.domain.user.repository.UserRepository;
import com.kdh.solvego.domain.user.type.SubscriptionPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private AttemptRepository attemptRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("회원가입에 성공한다")
    void signup_success(){
        // given
        SignupRequest request = new SignupRequest("username", "1234");

        when(userRepository.existsByUsername("username"))
                .thenReturn(false);

        when(passwordEncoder.encode("1234"))
                .thenReturn("encoded-password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation ->{
                    User savedUser = invocation.getArgument(0);
                    ReflectionTestUtils.setField(savedUser,"id",1L);
                    return savedUser;
                });

        // when
        SignupResponse response = userService.signup(request);

        //then
        assertThat(response.userId()).isEqualTo(1L);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getUsername()).isEqualTo("username");

        String savedPassword = (String) ReflectionTestUtils.getField(savedUser, "password");
        assertThat(savedPassword).isEqualTo("encoded-password");


        verify(userRepository).existsByUsername("username");
        verify(passwordEncoder).encode("1234");
    }

    @Test
    @DisplayName("중복된 username이면 예외가 발생한다")
    void duplicate_username_throws_exception() {
        // given
        SignupRequest request = new SignupRequest("username", "1234");

        when(userRepository.existsByUsername("username"))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.signup(request))
                .isInstanceOf(DuplicateUsernameException.class);

        verify(userRepository).existsByUsername("username");
        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("마이페이지에 사용자 정보, 활동 수, 등록 문제를 반환한다")
    void get_my_page_success() {
        User user = new User("username", "encoded");
        ReflectionTestUtils.setField(user, "id", 1L);
        ReflectionTestUtils.setField(
                user,
                "createdAt",
                LocalDateTime.of(2026, 1, 2, 3, 4)
        );
        Problem problem = org.mockito.Mockito.mock(Problem.class);
        when(problem.getId()).thenReturn(7L);
        when(problem.getTitle()).thenReturn("내 문제");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(problemRepository.findAllByCreatorIdOrderByIdDesc(1L))
                .thenReturn(List.of(problem));
        when(problemRepository.countByCreatorId(1L)).thenReturn(1L);
        when(attemptRepository.countDistinctProblemsByUserId(1L)).thenReturn(4L);
        when(attemptRepository.countDistinctWrongProblemsByUserId(1L)).thenReturn(2L);

        MyPageResponse response = userService.getMyPage(1L);

        assertThat(response.username()).isEqualTo("username");
        assertThat(response.registeredProblemCount()).isEqualTo(1);
        assertThat(response.solvedProblemCount()).isEqualTo(4);
        assertThat(response.wrongProblemCount()).isEqualTo(2);
        assertThat(response.plan()).isEqualTo(SubscriptionPlan.FREE);
        assertThat(response.problems()).hasSize(1);
        assertThat(response.problems().get(0).problemId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("현재 비밀번호를 확인한 뒤 새 비밀번호로 변경한다")
    void change_password_success() {
        User user = new User("username", "old-encoded");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "old-encoded")).thenReturn(true);
        when(passwordEncoder.encode("new")).thenReturn("new-encoded");

        userService.changePassword(1L, new PasswordChangeRequest("old", "new"));

        assertThat(ReflectionTestUtils.getField(user, "password"))
                .isEqualTo("new-encoded");
    }

    @Test
    @DisplayName("현재 비밀번호가 다르면 비밀번호를 변경하지 않는다")
    void change_password_fails_when_current_password_mismatches() {
        User user = new User("username", "old-encoded");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "old-encoded")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(
                1L,
                new PasswordChangeRequest("wrong", "new")
        )).isInstanceOf(CurrentPasswordMismatchException.class);

        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("회원 탈퇴는 FK 참조를 지키는 순서로 데이터를 삭제한다")
    void delete_account_deletes_relations_before_user() {
        when(userRepository.existsById(1L)).thenReturn(true);

        userService.deleteAccount(1L);

        InOrder order = inOrder(attemptRepository, problemRepository, userRepository);
        order.verify(attemptRepository).deleteAllByProblemCreatorId(1L);
        order.verify(attemptRepository).deleteAllByUserId(1L);
        order.verify(problemRepository).deleteAllByCreatorId(1L);
        order.verify(userRepository).deleteById(1L);
    }
}
