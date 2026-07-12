## Image Pre-Processing Overview

Image preprocessing improves image quality before higher-level computer vision tasks such as object detection or OCR. Common preprocessing operations include filtering, contrast enhancement, thresholding, normalization, and noise reduction.

### Specific Use

Improve OCR accuracy by enhancing Magic card images prior to text recognition.

### Key Concepts

- Noise reduction
- Gaussian filtering
- Median filtering
- Histogram equalization
- Adaptive thresholding
- Binary thresholding
- Morphological operations
- Image normalization

### Reference URLs

- https://docs.opencv.org/4.13.0/d7/d4d/tutorial_py_thresholding.html
- https://docs.opencv.org/4.13.0/d4/d13/tutorial_py_filtering.html
- https://szeliski.org/Book/
    - Chapter 3 - Image Processing

### Annotated Reading Notes

#### OpenCV Thresholding

Introduces global, adaptive, and Otsu thresholding methods for converting grayscale images into binary images. Adaptive thresholding is particularly useful when illumination varies across an image, while Otsu's method automatically selects a threshold based on image histogram statistics.

#### OpenCV Image Filtering

Describes common filtering techniques including averaging filters, Gaussian blur, median filters, and bilateral filtering. Each technique reduces different types of image noise while preserving important image features.

#### Szeliski Chapter 3 – Image Processing

This chapter explains the mathematical foundations of image filtering and enhancement. Topics include convolution, Gaussian filtering, edge detection, histogram manipulation, and image pyramids. These operations form the preprocessing stage used by many computer vision systems before feature extraction or recognition.

### Takeaways for My Project

- Apply Gaussian filtering to reduce sensor noise.
- Evaluate adaptive thresholding under uneven lighting.
- Investigate histogram equalization to improve text contrast.
- Compare grayscale versus individual color channels before OCR.
- Keep preprocessing computationally lightweight to maintain real-time video performance.
