## Summary
Briefly describe what this pull request changes and why.

## Related Issues
Closes #(issue number)

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] PDF / GST calculation improvement
- [ ] Refactoring / documentation / tests

## Verification Checklist
- [ ] `./gradlew :app:assembleDebug` (or `gradle :app:assembleDebug`) succeeds cleanly
- [ ] `./gradlew :app:testDebugUnitTest` passes 100% (including new/updated tests)
- [ ] `./gradlew :app:lintDebug` passes without new errors
- [ ] Preserves offline-first privacy and zero runtime permissions
- [ ] Interactive controls include `contentDescription`, `testTag`, and >= 48dp touch targets
- [ ] No keystores, secrets, or `local.properties` files committed
