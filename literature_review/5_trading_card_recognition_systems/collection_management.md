## Collection Management Overview

Collection management systems organize, search, edit, and maintain information about user-owned Magic: The Gathering cards. These systems typically store card metadata such as card name, set, printing, quantity, and ownership information while providing tools for searching, filtering, and managing a collection.

### Specific Use

For a Magic: The Gathering card scanner, collection management represents the final stage of the recognition pipeline by converting identified cards into a usable inventory system.

Provide users with a searchable local database of scanned Magic cards and support future export or synchronization features.

### Key Concepts

- Collection databases
- Card metadata management
- Inventory tracking
- Search and filtering
- Duplicate handling
- Card quantity management
- Printing identification
- Offline-first applications
- Data synchronization
- User experience design

### Reference URLs

- https://developer.android.com/training/data-storage/room
- https://delverlab.com/
- https://manabox.app/

### Annotated Reading Notes

#### Room Persistence Library Documentation

The Room documentation provides the foundation for implementing local collection storage within an Android application. It describes how structured application data can be modeled using entities, accessed through data access objects (DAOs), and persisted in a local SQLite database.

For collection management, Room provides the necessary tools to represent cards as structured records and perform operations such as adding scanned cards, updating quantities, and querying stored information.

Relevant concepts for this project include:
- Designing a persistent collection data model.
- Managing card records locally.
- Supporting efficient searches and updates.

#### Delver Lens

Delver Lens is an example of an existing Magic: The Gathering card scanning application that demonstrates practical approaches to card recognition and collection management. The application combines image recognition, card database lookup, and inventory tracking to allow users to digitize physical card collections.

Examining existing scanners provides insight into common user workflows, including:
- Scanning cards through a camera interface.
- Identifying cards using visual recognition.
- Adding recognized cards to a collection.
- Managing large inventories.

For this project, existing MTG scanner applications provide examples of expected functionality and help identify opportunities for improvement.

#### ManaBox

ManaBox is a Magic: The Gathering collection management application that demonstrates features commonly expected in digital collection tools. These features include organizing cards, viewing card information, tracking quantities, searching collections, and managing deck-related information.

The application provides examples of how recognized card data can be presented to users after scanning or importing.

Relevant concepts for this project include:
- Providing useful collection views after recognition.
- Designing user-friendly card organization tools.
- Supporting future expansion beyond basic scanning.

### Takeaways for My Project

- Treat collection management as the final stage of the scanning pipeline.
- Store recognized cards with sufficient metadata to uniquely identify each printing.
- Support quantity tracking to handle multiple copies of the same card.
- Provide search and filtering functionality for usability.
- Use Room to maintain a local offline collection database.
- Design the data model to allow future features such as exporting collections, synchronization, and deck management.
- Use existing MTG collection applications as references for expected user workflows and interface design.
