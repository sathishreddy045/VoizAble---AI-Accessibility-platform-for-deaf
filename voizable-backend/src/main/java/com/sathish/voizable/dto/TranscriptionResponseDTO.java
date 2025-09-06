package com.sathish.voizable.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

// This class maps directly to the JSON object returned by our Python AI service.
public class TranscriptionResponseDTO {

    @JsonProperty("plain_text")
    private String plainText;

    @JsonProperty("srt_content")
    private String srtContent;

    // Getters and Setters
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
}