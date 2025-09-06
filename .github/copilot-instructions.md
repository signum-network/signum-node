# GitHub Copilot Instructions for Signum Node

## Primary Reference
**IMPORTANT**: Always consult CLAUDE.md for comprehensive development guidelines before writing code.

## Project Overview
Signum Node is a Java 21 blockchain implementation with:
- Gradle build system
- Multi-database support (SQLite, MariaDB, PostgreSQL)
- Layered architecture with strict separation of concerns
- Web APIs and embedded UIs

## Architecture Constraints

### Layer Separation (MANDATORY)
```
Presentation Layer (brs.web.api.http.handler) 
    ↓
Business Logic Layer (brs.services.impl)
    ↓  
Data Access Layer (brs.db.store)
    ↓
Database Layer (SQLite/MariaDB/PostgreSQL)
```

### Dependency Flow Rules
- Services inject Stores (never direct database access)
- Use constructor injection only
- Interface segregation: Services depend on interfaces, not implementations

## Code Generation Standards

### Class Structure Template
```java
// Service Implementation Pattern
public class [Entity]ServiceImpl implements [Entity]Service {
    private static final Logger logger = LoggerFactory.getLogger([Entity]ServiceImpl.class);
    
    private final [Entity]Store entityStore;
    private final PropertyService propertyService;
    
    public [Entity]ServiceImpl([Entity]Store entityStore, PropertyService propertyService) {
        this.entityStore = entityStore;
        this.propertyService = propertyService;
    }
    
    @Override
    public [Entity] get[Entity](long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Invalid entity ID: " + id);
        }
        
        [Entity] entity = entityStore.get[Entity](id);
        if (entity == null) {
            throw new [Entity]NotFoundException("Entity not found: " + id);
        }
        
        return entity;
    }
}
```

### Unit Test Template
```java
@ExtendWith(MockitoExtension.class)
class [Entity]ServiceImplTest {
    
    @Mock private [Entity]Store entityStore;
    @Mock private PropertyService propertyService;
    @InjectMocks private [Entity]ServiceImpl entityService;
    
    @Test
    void get[Entity]_GivenValidId_ReturnsEntity() {
        // Arrange
        long entityId = 123L;
        [Entity] expected = create[Entity](entityId);
        when(entityStore.get[Entity](entityId)).thenReturn(expected);
        
        // Act
        [Entity] result = entityService.get[Entity](entityId);
        
        // Assert
        assertEquals(expected, result);
        verify(entityStore).get[Entity](entityId);
    }
    
    @Test
    void get[Entity]_GivenInvalidId_ThrowsException() {
        // Act & Assert
        assertThrows(IllegalArgumentException.class, 
            () -> entityService.get[Entity](-1L));
    }
    
    @Test
    void get[Entity]_GivenNonExistentId_ThrowsNotFoundException() {
        // Arrange
        when(entityStore.get[Entity](999L)).thenReturn(null);
        
        // Act & Assert
        assertThrows([Entity]NotFoundException.class,
            () -> entityService.get[Entity](999L));
    }
}
```

### API Handler Template
```java
public final class Get[Entity] extends APIRequestHandler {
    
    private final ParameterService parameterService;
    private final [Entity]Service entityService;
    
    public Get[Entity](ParameterService parameterService, [Entity]Service entityService) {
        this.parameterService = parameterService;
        this.entityService = entityService;
    }
    
    @Override
    protected JSONStreamAware processRequest(HttpServletRequest req) throws SignumException {
        long entityId = parameterService.getEntityId(req);
        
        [Entity] entity = entityService.get[Entity](entityId);
        
        JSONObject response = new JSONObject();
        response.put("entity", JSONData.serialize[Entity](entity));
        return response;
    }
}
```

## Design Patterns to Apply

### Factory Pattern
Use for complex object creation:
```java
public class [Entity]Factory {
    public static [Entity] create(EntityType type, Map<String, Object> properties) {
        return switch (type) {
            case TYPE_A -> new [Entity]TypeA(properties);
            case TYPE_B -> new [Entity]TypeB(properties);
            default -> throw new IllegalArgumentException("Unknown type: " + type);
        };
    }
}
```

### Observer Pattern
Use for event handling:
```java
public class [Entity]EventListener implements BlockchainProcessor.Listener {
    @Override
    public void notify(Block block) {
        // Handle entity-specific blockchain events
    }
}
```

### Strategy Pattern
Use for algorithm variants:
```java
public interface [Entity]ProcessingStrategy {
    ProcessingResult process([Entity] entity);
}

public class [Entity]Processor {
    private final [Entity]ProcessingStrategy strategy;
    
    public [Entity]Processor([Entity]ProcessingStrategy strategy) {
        this.strategy = strategy;
    }
}
```

## Testing Requirements

### Every Method Must Have Tests
- Happy path scenario
- Error/exception scenarios  
- Boundary conditions (null, empty, max values)
- Edge cases specific to blockchain logic

### Mock Strategy
- Mock all dependencies (stores, services)
- Use @Mock and @InjectMocks annotations
- Verify interactions with mocks
- Never test multiple classes together

## Security Guidelines

### Input Validation
```java
public void process[Entity]([Entity]Request request) {
    // Validate all inputs
    if (request == null) {
        throw new IllegalArgumentException("Request cannot be null");
    }
    
    if (request.getEntityId() <= 0) {
        throw new IllegalArgumentException("Invalid entity ID: " + request.getEntityId());
    }
    
    // Use ParameterParser for API parameters
    long sanitizedId = parameterService.getEntityId(request);
}
```

### Exception Handling
```java
try {
    // Business logic
} catch (SpecificException e) {
    logger.warn("Specific error occurred: {}", e.getMessage());
    throw new [Entity]ProcessingException("Failed to process entity", e);
} catch (Exception e) {
    logger.error("Unexpected error", e);
    throw new InternalServerException("Internal processing error");
}
```

## Build Commands Reference
- `./gradlew build` - Full build
- `./gradlew test` - Run tests  
- `./gradlew check` - Run quality checks
- `java -jar signum-node.jar --headless` - Run node

## Quality Checklist
Before generating code, ensure:
- [ ] Follows layered architecture
- [ ] Uses dependency injection
- [ ] Has comprehensive unit tests
- [ ] Applies appropriate design patterns
- [ ] Includes input validation
- [ ] Uses proper exception handling
- [ ] Follows naming conventions
- [ ] Includes logging where appropriate

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

// Project-specific
import brs.services.*;
import brs.db.store.*;
import brs.web.api.http.common.*;
```

Generate clean, tested, enterprise-grade code that supports maintainable blockchain infrastructure.