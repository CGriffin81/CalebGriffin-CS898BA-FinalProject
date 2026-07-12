## Perspective Correction Overview

Perspective correction (also called perspective rectification) is the process of transforming an image of a planar object captured from an arbitrary viewing angle into a normalized, front-facing view. By estimating the geometric relationship between the camera and the object, perspective correction compensates for projective distortion, allowing the object to be analyzed as if it were viewed perpendicular to its surface.

### Specific Use

Correct skewed images of Magic: The Gathering cards before OCR to improve text recognition accuracy and standardize the appearance of cards captured from different viewing angles.

### Key Concepts

- Perspective projection
- Projective geometry
- Homography
- Perspective transformation
- Image warping
- Planar object rectification
- Four-point transformation
- Feature correspondence

### Reference URLs

- https://docs.opencv.org/4.x/da/d54/group__imgproc__transform.html
- https://szeliski.org/Book/
    - Chapter 8 - Image Alignment and Stitching
        - Homographies
        - Image Warping

### Annotated Reading Notes

#### OpenCV Geometric Image Transformations

The OpenCV documentation describes a collection of geometric transformation functions that modify the spatial arrangement of image pixels. These include scaling, rotation, affine transformations, perspective transformations, and remapping operations. For planar objects such as trading cards, perspective transformations are used to compute a homography from four corresponding points, allowing an arbitrarily oriented quadrilateral to be transformed into a rectangular image.

The documentation explains the use of functions such as `getPerspectiveTransform()` to compute the transformation matrix and `warpPerspective()` to apply the transformation to an image. These operations preserve the relative geometry of the card while correcting for camera viewpoint.

Relevant APIs include:

- `getPerspectiveTransform()`
- `warpPerspective()`
- `perspectiveTransform()`
- `warpAffine()`

#### Szeliski – Chapter 8: Image Alignment and Stitching

This chapter presents the mathematical foundations of image alignment and geometric transformations. It explains how corresponding points between two images can be used to estimate a homography, which models the projective relationship between two views of the same planar surface. The chapter also discusses image warping techniques that apply these transformations to generate geometrically aligned images.

The text emphasizes that homographies are particularly well suited for planar objects, making them an ideal approach for correcting photographs of documents, signs, or trading cards captured from arbitrary viewing angles. By rectifying images before subsequent processing, later recognition algorithms receive more consistent inputs and generally achieve higher accuracy.

### Takeaways for My Project

- Treat each detected Magic card as a planar object.
- Detect the four corners of each card after contour extraction.
- Compute a homography using the detected corner coordinates.
- Use `getPerspectiveTransform()` and `warpPerspective()` to generate a normalized, front-facing image of each card.
- Perform OCR only after perspective correction to improve recognition accuracy.
- Normalize every detected card to a consistent image size before passing it to the OCR pipeline.
