package com.smartexam.repository;

import com.smartexam.entity.Domain.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface Teachers extends JpaRepository<Teacher, Long> {
    Optional<Teacher> findByUserId(Long userId);
}
