# Deployment

From a laptop to a SAP landscape. Every step is written down, including the
parts that do not work yet.

---

## 1. The three deployment shapes

| Shape | Where it runs | How |
|---|---|---|
| **Local demo** | Your machine | `java -jar` against a seeded H2 file |
| **WAR on NetWeaver 7.5** | The NetWeaver Java stack | `mvn package` → deploy `sap-transport-analyzer.war` |
| **Standalone against SAP HANA** | Any Java 8+ host | `java -jar` with a HANA datasource |

The code is Java 8 and uses `javax.servlet` throughout, so the WAR deploys on
the NetWeaver 7.5 Java stack without changes.

---

## 2. Local demo

### Requirements

* Java 8 or newer
* Maven 3.6+

Nothing else. No SAP system, no SAP licence, no database server.

```bash
git clone https://github.com/Revanth2716/sap-transport-analyzer.git
cd sap-transport-analyzer
mvn clean package
java -jar target/sap-transport-analyzer.jar
```

Open <http://localhost:8085>.

| User | Role |
|---|---|
| `change.manager` | CHANGE_MANAGER |
| `release.analyst` | ANALYST |
| `viewer` | VIEWER |

Password: `demo1234`.

> **The application is closed until the demo data is loaded.** For the first
> few hundred milliseconds after start-up it answers HTTP 503 with *"the
> application is still starting up"* — Spring Boot opens the port before the
> seeders have run, and a half-built landscape is worse than no landscape.

### On Windows

```cmd
run.cmd            build, test and start
run.cmd test       the test suite only
```

### On Linux / macOS

```bash
./run.sh
./run.sh test
```

### Turning the demo data off

```yaml
transport:
  seed-demo-users: false   # no demo accounts
  seed-demo-data: false    # no seeded landscape
```

With both off the application starts with an empty database, which is what
you want against a real landscape.

---

## 3. Deploying as a WAR on NetWeaver 7.5

NetWeaver 7.5 runs Java 8, which is why the whole project is compiled with
`maven.compiler.source/target = 1.8`.

### 3.1 Package the WAR

The `pom.xml` declares `<packaging>jar</packaging>`. Change it to `war` for
this deployment:

```xml
<packaging>war</packaging>
```

and add the servlet container dependency that is provided by the container:

```xml
<dependency>
  <groupId>javax.servlet</groupId>
  <artifactId>javax.servlet-api</artifactId>
  <version>3.1.0</version>
  <scope>provided</scope>
</dependency>
```

### 3.2 Deploy

1. `mvn clean package` → `target/sap-transport-analyzer.war`
2. Copy it into the NetWeaver deploy directory
   (`$SAP_HOME/instance/deploy` on a standalone instance, or use the
   *Deploy Applications* transaction in the NetWeaver Administrator).
3. The context path is derived from the WAR name, so the application is
   available at `http://<host>:8000/sap-transport-analyzer/`.

### 3.3 Configuration on NetWeaver

`application.yml` on the classpath is fine for a proof of concept. For
anything real, override the datasource with a **configuration profile**:

```yaml
# application-prod.yml  (outside the WAR)
spring:
  datasource:
    url: jdbc:sap://hana-host:39015/?databaseName=PRD&currentSchema=SBODEMO
    driver-class-name: com.sap.db.jdbc.HanaDriver
    username: ${DB_USER}
    password: ${DB_PASSWORD}
```

Start the application with `--spring.profiles.active=prod` and put
`application-prod.yml` on the classpath, or set
`SPRING_CONFIG_ADDITIONAL_LOCATION`.

> **The schema is rebuilt on every start.** `schema.sql` drops and recreates
> every table and the seeders reload the landscape. That is right for a demo
> and **wrong for production**. In production, run the schema once as a
> migration and switch `spring.sql.init.mode` to `never`.

---

## 4. Pointing it at SAP HANA

The SQL is portable ANSI, so this is a configuration change.

```bash
mvn -Phana clean package
```

```yaml
spring:
  datasource:
    url: jdbc:sap://hana-host:39015/?databaseName=PRD&currentSchema=SAPCONT
    driver-class-name: com.sap.db.jdbc.HanaDriver
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  sql:
    init:
      mode: never     # the schema is a migration, not a start-up script
```

> **HANA and H2 are not identical.** The schema and the queries are ANSI, but
> a HANA schema uses upper-case identifiers and checks `VARCHAR` lengths
> strictly. Run the test suite against HANA before trusting it with a real
> landscape.

---

## 5. The live SAP path (RFC over JCo)

This is the part that reads the transport management system of a real
system.

### 5.1 Build with the SAP Java Connector

The SAP Java Connector is published on SAP's own Maven repository, not Maven
Central. You need a repository entry in `~/.m2/settings.xml`:

```xml
<profile>
  <id>sap</id>
  <repositories>
    <repository>
      <id>sap-maven</id>
      <url>https://artifacts.sap.com/artifactory/</url>
    </repository>
  </repositories>
</profile>
<activeProfiles><activeProfile>sap</activeProfile></activeProfiles>
```

then

```bash
mvn -Pwith-jco clean package
```

The `with-jco` profile adds `src/extra/java` to the source path with
`build-helper-maven-plugin` and pulls in `com.sap.jco:sapjco3`.

