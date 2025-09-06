package com.sathish.voizable.service;

import com.sathish.voizable.model.CaptionJob;
import com.sathish.voizable.model.Transcription;
import com.sathish.voizable.repository.CaptionJobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.logging.Logger;

@Service
public class CaptionService {
    private static final Logger logger = Logger.getLogger(CaptionService.class.getName());

    @Autowired
    private TranscriptionService transcriptionService;

    @Autowired
    private FFmpegService ffmpegService;

    @Autowired
    private CaptionJobRepository captionJobRepository;

    @Value("${uploads.dir}")
    private String uploadsDir;

    @Value("${app.cleanup.max-retries:5}")
    private int cleanupMaxRetries;

    @Value("${app.cleanup.initial-delay:100}")
    private long cleanupInitialDelay;

    public CaptionJob startCaptioningJob(MultipartFile videoFile) {
        CaptionJob job = new CaptionJob();
        job.setOriginalFileName(videoFile.getOriginalFilename());
        return captionJobRepository.save(job);
    }

    @Async
    public void generateCaptionedVideo(MultipartFile videoFile, String fontStyle, String jobId) {
        CaptionJob job = captionJobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalStateException("Job not found with ID: " + jobId));

        job.setStatus(CaptionJob.Status.PROCESSING);
        captionJobRepository.save(job);

        File srtFile = null;
        File originalVideoFile = null;

        try {
            // This reuses your existing transcription logic to get the SRT content
            Transcription transcription = transcriptionService.transcribeAndSave(videoFile);
            String srtContent = transcription.getSrtContent();

            Path uploadsPath = Paths.get(uploadsDir);
            String srtFileName = UUID.randomUUID().toString() + ".srt";
            Path srtPath = uploadsPath.resolve(srtFileName);
            Files.writeString(srtPath, srtContent);
            srtFile = srtPath.toFile();

            String videoFileName = UUID.randomUUID().toString() + "_" + videoFile.getOriginalFilename();
            Path videoPath = uploadsPath.resolve(videoFileName);
            videoFile.transferTo(videoPath);
            originalVideoFile = videoPath.toFile();

            File captionedVideoFile = ffmpegService.burnSubtitles(originalVideoFile, srtFile, fontStyle);

            // Save the final video's filename to the job record
            job.setCaptionedVideoPath(captionedVideoFile.getName());
            job.setStatus(CaptionJob.Status.COMPLETED);

        } catch (Exception e) {
            logger.severe("Caption generation failed for job " + jobId + ": " + e.getMessage());
            e.printStackTrace();
            job.setStatus(CaptionJob.Status.FAILED);
            job.setErrorMessage(e.getMessage());
        } finally {
            job.setCompletedAt(LocalDateTime.now());
            captionJobRepository.save(job);

            // Clean up temporary files
            if (srtFile != null) cleanupFileWithRetry(srtFile.toPath(), "SRT file");
            if (originalVideoFile != null) cleanupFileWithRetry(originalVideoFile.toPath(), "Original video file");
        }
    }

    private void cleanupFileWithRetry(Path filePath, String fileType) {
        if (filePath == null || !Files.exists(filePath)) {
            return;
        }
        long retryDelayMs = cleanupInitialDelay;
        for (int i = 0; i < cleanupMaxRetries; i++) {
            try {
                Files.deleteIfExists(filePath);
                logger.info("Successfully cleaned up " + fileType + ": " + filePath.getFileName());
                return;
            } catch (IOException e) {
                if (i == cleanupMaxRetries - 1) {
                    logger.warning("Failed to clean up " + fileType + " after " + cleanupMaxRetries + " attempts: " + filePath.getFileName());
                } else {
                    try {
                        Thread.sleep(retryDelayMs);
                        retryDelayMs *= 2;
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }
}
