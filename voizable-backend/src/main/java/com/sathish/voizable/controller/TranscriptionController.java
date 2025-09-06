package com.sathish.voizable.controller;

import com.sathish.voizable.model.Transcription;
import com.sathish.voizable.service.TranscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/audio") // Changed from /api/v1/transcriptions
public class TranscriptionController {

    @Autowired
    private TranscriptionService transcriptionService;

    @PostMapping("/transcribe") // Added /transcribe mapping
    public ResponseEntity<?> createTranscription(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty. Please upload a valid file."));
        }

        try {
            Transcription savedTranscription = transcriptionService.transcribeAndSave(file);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedTranscription);
        } catch (Exception e) {
            // Log the exception for debugging
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An error occurred during transcription: " + e.getMessage()));
        }
    }
}