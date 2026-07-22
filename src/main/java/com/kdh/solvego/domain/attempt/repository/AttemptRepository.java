package com.kdh.solvego.domain.attempt.repository;

import com.kdh.solvego.domain.attempt.entity.Attempt;
import com.kdh.solvego.domain.problem.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    @Query("""
            select p
            from Attempt a
            join a.problem p
            where a.user.id = :userId
              and a.isCorrect = false
            group by p
            order by max(a.attemptedAt) desc
            """)
    List<Problem> findWrongProblemsByUserId(@Param("userId") Long userId);

    @Modifying(
            clearAutomatically = true
    )
    @Query("""
            delete from Attempt a
            where a.problem.id = :problemId
            """)
    void deleteAllByProblemId(@Param("problemId") Long problemId);
}
