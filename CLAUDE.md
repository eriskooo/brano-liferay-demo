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
- `legacy/`, `sso-mock/`, `docker/` – the runnable "before" app on Liferay 6.2 (sub-project 1, done; see `legacy/README.md`).
- `crm-web/` – the migrated Spring Boot "after" app (sub-project 2, done; see `crm-web/README.md`, `docs/MIGRACIA.md`).

Docs and notes are written in **Slovak**; the source materials are in Czech.

## Current status (2026-10-08)

- Phase 1 (analysis) done: `docs/ANALYZA.md`, knowledge base in `poznamky/`.
- Phase 2 design approved: spec `docs/superpowers/specs/2026-10-08-liferay-migracia-demo-design.md`
  (user decisions: blacklist + simplified service cancellation, Spring MVC + JSP with same-interface tags, Java 21,
  Spring Boot 4.1.1, security unchanged).
- Implementation plan for the "after" app: `docs/superpowers/plans/2026-10-08-liferay-migracia-demo.md` (13 TDD tasks) –
  not executed; it must be revised first (see sub-project 2 below).
- 2026-10-12 scope change – the demo shows **both** states, split into two sub-projects:
  1. **Legacy "before" app — done** (`legacy/`, `sso-mock/`, `docker/`; spec + plan `2026-10-12-legacy-liferay-app*`):
     Liferay 6.2 CE GA6 + Java 8 (Zulu 8u252 – update must be ≤ 255, ICU bug) + Spring Portlet MVC 4.3.6,
     SSO mock (HTTP + API JAR in `lib/ext`), dual session via Redis (flag `crm:flags:session-read-source`).
  2. **Spring Boot "after" app — done** (`crm-web/`; spec + plan `2026-10-12-crm-web-migration*`): blacklist migrated,
     dashboard stays in Liferay; nginx strangler `/web/crm/blacklist` → `/crm/blacklist/search`; shared SSO identity and
     Redis session (same JSON as legacy); legacy flag must be `REDIS` for cross-app state. Verify: `bash docker/smoke-migration.sh`.
- Git: the user allows commits only when they explicitly ask; never push unless asked.

## Anonymization (hard rule)

Everything outside `input/` is committed — including `poznamky/`, this file, and superpowers outputs. It must not contain:
client/vendor/people names, internal system names, internal URLs or hostnames, credentials, data from the logs,
or code copied from `input/`. "Before" examples are rewritten as minimal anonymized snippets.
Use the abbreviations from `poznamky/00-konvencie.md` (e.g. `$FE` for the unpacked frontend project, `<pkg>` for its base package).
Do not commit or push; the user does that.

Check before the user commits (Bash, repo root): `bash poznamky/LOCAL-anonymizacia.sh` — expected output: nothing (grep exit 1).
If the script is missing on this machine, it has to be copied manually together with `poznamky/LOCAL-aliasy.md`.

## Build & run (legacy "before" app)

- Unit tests: `mvn -q -B -f legacy/pom.xml verify`, `mvn -q -B -f sso-mock/pom.xml verify`;
  one class: `mvn -q -B -f legacy/pom.xml test -pl <module> -am -Dtest=ClassName -Dsurefire.failIfNoSpecifiedTests=false`.
- Stack: `bash docker/build-and-deploy.sh` (or `docker\build-and-deploy.ps1`), then `bash docker/smoke.sh` → `SMOKE OK`.
  CRM at http://localhost:8000, RedisInsight :5540, Liferay admin :8080 (`test@liferay.com`/`test`).
- Shared session: `bash docker/show-session.sh`, `bash docker/session-flag.sh PORTLET|REDIS`.
- New app: `mvn -q -B -f crm-web/pom.xml verify` (needs Docker for Testcontainers); stack smoke: `bash docker/smoke-migration.sh` → `MIGRATION SMOKE OK`.
- Legacy modules compile with `--release 8` – Java 8 API only (no records, `var`, `List.of`, `isBlank`, `toList()`).

## "After" app architecture and conventions

- `crm-web/` Maven WAR module, context path `/crm`, package `com.example.crm`. Portlet phases map to Spring MVC:
  render = GET, action = POST + redirect (PRG), resource = AJAX endpoint (HTML fragment or JSON).
- Custom JSP tags `portal:*` / `common:*` keep the **legacy interface** but are implemented without Liferay
  (`web/tag/`, TLDs in `src/main/webapp/WEB-INF/tld/`); the session facade (`CrmSession`) and ACL keep the legacy contract;
  backend services are interfaces with in-memory implementations.
- Every JSP starts with `<%@ include file="/WEB-INF/jsp/layout/taglibs.jspf" %>` and has no own `page` directive.
- Identifiers in English; code comments, UI texts (`messages.properties`) and docs in **Slovak** (this overrides the global
  "comments in Czech" rule for this repo). Comments explain the migration decision / legacy equivalent, not what the code does.

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
