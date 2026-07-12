## OCR and Fuzzy Matching Overview

OCR and fuzzy matching are used together to improve identification accuracy when text recognition produces imperfect results. OCR systems frequently produce errors caused by image quality, font variations, lighting conditions, perspective distortion, or ambiguous characters. Fuzzy matching algorithms compare imperfect OCR output against a known database to identify the most likely intended result.

### Specific Use

For a Magic: The Gathering card scanner, fuzzy matching provides a method for converting noisy OCR output into a valid card identification by comparing recognized text against known card names and metadata.

Match imperfect OCR output to Magic card names with high accuracy despite recognition errors.

### Key Concepts

- String similarity
- Approximate matching
- Edit distance
- Levenshtein distance
- Candidate ranking
- Confidence scoring
- OCR error correction
- Database lookup
- Metadata matching
- Entity resolution

### Reference URLs

- https://scryfall.com/docs/api
- https://arxiv.org/abs/1109.3317

### Annotated Reading Notes

#### Scryfall API Documentation

The Scryfall API documentation describes the data structures and endpoints available for accessing Magic: The Gathering card information. Scryfall provides searchable card metadata, including card names, sets, collector numbers, and unique identifiers.

For an OCR-based card scanner, Scryfall provides the reference dataset needed to convert recognized text into a validated card identity. Instead of relying only on OCR output, the application can compare recognized text against a known collection of card names and metadata.

Relevant concepts for this project include:
- Using an authoritative card database for validation.
- Separating text recognition from card identification.
- Supporting multiple printings and card versions.
- Retrieving additional metadata after identification.

#### Design of an OCR System for Camera-Based Handheld Devices

This paper discusses OCR challenges in images captured from handheld cameras and describes methods for improving recognition accuracy through preprocessing and post-processing techniques. It emphasizes that OCR output should not always be treated as final because camera-based recognition is affected by image quality and environmental conditions.

Post-processing techniques can improve OCR results by using additional information about expected text. Comparing OCR output against a known vocabulary or database allows systems to correct recognition errors and select more likely interpretations.

Relevant concepts for this project include:
- Treating OCR results as candidates rather than guaranteed values.
- Using contextual information to improve recognition.
- Applying post-processing after text recognition.

### Takeaways for My Project

- Use OCR output as an input to card identification rather than directly storing the recognized text.
- Compare OCR results against Scryfall card names to identify the intended card.
- Use fuzzy matching techniques to correct common OCR mistakes such as:
    - Character substitutions.
    - Missing letters.
    - Extra characters.
    - Incorrect spacing.
- Rank possible card matches using similarity scores and OCR confidence values.
- Require additional confirmation when multiple possible cards have similar names.
- Store the Scryfall identifier after matching to ensure accurate collection tracking.
- Separate OCR, fuzzy matching, and metadata retrieval into independent pipeline stages.
