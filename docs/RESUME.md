# Resume content

Copy-paste blocks. Pick the length that fits the CV and delete the rest.

> **Do not paste the project title as a client engagement.** This is
> self-initiated portfolio work. Say *"a self-initiated project I built to
> practise..."* — an interviewer who asks "who was the client?" and gets a
> vague answer will stop asking the good questions.

---

## 1. The one-line version

> **SAP Transport & Change Management Analyzer** — change and transport
> management for SAP landscapes: object conflict detection, dependency chains,
> stale request detection and a release train per target system.

---

## 2. The three-bullet version

> **SAP Transport & Change Management Analyzer** — *Java 8 · Spring Boot 2.7 · JdbcTemplate · SAP HANA · Thymeleaf · ABAP RFC · SAP Java Connector*
>
> - Built a **change and transport management analyzer** for SAP landscapes that detects **object conflicts** across open transport requests — the case where two requests carry the same object and the second import silently overwrites the first — resolves **dependency chains against the target system**, and verifies **import evidence** and objects changed after the last transport.
> - Designed a **release train** per target system that separates *ready*, *blocked* and *unfinished* requests, orders the importable ones by dependency, and generates an **import checklist with the `tp` commands** — so a release window is planned from evidence rather than from memory.
> - Shipped the SAP-side artifacts (**RFC-enabled ABAP function module** reading `E070` and `TRDIR`, **SAP Java Connector client** behind an optional Maven profile) on **portable ANSI SQL** that runs unchanged on local H2 and SAP HANA, with **65 automated tests**.

---

## 3. The two-bullet version

> **SAP Transport & Change Management Analyzer** — change and transport management for SAP landscapes
> *Java 8 · Spring Boot 2.7 · JdbcTemplate · SAP HANA · ABAP RFC*
>
> - Built a **transport analyzer** that finds **conflicting objects** across open requests, resolves **dependency chains** and **import evidence**, flags requests that were **changed after they went to production**, and produces a per-request readiness score with an actionable finding behind it.
> - Turned the analysis into a **release train** — what can go, in what order, and what is stopping the rest — with a generated **import checklist**, plus an **RFC-enabled ABAP function module** and **SAP Java Connector client** behind an optional Maven profile, on **portable ANSI SQL** for H2 and SAP HANA, with **65 tests**.

---

## 4. The one-bullet version

> Built a **change and transport management analyzer for SAP landscapes** (Java 8, Spring Boot, JdbcTemplate, SAP HANA, ABAP RFC) that detects conflicting objects across open transport requests, resolves dependency chains against the target system, verifies import evidence, and produces a per-system **release train** with a generated import checklist — with an RFC-enabled ABAP function module, a SAP Java Connector client and 65 automated tests.

---

## 5. Where it fits in the CV

This is the **seventh** BASIS portfolio project. The set covers seven
distinct workstreams with no overlap, which is the point — it shows range,
not seven versions of the same CRUD app.

| # | Project | Workstream |
|---|---|---|
| 1 | `sap-basis-ops-console` | Monitoring and governance analytics (Python CLI) |
| 2 | `sap-basis-operations-portal` | Operations: leave self-service and governance |
| 3 | `sap-custom-code-analyzer` | Repository analysis: where-used and custom code quality |
| 4 | `sap-interface-cockpit` | Integration: RFC, IDoc and BAPI operations |
| 5 | `sap-performance-analyzer` | Performance: ST12 and ST03 trace analysis |
| 6 | `sap-backup-readiness` | Continuity and DR: backup, restore and capacity |
| 7 | **`sap-transport-analyzer`** | **Change and transport management: conflicts, dependencies, release trains** |

Recommended CV section:

```markdown
### SAP Basis Analyst — Portfolio Projects

**SAP Transport & Change Management Analyzer** · Java 8 · Spring Boot · JdbcTemplate · SAP HANA · ABAP RFC
Built a change and transport management analyzer for SAP landscapes that detects object
conflicts across open transport requests, resolves dependency chains against the target
system, verifies import evidence and objects changed after the last transport, and produces
a per-system release train with a generated import checklist. 65 automated tests.
→ github.com/Revanth2716/sap-transport-analyzer

**SAP Backup & Restore Readiness** · ...
**SAP Performance & Workload Analyzer** · ...
```

