# Claude AI PR Review Template

This template defines the review criteria and format that Claude AI uses when reviewing Pull Requests for the Signum Node project.

## Review Priorities (Ordered by Importance)

### 1. 🔒 Security Review
- **Input Validation**: All external inputs properly validated
- **Cryptographic Operations**: Only use existing utilities in `brs.crypto`
- **SQL Injection**: Proper use of JOOQ prepared statements
- **Authentication/Authorization**: Proper access controls
- **Sensitive Data**: No secrets, keys, or sensitive info in code

### 2. 🏗️ Architecture Compliance
- **Layer Separation**: API → Services → Stores → Database
- **Dependency Injection**: Constructor injection only
- **Package Structure**: Follows `brs.*` organization
- **Interface Segregation**: Services depend on interfaces
- **Single Responsibility**: One reason to change per class

### 3. 🧪 Testing Coverage
- **Unit Tests**: Every new/modified method has tests
- **Test Structure**: Arrange-Act-Assert (AAA) pattern
- **Mocking Strategy**: All dependencies mocked with Mockito
- **Coverage**: Happy path + error conditions + boundary cases
- **Test Naming**: `testMethod_GivenCondition_ExpectedBehavior`

### 4. 🎨 Design Patterns
- **Factory Pattern**: Complex object creation
- **Observer Pattern**: Event handling (blockchain events)
- **Strategy Pattern**: Algorithm variants
- **Command Pattern**: API handlers
- **Template Method**: Common processing patterns

### 5. 📝 Code Quality
- **Method Length**: < 20 lines preferred
- **Meaningful Names**: Clear, descriptive identifiers
- **Early Returns**: Reduce nesting complexity
- **Error Handling**: Specific exception types
- **Logging**: Appropriate SLF4J levels

### 6. ⚡ Performance
- **Database Access**: Efficient queries, pagination
- **Resource Management**: Try-with-resources for closeables
- **Memory Usage**: Avoid large object creation in loops
- **Caching**: Consider for expensive operations

## Review Format Template

```markdown
## 🤖 Claude AI Code Review

### Overall Assessment: [APPROVE/REQUEST_CHANGES/COMMENT]

### Summary
[Brief overview of the changes and overall quality]

### Security Analysis
[Security-specific findings with ⚠️ for critical issues]

### Architecture Review
[Compliance with layered architecture and dependency injection]

### Testing Assessment
[Coverage analysis and test quality review]

### Code Quality Findings

#### ✅ Good Practices Observed
- [List positive findings]

#### ❌ Issues Requiring Attention
- [File:Line] Issue description with suggested fix

#### 🔄 Suggestions for Improvement
- [Optional improvements that would enhance code quality]

### Performance Considerations
[Database, memory, or performance-related observations]

### Design Patterns Usage
[Assessment of appropriate pattern application]

### Compliance Checklist
- [ ] Follows layered architecture
- [ ] Uses constructor dependency injection
- [ ] Has comprehensive unit tests
- [ ] Applies appropriate design patterns
- [ ] Handles errors with specific exceptions
- [ ] Includes appropriate logging
- [ ] Validates external inputs
- [ ] No hardcoded values
- [ ] No direct database access from business logic
- [ ] Follows naming conventions
- [ ] No security vulnerabilities

### Action Items
1. [Priority] [Description] - [File:Line]
2. [Priority] [Description] - [File:Line]

### Recommendation
[Final recommendation: APPROVE/REQUEST_CHANGES with reasoning]
```

## File-Specific Review Focus

### Java Service Classes (`brs.services.impl.*`)
- Constructor dependency injection
- Interface implementation compliance
- Business logic separation
- Error handling and validation
- Unit test coverage

### API Handlers (`brs.web.api.http.handler.*`)
- Command pattern implementation
- Parameter validation using `ParameterService`
- Proper exception handling
- JSON response formatting
- Security input validation

### Data Stores (`brs.db.store.*`)
- JOOQ usage for SQL safety
- Proper transaction handling
- Result set pagination
- Connection management
- Database abstraction

### Test Classes (`test/java/**/*Test.java`)
- AAA structure compliance
- Mock usage correctness
- Test coverage completeness
- Assertion appropriateness
- Test naming conventions

### Configuration Files (`*.properties`, `*.gradle`)
- No hardcoded secrets
- Proper property naming
- Environment-specific configs
- Build configuration sanity

### Frontend Code (`neo-page/**`, `openapi/**`)
- TypeScript type safety
- React best practices
- Security in API calls
- Error handling
- Performance considerations

## Critical Blocking Conditions

Claude will flag these as blocking issues requiring immediate attention:

1. **Security Vulnerabilities**: Input validation missing, crypto misuse
2. **Architecture Violations**: Direct database access from business logic
3. **Missing Tests**: New code without corresponding tests
4. **Hardcoded Secrets**: API keys, passwords, tokens in code
5. **SQL Injection Risks**: Raw SQL instead of JOOQ
6. **Performance Issues**: N+1 queries, resource leaks

## Review Thresholds

- **APPROVE**: No critical issues, minor suggestions only
- **REQUEST_CHANGES**: Critical issues or significant architecture violations
- **COMMENT**: Feedback provided but no blocking issues

## Integration Notes

This template is used by the GitHub Action `.github/workflows/claude-pr-review.yml` which:
- Triggers on PR open/update to `develop` or `main` branches
- Reads project guidelines from `CLAUDE.md`
- Analyzes code diffs against these criteria
- Posts structured feedback as PR comments
- Blocks merge on critical security issues

## Customization

To modify review criteria:
1. Update this template
2. Adjust the GitHub Action's system prompt
3. Update `CLAUDE.md` guidelines
4. Test with a sample PR

The goal is consistent, thorough reviews that maintain code quality and security standards for this critical blockchain infrastructure.