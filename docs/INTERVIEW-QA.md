# Interview questions and answers

The questions this project invites, and the answers that are actually true.

---

## 1. The domain

### "What is a transport request?"

> "A container for changes. It is the unit that travels: everything in it goes
> to the target system together, or none of it does.
>
> "There are two kinds that matter. A **workbench request** carries repository
> objects — a class, a table, an enhancement. A **customizing request** carries
> configuration. And there is a third: a **task**, which is a unit of work
> *inside* a workbench request — a developer takes a task, does the work in
> their own system, and hands it back. A task cannot be transported on its
> own; it travels inside its parent."

### "What is the difference between a workbench request and a customizing request?"

> "Different content, and — this is the part that matters — different
> dependencies. A customizing request can *reference* a workbench request: the
> configuration activates a BADI that only exists in the workbench request. So
> the customizing request cannot go into the target system before the
> workbench request it references does.
>
> "That is why the analyzer resolves dependencies against the **target system**
> and not against 'was it imported somewhere'. A workbench request that
> reached QA but not PRD does not unblock a customizing request that is going
> to PRD."

### "How do you detect a transport conflict?"

> "By the **object key**, not by the request.
>
> "Two requests conflict when they change the same thing. For a workbench
> object the key is the name **and the type** — `ZCL_PAYMENT (CLAS)`. For a
> customizing entry it is the name **and the field** — `ZPAYMENT_BADI.ACTIVE`.
> Two requests setting the same field of the same table collide; two requests
> setting different fields of one table do not.
>
> "And only *open* requests conflict. A request that was already imported is
> not a collision — its objects are in the target, and a new request changing
> the same object is normal succession."

### "Why does a conflict matter so much?"

> "Because nothing errors. The second import overwrites the first, the
> transport log says the import succeeded, and the first developer's work
> disappears. It surfaces weeks later as a bug that 'worked in development',
> because in development both changes are present.
>
> "That is why the analyzer reports it as **critical** and names both
> requests — the fix is to merge them before the window, not to debug it
> afterwards."

### "What is the transport of copies, and when would you use it?"

> "A transport of copies copies repository objects without their history, and
> it does not move them between systems — the object stays where it is and
> the copy is created. It is used when two systems need the same object but
> the development history should not travel with it, or when you need the
> object in a system without moving it out of the one it is in.
>
> "This analyzer does not model the transport of copies as a separate case —
> that is on the roadmap, and it is the honest answer rather than pretending
> the model is complete."

### "How do you know a request actually reached production?"

> "From the **import history**, not from the status. A request can be
> released, everybody believes it went out, and there is no record of it ever
> being imported — that is a real situation, and it is the one that hurts.
>
> "The analyzer treats a released request with no import record at all as a
> **high** finding, and a request whose last import *failed* as a **critical**
> one — because a failed import leaves the target system in an unknown state.
>
> "The honest limitation is that the import history comes from the transport
> log, which `tp` writes to the file system of the application server, not to
> the database. Reading the raw log is separate work."

### "What is an object that was changed after the import?"

> "The hotfix that was never taken. A request went to production two months
> ago; since then somebody changed an object in it in development. The
> production system is now running an older version of that object than the
> development system, and there is no error anywhere — the transport system
> knows about the request, and the request was imported.
>
> "The analyzer compares the object's last change with the last successful
> import and raises it as a **high** finding, because the next incident will
> be reproducible in development and not in production."

---

## 2. The design

### "Why is the severity not derived from the score?"

> "Because that is how problems hide. A failed import costs 35 points, so the
> request scores 65 — 'medium' on a score band. The import **failed**.
>
> "If the severity came from the score, the conflict that breaks the
> transport would be filed next to a stale request, because the other findings
> drag the average up. So the severity is the worst finding, always. The
> score exists only to sort thirty requests so the worst one is on top."

### "What is the difference between 'blocked' and 'in development' on your release train?"

> "Blocked means released and *held back* — a conflict, an unmet dependency, a
> task still open, or a failed import. In development means not released yet.
>
> "The first version collapsed them, and the result was a release train where
> nothing was ever importable — because a request that had not been worked on
> yet counted as blocked. That is a train nobody trusts, and it was a bug that
> only showed up when I looked at the real output."

### "How do you order the requests that can go?"

> "A topological sort of the dependency edges, so a customizing request never
> appears before the workbench request it references. Within that, oldest
> first — a change that has been waiting longest goes first.
>
> "The train is what a release manager actually works from, and the checklist
> that comes out of it has the `tp` command for each step."

### "Why do you draw the charts on the server?"

> "Two reasons. Corporate networks block outbound internet, and a chart
> library from a CDN renders as an empty box in exactly the environment where
> this has to work. And a change board has to be able to save the page and
> still see the evidence.
>
> "The release train is plain SVG — a row of coloured boxes in import order.
> It is less impressive to write than a chart library, and it works
> everywhere."

### "Why JdbcTemplate and not JPA?"

> "Portability, first. The SQL has to run unchanged on H2 for the demo and on
> SAP HANA for production, and hand-written ANSI SQL is the only way to
> guarantee that.
>
> "Second: `LIMIT` is not valid on SAP HANA. Every 'top N' query here fetches
> the rows and cuts the list in Java. That was a deliberate constraint from
> the start."

