## Existing MTG Scanners Overview

Existing Magic: The Gathering card scanner applications demonstrate practical implementations of card recognition, database lookup, duplicate handling, and collection management. These applications provide examples of real-world workflows and user expectations for scanning physical cards into digital collections.

### Specific Use

Studying existing MTG scanners helps identify common design patterns, evaluate available features, and determine opportunities for improvement in a custom scanning application.

### Key Concepts

- Camera-based card scanning
- Card recognition pipelines
- OCR-based identification
- Image-based card matching
- Collection management
- Duplicate detection
- Card metadata lookup
- User workflow design
- Batch scanning
- Collection synchronization

### Reference URLs

- https://delverlab.com/
- https://manabox.app/
- https://www.tcgplayer.com/mobile-app

### Annotated Reading Notes

#### Delver Lens

Delver Lens is an example of a Magic: The Gathering card scanning application designed to digitize physical card collections using a smartphone camera. The application demonstrates a complete scanning workflow that includes image capture, card recognition, metadata retrieval, and collection organization.

Existing scanning applications such as Delver Lens demonstrate the importance of minimizing user interaction during scanning. A successful workflow should allow users to scan multiple cards quickly while automatically identifying cards and handling duplicate entries.

Relevant concepts for this project include:
- Real-time or batch card scanning workflows.
- Automatic card identification.
- Collection organization after recognition.
- Reducing manual data entry.

#### ManaBox

ManaBox is a Magic: The Gathering collection management application focused on organizing cards, viewing card information, and supporting collection-related workflows. It demonstrates how recognized card data can be presented to users through search, filtering, and organization tools.

While ManaBox is primarily a collection management tool rather than a computer vision system, it provides insight into the features users expect after cards are identified.

Relevant concepts for this project include:
- Collection browsing interfaces.
- Card metadata presentation.
- Search and filtering functionality.
- User-friendly inventory management.

#### TCGplayer Mobile App

The TCGplayer mobile application provides an example of integrating card identification with market and collection information. It demonstrates how recognized cards can be connected to additional metadata and external services.

This approach highlights the value of separating card recognition from card information storage. A recognition system identifies the card, while external databases provide additional details such as pricing, legality, and printing information.

Relevant concepts for this project include:
- Integrating recognition results with external card databases.
- Separating identification from metadata management.
- Supporting future expansion beyond basic collection tracking.

### Takeaways for My Project

- Analyze existing MTG scanners to identify expected user workflows and feature requirements.
- Support rapid scanning with minimal user interaction.
- Implement duplicate detection to avoid repeatedly adding the same card.
- Use authoritative card databases such as Scryfall for metadata matching.
- Store sufficient card information to distinguish between different printings.
- Design the application architecture so recognition, metadata lookup, and collection management remain separate components.
- Evaluate existing applications as benchmarks for usability rather than directly copying their implementations.
