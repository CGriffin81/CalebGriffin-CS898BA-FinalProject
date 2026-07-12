## CameraX

CameraX is Android's modern camera framework that simplifies camera development by providing a lifecycle-aware API with consistent behavior across a wide variety of Android devices. It supports camera preview, image capture, and real-time image analysis, making it well suited for mobile computer vision applications.

### Specific Use

Capture a continuous video stream and provide image frames for real-time Magic: The Gathering card detection and OCR.

### Key Concepts

- Lifecycle-aware camera management
- Camera Preview
- ImageAnalysis use case
- ImageCapture use case
- Backpressure strategies
- Frame rotation handling
- Device compatibility
- CameraX Extensions

### Reference URLs

- https://developer.android.com/media/camera/camerax
- https://developer.android.com/codelabs/camerax-getting-started#0

### Annotated Reading Notes

#### CameraX Overview

This documentation introduces CameraX as the recommended camera framework for modern Android development. CameraX abstracts many device-specific camera differences while exposing three primary use cases: Preview, ImageCapture, and ImageAnalysis. The ImageAnalysis API delivers camera frames directly to an analyzer, allowing computer vision algorithms to process live video without requiring image capture.

Important concepts include lifecycle-aware camera binding, executor-based frame processing, and configurable backpressure strategies to prevent analysis from falling behind the camera frame rate.

#### CameraX Codelab

The codelab demonstrates how to initialize CameraX, display a live camera preview, bind lifecycle-aware camera use cases, and implement an ImageAnalysis analyzer. It also explains runtime permission handling and camera selection.

### Takeaways for My Project

- Use CameraX ImageAnalysis rather than ImageCapture.
- Configure KEEP_ONLY_LATEST backpressure to maintain real-time performance.
- Process frames asynchronously using Kotlin Coroutines.
- Allow CameraX to manage camera lifecycle automatically.
- Develop using CameraX to maximize compatibility across Android devices.
