package com.sathish.voizable.repository;

import com.sathish.voizable.model.CaptionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CaptionJobRepository extends JpaRepository<CaptionJob, String> {
    // Spring Data JPA automatically provides all the necessary database
    // operations like save(), findById(), etc. based on this interface.
}
