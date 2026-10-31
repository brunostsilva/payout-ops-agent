## 📝 Summary

<!-- What does this PR do, and why? Keep it to a few sentences. -->

## 🏷️ Type of change

<!-- Check all that apply. Should match the Conventional Commit prefix of the PR title. -->

- [ ] ✨ `feat` — New feature
- [ ] 🐛 `fix` — Bug fix
- [ ] ♻️ `refactor` — Code change that neither fixes a bug nor adds a feature
- [ ] ⚡ `perf` — Performance improvement
- [ ] ✅ `test` — Adding or updating tests
- [ ] 📚 `docs` — Documentation only
- [ ] 🔧 `chore` / 🏗️ `build` / 👷 `ci` — Tooling, dependencies, build or CI

## 🔍 Changes

<!-- Bullet list of the main changes. Point reviewers to the files that matter most. -->

-

## 🧪 How to test

<!-- Steps a reviewer can follow to verify this change locally. Integration tests need Docker. -->

```bash
mvn verify
```

## 📸 Evidence (optional)

<!-- Sample requests/responses, logs, screenshots, trace views... -->

## ✅ Checklist

- [ ] PR title follows [Conventional Commits](https://www.conventionalcommits.org/) (e.g. `feat(api): add payout comparison endpoint`)
- [ ] Tests added or updated, and `mvn verify` passes (CI is green)
- [ ] No secrets, API keys or `.env` files committed
- [ ] Flyway migrations are additive (no edits to already-applied versioned migrations)
- [ ] README updated if behavior, endpoints or configuration changed

## ⚠️ Breaking changes / notes for reviewers

<!-- Anything reviewers should pay special attention to. Write "None" if not applicable. -->
