# Contributing Guidelines

Thank you for your interest in contributing to PromoCodeApp! This document provides guidelines and instructions for contributing to the project.

## Code of Conduct

- Be respectful and inclusive
- Welcome all levels of experience
- Provide constructive feedback
- Focus on ideas, not individuals

## Getting Started

1. Fork the repository
2. Clone your fork: `git clone https://github.com/YOUR_USERNAME/PromoCodeApp.git`
3. Create a feature branch: `git checkout -b feature/your-feature-name`
4. Make your changes
5. Commit with clear messages
6. Push to your fork
7. Open a Pull Request

## Development Workflow

### Code Style

Follow official Kotlin style guide: https://kotlinlang.org/docs/coding-conventions.html

**Key points**:
- Indent with 4 spaces
- Max line length: 120 characters
- Use meaningful variable names
- Document public APIs with KDoc

**Example**:
```kotlin
/**
 * Creates a new coupon and stores it locally and remotely.
 *
 * @param coupon The coupon to create
 * @return Result containing the created coupon with ID, or failure
 */
suspend fun createCoupon(coupon: Coupon): Result<Coupon>
```

### Commit Messages

Use clear, descriptive commit messages following this format:

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code refactoring
- `docs`: Documentation
- `test`: Adding tests
- `style`: Code style changes
- `perf`: Performance improvements
- `chore`: Build, CI, dependencies

**Example**:
```
feat(coupon): add barcode scanning capability

Implement barcode scanner integration using ML Kit Vision.
- Add BarcodeScanner composable
- Integrate ML Kit dependencies
- Handle QR code and UPC formats

Closes #123
```

### Branch Naming

Use descriptive branch names:
- Feature: `feature/coupon-search`
- Bug fix: `bugfix/geofence-crash`
- Documentation: `docs/api-reference`
- Chore: `chore/update-dependencies`

## Pull Request Process

1. **Before submitting**:
   - Run `./gradlew build` to ensure no compile errors
   - Run `./gradlew test` to ensure tests pass
   - Run `./gradlew lint` to check code quality
   - Update README.md if needed

2. **PR Description template**:
   ```
   ## Description
   Brief description of changes

   ## Type of Change
   - [ ] New feature
   - [ ] Bug fix
   - [ ] Breaking change
   - [ ] Documentation update

   ## Changes Made
   - Change 1
   - Change 2

   ## Testing
   Describe how you tested these changes

   ## Screenshots (if applicable)
   Add screenshots for UI changes

   ## Checklist
   - [ ] Code follows style guidelines
   - [ ] Self-review completed
   - [ ] Comments added for complex code
   - [ ] Documentation updated
   - [ ] No breaking changes
   - [ ] Tests added/updated
   ```

3. **Code Review**:
   - Maintainers will review your code
   - Address feedback and make requested changes
   - Re-request review when ready

4. **Merge**:
   - Ensure all CI checks pass
   - Squash commits if needed
   - Merge to main branch

## Testing Requirements

### Unit Tests
- Required for business logic (repository, ViewModel)
- Use JUnit 4 and Mockk
- Minimum coverage: 70% of domain/data layers

### Integration Tests
- Required for database operations
- Use Android Test Framework
- Test actual Room database interactions

### UI Tests
- Write Compose tests for critical screens
- Use ComposeTestRule
- Test user interactions and state changes

**Example test structure**:
```kotlin
@RunWith(JUnit4::class)
class CouponRepositoryTest {
    private lateinit var couponDao: CouponDao
    private lateinit var repository: CouponRepository

    @Before
    fun setup() {
        // Initialize test doubles and real instances
    }

    @Test
    fun createCoupon_savesCorrectly() = runTest {
        // Given
        val coupon = testCoupon()

        // When
        val result = repository.createCoupon(coupon)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(coupon.code, result.getOrNull()?.code)
    }
}
```

## Documentation

### Code Documentation
- Add KDoc for all public functions and classes
- Explain why, not just what
- Include parameter and return descriptions
- Add examples for complex functions

### README Updates
- Update for new features
- Update architecture if structure changes
- Add troubleshooting for known issues

### API Documentation
- Document new API endpoints
- Include request/response examples
- Document error codes and meanings

## Filing Issues

### Bug Reports
Include:
- Description of the bug
- Steps to reproduce
- Expected vs actual behavior
- Android version and device
- Logs or stack traces

### Feature Requests
Include:
- Clear description of feature
- Use cases and benefits
- Proposed UI/UX (sketches if applicable)
- Acceptance criteria

## Review Checklist for Maintainers

- [ ] Code follows style guide
- [ ] No commented-out code
- [ ] Meaningful variable names
- [ ] Proper error handling
- [ ] Tests are included
- [ ] Documentation is updated
- [ ] No security issues
- [ ] No performance degradation
- [ ] Follows architecture patterns

## Release Process

1. Increment version in `build.gradle.kts`
2. Update CHANGELOG.md
3. Create release branch: `release/v1.0.0`
4. Create git tag: `v1.0.0`
5. Push tag to trigger CI/CD
6. Create release notes on GitHub

## Feature Roadmap

Current priorities:
1. **Phase 1 (MVP)**: Core coupon management, geofencing, notifications
2. **Phase 2**: Barcode scanning, memberships, offline sync
3. **Phase 3**: iOS port
4. **Phase 4**: API integrations, recommendations

## Questions?

- Open an issue with `question` label
- Discuss in GitHub Discussions
- Email: support@promocodeapp.com

---

**Thank you for contributing to PromoCodeApp!** 🎉
