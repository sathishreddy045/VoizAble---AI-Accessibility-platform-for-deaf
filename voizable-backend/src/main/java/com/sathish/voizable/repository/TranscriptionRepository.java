package com.sathish.voizable.repository;

import com.sathish.voizable.model.Transcription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TranscriptionRepository extends JpaRepository<Transcription, Long> {
    // JpaRepository provides all the standard database operations like
    // save(), findById(), findAll(), delete(), etc.
    // We can add custom query methods here if needed in the future.
}
