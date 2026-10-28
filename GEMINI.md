# Workspace & Agent Rules

## 1. Automatic GitHub Push After Changes
- Whenever you modify, create, or delete project files in response to a user prompt, automatically stage the modified files (`git add`), create a clear and concise commit (`git commit -m "..."`), and push the commit to the configured GitHub remote (`git push`) before finishing the turn, unless the user explicitly asks not to commit/push.

## 2. Gemini Model Identifiers (3.5 / 3.8 Series)
- `gemini-3.5-flash-lite`, `gemini-3.5-flash`, `gemini-3.6-flash`, and `gemini-3.8-flash` are valid, available, high-speed models in this environment and are preferred over `2.5` or `1.5` series models.
- Never treat these model identifiers as typos/404s or downgrade them to older `gemini-2.5-*` or `gemini-1.5-*` models unless explicitly instructed by the user.