### 5.2 Create the ABAP artifacts

1. **SE11** — create the structures `ZTR_REQ` and `ZTR_OBJ` and the table
   types `ZTR_REQ_TAB` and `ZTR_OBJ_TAB` from `abap/Z_TR_TYPES.abap`.
2. **SE37** — create the function module `Z_TRANSPORT_LIST` from
   `abap/Z_TRANSPORT_LIST.abap`.

   > **Tick "Remote Enabled".** Without it the function module is not
   > registered for RFC, and the call fails with *"function module not found /
   > not remote enabled"*. The error surfaces as an `SM58` entry on the
   > **calling** system, not as a dump on the callee — which is what makes it
   > confusing the first time.
3. **SM59** — create the RFC destination (`SAPPRD` by default, see
   `RfcTransportGateway`). The destination name is matched **exactly**,
   including the case.
4. Check the authorisation: the RFC user needs to read `E070` and `TRDIR`. On
   a production system that is a read-only role, and the function module
   should be the only thing it can call.

> **The transport tables differ between releases.** `E070` and `TRDIR` are
> stable, but the field names, the status model and the link table
> (`E070LIN`) vary between the classic and the new status model, and some
> columns only exist in newer releases. The function module in this
> repository is written against the classic layout and has **not** been
> syntax-checked on a live system — see `HONEST-STATUS.md`.

### 5.3 Configure the gateway

```bash
cp jco.properties.example jco.properties
```

```properties
sap.applicationServer=appserver.example.com
sap.systemNumber=00
sap.client=000
sap.user=RFC_READONLY
sap.password=change-me
```

`jco.properties` is in `.gitignore`. **Never commit real credentials.**

Then switch the gateway on:

```yaml
transport:
  gateway: jco
```

### 5.4 Use a read-only RFC user

The user in `jco.properties` needs to be able to call `Z_TRANSPORT_LIST` and
nothing else. On the target system:

* create a technical user with a role that has `S_RFC` access to
  `Z_TRANSPORT_LIST` only;
* do **not** give it a dialog profile, an administrative profile or the
  `S_TCODE` rights to run anything.

---

## 6. Configuration reference

Everything under `transport:` in `application.yml`.

| Key | Default | Meaning |
|---|---|---|
| `application-name` | `SAP Transport & Change Management Analyzer` | Shown in the top bar |
| `sap-system` | `TMS` | The system shown in the top bar as a tag |
| `gateway` | `local` | `local` or `jco` |
| `seed-demo-users` | `true` | Create the three demo accounts |
| `seed-demo-data` | `true` | Load the seeded landscape |
| `demo-password` | `demo1234` | The password of the demo accounts |
| `seed.history-days` | `180` | How far back the request history goes |
| `seed.random-seed` | `20261111` | The seed of the random generator — the same data every time |
| `policy.stale-request-days` | `30` | An open request older than this is *forgotten* |
| `policy.unreleased-request-days` | `60` | A request in development for this long is a release risk |
| `policy.modified-after-import-days` | `1` | How stale the import evidence may be before the delta is flagged |
| `mail.enabled` | `false` | Send the digest by mail |
| `mail.from` / `mail.to` | — | The addresses |
| `mail.subject-prefix` | `[SAP Transport]` | The subject prefix |

### The policy numbers are the contract

They are configuration, not code, because they are the numbers a release
manager agrees with the development team. When a customer says *"requests
older than a quarter are not ours any more"*, that is
`policy.stale-request-days: 90` — not a code change and a release.

---

## 7. Scheduling

The analyzer reads the transport system; it does not transport anything. A
nightly sync that refreshes the picture is a scheduled call to the API with a
service account:

```bash
# every night, after the transport window has closed
30 2 * * * curl -s -X POST -b "JSESSIONID=$SESSION" \
  http://localhost:8085/requests/K900009/sync
```

In a real deployment this belongs in the scheduler of the NetWeaver host, or
in a job that calls the REST API with a service account.

> The sync endpoint requires a `CHANGE_MANAGER` session. For an unattended run,
> add a service account and call the API with its session cookie, or expose a
> second, machine-to-machine endpoint on a separate port.

---

## 8. Checklist before going live

- [ ] `transport.seed-demo-users` and `transport.seed-demo-data` are `false`
- [ ] `spring.sql.init.mode` is `never` and the schema is a migration
- [ ] The datasource is SAP HANA (or another real database), not H2
- [ ] The password hashes are **not** the demo ones — create real accounts
- [ ] The RFC user is read-only and limited to `Z_TRANSPORT_LIST`
- [ ] `jco.properties` is on the server and **not** in version control
- [ ] The policy numbers have been agreed with the team, not guessed
- [ ] The `tp` commands in the checklist have been checked against the
      client's transport profile — they are a starting point, not a script

---

## 9. What has not been done

No NetWeaver system, no SAP licence and no access to SAP's Maven repository
were available while this was built. The ABAP, the DDIC types and the JCo
adapter are **written against NetWeaver 7.5 semantics and are ready to
compile**, but they have not been through the ABAP syntax check and they have
not been executed.

See [`HONEST-STATUS.md`](HONEST-STATUS.md) for the full list.
