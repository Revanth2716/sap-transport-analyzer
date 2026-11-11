# Troubleshooting

The problems that actually happen, and what they mean.

---

## 1. The application

### `Port 8085 was already in use`

Another process holds the port.

```bash
# Windows
netstat -ano | findstr :8085
tasklist /FI "PID eq 12345"

# Linux
lsof -i :8085
```

Or start on a different port:

```bash
java -jar target/sap-transport-analyzer.jar --server.port=9090
```

> If you started the application from the IDE and it is still running, the
> old process still holds the **JAR file open**. `mvn clean` then fails with
> *"the process cannot access the file because it is being used by another
> process"*. Stop the application before cleaning.

### `503 Service Unavailable` — "the application is still starting up"

This is the **start-up gate** doing its job. Spring Boot opens the web server
before the `CommandLineRunner` beans have run, and the application refuses to
serve a half-built landscape. It opens as soon as the seeders have finished —
a fraction of a second.

If it persists, the seeder failed. Check the log for a constraint violation
or a missing table.

### `Table "TMS_REQUEST" not found`

The schema was not created.

```bash
# The schema runs at start-up from classpath:db/schema.sql
java -jar target/sap-transport-analyzer.jar
```

Check the log for `Copying 8 resources`. If the schema ran and the tables are
still missing, check that `spring.sql.init.mode` is `always`.

### The application starts but every page is empty

The seeding did not run.

```yaml
transport:
  seed-demo-data: true
```

The seeder is **idempotent**: it does nothing when `TMS_REQUEST` already has
rows. If you want the demo landscape back, delete the database file:

```bash
rm -rf data/          # Linux / macOS
# Windows: delete the data folder
```

---

## 2. A finding looks wrong

### "Object ZCL_PAYMENT is also in the open request K900002" — but they are not the same object

Check the **conflict key**, not the name. The key is shown in the object
table's *Type* column together with the name:

- a workbench object is `NAME (TYPE)` — a class and a table with the same
  name are different objects;
- a customizing entry is `NAME.FIELD` — two requests setting the same field
  collide, two requests setting different fields of one table do not.

