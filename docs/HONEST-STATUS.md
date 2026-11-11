# Honest status

What in this repository is **verified working**, what is **written but not yet
executed**, and what is **modelled rather than measured**. Read this before
describing the project in an interview.

---

## 1. Verified working today

| Item | Evidence |
|---|---|
| The application builds | `mvn clean package` → `BUILD SUCCESS`, executable JAR produced |
| The whole test suite passes | `mvn test` → **65 tests, 0 failures, 0 errors** |
| The application starts and serves | `java -jar target/sap-transport-analyzer.jar` → HTTP 200 on `/api/v1/health` |
| Every screen renders | Six screens captured from the running application into `docs/screenshots/` |
| The conflict detection works | `TransportAnalysisServiceTest` — one object in two open requests is a conflict; a request that was already imported is not |
| The conflict key is right | `TransportAnalysisServiceTest` — the key is name **and** type for a workbench object, name **and** field for a customizing entry |
| The dependency rule is right | The same test — a dependency that reached **QA** does not unblock a request going to **PRD** |
| The task rule works | A workbench request with a task still in development is held back |
| The import evidence rules work | A failed import is critical; a released request with no import record at all is high; an object changed after the import is high |
| The severity rule holds | `severityIsTheWorstFindingNotTheScoreBand` — a failed import stays critical at a score of 65 |
| The landscape tells the story | `TransportLandscapeTest` — K900002 35 critical, K900001 45 critical, K900006 30 high, K900009 100 clean, average 61, exactly one conflict |
| The release train works | Exactly one request ready, three blocked, one unfinished, two imported — and each blocked request says why |
| The checklist is usable | It names the request, the owner, the object count and the `tp` command, and the pre-flight checks name the conflicting object |
| A task never appears on the train | `tasksAreNotOnTheTrain` — a task travels inside its workbench request |
| The API works | `ApiControllerTest` — health, requests, one request, findings, conflicts, release trains, the checklist |
| The API is authenticated | Every endpoint except `/api/v1/health` returns 401 for an anonymous caller |
| Role gating works | `PageRenderTest` — a viewer is blocked from the release train, an analyst cannot release a request |
| The start-up gate works | `StartupGateTest` — the application is closed until the seeders have run |
| The log is clean | No exception or error in the application log during a full pass |

**Reproduce all of it:**

```bash
mvn clean package
java -jar target/sap-transport-analyzer.jar
```

---

## 2. Written but not yet executed on a live SAP system

| Item | Status | Why |
|---|---|---|
| `abap/Z_TRANSPORT_LIST.abap` | **Not yet compiled on NetWeaver** | No SAP system was available in the build environment. The code follows NetWeaver 7.5 / ABAP 7.40+ syntax and the Remote-Enabled flag is documented in the header, but it has not been through the ABAP syntax check. |
| `abap/Z_TR_TYPES.abap` | **Not yet created in SE11** | These are the DDIC structures the function module imports and exports. |
| `src/extra/java/.../RfcTransportGateway.java` | **Not yet compiled** | The SAP Java Connector (`com.sap.jco:sapjco3`) is published on SAP's own Maven repository, not Maven Central, and that host was unreachable from the build environment. The `with-jco` profile declares the exact coordinates needed. |
| The HANA JDBC path | **Configured, not exercised** | The `hana` profile and the datasource configuration are in place; no HANA instance was available. |
| The WAR deployment on NetWeaver 7.5 | **Designed, not executed** | `DEPLOYMENT.md` describes it; the project is built as a JAR, and the `pom.xml` change to `war` is documented but not committed. |
| `E070` / `TRDIR` field names | **Unverified against a live release** | The transport tables are stable, but the field names, the status model and the link table (`E070LIN`) differ between the classic and the new status model, and some columns exist only in newer releases. |
| The per-object change timestamp | **Not from the transport tables** | The transport tables do not hold it — it comes from the change log (`CDHDR` / `CDCLS`). The function module falls back to the request date and says so in its header. |

**What this means in an interview:** say *"the SAP-side artifacts are written
against NetWeaver 7.5 semantics and are ready to compile, but I could not run
them because I did not have a system available."*

**The plan to close the gap** is in [`DEPLOYMENT.md`](DEPLOYMENT.md): create
the DDIC structures in `SE11`, paste the function module into `SE37` with
**Remote-Enabled** ticked, create the RFC destination in `SM59`, and build the
JCo adapter against SAP's Maven repository.

---

## 3. Modelled rather than measured

| Area | What is modelled | What production needs |
|---|---|---|
| The requests | A seeded set of workbench, customizing and task requests with the classic status codes | The real requests of the real landscape, across every client |
| The object list | A seeded list of workbench and customizing objects with their conflict keys | The real object lists from `TRDIR` / `E070LIN` |
| The import history | A seeded table of import attempts | The **transport log** written by `tp` to the application server's file system — not in the database |
| The dependency edges | A seeded set of `TASK_PARENT` and `CONFIG_REFERENCE` edges | The real references, including the customizing entries that point at a workbench request |
| The landscape | Three systems, nine requests | Every system in the landscape, and every client |
| The `tp` commands | The documented request-import form | The exact options of the client's transport profile |

---

## 4. Known limitations — the ones an interviewer would find

1. **The import history is a table, not the transport log.** It is seeded, and
   a real implementation has to read the log `tp` writes on the application
   server. This is the biggest gap and it is on the roadmap.
2. **The transport of copies is not modelled.** A transport of copies behaves
   differently — it does not move the object between systems — and the
   analyzer does not distinguish it.
3. **The dependency edges are explicit.** In a live system they are derived
   from the customizing entries that reference a workbench request, and the
   derivation is not implemented in the ABAP yet.
4. **The object timestamp falls back to the request date.** The real value
   comes from the change log, and until that is read the
   "modified after import" finding uses an approximation.
5. **The schema is rebuilt on every start.** `schema.sql` drops and recreates
   every table and the seeders reload the landscape. That is predictable for a
   demo and wrong for production — migrations replace it, and
   `spring.sql.init.mode` becomes `never`.
6. **The `tp` commands are a starting point.** The options depend on the
   client's transport profile, and the checklist says so rather than pretending
   otherwise.
7. **One landscape, three systems.** There is no comparison against a previous
   release, so "this release was worse than the last one" is not a question
   the tool can answer yet.

---

## 5. What the project is not

* It is **not** connected to a live SAP system out of the box — it runs
  against the seeded dataset, and the RFC path needs a system that was not
  available.
* It is **not** a transport tool. It never moves anything. It reads what is
  there and judges whether it can go.
* It is **not** a change-management workflow. It does not plan the release,
  schedule the window or chase the approvers.
* It is **not** a replacement for SE09. It is what you read *after* SE09,
  when SE09 has shown you forty requests and told you nothing about which two
  of them are going to fight.
* It is **not** a code review tool. It sees the objects in a request, not
  what is in them.

---

## 6. Why the gaps were left open rather than faked

A portfolio project that pretends to have run against a production system is
easy to expose in an interview: the interviewer asks which system, which
client, which transport, and the story collapses. The gaps above are
specific, explained, and each one comes with the concrete step that closes
it.

The same rule applies to the data. The seeded landscape is explicitly
synthetic, and the way it is seeded — one deliberate failure mode per
request — makes it a **test fixture**, not a pretence of a customer. A
portfolio project that fabricates customer data teaches an interviewer to
check everything else in the repository.
