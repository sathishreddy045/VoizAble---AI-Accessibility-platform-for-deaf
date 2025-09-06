package com.sathish.voizable.service;

import com.sathish.voizable.dto.TranscriptionResponseDTO;
import com.sathish.voizable.model.Transcription;
import com.sathish.voizable.repository.TranscriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.UUID;

@Service
public class TranscriptionService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private FFmpegService ffmpegService;

    @Autowired
    private TranscriptionRepository transcriptionRepository;

    @Value("${ai.service.transcription.url}")
    private String aiServiceUrl;

    @Value("${uploads.dir}")
    private String uploadsDir;

    public Transcription transcribeAndSave(MultipartFile file) throws IOException, InterruptedException {
        // Start a timer for the whole process
        long totalStartTime = System.currentTimeMillis();
        System.out.println("INFO: Transcription process started for file: " + file.getOriginalFilename());

        File audioFile = null;
        try {
            // Step 1: Prepare the audio file
            String contentType = file.getContentType();
            if (contentType != null && contentType.startsWith("video")) {
                // Start a timer for the FFmpeg step
                long ffmpegStartTime = System.currentTimeMillis();
                System.out.println("INFO: It's a video. Extracting audio with FFmpeg...");
                audioFile = ffmpegService.extractAudio(file);
                long ffmpegEndTime = System.currentTimeMillis();
                System.out.println("INFO: FFmpeg audio extraction finished. Time taken: " + (ffmpegEndTime - ffmpegStartTime) + " ms");
            } else {
                // This part handles audio files directly
                System.out.println("INFO: It's an audio file. Saving it temporarily...");
                Path uploadsPath = Paths.get(uploadsDir);
                if (!Files.exists(uploadsPath)) {
                    Files.createDirectories(uploadsPath);
                }
                String tempAudioFileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
                audioFile = uploadsPath.resolve(tempAudioFileName).toFile();
                file.transferTo(audioFile);
                System.out.println("INFO: Temporary audio file saved.");
            }

            // Step 2: Call the Python AI Service
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new FileSystemResource(audioFile));

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // Start a timer for the AI service call
            long apiCallStartTime = System.currentTimeMillis();
            System.out.println("INFO: Calling Python AI service at " + aiServiceUrl + "...");
            ResponseEntity<TranscriptionResponseDTO> response = restTemplate.postForEntity(
                    aiServiceUrl,
                    requestEntity,
                    TranscriptionResponseDTO.class
            );
            long apiCallEndTime = System.currentTimeMillis();
            System.out.println("INFO: Python AI service call finished. Time taken: " + (apiCallEndTime - apiCallStartTime) + " ms");


            // Step 3: Create and save the Transcription entity to the database
            System.out.println("INFO: Saving transcription to database...");
            TranscriptionResponseDTO responseDTO = Objects.requireNonNull(response.getBody());
            Transcription transcription = new Transcription();
            transcription.setOriginalFileName(file.getOriginalFilename());
            transcription.setPlainText(responseDTO.getPlainText());
            transcription.setSrtContent(responseDTO.getSrtContent());

            Transcription savedTranscription = transcriptionRepository.save(transcription);
            System.out.println("INFO: Saved to database with ID: " + savedTranscription.getId());

            return savedTranscription;

        } finally {
            // Step 4: Clean up the temporary audio file
            if (audioFile != null && audioFile.exists()) {
                Files.delete(audioFile.toPath());
                System.out.println("INFO: Cleaned up temporary audio file: " + audioFile.getName());
            }
            // Stop the timer for the whole process and print the total time
            long totalEndTime = System.currentTimeMillis();
            System.out.println("INFO: Transcription process finished. Total time: " + (totalEndTime - totalStartTime) + " ms");
        }
    }
}
