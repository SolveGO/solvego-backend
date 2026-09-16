package com.kdh.solvego.domain.user.controller;

import com.kdh.solvego.domain.problem.dto.WrongProblemResponse;
import com.kdh.solvego.domain.attempt.service.AttemptService;
import com.kdh.solvego.domain.user.service.UserService;
import com.kdh.solvego.domain.user.dto.MyPageResponse;
import com.kdh.solvego.domain.user.dto.PasswordChangeRequest;
import com.kdh.solvego.domain.user.dto.SignupRequest;
import com.kdh.solvego.domain.user.dto.SignupResponse;
import com.kdh.solvego.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "User", description = "사용자 관련 API")
@RestController
@RequestMapping(value="/api/users",produces = MediaType.APPLICATION_JSON_VALUE)
public class UserController {

    private final UserService userService;
    private final AttemptService attemptService;

    public UserController(UserService userService, AttemptService attemptService) {
        this.userService = userService;
        this.attemptService = attemptService;
    }


    @Operation(
            summary = "회원가입",
            description = "사용자 아이디와 비밀번호를 입력받아 새로운 사용자를 생성합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "회원가입 성공"),
            @ApiResponse(
                    responseCode = "400",
                    description = "요청 형식 오류",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "이미 존재하는 username",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SignupResponse signup(@Valid @RequestBody SignupRequest request){
        return userService.signup(request);
    }

    @Operation(
            summary = "틀린 문제 조회",
            description = "로그인한 사용자가 틀렸던 문제 목록을 조회합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "틀린 문제 목록 조회 성공"),
            @ApiResponse(
                    responseCode = "401",
                    description = "인증되지 않은 사용자",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ErrorResponse.class)
                    )
            )
    })

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me/wrong-problems")
    public List<WrongProblemResponse> getWrongProblems(
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();

        return attemptService.getWrongProblems(userId);
    }

    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public MyPageResponse getMyPage(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return userService.getMyPage(userId);
    }

    @SecurityRequirement(name = "bearerAuth")
    @PutMapping(
            value = "/me/password",
            consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            Authentication authentication,
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        Long userId = (Long) authentication.getPrincipal();
        userService.changePassword(userId, request);
    }

    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        userService.deleteAccount(userId);
    }
}
