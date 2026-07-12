## Classical Contour-Based Detection Overview

Classical contour-based detection identifies object boundaries using image processing techniques such as edge detection, thresholding, connected components, and contour extraction rather than machine learning models. These methods rely on analyzing visual properties such as intensity changes, shape, and geometry to locate objects within an image.

### Specific Use

Provides a lightweight approach for locating cards within a camera frame before applying perspective correction and OCR.

### Key Concepts

- Edge detection
- Canny edge detector
- Image thresholding
- Binary images
- Connected components
- Contour extraction
- Shape approximation
- Bounding rectangles
- Aspect ratio filtering
- Object segmentation

### Reference URLs

- https://docs.opencv.org/4.x/da/d22/tutorial_py_canny.html
- https://docs.opencv.org/4.x/d4/d73/tutorial_py_contours_begin.html
- https://szeliski.org/Book/
    - Chapter 3 - Image Processing
    - Chapter 5 - Segmentation

### Annotated Reading Notes

#### OpenCV Canny Edge Detection

The Canny edge detection tutorial describes an algorithm for identifying significant intensity changes in an image while reducing sensitivity to noise. The algorithm uses multiple stages, including Gaussian smoothing, gradient calculation, non-maximum suppression, double thresholding, and edge tracking by hysteresis.

Edge detection is commonly used as an initial step for object detection because object boundaries often correspond to strong intensity transitions. For trading cards, the contrast between the card border and the surrounding background can provide useful edge information for identifying card boundaries.

Relevant concepts for this project include:
- Reducing image noise before detecting edges.
- Selecting appropriate threshold values for different environments.
- Producing edge maps suitable for contour extraction.

#### OpenCV Contours: Getting Started

The OpenCV contours tutorial explains how contours represent the boundaries of connected regions within a binary image. Contours are typically extracted after thresholding or edge detection and can be analyzed to determine object shape, area, and position.

The tutorial introduces contour retrieval modes and contour approximation methods that reduce complex boundaries into simpler geometric representations. This is useful for identifying objects such as cards because a detected card contour can be approximated as a four-sided polygon.

Relevant concepts for this project include:
- Extracting candidate objects from binary images.
- Approximating contours using polygons.
- Filtering contours based on geometric properties.

#### Szeliski – Chapter 3: Image Processing

Chapter 3 introduces the fundamental image processing operations used to prepare images for computer vision tasks. Topics include filtering, image enhancement, gradients, edge detection, and image transformations.

The chapter explains how image derivatives can be used to detect changes in intensity and identify important structures within images. These concepts provide the mathematical foundation for techniques such as the Canny edge detector.

#### Szeliski – Chapter 5: Segmentation

Chapter 5 discusses methods for separating objects from their surrounding environment. Segmentation techniques divide images into meaningful regions based on properties such as intensity, color, or boundaries.

Contour-based card detection is a form of segmentation where the goal is to separate card regions from the background before recognition. The chapter provides background on why identifying object boundaries is a critical step in many computer vision pipelines.

### Takeaways for My Project

- Use classical computer vision methods as an initial approach for detecting Magic cards because cards have predictable rectangular geometry.
- Apply edge detection and contour extraction to identify candidate card regions.
- Filter detected contours using card-specific properties such as rectangular shape, area, and aspect ratio.
- Use contour detection as a lightweight alternative to deep learning models when processing real-time camera frames.
- Combine contour detection with perspective correction to generate normalized card images for OCR.
- Evaluate limitations of contour-based methods under challenging conditions such as overlapping cards, cluttered backgrounds, and poor lighting.
