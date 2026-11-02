package com.smartexam.repository;

import com.smartexam.entity.Domain.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface Students extends JpaRepository<Student, Long> {
    Optional<Student> findByUserId(Long userId);
    @Query(value = "select * from students where user_id = :userId for update", nativeQuery = true)
    Optional<Student> lockByUserId(@Param("userId") Long userId);
    boolean existsByUsnIgnoreCase(String usn);
    List<Student> findByBranchIdAndSemesterNumber(Long branchId, int number);
}
