package com.smartexam.repository;

import com.smartexam.entity.Domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface Branches extends JpaRepository<Branch, Long> {
    Optional<Branch> findByCodeIgnoreCase(String code);
}