---

## 3. The implementation

### "Walk me through the request path for a request detail screen."

> "The interceptor checks the start-up gate, the session and the role. The
> controller asks the analysis service for the assessment of that one
> request. The service loads the whole landscape once into a snapshot — the
> requests, the objects, the imports and the dependency edges — because
> conflict detection needs to know what the *other* requests hold, and
> loading that per request would be an N+1 query per finding.
>
> "Then the rules run, the findings are produced, the controller puts it in
> the model and the view renders it. There is no client-side JavaScript
> anywhere in the path."

### "How do you test a scoring rule?"

> "One scenario per test, with the repository mocked so that the test
> contains exactly the situation it is about — a conflict and nothing else, a
> dependency that reached the wrong system and nothing else. When a test fails
> it names the rule that broke.
>
> "On top of that there is a test against the whole seeded landscape, because
> the seed has to tell a specific story: one request that can go, three that
> are blocked, one unfinished, two already in production, and one conflict."

### "What is the `PageRenderTest` for, and why is it in every project?"

> "A template can compile, the service layer can be fully green, and one
> screen can still return HTTP 500 because a single expression in the markup
> does not resolve. Nothing else in the suite would catch that — the service
> tests never touch the templates."

### "What is the start-up gate?"

> "Spring Boot opens the web server before the `CommandLineRunner` beans have
> run. So for the first few hundred milliseconds the application serves a
> half-built landscape — a dashboard showing three requests instead of nine.
>
> "I found it because my own smoke test read the health endpoint the instant
> the port opened and got an average score computed over three requests
> instead of nine. The gate now holds the application closed until the
> seeders have finished, and answers 503 until then. A load balancer that
> probes the application the moment it starts should see 'not ready', not an
> empty dashboard."

---

## 4. The SAP side

### "How does the live path work?"

> "Two artifacts. The ABAP function module `Z_TRANSPORT_LIST` reads the
> request headers from `E070` and the object lists from `TRDIR`. The JCo
> adapter calls it over RFC through a destination maintained in `SM59`.
>
> "The important design point is the interface. `TransportGateway` is the
> boundary; the local implementation reads the database, the RFC
> implementation calls the function module. Nothing above the interface knows
> that RFC exists."

### "What goes wrong first with RFC?"

> "The **Remote Enabled** flag. If the function module is created in `SE37`
> without it, the call fails with 'function module not found / not remote
> enabled'. And the error appears as an `SM58` entry on the **calling**
> system, not as a dump on the callee — so the system you are looking at is
> the one that looks fine."

### "Why is the JCo adapter not in the main source path?"

> "Because the SAP Java Connector is published on SAP's own Maven
> repository, not Maven Central. If it were in the main build, `mvn package`
> would fail for anyone without access to SAP's repository — including every
> recruiter and every person who clones the repository to look at it. It lives
> in `src/extra/java` and is added by the `with-jco` profile, so the default
> build compiles and tests anywhere, with no licence and no SAP access."

### "What does the function module deliberately not do?"

> "Decide whether a request may be imported. The database knows what was
> transported; only the analyzer knows what conflicts with what. And it does
> not read the transport log — that lives in the file system of the
> application server, not in the database."

---

## 5. The honest questions

### "Has this run against a real SAP system?"

> "No. The Java side runs and is tested — 65 tests, and every screenshot in
> the repository was captured from the running application. The ABAP, the DDIC
> types and the JCo adapter are written against NetWeaver 7.5 semantics and
> are ready to compile, but there was no NetWeaver system and no access to
> SAP's Maven repository in the build environment, so they have not been
> executed. The repository says so in `HONEST-STATUS.md`, and I would rather
> say it here than have it come out in an interview."

### "Where does the data come from then?"

> "It is a seeded synthetic landscape, and the seed is deliberately a *story*
> rather than noise. Nine requests, each with one specific failure mode: a
> shared object between two open requests, a customizing request blocked
> because its dependency only reached QA, a task that has been in development
> for 70 days, an object changed ten days after the transport went out, and
> one request that is clean and ready. The dates are relative to the current
> time, so the findings are real findings at the moment the application runs."

### "What would you change first with a real customer?"

> "The import evidence. In this project it is a seeded table. In a real
> landscape it is the transport log that `tp` writes to the application
> server's file system, and reading that is the difference between a report
> and a tool.
>
> "Second, the per-object change timestamp. The transport tables do not hold
> it — it comes from the change log (`CDHDR` / `CDCLS`) — and right now the
> ABAP falls back to the request date, which is a documented compromise
> rather than a solution."

### "What would you not build?"

> "I would not build the import itself into this tool. It analyses what can
> go and produces the checklist; the transport is still `tp`'s job, and a
> tool that decides to transport something at 02:00 on a Sunday is a tool
> people stop trusting."

---

## 6. Questions to ask them back

* "How do you currently find out that two requests hold the same object?"
* "What happens when a transport fails halfway through?"
* "How do you know which requests went into the last release?"
* "Who owns the transport queue — the BASIS team, the development team, or
  the release manager?"
* "How do you handle a hotfix that has to skip the normal route?"
* "How long does a release window normally take, and who is in it?"
