# AI Assistant Instructions for Signum Node Development

## Essential Reading
**🔴 CRITICAL**: Read CLAUDE.md in the repository root for complete development guidelines. This file contains the master reference for all coding standards, architecture patterns, and quality requirements.

## Project Summary
Signum Node: Java 21 blockchain implementation with energy-efficient Proof-of-Commitment consensus, supporting SQLite/MariaDB/PostgreSQL backends and multiple web interfaces.

## Architecture Overview
```
┌─────────────────────────────────────────────────────────────┐
│                    Presentation Layer                       │
│    brs.web.api.http.handler (REST APIs)                    │
│    brs.web.api.ws (WebSocket APIs)                         │
└─────────────────────┬───────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                   Business Logic Layer                     │
│    brs.services (interfaces)                               │
│    brs.services.impl (implementations)                     │
└─────────────────────┬───────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                   Data Access Layer                        │
│    brs.db.store (data access interfaces & implementations) │
└─────────────────────┬───────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────┐
│                   Database Layer                           │
│    SQLite (default) / MariaDB / PostgreSQL                 │
└─────────────────────────────────────────────────────────────┘
```

## Core Development Principles

### 1. Mandatory Architecture Rules
- **Layer Isolation**: Never skip layers (API → Service → Store → Database)
- **Dependency Direction**: Always inject dependencies through constructors
- **Interface Segregation**: Depend on interfaces, not concrete classes
- **Single Responsibility**: One class, one purpose, one reason to change

### 2. Design Pattern Implementation
Apply Gang of Four patterns where appropriate:

**Factory Pattern**: Complex object creation
```java
// Example: Database type selection
DatabaseInstance db = DatabaseInstanceFactory.createInstance(dbType);
```

**Observer Pattern**: Event-driven architecture
```java
// Example: Blockchain events
blockchainProcessor.addListener(listener, Event.AFTER_BLOCK_APPLY);
```

**Strategy Pattern**: Algorithm variations
```java
// Example: Mining strategies
Generator generator = useMockMining ? new MockGenerator() : new GeneratorImpl();
```

**Command Pattern**: API request handling
```java
// Example: Each API endpoint is a command
public class GetAccountCommand extends APIRequestHandler {
    protected JSONStreamAware processRequest(HttpServletRequest req) { ... }
}
```

### 3. Testing Mandate
**EVERY code change requires corresponding tests:**

- Unit tests for all new methods
- Test structure: Arrange-Act-Assert (AAA)
- Mock all dependencies using Mockito
- Test happy path + error scenarios + edge cases
- Place tests in `test/java/` mirroring source structure

### 4. Code Quality Standards

**Method Design:**
- Keep methods under 20 lines
- Use early returns to reduce nesting
- Meaningful names for all identifiers
- Single level of abstraction per method

**Error Handling:**
- Use specific exception types
- Include contextual error messages
- Log at appropriate levels (DEBUG/INFO/WARN/ERROR)
- Validate all external inputs

**Security Requirements:**
- Validate API parameters using ParameterParser
- Use existing crypto utilities (brs.crypto package)
- Never implement custom cryptographic functions
- Sanitize all database inputs

## Common Code Templates

### Service Implementation
```java
public class EntityServiceImpl implements EntityService {
    private static final Logger logger = LoggerFactory.getLogger(EntityServiceImpl.class);
    
    private final EntityStore entityStore;
    private final PropertyService propertyService;
    
    public EntityServiceImpl(EntityStore entityStore, PropertyService propertyService) {
        this.entityStore = requireNonNull(entityStore);
        this.propertyService = requireNonNull(propertyService);
    }
    
    @Override
    public Entity getEntity(long id) {
        validateEntityId(id);
        
        Entity entity = entityStore.getEntity(id);
        if (entity == null) {
            throw new EntityNotFoundException("Entity not found: " + id);
        }
        
        logger.debug("Retrieved entity: {}", id);
        return entity;
    }
    
    private void validateEntityId(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Entity ID must be positive: " + id);
        }
    }
}
```

