# Architecture

Why the SAP Transport & Change Management Analyzer is built the way it is.

---

## 1. The shape of the problem

A BASIS team owns the transport system, and the transport system answers
"which requests are open" very well. What it does not answer is the question
a release manager asks on Thursday afternoon:

> **What can go into the next transport, in what order, and what is stopping
> the rest?**

That question has four parts, and each one fails in a different, silent way:

| Question | Where the answer lives | How it fails |
|---|---|---|
| What can go? | The request list | Two requests hold the same object. The second import overwrites the first, silently |
| In what order? | The dependency chain | A customizing request needs the workbench request it references, and the reference is not obvious |
| What is stopping it? | Nothing | A task is still in development, so the workbench request cannot be released |
| Did it actually go? | The transport log | A request was released and imported, and then somebody changed an object in it — and nobody transported the change |

The value of this application is not that it lists requests. It is that it
answers those four questions **together**, for one landscape, with the
evidence behind each answer.

---

## 2. The layers

```
web/          Controllers, session auth, role gating, REST API
service/      The rules: analysis, release train, audit
repo/         JdbcTemplate over portable ANSI SQL
domain/       Plain Java objects, no framework annotations
support/      Server-side SVG rendering
integration/  The boundary to a live SAP system
seed/         The demo landscape and the demo accounts
```

The dependency rule is one-directional and never bent: `web → service → repo →
domain`. The domain objects know nothing about Spring, the repository knows
nothing about HTTP, and the services know nothing about Thymeleaf.

### Why `JdbcTemplate` and not JPA

1. **Portability.** The SQL has to run unchanged on H2 (the demo) and on SAP
   HANA (production). Hand-written ANSI SQL is the only way to guarantee that.
2. **No `LIMIT`.** `LIMIT` is not valid on SAP HANA. Every "top N" query here
   fetches the rows and cuts the list **in Java**
   (`PlatformRepository.findRecentAudit`).
3. **The schema is five tables.** An ORM would add more configuration than the
   schema itself.

---

## 3. The domain model

```
TransportRequest    one request: number, type, status, owner, target, age
   │
   ├── TransportObject     one object in the request, with its conflict key
   │
   ├── TransportDependency one edge: this request depends on that one
   │
   ├── ImportRecord        one import attempt into one system
   │
   └── TransportAssessment the assessment: score, severity, findings
            │
            ├── TransportFinding  severity + category + message + action
            └── ObjectConflict    one object held by several open requests
```

`ReleaseSystem` is the landscape — DE1, QAS, PRD — and each has a role and a
flag saying whether it is a production target.

---

## 4. The conflict key

This is the idea the whole project rests on.

Two requests conflict when they change **the same thing**. "The same thing"
is not always the same answer:

| Object | Conflict key | Why |
|---|---|---|
| A workbench class | `ZCL_PAYMENT (CLAS)` | Name **and** type. A class and a table that happen to share a name are different objects |
| A customizing entry | `ZPAYMENT_BADI.ACTIVE` | Name **and** field. Two requests setting the same field of the same table collide; two requests setting *different* fields of one table do not |

Getting this wrong in either direction is costly:

- Too coarse (name only) — `ZCL_PAYMENT` and `ZTABLE_PAYMENT` look like a
  conflict, and the team merges two perfectly compatible requests.
- Too fine (name + type + field always) — the same customizing table touched
  in two different fields looks like a conflict, and the team merges two
  requests that could both have gone.

The key is computed in the seeder and in the RFC adapter, and it is the thing
the conflict index is built on.

---

## 5. The rules

`TransportAnalysisService.assess()` runs eight checks per request.

| # | Condition | Finding | Severity | Penalty |
|---|---|---|---|---|
| 1 | An object in this request is also in another **open** request | `OBJECT_CONFLICT` | CRITICAL | 40 |
| 2 | A customizing request references a request that has not reached **the target system** | `BLOCKED_DEPENDENCY` | CRITICAL | 35 |
| 3 | The last import attempt failed | `FAILED_IMPORT` | CRITICAL | 35 |
| 4 | An object was changed **after** the last successful import | `MODIFIED_AFTER_IMPORT` | HIGH | 30 |
| 5 | A task of this request is still in development | `TASK_IN_DEVELOPMENT` | HIGH | 30 |
| 6 | The request has been in development for too long | `UNRELEASED_TOO_LONG` | HIGH | 25 |
| 7 | The request is released and has no import record at all | `NO_IMPORT_EVIDENCE` | HIGH | 25 |
| 8 | The request is open for too long | `STALE_REQUEST` | MEDIUM | 15 |
| 9 | The request is released and still targets a non-production system | `NOT_TARGETED` | MEDIUM | 10 |
| 10 | The request has no owner | `NO_OWNER` | LOW | 10 |

### Three rules that are easy to get wrong

**Only *open* requests conflict.** A request that has already been imported
is not a conflict — its objects are in the target system, and a new request
changing the same object is normal succession, not a collision.

**A dependency is satisfied by the target system.** K900001 reached QA but not
PRD. A customizing request going to PRD is still blocked. Resolving the
dependency against "was it imported *anywhere*" would have marked K900003 as
ready and put it in the transport — where it would have failed.

**"Blocked" and "unfinished" are different.** A request still in development
is not blocked; it is not ready. A request that is released and *cannot go* is
blocked. The release train needs the distinction, or nothing is ever
importable.

### One finding per problem

A request whose only import attempt failed produces **one** finding, not two.
`NO_IMPORT_EVIDENCE` fires only when there is no import attempt at all. The
first version fired both, and the report said the same thing twice.

---

## 6. The score, and why the severity is not the score

```
score    = max(0, 100 - sum of the penalties)
severity = the worst severity among the findings
```

