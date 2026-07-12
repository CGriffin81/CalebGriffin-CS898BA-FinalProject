## Room Overview

Room is Android's object-relational mapping (ORM) library built on top of SQLite that provides an abstraction layer for local data persistence. It simplifies database operations by allowing developers to define structured data models, queries, and relationships using Kotlin while maintaining the reliability and performance of SQLite.

### Specific Use

For a Magic: The Gathering card scanner, Room provides local storage for scanned cards, collection quantities, card metadata, and user-created information while allowing the application to function without requiring constant network access.

### Key Concepts

- Local database storage
- SQLite abstraction
- Entities
- Data Access Objects (DAOs)
- Database schemas
- Queries
- Data persistence
- Offline-first applications
- Reactive data streams
- Data modeling

### Reference URLs

- https://developer.android.com/training/data-storage/room
- https://developer.android.com/codelabs/basic-android-kotlin-compose-persisting-data-room

### Annotated Reading Notes

#### Room Persistence Library Documentation

The Room documentation introduces Room as a database layer designed to provide structured local storage for Android applications. Room reduces the complexity of working directly with SQLite by providing compile-time verification of database queries, object mapping between Kotlin classes and database tables, and integration with Android application architecture components.

The documentation describes three primary components of Room:

- **Entities** represent tables and define the structure of stored data.
- **DAOs (Data Access Objects)** define database operations and queries.
- **The Room database class** provides access to the application's stored data.

These concepts are relevant for organizing scanned card information into a persistent collection database.

Relevant concepts for this project include:
- Creating structured card data models.
- Storing recognized cards locally.
- Querying and updating collection information efficiently.
- Supporting offline application functionality.

#### Room Database Codelab

The Room codelab demonstrates how to implement local persistence in an Android application using Kotlin and Jetpack Compose. It introduces creating entities, defining DAOs, configuring a database instance, and connecting stored data with UI state.

The codelab demonstrates how database changes can be reflected automatically in the user interface through reactive data patterns. This approach is useful for collection management because newly scanned cards can immediately appear in the user's collection view.

Relevant concepts for this project include:
- Connecting persistent storage to the UI layer.
- Managing database operations asynchronously.
- Updating collection displays when new cards are added.

### Takeaways for My Project

- Use Room as the local database layer for storing scanned Magic cards.
- Store card information such as name, set, collector number, rarity, and quantity.
- Separate database logic from the computer vision and user interface layers.
- Support offline scanning and collection browsing without requiring continuous network access.
- Use asynchronous database operations to avoid blocking camera processing.
- Allow recognized cards from the OCR pipeline to be inserted automatically into the user's collection.
- Design the database schema to support future features such as search, filtering, and collection statistics.
