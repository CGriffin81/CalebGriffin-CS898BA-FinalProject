## OpenCV Overview

OpenCV (Open Source Computer Vision Library) is an open-source library containing algorithms and tools for image processing, computer vision, and machine learning. It provides efficient implementations of many foundational computer vision operations, including filtering, feature extraction, contour detection, geometric transformations, and object detection. OpenCV is widely used in both research and industry and serves as the primary image processing library for this project.

### Specific Use

Detect card boundaries, preprocess images, perform perspective correction, and prepare Magic: The Gathering card images for OCR.

### Key Concepts

- Image filtering
- Edge detection
- Contour extraction
- Geometric transformations
- Perspective transformation
- Affine transformation
- Image thresholding
- Morphological operations
- Feature detection

### Reference URLs

- https://docs.opencv.org/4.x/
- https://docs.opencv.org/4.x/da/d22/tutorial_py_canny.html
- https://docs.opencv.org/4.x/d4/d73/tutorial_py_contours_begin.html
- https://docs.opencv.org/4.x/da/d54/group__imgproc__transform.html
- https://szeliski.org/Book/
    - Chapter 3 - Image Processing

### Annotated Reading Notes

#### OpenCV Documentation

The OpenCV documentation provides comprehensive reference material for the library's image processing and computer vision APIs. The documentation describes the purpose, implementation, and usage of algorithms including filtering, thresholding, contour detection, feature extraction, image transformations, and camera geometry. It also provides practical examples demonstrating how these algorithms are applied within computer vision pipelines.

Relevant APIs for this project include the `imgproc` module for image preprocessing, contour analysis, and geometric transformations.

#### Canny Edge Detection

This tutorial explains the Canny edge detector, a multi-stage algorithm used to identify significant image intensity gradients while suppressing image noise. The algorithm consists of Gaussian smoothing, gradient computation, non-maximum suppression, double thresholding, and edge tracking by hysteresis.

The tutorial discusses the tradeoff between sensitivity and noise reduction and demonstrates how threshold selection affects edge quality.

Potential application:
- Detect the outer borders of trading cards.
- Produce cleaner inputs for contour extraction.

#### Contours: Getting Started

This tutorial introduces contour extraction as a method of identifying object boundaries within binary images. Contours are generated after thresholding or edge detection and represent continuous curves surrounding connected regions.

The tutorial explains contour retrieval modes, contour approximation methods, and techniques for drawing and analyzing contours.

Potential application:
- Identify rectangular card outlines.
- Filter detected objects according to size and aspect ratio.
- Locate multiple cards within a single camera frame.

#### Geometric Image Transformations

This section documents OpenCV functions for resizing, rotation, affine transformations, and perspective transformations. Perspective transforms use four corresponding points to compute a homography that maps an arbitrary quadrilateral into a rectangular image.

For document-like objects such as trading cards, perspective correction produces a normalized image that improves downstream OCR accuracy.

Relevant functions include:

- `warpPerspective()`
- `getPerspectiveTransform()`
- `warpAffine()`
- `resize()`
- `remap()`

#### Szeliski – Chapter 3: Image Processing

Chapter 3 introduces the mathematical foundations of image processing. Topics include convolution, linear and nonlinear filtering, image pyramids, histogram manipulation, gradient computation, edge detection, and feature extraction. These operations form the preprocessing stage used by many computer vision systems before object recognition.

The chapter emphasizes how preprocessing improves image quality while preserving the structural information needed by later recognition algorithms.

### Takeaways for My Project

- Use OpenCV as the primary computer vision library for all image preprocessing operations.
- Apply Gaussian filtering before edge detection to reduce sensor noise.
- Use the Canny edge detector to identify candidate card boundaries.
- Extract contours from edge images and filter them by rectangular shape and expected card aspect ratio.
- Use perspective transformations to normalize card orientation before OCR.
- Design the preprocessing pipeline to remain computationally efficient for real-time video processing.
