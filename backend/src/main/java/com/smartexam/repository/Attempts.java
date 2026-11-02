package com.smartexam.repository;

import com.smartexam.entity.Domain.Attempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface Attempts extends JpaRepository<Attempt, Long> {
    Optional<Attempt> findByIdAndStudentId(Long id, Long studentId);
    @Query(value = "select * from exam_attempts where id = :attemptId and student_id = :studentId for update", nativeQuery = true)
    Optional<Attempt> lockByIdAndStudentId(@Param("attemptId") Long attemptId, @Param("studentId") Long studentId);
    @Query(value = "select * from exam_attempts where submitted_at is null and deadline <= :now for update", nativeQuery = true)
    List<Attempt> lockExpiredAttempts(@Param("now") java.time.Instant now);
    List<Attempt> findByStudentIdOrderByStartedAtDesc(Long studentId);
    @Query(value = "select * from exam_attempts where student_id = :studentId order by started_at desc for update", nativeQuery = true)
    List<Attempt> lockByStudentIdOrderByStartedAtDesc(@Param("studentId") Long studentId);
    boolean existsByExamIdAndStudentId(Long examId, Long studentId);
    boolean existsByExamId(Long examId);
}
