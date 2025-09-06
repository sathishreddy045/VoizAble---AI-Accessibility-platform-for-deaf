package com.sathish.voizable.dto;

import org.springframework.web.multipart.MultipartFile;

public class CaptionRequestDTO {

    private MultipartFile file;
    private String fontStyle;

    // Getters and Setters
    public MultipartFile getFile() {
        return file;
    }

    public void setFile(MultipartFile file) {
        this.file = file;
    }

    public String getFontStyle() {
        return fontStyle;
    }

    public void setFontStyle(String fontStyle) {
        this.fontStyle = fontStyle;
    }
}
