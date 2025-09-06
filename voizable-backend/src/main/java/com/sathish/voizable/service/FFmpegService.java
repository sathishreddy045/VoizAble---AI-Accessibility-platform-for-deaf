package com.sathish.voizable.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

@Service
public class FFmpegService {

    private static final Logger logger = Logger.getLogger(FFmpegService.class.getName());

    @Value("${ffmpeg.path}")
    private String ffmpegPath;

    @Value("${uploads.dir}")
    private String uploadsDir;

    // Helper class to consume and capture the process output stream.
    private static class StreamGobbler implements Runnable {
        private final InputStream inputStream;
        private final StringBuilder outputBuffer = new StringBuilder();

        public StreamGobbler(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                reader.lines().forEach(line -> outputBuffer.append(line).append("\n"));
            } catch (IOException e) {
                logger.warning("Error reading process output: " + e.getMessage());
            }
        }

        public String getOutput() {
            return outputBuffer.toString();
        }
    }

    public File extractAudio(MultipartFile videoFile) throws IOException, InterruptedException {
        Path uploadsPath = Paths.get(uploadsDir);
        if (!Files.exists(uploadsPath)) {
            Files.createDirectories(uploadsPath);
        }

        String originalFileName = videoFile.getOriginalFilename();
        Path tempVideoPath = uploadsPath.resolve(UUID.randomUUID().toString() + "_" + originalFileName);
        File audioOutputFile = null;

        try {
            videoFile.transferTo(tempVideoPath);

            String audioFileName = UUID.randomUUID().toString() + ".wav";
            audioOutputFile = uploadsPath.resolve(audioFileName).toFile();

            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegPath,
                    "-i", tempVideoPath.toAbsolutePath().toString(),
                    "-vn",
                    "-acodec", "pcm_s16le",
                    "-ar", "16000",
                    "-ac", "1",
                    audioOutputFile.getAbsolutePath()
            );

            Process process = processBuilder.start();

            StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream());
            new Thread(errorGobbler).start();

            boolean finished = process.waitFor(10, TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                throw new IOException("FFmpeg process timed out after 10 minutes.");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String ffmpegError = errorGobbler.getOutput();
                throw new IOException("FFmpeg failed to extract audio. Exit code: " + exitCode + ". Error: " + ffmpegError);
            }

            return audioOutputFile;
        } finally {
            cleanupWithRetry(tempVideoPath);
        }
    }

    public File burnSubtitles(File videoFile, File srtFile, String fontStyle) throws IOException, InterruptedException {
        Path uploadsPath = Paths.get(uploadsDir);
        String captionedVideoFileName = "captioned_" + UUID.randomUUID().toString() + ".mp4";
        File captionedVideoFile = uploadsPath.resolve(captionedVideoFileName).toFile();
        File tempFontFile = null;

        try {
            // Map font styles to actual font files
            String fontFileName = switch (fontStyle.toLowerCase()) {
                case "arial" -> "Arial.ttf";
                case "roboto" -> "Roboto-Regular.ttf";
                case "atma" -> "Atma-Regular.ttf";
                case "bangers" -> "Bangers-Regular.ttf";
                default -> "Poppins-Regular.ttf";
            };

            // Extract font to temporary file
            ClassPathResource fontResource = new ClassPathResource("fonts/" + fontFileName);
            if (!fontResource.exists()) {
                throw new IOException("Font file not found in resources: " + fontFileName);
            }
            tempFontFile = Files.createTempFile("font-", ".ttf").toFile();
            try (InputStream inputStream = fontResource.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(tempFontFile)) {
                FileCopyUtils.copy(inputStream, outputStream);
            }

            // Normalize paths for Windows - Use forward slashes and escape colons properly
            String srtPath = srtFile.getAbsolutePath().replace("\\", "/");
            String fontPath = tempFontFile.getAbsolutePath().replace("\\", "/");

            // For Windows paths, we need to escape the colon after drive letter
            if (srtPath.contains(":")) {
                srtPath = srtPath.replaceFirst(":", "\\\\:");
            }
            if (fontPath.contains(":")) {
                fontPath = fontPath.replaceFirst(":", "\\\\:");
            }

            // Create the subtitle filter - simplified approach
            String subtitleFilter = String.format(
                    "subtitles='%s':force_style='FontFile=%s,FontSize=18,PrimaryColour=&HFFFFFF,OutlineColour=&H000000,Outline=1,Shadow=1'",
                    srtPath, fontPath
            );

            logger.info("FFmpeg subtitle filter: " + subtitleFilter);

            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffmpegPath,
                    "-i", videoFile.getAbsolutePath(),
                    "-vf", subtitleFilter,
                    "-c:a", "copy",
                    "-y", // Overwrite output file if it exists
                    captionedVideoFile.getAbsolutePath()
            );

            // Set working directory to uploads folder
            processBuilder.directory(new File(uploadsDir));

            logger.info("Starting FFmpeg subtitle burn process...");
            logger.info("Command: " + String.join(" ", processBuilder.command()));

            Process process = processBuilder.start();

            StreamGobbler outputGobbler = new StreamGobbler(process.getInputStream());
            StreamGobbler errorGobbler = new StreamGobbler(process.getErrorStream());

            Thread outputThread = new Thread(outputGobbler);
            Thread errorThread = new Thread(errorGobbler);

            outputThread.start();
            errorThread.start();

            boolean finished = process.waitFor(15, TimeUnit.MINUTES);

            // Wait for threads to finish
            outputThread.join(2000);
            errorThread.join(2000);

            if (!finished) {
                process.destroyForcibly();
                throw new IOException("FFmpeg subtitle process timed out.");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String output = outputGobbler.getOutput();
                String error = errorGobbler.getOutput();
                logger.severe("FFmpeg failed. Output: " + output);
                logger.severe("FFmpeg failed. Error: " + error);
                throw new IOException("FFmpeg failed to burn subtitles. Exit code: " + exitCode +
                        ". Output: " + output + ". Error: " + error);
            }

            logger.info("FFmpeg subtitle burn completed successfully.");
            Thread.sleep(200); // Small delay to ensure file handles are released

            return captionedVideoFile;
        } finally {
            if (tempFontFile != null) {
                cleanupWithRetry(tempFontFile.toPath());
            }
        }
    }

    private void cleanupWithRetry(Path filePath) {
        if (filePath == null || !Files.exists(filePath)) {
            return;
        }
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                Files.deleteIfExists(filePath);
                logger.info("Successfully cleaned up: " + filePath.getFileName());
                return;
            } catch (IOException e) {
                if (i == maxRetries - 1) {
                    logger.warning("Failed to clean up file after " + maxRetries + " attempts: " +
                            filePath.getFileName());
                } else {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }
}
