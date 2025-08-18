# Claude AI PR Review Setup Guide

This guide explains how to set up and use the Claude AI-powered Pull Request review system for the Signum Node project.

## Overview

The Claude PR Review system automatically reviews Pull Requests using Claude AI to ensure code quality, security, and adherence to project guidelines. It leverages the comprehensive coding standards defined in `CLAUDE.md` and related guideline files.

## Setup Requirements

### 1. GitHub Repository Secrets

You need to configure the following secrets in your GitHub repository:

#### Required Secrets:
- `CLAUDE_API_KEY`: Your Anthropic Claude API key
  - Get from: https://console.anthropic.com/
  - Required permissions: Claude API access
  - Recommended model: Claude 3.5 Sonnet

#### Optional Secrets:
- `GITHUB_TOKEN`: Automatically provided by GitHub Actions (no setup needed)

### 2. Setting up Claude API Key

1. **Get Claude API Access:**
   - Visit https://console.anthropic.com/
   - Create an account or sign in
   - Navigate to API Keys section
   - Generate a new API key

2. **Add to GitHub Secrets:**
   - Go to your repository → Settings → Secrets and variables → Actions
   - Click "New repository secret"
   - Name: `CLAUDE_API_KEY`
   - Value: Your Claude API key
   - Click "Add secret"

### 3. Permissions Setup

The GitHub Action requires these permissions (already configured in the workflow):
- `contents: read` - To read repository files
- `pull-requests: write` - To post review comments
- `issues: write` - To create/update issue comments

## How It Works

### Trigger Conditions
The Claude review runs when:
- A Pull Request is **opened** against `develop` or `main` branches
- An existing PR is **updated** (new commits pushed)
- A PR is **reopened**
- The PR is **not in draft mode**

### Review Process

1. **File Analysis**: Detects changed files (Java, Gradle, properties, Markdown, TypeScript, JavaScript)
2. **Diff Generation**: Creates a code diff for review (limited to 100KB to respect API limits)
3. **Guidelines Loading**: Reads project guidelines from `CLAUDE.md` and `.cursorrules`
4. **AI Review**: Claude analyzes the code against project standards
5. **Comment Posting**: Review results are posted as PR comments
6. **Critical Issue Detection**: Blocks merge if security vulnerabilities are found

### Review Criteria

Claude evaluates PRs based on:

#### 🔒 Security (Highest Priority)
- Input validation for all external data
- Proper use of cryptographic utilities
- SQL injection prevention
- No hardcoded secrets or sensitive data

#### 🏗️ Architecture Compliance
- Layered architecture adherence (API → Services → Stores → Database)
- Constructor dependency injection
- Proper package organization
- Interface segregation

#### 🧪 Testing Requirements
- Unit tests for all new/modified methods
- Proper test structure (Arrange-Act-Assert)
- Comprehensive coverage (happy path + errors + boundaries)
- Correct use of mocking

#### 🎨 Design Patterns
- Appropriate application of Gang of Four patterns
- Factory, Observer, Strategy, Command, Template Method usage

#### 📝 Code Quality
- Clean, readable, maintainable code
- Proper error handling with specific exceptions
- Meaningful naming conventions
- Appropriate logging

#### ⚡ Performance
- Efficient database access
- Resource management
- Memory usage optimization

## Usage Examples

### Example Review Comment

```markdown
## 🤖 Claude AI Code Review

### Overall Assessment: REQUEST_CHANGES

### Summary
This PR adds a new AccountService method but has several issues that need addressing before merge.

### Security Analysis
⚠️ **CRITICAL**: Missing input validation in `getAccountBalance()` method - could lead to SQL injection

### Architecture Review
✅ Properly uses constructor dependency injection
❌ `AccountController` directly accesses `AccountStore` - should use `AccountService`

### Testing Assessment
❌ Missing unit tests for new `calculateTotalBalance()` method
❌ Existing test `getAccount_GivenValidId_ReturnsAccount()` doesn't verify mock interactions

### Code Quality Findings

#### ✅ Good Practices Observed
- Proper use of SLF4J logging
- Meaningful method and variable names
- Early returns to reduce nesting

#### ❌ Issues Requiring Attention
- **AccountServiceImpl.java:45** - Missing null check for accountId parameter
- **AccountServiceImpl.java:67** - Generic Exception caught instead of specific type
- **AccountController.java:23** - Direct store access violates layered architecture

### Action Items
1. **HIGH** Add input validation to `getAccountBalance()` - AccountServiceImpl.java:45
2. **HIGH** Create unit tests for `calculateTotalBalance()` method
3. **MEDIUM** Replace direct store access with service injection - AccountController.java:23

### Recommendation
REQUEST_CHANGES - Address security and architecture violations before merge.
```

