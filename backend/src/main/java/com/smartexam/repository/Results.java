package com.smartexam.repository;

import com.smartexam.entity.Domain.Result;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface Results extends JpaRepository<Result, Long> {
    Optional<Result> findByAttemptId(Long attemptId);
    List<Result> findAllByOrderByIdDesc();
}
