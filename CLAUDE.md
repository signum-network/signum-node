# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Signum Node is the reference implementation of the Signum blockchain - a cryptocurrency that uses an energy-efficient Proof-of-Commitment (PoC+) consensus algorithm. This is a Java-based blockchain node with embedded web UI components.

## Build & Development Commands

### Core Build Commands
- `./gradlew build` - Compile and build the project
- `./gradlew test` - Run unit tests (excludes some problematic tests by default)
- `./gradlew dist` - Create distribution ZIP package
- `./gradlew shadowJar` - Create fat JAR with all dependencies
- `./gradlew release` - Full release build including tests, dist, and Windows executable

### Running the Node
- `java -jar signum-node.jar` - Start node with GUI (if available)
- `java -jar signum-node.jar --headless` - Start node without GUI
- `java -jar signum-node.jar --config <path>` - Use custom config folder

### Testing Commands
- `./gradlew test` - Run unit tests (some tests excluded by default in build.gradle)
- Tests are located in `test/java/` directory
- Integration tests are in `test/java/it/` 
- Test configuration excludes problematic tests like ATTest, FeeSuggestionCalculatorTest

### Web UI Components
- `./gradlew buildOpenApi` - Build OpenAPI documentation (requires Node.js)
- OpenAPI docs are built from `openapi/` directory using Node.js/npm
- Neo-page (modern UI) in `neo-page/` directory with its own package.json

### Database Schema Generation
- `./gradlew generateJooq` - Generate JOOQ database schema classes
- Requires SQLite database migration first via `./gradlew flywayMigrate`

## Code Architecture

### Main Entry Points
- `signum.Launcher` - Main application launcher (handles GUI vs headless mode)
- `brs.Signum` - Core blockchain node implementation
- Application starts in `Launcher.main()` → `Signum.main()`

### Core Packages Structure

#### Database Layer (`brs.db`)
- `sql/` - SQL implementations for different database types (SQLite, MariaDB, PostgreSQL)
- `store/` - Data access layer with service interfaces
- `cache/` - Database caching implementation
- `migration/` - Flyway database migrations

#### Blockchain Core (`brs`)
- `Blockchain` & `BlockchainImpl` - Core blockchain logic
- `BlockchainProcessor` - Block validation and processing
- `TransactionProcessor` - Transaction validation and processing
- `Generator` - Block mining/generation logic

#### Services Layer (`brs.services`)
- Service interfaces in main package, implementations in `impl/`
- Key services: `AccountService`, `TransactionService`, `BlockService`, `ATService` (smart contracts)

#### Smart Contracts (`brs.at`)
- AT (Automated Transactions) - Signum's smart contract system
- `AtController` - Smart contract execution engine
- `AtApi` - Smart contract API interface

#### Network Layer (`brs.peer`)
- P2P networking implementation
- Peer discovery and communication protocols

#### Web API (`brs.web`)
- `api/http/handler/` - REST API endpoints
- `api/ws/` - WebSocket implementations
- `server/` - Jetty web server configuration

#### Asset Exchange (`brs.assetexchange`)
- Digital asset trading functionality
- Order management and trade execution

### Configuration System
- Properties loaded from `conf/node-default.properties` (defaults)
- User overrides in `conf/node.properties`
- Network-specific configs in `conf/mainnet/`, `conf/testnet/`
- `brs.props.PropertyService` handles configuration management

### Multi-Network Support
- `signum.net.NetworkParameters` - Network configuration base
- Built-in networks: Mainnet, Testnet, MockNetwork
- Switch networks via `node.network` property

### Database Support
- SQLite (default, embedded database)
- MariaDB and PostgreSQL (for production/public nodes)
- JOOQ for type-safe SQL generation
- Flyway for database migrations

## Development Guidelines

### Code Style
- Uses `eclipse-java-style.xml` for formatting
- Checkstyle configuration in `checkstyle.xml`
- Format code before submitting PRs

### Testing Approach
- JUnit 5 for unit tests
- Mockito for mocking
- Some integration tests for blockchain functionality
- Test configuration excludes flaky tests by default

### Database Development
- See `DB_DEV.md` for detailed database development guide
- Schema changes require Flyway migrations
- JOOQ classes are generated from database schema

### Contributing Workflow
- Fork repository and create feature branches
- Use signed commits when possible
- Submit PRs against `develop` branch
- Minimum 1 approving review required
- Squash merges preferred

### Multi-Language Components
- Core node: Java 21
- OpenAPI docs: Node.js/TypeScript
- Neo-page UI: React/TypeScript with Vite
- Classic UI: Static HTML/JS (in `html/ui/classic/`)

### Build Configuration Notes
- Gradle multi-project setup with main node + OpenAPI generation
- Windows executable generation via jpackage
- Distribution includes JRE bundling for Windows
- Some tests are excluded by default due to reliability issues

## Clean Code Guidelines for AI Development

### MANDATORY: Code Quality Standards

**ALWAYS follow these principles when writing or modifying code:**

#### 1. Separation of Concerns (SoC)
- **Single Responsibility**: Each class should have ONE reason to change
- **Layer Separation**: Keep business logic separate from data access, presentation, and infrastructure
- **Package Organization**: Follow existing package structure strictly
  - `brs.services` - Business logic interfaces
  - `brs.services.impl` - Business logic implementations
  - `brs.db.store` - Data access layer
  - `brs.web.api.http.handler` - API presentation layer

