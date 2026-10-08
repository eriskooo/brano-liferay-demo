# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repository is

A **learning demo** of migrating a legacy customer-care CRM frontend (Liferay 6.2 portlets) to Spring Boot.
The repository is meant for GitHub; the original client materials live in `input/`, which is
**git-ignored and read-only** — never modify, move or commit anything there.

Output layout (outside `input/`):
- `docs/` — `ANALYZA.md` (analysis of the legacy project), `MIGRACIA.md` (before → after, mapping table),
  `docs/superpowers/` (specs and plans).
- `poznamky/` — a portable **knowledge base**: the user will import it into the real project to orient
  themselves and carry out the migration. Keep it practical (where to look, concepts → replacements, playbook, pitfalls)
  and add to it as you learn. Index: `poznamky/README.md`; abbreviations and anonymization: `poznamky/00-konvencie.md`.
  `poznamky/LOCAL-*` (alias → real-name mapping, anonymization check script) is git-ignored and copied manually.
- The demo Spring Boot project itself (Phase 2, not yet created).

Docs and notes are written in **Slovak**; the source materials are in Czech.

## Current status (2026-10-08)

- Phase 1 (analysis) done: `docs/ANALYZA.md`, knowledge base in `poznamky/`.
- Phase 2 design approved: spec `docs/superpowers/specs/2026-10-08-liferay-migracia-demo-design.md`
  (user decisions: blacklist + simplified service cancellation, Spring MVC + JSP with same-interface tags, Java 21,
  Spring Boot 4.1.1, security unchanged).
- Implementation plan written: `docs/superpowers/plans/2026-10-08-liferay-migracia-demo.md` (13 TDD tasks).
  **Next step:** the user reviews the plan and picks the execution method (recommended: native – superpowers:executing-plans);
  nothing of the demo code exists yet.
- Git: the user allows commits only when they explicitly ask; never push unless asked.

## Anonymization (hard rule)

Everything outside `input/` is committed — including `poznamky/`, this file, and superpowers outputs. It must not contain:
client/vendor/people names, internal system names, internal URLs or hostnames, credentials, data from the logs,
or code copied from `input/`. "Before" examples are rewritten as minimal anonymized snippets.
Use the abbreviations from `poznamky/00-konvencie.md` (e.g. `$FE` for the unpacked frontend project, `<pkg>` for its base package).
Do not commit or push; the user does that.

## The legacy project (in `input/`, for reference only)

- `$FE` = the unpacked `frontend` Maven module. Its `target/` dirs are build output, not source.
  The archive was corrupted: at least one backend JAR in `customercare-web/target/.../WEB-INF/lib` is missing, possibly more.
- `input/pom.xml` — root parent POM (Liferay 6.2.5, Spring 4.3.6, Hibernate 4.2, Java 1.8). Sibling modules and the private
  Maven repository are not available, so **the legacy code cannot be built here**.
- Modules: `customercare-portlets` (legacy Liferay portlet WAR, Spring Portlet MVC, ~1240 JSPs, custom taglibs, Liferay hooks),
  `frontend-base-customization` (shared base library), `customercare-theme` (Liferay theme), `customercare-web`
  (an earlier PoC: plain Spring MVC WAR, JSPs ported 1:1, taglib compatibility layer, backend services stubbed —
  **only `target/` exists, no `src/`**), `customercare-testing`, `one-portal-portlets` (prebuilt WAR only).
- `input/*.pptx`, `*.docx`, `*.html` + `*.py` generators — presentations and a Q&A document describing the migration approach.
- `input/*_war*.log` — runtime logs of the PoC (stubbed services, one JSP binding error).
