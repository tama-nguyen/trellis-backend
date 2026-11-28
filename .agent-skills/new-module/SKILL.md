---
name: new-module
description: >
  Add a business module to trellis-backend: run the generator, grant its permissions, then work
  through the five edit points that turn the generated skeleton into real business code. Use when
  asked to add a domain, module or aggregate (course, enrolment, ...) to the backend.
---

# Add a business module

The generator handles scaffolding, Gradle wiring, the Liquibase include and the REST create quota.
Your job is steps 2 and 3 below. The full walkthrough, with real command output, is
`../../docs/guide/new-module.md`; read it rather than guessing, and do not restate it here.
Apply [Java and JUnit conventions](../../docs/guide/java-testing-conventions.md) when filling in
the generated code. Spring Data JPA is supported; use AGENTS.md's persistence rules.

The implementation is `build-logic/scripts/dev/new_module.py`. Generated roots use Long IDs,
repository create/save returning the saved aggregate and Long cursor tie-breakers (ADR 0004).
The domain draft factory defines initial state before the repository assigns an ID.
After template changes, generate and compile a disposable module; string inspection is insufficient.

---

When defining the generated aggregate’s schema and constraints, apply the [database design workflow](../skills/database-design/SKILL.md).

## Multi-agent compatibility

Nothing here is agent-specific: a shell, a text editor and `git` are enough. Claude Code users get
the read-only `./gradlew` commands pre-allowed by `.claude/settings.json`.

---

## 1. Generate

```bash
build-logic/scripts/dev/new-module.sh --name <name> --aggregate <Aggregate> --plural <plural> --dry-run
build-logic/scripts/dev/new-module.sh --name <name> --aggregate <Aggregate> --plural <plural>
```

`--name` is lower-case singular and becomes the directory `trellis-<name>/`, the package
`io.trellis.<name>` and the permission prefix. Always run `--dry-run` first and show the file list
before writing. The script updates `settings.gradle.kts`, `trellis-app/build.gradle.kts`, the
master changelog and the app's `config/rate-limit.yml`. The generated REST controller applies the
create policy per actor. Verify the quota through HTTP when changing the rate limit template.

Confirm the skeleton builds before touching it:

```bash
./gradlew build
```

---

## 2. Grant the permissions, or everything answers 403

The module declares `<name>:read` and `<name>:manage`, and **nobody has them**. Role-to-permission
mapping is data, not configuration: it lives in the `role_permission` table, and the module seeds its own
rows with a Liquibase changeset (`db/changelog/<module>/002-<name>-roles.sql`, included at the end of
`db.changelog-schema.yaml`; `../../docs/guide/new-module.md` §3 shows one). It runs in every environment;
an environment whose IdP uses different role names changes the table with SQL afterwards.

Ask the owner which roles should get them rather than inventing a mapping.

---

## 3. The five edit points, in this order

The skeleton is deliberately poor: the aggregate has a name, a description and a
DRAFT -> ACTIVE -> ARCHIVED lifecycle. `../../docs/guide/new-module.md` §5 is the reference; the
order matters because each step constrains the next.

1. `domain/model/<Aggregate>.java` — the real fields and the real **rules**. Business rules belong
   to the domain, because REST, GraphQL and consumers must all obey the same ones.
2. `domain/value/` — anything with a rule of its own becomes a value object instead of a `String`.
3. `application/validation/<Aggregate>Validator.java` — input checks, collected and returned
   together through `ValidationPipeline`, not failing on the first one.
4. `resources/db/changelog/<name>/001-<name>.sql` — the matching columns. Before this module has
   run anywhere, edit this changeset; once it has, add `002-...sql` and never touch the first.
5. `web/rest/<Aggregate>Controller.java` — the missing endpoints. Read the request, call
   the corresponding `<Verb><Noun>UseCase`, return. No business `if`.

Then whatever the module actually needs — an event, a GraphQL field, a read model, an outbound
client — picking the home from the table in `../../docs/guide/new-module.md` §6. Events start with
the schema in `contracts/events/`, not with Java.

---

## 4. Finish

```bash
./gradlew build                 # ArchUnit, context load, config classification, contract check
```

Then review the result against `../review/SKILL.md`, and if the module adds an endpoint or a
GraphQL field, export the contracts and regenerate the clients as `../../AGENTS.md`
"REST, GraphQL and generated clients" describes.

Common first failures — all request answer 403, the table is missing, ArchUnit is red,
`@Transactional` does nothing — are listed with their causes in `../../docs/guide/new-module.md`
§7. Check there before debugging.

Removing a module again: `../../docs/guide/new-module.md` §8.
