package com.sathish.voizable.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "transcriptions")
public class Transcription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String originalFileName;

    @Lob // Specifies that the field should be stored as a Large Object (TEXT type)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String plainText;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String srtContent;

    @CreationTimestamp // Automatically sets the value when the record is first created
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    // --- Constructors ---
    public Transcription() {
    }

    // --- Getters and Setters ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getPlainText() {
        return plainText;
    }

    public void setPlainText(String plainText) {
        this.plainText = plainText;
    }

    public String getSrtContent() {
        return srtContent;
    }

    public void setSrtContent(String srtContent) {
        this.srtContent = srtContent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