#### 2. Dependency Injection Pattern
- Use constructor injection for required dependencies
- Follow existing patterns: Services inject stores, not direct database access
- Example: `AccountServiceImpl` injects `AccountStore`, never directly accesses database
- **BAD**: `new SomeService()` in business logic
- **GOOD**: Constructor parameter with interface type

#### 3. Gang of Four Design Patterns (Use When Appropriate)

**Factory Pattern** - For object creation (see `DatabaseInstanceFactory`)
```java
// Use for complex object creation with multiple variants
DatabaseInstance instance = DatabaseInstanceFactory.createInstance(databaseType);
```

**Observer Pattern** - For event handling (see `BlockchainProcessor` listeners)
```java
// Use for decoupled event notifications
blockchainProcessor.addListener(listener, BlockchainProcessor.Event.AFTER_BLOCK_APPLY);
```

**Strategy Pattern** - For algorithm variants (see `Generator` implementations)
```java
// Use when you have multiple ways to perform the same operation
Generator generator = mockMining ? new MockGenerator(...) : new GeneratorImpl(...);
```

**Command Pattern** - For API handlers (see `brs.web.api.http.handler`)
```java
// Each API endpoint is a command that can be executed
public abstract class APIRequestHandler {
    protected abstract JSONStreamAware processRequest(HttpServletRequest req) throws SignumException;
}
```

**Template Method** - For common processing patterns
```java
// Use when you have a common algorithm with variable steps
public abstract class AbstractTransactionTest {
    // Template method with common test setup
    protected final void executeTransactionTest() {
        setupTest();      // Common
        createTransaction(); // Variable - implemented by subclasses
        validateTransaction(); // Common
    }
}
```

#### 4. MANDATORY Testing Requirements

**For EVERY new class or method, you MUST:**

1. **Create Unit Tests**
   - Place in corresponding `test/java/` directory structure
   - Use `@Test` annotation from JUnit 5
   - Follow naming: `ClassName` → `ClassNameTest`
   - Method naming: `testMethodName_GivenCondition_ExpectedBehavior()`

2. **Test Structure (AAA Pattern)**
```java
@Test
void calculateBalance_GivenValidAccount_ReturnsCorrectBalance() {
    // Arrange
    Account account = createTestAccount();
    when(accountStore.getAccount(accountId)).thenReturn(account);
    
    // Act
    long balance = accountService.calculateBalance(accountId);
    
    // Assert
    assertEquals(expectedBalance, balance);
    verify(accountStore).getAccount(accountId);
}
```

3. **Mock External Dependencies**
   - Use `@Mock` for injected services/stores
   - Use `@InjectMocks` for class under test
   - Never test multiple classes together in unit tests

4. **Test Coverage Requirements**
   - Test happy path AND error conditions
   - Test boundary conditions (null, empty, max values)
   - Test exception scenarios with `assertThrows()`

#### 5. Code Structure Enforcement

**Class Design Rules:**
```java
// GOOD: Clear separation, single responsibility
public class AccountServiceImpl implements AccountService {
    private final AccountStore accountStore;        // Data access
    private final AssetTransferStore transferStore; // Related data access
    
    public AccountServiceImpl(AccountStore accountStore, AssetTransferStore transferStore) {
        this.accountStore = accountStore;
        this.transferStore = transferStore;
    }
    
    @Override
    public Account getAccount(long id) {
        // Business logic only - no direct SQL
        return accountStore.getAccount(id);
    }
}
```

**Method Design Rules:**
- Methods should be < 20 lines when possible
- Use early returns to reduce nesting
- Extract complex logic into private methods with descriptive names
- Use meaningful parameter and variable names

#### 6. Error Handling Standards

**Use Specific Exceptions:**
```java
// GOOD: Specific, informative exceptions
public void transferAsset(long fromId, long toId, long assetId, long quantity) {
    if (quantity <= 0) {
        throw new IllegalArgumentException("Transfer quantity must be positive: " + quantity);
    }
    
    Account fromAccount = accountStore.getAccount(fromId);
    if (fromAccount == null) {
        throw new AccountNotFoundException("Account not found: " + fromId);
    }
    
    // Continue with business logic...
}
```

**Logging Standards:**
- Use SLF4J logger: `private static final Logger logger = LoggerFactory.getLogger(ClassName.class);`
- Log at appropriate levels: DEBUG for detailed flow, INFO for important events, WARN for recoverable issues, ERROR for failures
- Include relevant context in log messages

#### 7. Performance Considerations

**Database Access:**
- Always use prepared statements (JOOQ handles this)
- Implement pagination for large result sets
- Use appropriate indexes (defined in Flyway migrations)
- Avoid N+1 query problems

**Memory Management:**
- Use try-with-resources for closeable resources
- Avoid large object creation in loops
- Consider caching for expensive computations (see `DBCacheManagerImpl`)

#### 8. Security Guidelines

**Input Validation:**
- Validate all external inputs (API parameters, configuration values)
- Use `ParameterParser` for API parameter validation
- Sanitize inputs that will be used in database queries
- Never trust client-provided data

**Cryptographic Operations:**
- Use existing crypto utilities in `brs.crypto` package
- Never implement custom cryptographic algorithms
- Use secure random number generation

### Code Review Checklist

Before submitting code, verify:
- [ ] Follows existing package and naming conventions
- [ ] Has comprehensive unit tests with >80% coverage
- [ ] Uses dependency injection correctly
- [ ] Applies appropriate design patterns
- [ ] Handles errors gracefully with specific exceptions
- [ ] Includes proper logging
- [ ] No hardcoded values (use configuration)
- [ ] No direct database access from business logic
- [ ] Javadoc for public APIs
- [ ] No security vulnerabilities