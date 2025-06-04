
package com.plcoding.audiorecorder.forms;
public class PhotoSourceConfig {
    private String source;
    private boolean camera_enabled;
    private boolean gallery_enabled;
    private String description;
    private String camera_preference;
    private boolean prefer_front_camera;
    private boolean prefer_back_camera;
    private boolean allow_camera_choice;
    private String camera_description;
    private String camera_instructions;

    // Default constructor
    public PhotoSourceConfig() {
        this.source = "both";
        this.camera_enabled = true;
        this.gallery_enabled = true;
        this.description = "Camera and Gallery";
        this.camera_preference = "back";
        this.prefer_front_camera = false;
        this.prefer_back_camera = true;
        this.allow_camera_choice = false;
        this.camera_description = "Back Camera";
        this.camera_instructions = "Use the back camera for taking photos";
    }

    // Constructor with parameters
    public PhotoSourceConfig(String source, boolean cameraEnabled, boolean galleryEnabled, String cameraPreference) {
        this();
        this.source = source;
        this.camera_enabled = cameraEnabled;
        this.gallery_enabled = galleryEnabled;
        this.camera_preference = cameraPreference;
        updateDerivedFields();
    }

    // Update derived fields based on camera preference
    private void updateDerivedFields() {
        if ("front".equals(camera_preference)) {
            this.prefer_front_camera = true;
            this.prefer_back_camera = false;
            this.allow_camera_choice = false;
            this.camera_description = "Front Camera";
            this.camera_instructions = "Use the front camera for taking photos";
        } else if ("back".equals(camera_preference)) {
            this.prefer_front_camera = false;
            this.prefer_back_camera = true;
            this.allow_camera_choice = false;
            this.camera_description = "Back Camera";
            this.camera_instructions = "Use the back camera for taking photos";
        } else if ("any".equals(camera_preference)) {
            this.prefer_front_camera = false;
            this.prefer_back_camera = false;
            this.allow_camera_choice = true;
            this.camera_description = "Any Camera";
            this.camera_instructions = "Choose between front or back camera";
        }

        // Update description based on enabled sources
        if (camera_enabled && gallery_enabled) {
            this.description = "Camera and Gallery";
        } else if (camera_enabled) {
            this.description = "Camera Only";
        } else if (gallery_enabled) {
            this.description = "Gallery Only";
        } else {
            this.description = "No Source Available";
        }
    }

    // Getters and setters
    public String getSource() { return source; }
    public void setSource(String source) {
        this.source = source;
        updateDerivedFields();
    }

    public boolean isCameraEnabled() { return camera_enabled; }
    public void setCameraEnabled(boolean camera_enabled) {
        this.camera_enabled = camera_enabled;
        updateDerivedFields();
    }

    public boolean isGalleryEnabled() { return gallery_enabled; }
    public void setGalleryEnabled(boolean gallery_enabled) {
        this.gallery_enabled = gallery_enabled;
        updateDerivedFields();
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCameraPreference() { return camera_preference; }
    public void setCameraPreference(String camera_preference) {
        this.camera_preference = camera_preference;
        updateDerivedFields();
    }

    public boolean isPrefersrontCamera() { return prefer_front_camera; }
    public void setPrefersrontCamera(boolean prefer_front_camera) { this.prefer_front_camera = prefer_front_camera; }

    public boolean isPrefersBackCamera() { return prefer_back_camera; }
    public void setPrefersBackCamera(boolean prefer_back_camera) { this.prefer_back_camera = prefer_back_camera; }

    public boolean allowsCameraChoice() { return allow_camera_choice; }
    public void setAllowsCameraChoice(boolean allow_camera_choice) { this.allow_camera_choice = allow_camera_choice; }

    public String getCameraDescription() { return camera_description; }
    public void setCameraDescription(String camera_description) { this.camera_description = camera_description; }

    public String getCameraInstructions() { return camera_instructions; }
    public void setCameraInstructions(String camera_instructions) { this.camera_instructions = camera_instructions; }

    // Helper methods
    public boolean isCameraOnly() {
        return camera_enabled && !gallery_enabled;
    }

    public boolean isGalleryOnly() {
        return !camera_enabled && gallery_enabled;
    }

    public boolean isBothAllowed() {
        return camera_enabled && gallery_enabled;
    }

    public boolean isValidConfiguration() {
        return camera_enabled || gallery_enabled;
    }

    // Static factory methods
    public static PhotoSourceConfig cameraOnly(String cameraPreference) {
        return new PhotoSourceConfig("camera", true, false, cameraPreference);
    }

    public static PhotoSourceConfig galleryOnly() {
        return new PhotoSourceConfig("gallery", false, true, "back");
    }

    public static PhotoSourceConfig both(String cameraPreference) {
        return new PhotoSourceConfig("both", true, true, cameraPreference);
    }

    public static PhotoSourceConfig backCameraOnly() {
        return cameraOnly("back");
    }

    public static PhotoSourceConfig frontCameraOnly() {
        return cameraOnly("front");
    }

    public static PhotoSourceConfig anyCameraOnly() {
        return cameraOnly("any");
    }

    @Override
    public String toString() {
        return "PhotoSourceConfig{" +
                "source='" + source + '\'' +
                ", camera_enabled=" + camera_enabled +
                ", gallery_enabled=" + gallery_enabled +
                ", camera_preference='" + camera_preference + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}