# PR Review Rules for Cursor

When reviewing a Pull Request, check:

## Architecture
- SOLID principles followed
- Clear separation of concerns
- No circular dependencies

## Security  
- No hardcoded secrets
- Input validation present
- Proper error handling

## Testing
- Unit tests included
- Edge cases covered
- Tests are maintainable

## Documentation
- Public APIs documented
- Complex logic explained
- README updated if needed

## Use: @pr-review.md @Codebase review this PR

#
- Always ask before you push to the origin

## Git Workflow
- **NEVER push directly to main** - Always create a feature branch and PR
- All changes must go through pull requests, even small fixes
- Ask before bypassing branch protection rules
- Use descriptive branch names: `feature/`, `fix/`, `docs/`, `refactor/`
