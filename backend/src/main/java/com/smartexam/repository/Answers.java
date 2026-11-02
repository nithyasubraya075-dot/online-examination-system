package com.smartexam.repository;

import com.smartexam.entity.Domain.Answer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface Answers extends JpaRepository<Answer, Long> {
    List<Answer> findByAttemptId(Long attemptId);
    Optional<Answer> findByAttemptIdAndQuestionId(Long attemptId, Long questionId);
}
