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

---

## PR Review: Security and Protocol Integrity

**CRITICAL**: When reviewing Pull Requests, apply the following rules with highest priority. The Signum protocol is stable. Changes to consensus-critical code are only acceptable when fixing a verified, severe security vulnerability.

### Protocol-Frozen Files (REJECT changes unless fixing a proven CVE)

Any modification to these files/packages MUST be flagged as **REQUEST_CHANGES** with a clear explanation that protocol code is frozen. The only exception is a PR that explicitly references a disclosed security vulnerability being fixed.

#### Consensus & Mining (PoC+)
- `brs/GeneratorImpl.java` — deadline calculation, commitment factor, capacity estimation
- `brs/util/MiningPlot.java` — Shabal256 plot generation, scoop layout
- `brs/OCLPoC.java` — GPU PoC verification

#### Block Validation & Chain State
- `brs/BlockchainProcessorImpl.java` — pushBlock, popOffTo, fork resolution, generateBlock
- `brs/BlockchainImpl.java` — block reward schedule, cumulative difficulty
- `brs/services/impl/BlockServiceImpl.java` — verifyGenerationSignature, verifyBlockSignature, calculateBaseTarget, apply

#### Transaction Types & Fee Rules
- `brs/TransactionType.java` — all transaction type definitions, fee formulas, validation and apply logic
- `brs/Transaction.java` — wire format, signature verification
- `brs/Attachment.java` — attachment serialization/deserialization
- `brs/services/impl/TransactionServiceImpl.java` — cross-cutting transaction validation
- `brs/EconomicClustering.java` — EC fork protection

#### Protocol Constants & Fork Schedule
- `brs/Constants.java` — hardcoded protocol parameters (MAX_BALANCE_SIGNA, FEE_QUANT, BASE_TARGET, MAX_ROLLBACK, etc.)
- `brs/Genesis.java` — genesis block identity
- `brs/fluxcapacitor/FluxValues.java` — feature flags, block time, commitment parameters
- `brs/fluxcapacitor/HistoricalMoments.java` — hard-fork block heights
- `brs/fluxcapacitor/FluxCapacitorImpl.java` — feature flag evaluation
- `brs/props/Props.java` — overridable protocol properties (reward schedule, cash-back, fork heights)

#### Cryptography
- `brs/crypto/Crypto.java` — signing, verification, key derivation, shared secrets
- `brs/crypto/EncryptedData.java` — encrypted message handling

#### Smart Contracts (AT)
- `brs/at/AtController.java` — AT execution loop, gas metering
- `brs/at/AtMachineProcessor.java` — opcode interpreter
- `brs/at/OpCode.java` — opcode table
- `brs/at/AtConstants.java` — step fees, resource limits
- `brs/at/AtApiImpl.java`, `AtApiController.java`, `AtApiPlatformImpl.java` — AT API dispatch

#### P2P Network Protocol
- `brs/peer/PeerServlet.java` — P2P message dispatch
- `brs/peer/ProcessBlock.java` — peer block receipt
- `brs/peer/GetCumulativeDifficulty.java` — chain tip advertisement

### Blockchain-Specific Exploit Patterns (FLAG immediately)

When reviewing any PR, watch for these attack vectors:

#### Balance & Overflow Manipulation
- Integer overflow/underflow in balance arithmetic (NQT values are `long`) — e.g., `amount + fee` wrapping negative
- Changing or weakening checks against `MAX_BALANCE_SIGNA` or `ONE_SIGNA` constants
- Modifications to fee calculation that could allow zero-fee or negative-fee transactions
- Altered reward calculations that inflate or deflate block rewards

#### Consensus Attacks
- Changes to deadline calculation (`calculateDeadline`, `calculateHit`, `getCommitmentFactor`) — even small constant changes break consensus
- Modified base target adjustment (`calculateBaseTarget`) — can accelerate or stall block production
- Weakened block signature or generation signature verification
- Relaxed timestamp validation windows (enables timestamp manipulation)
- Changes to `MAX_ROLLBACK` — deeper reorgs enable double-spend attacks
- Weakened Economic Clustering checks — enables transaction replay across forks

#### Smart Contract Exploits
- Bypassed gas metering in `AtController.runSteps()` — allows infinite-loop ATs to halt the chain
- New or modified opcodes without proper step cost accounting
- AT balance manipulation (send more than balance, negative amounts)
- Altered `BLOCKS_FOR_RANDOM` — affects AT randomness security

#### Backdoor Patterns (HIGH SUSPICION)
- Hardcoded account IDs, public keys, or addresses (especially in reward distribution, fee handling, or AT logic)
- Conditional logic that activates at a specific block height not corresponding to a known fork in `HistoricalMoments`
- New `Props` entries that override protocol constants without clear documentation
- Code paths that skip signature verification under any condition
- "Debug" or "test" flags that disable validation in production code
- Obfuscated arithmetic — e.g., `(x ^ 0x1F3A) + 0x7B` instead of clear constant usage
- Unnecessarily complex implementations where a simple one exists — demand justification

#### Network-Level Attacks
- Weakened peer version checks (`MIN_PEER_VERSION`) — allows rogue peers
- Removed or relaxed blacklisting logic
- Increased payload size limits without justification — amplification attacks
- Changes to peer discovery that could enable eclipse attacks

### Suspicious Code Patterns (ALWAYS flag for human review)

These patterns are not necessarily malicious but require explicit justification:

1. **Any use of `java.lang.reflect`** in transaction processing or block validation — could bypass type safety
2. **Runtime class loading** (`ClassLoader`, `Class.forName`) in consensus code — code injection risk
3. **Native method calls** (`JNI`, `ProcessBuilder`, `Runtime.exec`) outside of existing OCL code
4. **Network calls** (`Socket`, `HttpClient`, `URL.openConnection`) from consensus or transaction code — data exfiltration or oracle attacks
5. **File system access** from transaction/block processing code — should never touch disk directly
6. **Thread manipulation** (`Thread.sleep`, custom `ExecutorService`) in validation paths — timing attacks
7. **Serialization changes** to `Transaction`, `Block`, or `Attachment` wire formats — breaks network consensus
8. **New external dependencies** added to `build.gradle` — each dependency is an attack surface; require justification and license check
9. **Changes to Flyway migrations** that alter existing migration files (rather than adding new ones) — can corrupt database state
10. **Modifications to `CODEOWNERS`** or GitHub workflow files — could weaken review requirements

### PR Quality Gates

In addition to security, flag these quality issues:

- **PR too large**: More than 10 files changed or more than 1000 new lines of code — request the contributor to split into smaller PRs
- **No tests**: New logic without corresponding unit tests — reject
- **No justification**: Changes to critical infrastructure without a linked issue or design discussion — reject
- **Unrelated changes**: Formatting-only changes mixed with logic changes to obscure the real diff — request separation into distinct PRs