package com.kdh.solvego.domain.problem.repository;

import com.kdh.solvego.domain.problem.entity.Problem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProblemRepository extends JpaRepository<Problem, Long> {

    @Query("""
       select p
       from Problem p
       join fetch p.creator
       order by p.id desc
       """)
    List<Problem> findAllWithCreatorOrderByIdDesc();

    @Query(
            value = """
                    select p
                    from Problem p
                    join fetch p.creator
                    order by p.id desc
                    """,
            countQuery = """
                    select count(p)
                    from Problem p
                    """
    )
    Page<Problem> findAllWithCreatorOrderByIdDesc(Pageable pageable);

    @Query("""
            select p
            from Problem p
            join fetch p.creator
            where p.id = :problemId
            """)
    Optional<Problem> findByIdWithCreator(@Param("problemId") Long problemId);

}