A failed import costs 35 points, so the request scores 65. On a score band of
"60–80 = medium", that is medium. The import **failed**. The severity says
**critical**.

If the severity came from the score, the conflict that breaks the transport
would be filed next to a stale request, because the other findings drag the
average up. The score exists for one purpose: sorting thirty requests so the
worst one is on top. It is never the answer on its own.

`TransportAnalysisServiceTest.severityIsTheWorstFindingNotTheScoreBand` pins
this down, and the test name says why it exists.

---

## 7. The release train

`ReleaseService` turns the assessments into the document a release manager
works from.

### The states

| State | Meaning |
|---|---|
| **READY** | Released, and nothing stands in the way of importing it |
| **BLOCKED** | Released and *would* go, but a conflict, a dependency, an open task or a failed import holds it back |
| **IN_DEVELOPMENT** | Not released yet — unfinished, not blocked |
| **IMPORTED** | Already in the target system |

### The order

1. Ready requests first — they are what the window is for.
2. Within the ready set, a **topological sort of the dependency edges**, so a
   customizing request never appears before the workbench request it
   references.
3. Blocked, then unfinished, then imported.
4. Within a group, oldest first — a change that has been waiting longest goes
   first.

### The checklist

`ReleaseService.checklist()` renders the same thing as Markdown: what is
ready with the `tp` command to run, what is blocked and why, what is still
unfinished, what is already in, and a **pre-flight section** naming the
conflicting objects that have to be merged before anything is imported.

> The `tp` options depend on the client's transport profile. The checklist
> says so, because a checklist that pretends its commands are universal is
> worse than one that tells you to check.

---

## 8. The gateway boundary

```java
public interface TransportGateway {
    String description();
    List<TransportRequest> fetchRequests();
    List<TransportObject> fetchObjects(String requestId);
}
```

| Implementation | Where | When |
|---|---|---|
| `LocalTransportGateway` | `src/main/java` | The default — reads the local database |
| `RfcTransportGateway` | `src/extra/java` | `transport.gateway: jco` — calls `Z_TRANSPORT_LIST` over RFC |

The JCo adapter lives in `src/extra/java` and is added to the build by the
`with-jco` profile through `build-helper-maven-plugin`. The SAP Java Connector
is published on SAP's own Maven repository, not Maven Central, so a plain
`mvn package` cannot resolve it. Keeping the adapter out of the default source
path means **the default build compiles and tests anywhere**, with no SAP
repository access and no licence.

---

## 9. The start-up gate

Spring Boot opens the web server **before** the `CommandLineRunner` beans have
run. For the first few hundred milliseconds the application answers requests
with a half-built landscape — a dashboard showing three requests instead of
nine, and a release train built from whichever ones happened to exist.

That window is short, but it is real: a load balancer that probes the
application the moment the port opens sees an empty dashboard, and somebody
refreshing the browser twice gets two different answers.

`StartupGate` is a `CommandLineRunner` with the highest order, so it is
always the last one. Until it runs, the interceptor answers **503 Service
Unavailable** with a message that says why. The application opens when the
landscape is actually there.

---

## 10. Security

| Concern | Decision |
|---|---|
| Password storage | BCrypt, hashed at runtime — no reusable hash is committed |
| Sessions | Server-side `HttpSession`, 30-minute timeout, `HttpOnly` and `SameSite=Lax` cookies |
| Authorisation | Three roles (`VIEWER`, `ANALYST`, `CHANGE_MANAGER`), one interceptor, one `@RequireRole` |
| The API | Authenticated like the screens; only `/api/v1/health` is open |
| Write operations | Releasing a request is `CHANGE_MANAGER` only, and is written to the audit trail |

Spring Security is used for the BCrypt encoder only — the filter chain is
deliberately not used, so the application stays deployable as a plain WAR on
the NetWeaver Java stack.

Every finding message and every recommendation is written by the service
layer, never by the view.

---

## 11. The audit trail

| Action | Why it matters |
|---|---|
| `LOGIN` / `LOGOUT` / `LOGIN_FAILED` | Who was in the system |
| `REQUEST_RELEASED` | **The moment a change is declared ready to move to the next system** |
| `RELEASE_TRAIN_VIEWED` | Somebody was shown what can go |
| `CHECKLIST_EXPORTED` | The document left the building |

Releasing a request is the write path of this application, and it is the
moment a change is declared fit to travel — which is exactly the moment
somebody will ask about later.

---

## 12. The demo landscape

Nine requests, each with one specific failure mode:

| Request | Demonstrates |
|---|---|
| K900001 | The conflict — shared object, open for 45 days |
| K900002 | The other half of the conflict, released and never imported |
| K900003 | A blocked dependency resolved against the *target* system |
| K900004 | An object changed after the import |
| K900005 | A task in development for 70 days |
| K900006 | The workbench request that task holds back |
| K900007 | Released, but still pointing at QA |
| K900008 | The control — imported cleanly and untouched |
| K900009 | The only request that can go |

Random noise would prove nothing. Every number exists because it demonstrates
one specific finding, and the release train for PRD is the proof that the
rules compose into an answer.

---

## 13. Testing strategy

| Layer | What is tested | How |
|---|---|---|
| Rules | Each rule on its own | `TransportAnalysisServiceTest` — a mocked repository per scenario, so a failure names the rule |
| Data | The story the seed has to tell | `TransportLandscapeTest` — the whole Spring context, the real seed |
| Contract | The API shape and the authentication | `ApiControllerTest` — MockMvc |
| Rendering | Every screen renders | `PageRenderTest` — MockMvc against every URL |
| Start-up | The application opens after the seed | `StartupGateTest` |

`PageRenderTest` is not optional. A template can compile, the service layer can
be fully green, and one screen can still return HTTP 500 because a single
expression in the markup does not resolve.
