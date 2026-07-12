## Real-Time Tracking Overview

Real-time tracking follows detected objects across consecutive video frames to maintain object identity over time. Tracking allows computer vision systems to understand whether objects appearing in different frames represent the same physical object, reducing repeated processing and improving the stability of recognition results.

### Specific Use

For a Magic: The Gathering card scanner, tracking enables the application to recognize when a card has already been scanned and prevents repeatedly adding the same card while it remains visible in the camera stream.

### Key Concepts

- Object tracking
- Frame-to-frame correspondence
- Object identity
- Feature matching
- Image alignment
- Motion estimation
- Optical flow
- Object persistence
- Duplicate detection
- Multi-object tracking

### Reference URLs

- https://developer.android.com/media/camera/camerax
- https://developers.google.com/ml-kit
- https://szeliski.org/Book/
    - Chapter 8 - Image Alignment and Stitching

### Annotated Reading Notes

#### CameraX Documentation

The CameraX documentation describes the framework used to provide continuous image streams from Android camera hardware. The ImageAnalysis use case enables applications to process frames sequentially, which is the foundation required for real-time tracking systems.

The documentation discusses managing camera frame delivery, image analysis pipelines, and maintaining synchronization between camera input and application processing. These concepts are important for tracking because object identity must be maintained across a sequence of frames rather than individual images.

Relevant concepts for this project include:
- Processing continuous camera frames.
- Maintaining consistent image coordinate systems.
- Managing frame rates for real-time analysis.

#### ML Kit Documentation

The ML Kit documentation describes Google's mobile machine learning APIs and their integration into real-time vision applications. Although ML Kit does not provide a complete general-purpose object tracking framework for this project, its design principles for streaming analysis are relevant.

ML Kit emphasizes efficient processing of camera frames, managing computational cost, and using confidence values when interpreting recognition results. These concepts support a tracking system that can combine OCR confidence and detection stability across multiple frames.

Relevant concepts for this project include:
- Using recognition confidence to determine stable detections.
- Processing sequential frames efficiently.
- Combining multiple observations to improve reliability.

#### Szeliski – Chapter 8: Image Alignment and Stitching

Chapter 8 discusses methods for aligning images by finding relationships between corresponding points across different views. It introduces concepts such as feature matching, geometric transformations, and image warping.

Although the chapter primarily focuses on image alignment and panorama construction, the underlying concept of determining correspondence between images is fundamental to tracking. Tracking systems use similar ideas to associate objects detected in one frame with their location in subsequent frames.

Relevant concepts for this project include:
- Establishing correspondence between image features.
- Understanding geometric relationships between frames.
- Maintaining object identity through image transformations.

### Takeaways for My Project

- Use tracking to prevent the same Magic card from being added multiple times during continuous scanning.
- Maintain a history of recently recognized cards and their positions within the camera frame.
- Require consistent recognition across multiple frames before adding a card to the collection.
- Combine object location, OCR results, and confidence scores to determine whether a detected card is new or previously scanned.
- Consider simple tracking methods before implementing complex multi-object tracking algorithms.
- Use frame-to-frame correspondence to improve recognition stability in a live video environment.
