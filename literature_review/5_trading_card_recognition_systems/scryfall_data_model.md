## Scryfall Data Model Overview

Scryfall maintains a comprehensive database of Magic: The Gathering card information and provides an API and bulk datasets containing structured metadata for cards, sets, and printings. The Scryfall data model provides a standardized representation of Magic cards, allowing applications to identify cards, retrieve additional information, and maintain accurate collection records.



### Specific Use

For a Magic: The Gathering card scanner, Scryfall serves as the authoritative source for validating OCR results and storing consistent card metadata after recognition.

### Key Concepts

- Card identifiers
- Card names
- Set information
- Card printings
- Collector numbers
- Unique card IDs
- Card faces
- Card metadata
- API queries
- Bulk data processing
- Database synchronization

### Reference URLs

- https://scryfall.com/docs/api
- https://scryfall.com/docs/api/bulk-data

### Annotated Reading Notes

#### Scryfall API Documentation

The Scryfall API documentation describes the available endpoints and data structures used to access Magic: The Gathering card information. The API provides structured JSON responses containing card metadata including names, sets, colors, types, oracle text, images, and unique identifiers.

The API separates card identification from additional metadata retrieval. This allows an application to first identify a card using recognition techniques and then retrieve the complete card record using a unique identifier.

For a card scanning application, this separation is important because OCR may only provide partial information, while the Scryfall database provides the authoritative representation needed for accurate collection storage.

Relevant concepts for this project include:
- Querying card information after recognition.
- Using unique identifiers instead of relying on text alone.
- Distinguishing between cards with similar names.
- Retrieving metadata for collection display.

#### Scryfall Bulk Data Documentation

The Scryfall Bulk Data documentation describes downloadable datasets containing large collections of Magic: The Gathering card data. Bulk datasets provide an alternative to querying the API for every card lookup and are useful for applications that require local searching or offline functionality.

For a mobile card scanner, a local subset or processed version of Scryfall data could allow faster matching between OCR results and known card names without requiring network access during every scan.

Relevant concepts for this project include:
- Maintaining a local card lookup database.
- Supporting offline recognition workflows.
- Reducing network dependency.
- Preprocessing card metadata for efficient searching.

### Takeaways for My Project

- Use Scryfall as the authoritative source for Magic card metadata.
- Store unique identifiers rather than relying only on card names.
- Use Scryfall data to validate fuzzy-matched OCR results.
- Include printing information to distinguish different versions of the same card.
- Consider maintaining a local searchable card database for faster offline matching.
- Store only the necessary metadata locally to reduce application storage requirements.
- Separate card recognition from card information retrieval:
    - OCR identifies possible text.
    - Fuzzy matching finds candidate cards.
    - Scryfall metadata confirms the final card identity.