## Customization

### Modifying Review Criteria

To customize the review behavior:

1. **Update Guidelines**: Modify `CLAUDE.md` with new or changed requirements
2. **Adjust Workflow**: Edit `.github/workflows/claude-pr-review.yml`
3. **Update Templates**: Modify `.github/PR_REVIEW_TEMPLATE.md`

### Changing Trigger Conditions

Edit the workflow's `on` section:
```yaml
on:
  pull_request:
    types: [opened, synchronize, reopened]
    branches: [develop, main, feature/*]  # Add more branches
```

### Adjusting File Filters

Modify the `changed-files` step to include/exclude file types:
```yaml
files: |
  **/*.java
  **/*.gradle
  **/*.kt        # Add Kotlin files
  !**/test/**    # Exclude test directories
```

## Troubleshooting

### Common Issues

#### 1. "Claude API Key not found"
**Solution**: Ensure `CLAUDE_API_KEY` is properly set in repository secrets

#### 2. "Rate limit exceeded"
**Solution**: Claude has rate limits. Consider:
- Adding delays between requests
- Limiting PR size (current limit: 100KB diff)
- Using batched reviews for large PRs

#### 3. "Permission denied"
**Solution**: Check that the GitHub Action has required permissions:
- Repository settings → Actions → General → Workflow permissions
- Select "Read and write permissions"

#### 4. "No review posted"
**Solution**: Check the Action logs:
- Go to Actions tab → Select failed run
- Review logs for error messages
- Common causes: API quota, malformed diff, network issues

### Debug Mode

To enable verbose logging, add to the workflow:
```yaml
env:
  ACTIONS_STEP_DEBUG: true
```

## Best Practices

### For Repository Maintainers

1. **Monitor API Usage**: Track Claude API usage to manage costs
2. **Review Thresholds**: Adjust review sensitivity based on team preferences
3. **Update Guidelines**: Keep `CLAUDE.md` current as project evolves
4. **Train Team**: Ensure developers understand review criteria

### For Developers

1. **Pre-Submit Review**: Run local checks before creating PRs
2. **Incremental Changes**: Keep PRs focused and reasonably sized
3. **Address Feedback**: Respond to Claude's suggestions promptly
4. **Learn Patterns**: Use Claude's feedback to improve coding practices

## Cost Considerations

- Claude API costs vary by model and token usage
- Estimate: ~$0.01-0.10 per PR review (depending on size)
- Consider setting up budget alerts in Anthropic Console
- Monitor usage patterns to optimize costs

## Security Notes

- Claude API key should be treated as sensitive information
- The system only reads repository contents (no write access to external systems)
- Review comments are posted using GitHub's standard permissions
- No sensitive data should be included in code diffs

## Integration with Existing Workflows

This system complements:
- **Existing CI/CD**: Runs alongside other checks (tests, linting)
- **Human Reviews**: Augments rather than replaces human reviewers
- **Branch Protection**: Can be required check for merge protection
- **Quality Gates**: Integrates with existing quality assurance processes

## Maintenance

### Regular Updates

1. **Model Updates**: Consider newer Claude models as they become available
2. **Guidelines Sync**: Keep review criteria aligned with project evolution
3. **Performance Monitoring**: Track review accuracy and usefulness
4. **Feedback Integration**: Incorporate team feedback to improve reviews

### Monitoring

Monitor these metrics:
- Review accuracy (false positives/negatives)
- Developer adoption and feedback
- Time saved in human review process
- Code quality improvements over time