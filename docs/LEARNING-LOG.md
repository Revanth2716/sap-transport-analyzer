# Learning log

The decisions behind the code, and the mistakes behind some of them. Written
for the person reading the repository in six months — including me.

---

## 1. The order the work happened in

1. The schema, the domain classes and the repository — because the shape of
   the data decides the shape of everything else.
2. The rules, written as a plain method with no framework in sight.
3. The seed — and only then, the screens.

That order matters. The rules are the product; the screens are the report of
the rules. Writing the screens first produces a dashboard that shows what is
easy to show.

---

## 2. The decision that shaped the whole project

**The unit of the analysis is the object, not the request.**

The first version of the conflict detection compared requests to requests:
"does any other open request contain one of my objects?" It worked, and it
produced a list of *requests that conflict*. The person reading it then had to
open both requests and work out which object was the problem.

The fix was to build the analysis around the **object key** — name plus type
for a workbench object, name plus field for a customizing entry — and to
report the conflict per object, with the requests that hold it.

This is the same idea as a lot of good tooling: the question the user has is
"what is wrong with *this thing*", and the report should be organised around
the thing, not around the container it happens to live in.

> The general lesson: when a report is organised around the wrong unit, every
> user has to translate it back. Fix the unit, not the wording.

---

## 3. Getting the conflict key wrong, in both directions

The key has to be exactly right, and both mistakes are plausible:

| Key | What breaks |
|---|---|
| Name only | `ZCL_PAYMENT` (a class) and `ZTABLE_PAYMENT` (a table) look like a conflict. The team is told to merge two perfectly compatible requests, and stops trusting the tool |
| Name + type + field always | Two requests setting different fields of the same customizing table look like a conflict. Same outcome |

The rule that works:

- **Workbench object** → `NAME (TYPE)`. The type is part of the identity of
  a repository object.
- **Customizing entry** → `NAME.FIELD`. The field is the identity of a
  customizing entry, because that is the granularity at which a customizing
  transport conflicts.

---

## 4. "Blocked" and "unfinished" are not the same

This was a real bug, caught by the release train test.

The first version checked the blockers first, so a request whose task was
still in development came out as **BLOCKED**. On the train that reads as
"released, and something is holding it back" — which is wrong, because the
request was never released in the first place.

The visible symptom was worse than the wrong label: the train showed **one**
ready request out of seven, and three of the others were unfinished work
that nobody was ever going to release in this window. A release train that
says almost nothing can go is a train nobody trusts.

The fix is the order of the checks:

```
imported into the target?   -> IMPORTED
still in development?        -> IN_DEVELOPMENT
held back by something?      -> BLOCKED
otherwise                    -> READY
```

---

## 5. One problem, one finding

A request whose only import attempt failed produced **two** findings:

- `FAILED_IMPORT` — the import failed;
- `NO_IMPORT_EVIDENCE` — there is no record of an import.

Both were true, and saying both was saying the same thing twice. The rule
now fires only when there is **no import attempt at all**, so a request that
was never transported is still reported, and a request whose transport failed
is reported once.

> The general lesson: a rule that fires on a *superset* of another rule's
> condition will double-report. Check the conditions overlap before you add
> the rule.

---

## 6. Resolving dependencies against the target, not against "somewhere"

The first version asked: "has the dependency been imported **anywhere**?"

That made K900003 ready, because its dependency K900001 had reached QA. It
would then have gone into the transport for PRD and failed, because PRD did
not have the workbench request the configuration depends on.

The fix is to resolve the dependency against the **target system of the
request being assessed**. A dependency that reached a *different* system does
not unblock anything.

This one rule is what turns the release train from plausible into correct,
and it is the rule I would want to defend hardest in an interview.

---

## 7. The start-up gate — a bug found by the smoke test, not by a test

The application was serving a **half-built landscape** for the first few
hundred milliseconds after start-up.

Spring Boot opens the web server before the `CommandLineRunner` beans have
run. The seeders are `CommandLineRunner`s, so there is a window in which the
port is open, the database has three of nine requests in it, and the dashboard
happily renders a score computed over whatever happened to exist.

I found it because my own smoke test read the health endpoint the instant the
port opened and got `averageScore: 50` instead of 64. The right response to
that is to ask *why*, not to re-run the test.

`StartupGate` is a `CommandLineRunner` with the highest order, so it is
always the last one to run, and the interceptor answers **503** until it does.

> The general lesson: a test that asserts a number is also a test of *when*
> that number is correct. The number was right — it was just computed over
> the wrong set of data.

---

## 8. The snapshot

Conflict detection needs to know what the *other* requests hold, and the
dependency analysis needs the whole graph. Loading that per request would be
an N+1 query for every finding.

`TransportSnapshot` loads the landscape once — the requests, the objects, the
imports and the dependency edges — and builds the conflict index
(`object key → open request ids`) while it is there. Every assessment then
reads from the snapshot.

It also removes a class of bug: the analysis can no longer see the landscape
change halfway through, because there is only one view of it.

---

## 9. Decisions that were easy

* **Plain Java domain classes.** No Lombok, no records (Java 8), no
  annotations.
* **JdbcTemplate.** Decided in the first ten minutes, for portability.
* **Server-side SVG.** Decided when the first chart was drawn and the CDN
  dependency was written down. It was never tempting.
* **The seeded landscape as a story.** Nine requests, one failure mode each.
  This is what makes the demo worth looking at, and it is also what makes the
  landscape tests possible.
* **`@Order` on the seeders.** `DemoUserSeeder` 1, `TransportSeeder` 2,
  `StartupGate` 100. The order is the whole design of the start-up.

---

## 10. Things I would do differently

* **Write the landscape test first.** I wrote the rules, then the seed, then
  the landscape test. The seed was supposed to demonstrate the rules, and it
  took three rounds of adjustment before it demonstrated all of them. Writing
  `TransportLandscapeTest` first would have made the intended story explicit
  before the data was written.
* **Name the states in one place.** `READY`, `BLOCKED`, `IN_DEVELOPMENT` and
  `IMPORTED` are string constants on `ReleaseEntry`, and the blocking
  categories are a string array in `ReleaseService`. They should be one
  concept in one class.
* **Model the transport route.** The systems are a list. A real landscape
  transports along a route (DE1 → QAS → PRD), and the checklist should show
  the whole path a change has to travel, not just the last hop.