### Unit Test Structure
```java
@ExtendWith(MockitoExtension.class)
class EntityServiceImplTest {
    
    @Mock private EntityStore entityStore;
    @Mock private PropertyService propertyService;
    @InjectMocks private EntityServiceImpl entityService;
    
    @Test
    void getEntity_GivenValidId_ReturnsEntity() {
        // Arrange
        long entityId = 1L;
        Entity expectedEntity = createTestEntity(entityId);
        when(entityStore.getEntity(entityId)).thenReturn(expectedEntity);
        
        // Act
        Entity result = entityService.getEntity(entityId);
        
        // Assert
        assertEquals(expectedEntity, result);
        verify(entityStore).getEntity(entityId);
    }
    
    @Test
    void getEntity_GivenInvalidId_ThrowsIllegalArgumentException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> entityService.getEntity(-1L));
    }
    
    @Test
    void getEntity_GivenNonExistentId_ThrowsEntityNotFoundException() {
        // Arrange
        when(entityStore.getEntity(999L)).thenReturn(null);
        
        // Act & Assert
        assertThrows(EntityNotFoundException.class,
            () -> entityService.getEntity(999L));
    }
    
    private Entity createTestEntity(long id) {
        // Create test entity with required fields
        return new Entity(id, "test-entity");
    }
}
```

### API Handler Structure
```java
public final class GetEntity extends APIRequestHandler {
    
    private final ParameterService parameterService;
    private final EntityService entityService;
    
    public GetEntity(ParameterService parameterService, EntityService entityService) {
        this.parameterService = requireNonNull(parameterService);
        this.entityService = requireNonNull(entityService);
    }
    
    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws SignumException {
        long entityId = parameterService.getEntityId(req);
        
        Entity entity = entityService.getEntity(entityId);
        
        JSONObject response = new JSONObject();
        response.put("entity", JSONData.serializeEntity(entity));
        return response;
    }
}
```

## Development Workflow

### Build Commands
```bash
./gradlew build          # Compile and build
./gradlew test           # Run unit tests
./gradlew check          # Quality checks
./gradlew dist           # Create distribution
./gradlew generateJooq   # Generate database schema classes
```

### Running the Node
```bash
java -jar signum-node.jar                    # With GUI
java -jar signum-node.jar --headless         # Headless mode
java -jar signum-node.jar --config ./conf    # Custom config
```

## Quality Gates Checklist

Before submitting code, verify:
- [ ] Follows layered architecture strictly
- [ ] Uses constructor dependency injection
- [ ] Has comprehensive unit tests (>80% coverage)
- [ ] Applies appropriate design patterns
- [ ] Handles errors with specific exceptions
- [ ] Includes appropriate logging
- [ ] Validates all external inputs
- [ ] No hardcoded values (use PropertyService)
- [ ] No direct database access from business logic
- [ ] Follows package and naming conventions
- [ ] Includes Javadoc for public APIs
- [ ] No security vulnerabilities

## Key Packages Reference

- `brs.services.*` - Business logic interfaces and implementations
- `brs.db.store.*` - Data access layer
- `brs.web.api.http.*` - REST API handlers
- `brs.at.*` - Smart contract (Automated Transaction) system
- `brs.peer.*` - P2P networking
- `brs.assetexchange.*` - Digital asset trading
- `brs.crypto.*` - Cryptographic utilities
- `brs.props.*` - Configuration management

## Common Imports
```java
// Logging
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Testing
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static java.util.Objects.requireNonNull;

// JSON handling
import org.json.simple.JSONObject;
import org.json.simple.JSONStreamAware;

// Project core
import brs.*;
import brs.services.*;
import brs.db.store.*;
import brs.web.api.http.common.*;
```

## Remember
Quality and maintainability over speed. This is critical blockchain infrastructure that must be reliable, secure, and extensible. Always prioritize clean, well-tested code that follows established patterns.