---

## 6. Keywords

For the systems that screen on keywords:

`SAP BASIS` · `SAP NetWeaver 7.5` · `Change and Transport Management` ·
`CTS` · `Transport management` · `SE09 / SE10` · `tp` · `Transport requests` ·
`Transport of copies` · `Workbench requests` · `Customizing requests` ·
`Release management` · `Release train` · `Import checklist` ·
`Object conflicts` · `Dependency resolution` · `ABAP` · `RFC` ·
`SAP Java Connector (JCo)` · `SM59` · `SE37` · `SE11` · `E070` · `TRDIR` ·
`Java 8` · `Spring Boot` · `JdbcTemplate` · `Thymeleaf` · `SAP HANA` ·
`JDBC` · `S/4HANA` · `Git`

---

## 7. How to talk about it in an interview

### The thirty-second version

> "I built a change and transport management analyzer for SAP landscapes. The
> problem it solves is the one that breaks a transport window: two requests
> carry the same object, the second import overwrites the first, and nobody
> finds out until the bug report arrives. The tool finds those conflicts by
> object rather than by request, resolves the dependency chains against the
> target system, and then produces a **release train** — what can go, in what
> order, and what is stopping the rest — with the `tp` commands."

### The design decision worth defending

**Why is the conflict found by object rather than by request?**

> "Because the fix is to merge two requests, and the person doing the merging
> needs to know *which objects*. A list of requests that conflict is a
> puzzle; a list of objects and the two requests that hold them is a job.
> And the key has to be right: name plus type for a workbench object, name
> plus field for a customizing entry. Too coarse and you merge two compatible
> requests; too fine and you miss a real collision."

### The bug worth telling

> "The first version reported a request still in development as *blocked* on
> the release train. Blocked means released and held back — a request that is
> not released yet is simply unfinished. Collapsing the two meant the train
> showed nothing as ever importable, which is a release train nobody trusts.
> The states are separate now, and the test says so."

### The thing to be honest about

> "The ABAP and the JCo adapter are written against NetWeaver 7.5 semantics
> and are ready to compile, but I did not have a system available, so they
> have not been executed. The Java side is fully tested — 65 tests, and every
> screenshot in the repository was captured from the running application."

---

## 8. What to say if you are asked "what was the hardest part?"

> "The hardest part was the conflict key. My first version used the object
> name alone, which meant `ZCL_PAYMENT` and `ZTABLE_PAYMENT` looked like a
> conflict — and the team would have been told to merge two perfectly
> compatible requests. The fix is that a workbench object conflicts on name
> **and type**, and a customizing entry on name **and field**, because two
> requests setting different fields of the same table are not a collision.
> Getting that definition right is what makes the tool worth trusting, and it
> is much harder to reason about than the rest of the project put together."

---

## 9. Things not to claim

| Do not say | Say instead |
|---|---|
| "I built this for a customer" | "I built this as a self-initiated portfolio project" |
| "It is deployed in a production landscape" | "It runs locally against a seeded database; the live SAP path is written but not executed" |
| "It transports changes" | "It analyses what can be transported and produces the checklist; it never transports anything" |
| "It reads the transport log" | "It reads the transport requests and the import history; reading the raw `tp` log is on the roadmap" |
| "It prevents transport errors" | "It finds the conflicts before the transport window; the import itself is still `tp`'s job" |

---

## 10. Interview questions this project invites

Have an answer ready for these — they are the good ones:

* "How do you detect a transport conflict?"
* "What is the difference between a task and a workbench request?"
* "How do you decide which requests can go into a release?"
* "What happens when two requests touch the same table?"
* "How do you know a request actually reached production?"
* "What is the transport of copies, and when would you use it?"
* "How would you model the transport route DE1 → QAS → PRD?"
* "What would you change first with a real customer?"

The full answers are in [`INTERVIEW-QA.md`](INTERVIEW-QA.md).
