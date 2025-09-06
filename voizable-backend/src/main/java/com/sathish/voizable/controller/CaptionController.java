package com.sathish.voizable.controller;

import com.sathish.voizable.model.CaptionJob;
import com.sathish.voizable.repository.CaptionJobRepository;
import com.sathish.voizable.service.CaptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api/captions")
public class CaptionController {

    @Autowired
    private CaptionService captionService;

    @Autowired
    private CaptionJobRepository captionJobRepository;

    @Value("${uploads.dir}")
    private String uploadsDir;

    @PostMapping("/generate")
    public ResponseEntity<?> generateCaptionedVideo(@RequestParam("file") MultipartFile file,
                                                    @RequestParam("fontStyle") String fontStyle) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty."));
        }
        try {
            CaptionJob job = captionService.startCaptioningJob(file);
            captionService.generateCaptionedVideo(file, fontStyle, job.getId());
            return ResponseEntity.accepted().body(Map.of("jobId", job.getId()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to start captioning job: " + e.getMessage()));
        }
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<?> getJobStatus(@PathVariable String jobId) {
        return captionJobRepository.findById(jobId)
                .map(job -> {
                    if (job.getStatus() == CaptionJob.Status.COMPLETED) {
                        String previewUrl = "/api/captions/preview/" + job.getCaptionedVideoPath();
                        String downloadUrl = "/api/captions/download/" + job.getId();
                        return ResponseEntity.ok(Map.of(
                                "status", job.getStatus(),
                                "previewUrl", previewUrl,
                                "downloadUrl", downloadUrl
                        ));
                    }
                    return ResponseEntity.ok(Map.of("status", job.getStatus()));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/preview/{filename}")
    public ResponseEntity<Resource> streamVideo(@PathVariable String filename) {
        File videoFile = Paths.get(uploadsDir, filename).toFile();
        if (!videoFile.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource fileSystemResource = new FileSystemResource(videoFile);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("video/mp4"))
                .body(fileSystemResource);
    }

    @GetMapping("/download/{jobId}")
    public ResponseEntity<Resource> downloadVideo(@PathVariable String jobId) {
        CaptionJob job = captionJobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != CaptionJob.Status.COMPLETED) {
            return ResponseEntity.notFound().build();
        }
        File videoFile = Paths.get(uploadsDir, job.getCaptionedVideoPath()).toFile();
        if (!videoFile.exists()) {
            return ResponseEntity.notFound().build();
        }
        Resource fileSystemResource = new FileSystemResource(videoFile);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("video/mp4"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + job.getOriginalFileName() + "\"")
                .body(fileSystemResource);
    }
}
