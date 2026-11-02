package com.smartexam.repository;

import com.smartexam.entity.Domain.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface Questions extends JpaRepository<Question, Long> {
    List<Question> findByExamIdOrderById(Long examId);
    long countByExamId(Long examId);
}