If the key is wrong, the key computation is wrong — see
[`ARCHITECTURE.md`](ARCHITECTURE.md#4-the-conflict-key).

### "The request depends on K900001, which has not been imported into PRD"

That is the point of the rule: the dependency is resolved against the
**target system**, not against "imported somewhere". K900001 reached QA but
not PRD, so it does not unblock a request going to PRD.

### "The request has been open for 45 days" — but we released it last week

Open and released are different things. The request is open until it has been
imported; a released request that has not been imported is open too, and it
is exactly the one that accumulates conflicting changes.

### "An object was changed after the import" — we did not change anything

The comparison is between the **object's last change** and the **last
successful import**. If the object was touched in development after the
transport went out, the finding is correct: that change is not in production.

In the demo landscape the object timestamp is seeded relative to the current
time, so the finding holds whenever the application runs.

### The score changed without anything changing

The score depends on the **current time**, not on the data. A request that
was 29 days old yesterday is 30 days old today, and the stale-request finding
appears overnight. That is the policy working as agreed.

### Two requests have the same score but different statuses

The severity is the **worst finding**, not the score band. Two requests can
score the same and be in different bands. That is deliberate — see
[`ARCHITECTURE.md`](ARCHITECTURE.md#6-the-score-and-why-the-severity-is-not-the-score).

### The release train shows nothing as READY

Check the state of each request:

- **IN_DEVELOPMENT** — not released yet. Nothing to import.
- **BLOCKED** — released, but a conflict, a dependency, an open task or a
  failed import holds it back. The table says which.
- **IMPORTED** — already in the target system.

A train with one ready request and three blocked ones is a normal, healthy
picture — it means the release window has one thing in it and three things to
fix first.

---

## 3. RFC and the live SAP system

### `RFC function module not found` / `not remote enabled`

**The single most common RFC problem.** The function module was created in
`SE37` but **Remote Enabled** was not ticked.

1. `SE37` → open `Z_TRANSPORT_LIST` → *Attributes* → tick **Remote Enabled**
2. Save and activate

> The error surfaces as an `SM58` entry on the **calling** system, not as a
> dump on the callee. That asymmetry is what makes it confusing: the system
> you are looking at is the one that looks fine.

### The error only appears in `SM58`

`SM58` on the **calling** system shows the RFC errors. Open it after a
failed call — the long text names the reason.

### `jco.properties not found`

The gateway reads `jco.properties` from the **working directory** of the
process.

```bash
cp jco.properties.example jco.properties
# and make sure you start the application from the directory that has it
java -jar target/sap-transport-analyzer.jar
```

### `Communication link failure` / `RFC destination not found`

The destination name in the code must match the name in `SM59` **exactly**,
including the case. The default is `SAPPRD`:

```java
public RfcTransportGateway() {
    this("SAPPRD");
}
```

### `Logon failed` / `Authentication failed`

The user in `jco.properties` cannot log on to the application server. Check
the password, the client (`sap.client`) and that the user is not locked.

### The call works but returns nothing

The function module found no rows. The most likely cause is that the
transport tables on the target system use a **different layout** than the
function module expects — the field names, the status model and the link
table differ between releases. Read the log: it says how many requests were
returned.

```sql
SELECT COUNT(*) FROM e070;
```

### The import history is empty against a live system

The transport tables hold the **requests**, not the **imports**. The import
history is the transport log written by `tp`, which lives in the file system
of the application server, not in the database. Reading it is a separate
piece of work — see the roadmap in the README.

---

## 4. The build

### `Could not resolve dependencies for project ... com.sap.jco:sapjco3`

The SAP Java Connector is not on Maven Central. Either add SAP's repository to
`~/.m2/settings.xml` (see
[`DEPLOYMENT.md`](DEPLOYMENT.md#51-build-with-the-sap-java-connector)) or build
**without** the profile:

```bash
mvn clean package          # the default build, no SAP repository needed
```

### `maven-compiler-plugin` fails on a Java 8 feature

The project is compiled with `source/target = 1.8` on purpose, so it stays
deployable on the NetWeaver 7.5 Java stack. Do not raise it.

### The build fails with "the process cannot access the file because it is being used"

A running application holds the JAR open. Stop it before `mvn clean`.

---

## 5. Tests

### `PageRenderTest` fails with 500

A template expression does not resolve. The response body contains the
`TemplateProcessingException`; the first line of the stack trace names the
template and the line.

| Symptom | Cause |
|---|---|
| `EL1008E: Property or field 'xxx' cannot be found` | The model attribute is not in the controller — usually a value that was added to the template but not to the model |
| `EL1007E: Property or field 'xxx' cannot be found on null` | A `null` object — usually a request that does not exist |
| `EL1028E: Expected to find attribute 'xxx'` | A `th:each` over a value that is not in the model |

### The landscape test fails with "expected 45 but was 0"

The seeder did not run, or the schema was rebuilt under it. The seeders are
idempotent and run in order: `DemoUserSeeder` (`@Order(1)`), then
`TransportSeeder` (`@Order(2)`), then `StartupGate` (`@Order(100)`) which
opens the application.

### A test fails only when the whole suite runs

Almost always the H2 database being shared between test classes. The test
profile uses `jdbc:h2:mem:transporttest;DB_CLOSE_DELAY=-1`, so the database
survives between classes and the schema is only created once.

---

## 6. Where to look first

| Symptom | Look at |
|---|---|
| A finding is wrong | `GET /api/v1/requests/{id}` — it returns the score, the findings and the underlying records |
| The data is stale | `GET /api/v1/health` — it returns the request count, the conflict count and the average score |
| Something was changed | The **Recent activity** table on the dashboard |
| The application misbehaved | The log — every sign in, released request and exported checklist is written to the audit trail |
