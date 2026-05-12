# Context

We want to make practical use of Firecrawl without turning every comment skill into a scraping skill. The current Product Hunt skill already has a Firecrawl section and is the strongest fit for launch-page capture, while the Reddit keyboard skill should explicitly avoid depending on Firecrawl because Reddit is a constrained source. The best outcome is a two-layer design: one reusable Firecrawl utility skill for page-context capture, plus a light integration into the Product Hunt workflow and a limitation note in the Reddit workflow.

# Recommended Approach

1. Create a new standalone skill at `C:\Users\a\.omc\skills\firecrawl-context\SKILL.md`.
   - Purpose: capture page context from one or more URLs using Firecrawl, not generate comments.
   - Keep the scope narrow: choose between scrape / map / crawl / interact, summarize extracted facts, note confidence and capture method, and stop there.
   - Structure it like the existing skills with frontmatter (`name`, `description`, `argument-hint`, `triggers`, `level`) plus sections for Goal, Main Workflow, Tool Rules, Output Format, and limits.
   - Reuse the style of the existing skill files in `C:\Users\a\.omc\skills\product-hunt-commenter\SKILL.md` and `C:\Users\a\.omc\skills\reddit-keyboard-promotion\SKILL.md` rather than inventing a new template.

2. Update `C:\Users\a\.omc\skills\product-hunt-commenter\SKILL.md` to make Firecrawl an explicit supporting data source.
   - In `## Main Workflow`, add a step after product-background research that uses Firecrawl when page text, maker framing, launch tone, or visible comments are needed.
   - Keep Tavily as the first-pass background source and position Firecrawl as the launch-page capture layer.
   - Expand `## Firecrawl / PH Page Capture` to reflect the researched recommendation order: scrape first, then interact/actions only when needed, and fall back to Tavily + user excerpt if Firecrawl cannot confirm details.
   - Preserve the current account-safety/comment-generation logic; only adjust the research/capture path.

3. Update `C:\Users\a\.omc\skills\reddit-keyboard-promotion\SKILL.md` with a concise Firecrawl limitation note.
   - Best insertion point: `## Tool Rules For OpenClaw` at `C:\Users\a\.omc\skills\reddit-keyboard-promotion\SKILL.md:234`.
   - Add that Reddit is not a default Firecrawl dependency here, so the skill should prefer Tavily/search/excerpts and downgrade confidence when thread facts cannot be verified.
   - Do not add Firecrawl as part of the main Reddit workflow.

4. Keep secrets out of skill text.
   - Do not hardcode the provided Firecrawl API key into any `SKILL.md` file.
   - Reference an environment variable placeholder such as `FIRECRAWL_API_KEY` or generic `<FIRECRAWL_KEY>` only.

# Critical Files

- `C:\Users\a\.omc\skills\firecrawl-context\SKILL.md` (new)
- `C:\Users\a\.omc\skills\product-hunt-commenter\SKILL.md`
- `C:\Users\a\.omc\skills\reddit-keyboard-promotion\SKILL.md`

# Existing Patterns To Reuse

- Skill frontmatter and structure from `C:\Users\a\.omc\skills\product-hunt-commenter\SKILL.md:1`
- Tool-rules section pattern from `C:\Users\a\.omc\skills\reddit-keyboard-promotion\SKILL.md:234`
- Firecrawl subsection already present in `C:\Users\a\.omc\skills\product-hunt-commenter\SKILL.md:247`

# Verification

1. Read all three final skill files to confirm the wording and placement are correct.
2. Run a skill discovery scan/list to ensure the new `firecrawl-context` skill is recognized alongside the existing two skills.
3. Spot-check that the Product Hunt skill now routes Firecrawl usage through the workflow and that the Reddit skill only contains a limitation note, not a Firecrawl dependency.
4. Confirm no API key was written to disk and only placeholders remain in examples.
