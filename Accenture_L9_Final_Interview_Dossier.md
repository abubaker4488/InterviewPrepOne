# Accenture L9 — Final / Techno-Managerial Interview War-Room Dossier

**Candidate:** Abubaker Siddique · Java / Spring Boot backend engineer · ~5.5 years
**Target:** Accenture · L9 · Custom Software Engineer (must-have: Spring Boot) · Final / Techno-Managerial round
**Primary project:** MBS billing platform at Lumen Technologies (Nov 2023 – May 2026)

---

## How to use this document

1. **Read Part 0 first.** It fixes the one story you tell, and lists the claims from older prep material that you must stop making.
2. **Learn Parts 2 and 4 until you can say them without notes.** The project story and Bill Rerun are where this round is won or lost.
3. **Skim Parts 5–14 for gaps.** They're concept refreshers tied back to your project.
4. **Rehearse Parts 15–18 out loud.** Managerial and "challenge my experience" questions are about delivery, not memorized facts.
5. **Night before:** Part 20 (revision sheet plus rapid-fire) and Part 21 (Top 30).

**Contents:** Part 0 One story · 1 Interview map · 2 Master story · 3 Project deep-dive (Q1–Q14) · **4 Bill Rerun (Q15–Q35)** · 5 Spring Boot (Q36–Q54) · 6 Java (Q55–Q65) · 7 REST & microservices (Q66–Q75) · 8 Database (Q76–Q85) · 9 System design (SD1–SD4) · 10 CI/CD & production (Q86–Q92) · 11 Security (Q93–Q98) · 12 Testing (Q99–Q102) · 13 Cloud (Q103–Q105) · 14 Frontend (Q106–Q107) · 15 Techno-managerial (M1–M26) · 16 Scenarios (S1–S18) · 17 Challenge my experience (C1–C15) · 18 Follow-up trees · 19 Questions to ask · 20 Revision sheet + rapid-fire · **21 Top 30**

Priority tags: **MUST PREPARE** · **SHOULD PREPARE** · **AWARENESS**.
Evidence tags used in project answers: **[Code]** = seen in the source code or git history · **[Stated]** = from your own resume or earlier notes · **[Inference]** = consistent with the evidence and normal practice, so phrase it as "my understanding is…".

---

# PART 0 — THE ONE STORY (decisions made for you)

The repository contains several versions of your project, and some of them contradict each other. I picked one version for each topic using this order: code evidence first, then detailed documentation, then AI summaries. **Use only these versions.**

## 0.1 Conflicts resolved

| Topic | Conflicting versions in the repo | **Version to use** | Why |
|---|---|---|---|
| What Bill Rerun does | HLD notes: "resumes from the failed stage, skips stages that succeeded" | **Reset and recompute.** Rerun deletes everything computed for that cycle (system or customer), reverts usage/payment/control flags, and hands the cycle back to the normal billing batch, which recomputes it from scratch. | The actual `BatchBillRerunServiceImpl` / `BatchBillRerunHelper` code does exactly this. Stage flags *gate* the normal pipeline; rerun deliberately *resets* them. |
| Status flag names | HLD: `bill_tax_hold`, `bill_release`, `bill_media` on BILL_CONTROL | **Two tables.** `BILLPULL_DETAIL` (per system + bill date): `BILL_RUN_INDR`, `BILL_REL_INDR`, `BILL_MEDIA_INDR`, `BILL_RERUN_INDR`. `BILL_CONTROL` (per customer + bill date + system): `BILL_COMPLETE`, `BILL_TYPE`, `RATING_HOLD`, `BILLING_HOLD`, `TAXING_HOLD`, `FORMATTING_HOLD`. | Entity classes in the code. |
| Meaning of `BILL_REL_INDR` | HLD: "Y = fully completed, excluded from future runs" | **Y = the cycle is released/open for the billing run.** The batch picks cycles with `RUN_INDR='N' AND REL_INDR='Y'`. | Batch repository queries. |
| Microservices? | Some notes say "microservices" | **"A multi-application Spring Boot platform"** (core API, operations GUI, ATC app, legacy batch engine) sharing an Oracle DB and integrating over REST. Not fine-grained microservices. | Separate WARs, shared DB. Calling it microservices falls apart after the first follow-up. |
| Tax engine | "Vertex" vs "CGS" | **An external tax service (CGS)** called by address/zip code. Vertex-style tax categories are mapped internally. | `CgsAdapter`, `VertexTaxCatRepository`. |
| How BRIM data reaches MBS | "Kafka + Python validator" vs "REST" | **Both, in different directions.** Inbound payment/adjustment data came through a Kafka-based pipeline with a validator **owned by a teammate**, then into MBS APIs. Outbound charge/tax feeds to BRIM are built by MBS services. | HLD (first-hand) + code. You owned neither the Kafka consumer nor the validator. |
| ATC | "Account & Teams Configuration", JSON-Patch PATCH endpoint, @Version, Envers | **ATC = Aid To Construction.** One composite `POST /{projectDataId}/edit` endpoint, `TransactionTemplate`, MBS sync *after* commit. | Code. The JSON-Patch version is invented. |
| Query-timeout story | "38s → 1.2s, 50M rows, composite index, JMeter, AppDynamics" | **A JPQL refactor of the Bill Preview query** (`getUnbilledCarrierDetails`): tighter state filters, `DISTINCT`, predictable ordering. **No numbers.** | Git commit `e8518ec`. The numbers appear nowhere in the evidence. |
| Deployment | "Helm, HPA, ArgoCD, Flyway" | **GUI:** Jenkins → Maven → SonarQube/JaCoCo → Docker (Tomcat 9) → Rancher-managed Kubernetes. **API:** WAR on Tomcat servers. **ATC:** GitHub Actions → build → SSH → WAR to Tomcat. | Jenkinsfile, k8s templates, `cicd.yml`. The Helm/HPA/ArgoCD version is invented. |

## 0.2 Claims to STOP making (from the older prep files)

These come from `Intv-QnA-Self-Exp.txt` and similar AI-generated drafts. **None of them is supported, and several contradict the code.** One of these in front of a senior interviewer can sink the whole round.

- ❌ Any made-up number: "38s → 1.2s", "85% fewer errors", "70% less reconciliation", "8x throughput", "<0.1% defects", "juniors handled 80% of bugs", "200+ agents", "10K IDocs/hr".
- ❌ "I designed the canonical SAP IDoc model, MapStruct mappers, XSD validation, and a Kafka DLQ."
- ❌ "Dual-write for 2 weeks with a reconciliation job every 15 minutes."
- ❌ "JSON Patch (RFC 6902), optimistic locking with @Version, Hibernate Envers" (ATC).
- ❌ "Helm charts, HPA at 60% CPU, ArgoCD canary, Flyway."
- ❌ "I ran brown-bag sessions for 15 developers" / "I mentored 3 juniors" (only if it actually happened).
- ❌ "I architected MBS" / "designed the schema" / "led a team of X".
- ❌ "Bill Rerun resumes from the failed stage."
- ❌ "We used Resilience4j circuit breakers / distributed tracing." Not evidenced; talk about these as concepts.

## 0.3 Your ownership statement (use it word for word when asked "what exactly did you do?")

> "MBS was an existing platform when I joined; I didn't architect it. I was a backend developer on the team, working mainly in the Spring Boot API. My clearest ownership was the **Bill Rerun workflow**: when billing batch logic moved from the legacy batch engine into the Spring Boot API, I stabilized the rerun service. I restructured it to process each bill cycle independently, return a clear success / partial / failure result, and clean up dependent data it had been leaving behind. I also worked on the **BRIM charge-feed mapping** (legacy product codes and cost-center validation for the S4 migration), fixed queries and filters on the GUI's **Bill Preview and Bill Process** screens, fixed a **log-injection** issue in the GUI, and on the **ATC** application consolidated the project-edit flow into a single API and worked on its **GitHub Actions pipeline**. Platform architecture, the database schema and the Kafka ingestion were owned by others."

Commit/ticket evidence behind this: `8fedd4b`, `b21683a`, `3f63398`, `12e841f` (Bill Rerun cluster, CPPEWMB-8599); `CPPEWMB-5443` (BRIM legacy product codes, 2024); `e8518ec`, `8e90e0e`, `36da477` (GUI queries/filters); `bb4e684` (log injection); `6cc89873` + `cicd.yml` (ATC).

## 0.4 Five-minute fact check before the interview (only you know these)

Fill these in with the truth. **If you don't know one, the answer is "I don't remember exactly", never a guess.**

1. What "MBS" stands for at Lumen (sources disagree: *Miscellaneous* vs *Managed* Billing System). If unsure, just say "MBS, Lumen's billing platform".
2. Team size and shape (lead? how many devs? QA? onshore counterpart?).
3. **How the rerun API was actually invoked** after the agent flagged a cycle: by the scheduled batch run for the system, or manually by support? (The code supports either.)
4. Why the 7-day filter on the Bill Process tab was removed (`36da477`). Was it user feedback?
5. Was the log-injection issue found by a scanner (CodeQL/Checkmarx/Sonar) or in code review?
6. Did you have production access, or did you work through logs and ops/DBA teams?
7. Where the "~7,000 enterprise customers / ~$30M annual revenue" figures came from (use them only if they're on your resume and you can say "those are business figures from the team").
8. What happened after May 2026 (your gap explanation, see Q-M16).
9. Details of your employment before Lumen (RECV fintech, TCS retail) as they appear on your resume.

---

# PART 1 — FINAL INTERVIEW MAP

## 1.1 What an Accenture L9 techno-managerial panel is really assessing

Accenture L9 is typically a **Team Lead / Consultant** level: the person who owns a module, guides 3–8 engineers, talks to client stakeholders, and can be trusted with delivery. The final round is usually led by a Senior Manager or Associate Director. They check five things:

| Dimension | What they want to see | How you show it |
|---|---|---|
| **Technical depth** | You understand what you built, not just what you used | Bill Rerun internals, @Transactional pitfalls, JPA, query tuning |
| **Ownership** | You drove something to production and stood behind it | Bill Rerun stabilization: problem → design → result → what you'd improve |
| **Problem solving** | A structured approach under ambiguity and pressure | Production triage framework (Part 10), scenario answers (Part 16) |
| **Architecture / decisions** | You can compare options and name trade-offs | Reset-vs-resume, 206 vs 200, TransactionTemplate vs @Transactional, manual vs automatic rerun |
| **Collaboration and leadership** | You can guide others, handle conflict, work across teams | Feature-level leadership, code reviews, cross-team BRIM/GUI coordination, KT |
| **Client orientation** | You protect the client's business outcome | Billing correctness ahead of speed; communicating risk to billing ops |
| **Communication** | Clear, structured and honest | Layered answers (30s → 2min), no inflation, "I'd verify" when unsure |
| **Fit for Accenture delivery** | Flexibility, learning agility, working within process | Agile ceremonies, estimation, quality gates, certifications (AZ-900) |

## 1.2 Priority table

| Area | Why it matters | Expected depth | Priority | Evidence from your experience |
|---|---|---|---|---|
| Project story and your role | First question, and it frames everything after | Explain at 30s / 2min / 5min; defend every sentence | **MUST** | MBS at Lumen [Code + Stated] |
| Bill Rerun | Your strongest ownership claim | 10-minute drill-down | **MUST** | `BatchBillRerunServiceImpl`, `BillRerunResult`, `BatchInvoiceController` [Code] |
| Spring Boot core / MVC / @Transactional | JD must-have | Internals + pitfalls + production | **MUST** | API on Boot 2.5, ATC on Boot 3.2 [Code] |
| JPA / Hibernate / SQL (Oracle) | Billing data is relational; the tuning story | Persistence context, bulk updates, indexes, locking | **MUST** | Repositories, `@Modifying`, TRIM/TRUNC queries [Code] |
| REST API design | JD skill; your rerun endpoints | Status codes, idempotency, errors, versioning | **MUST** | Rerun endpoints, Swagger annotations [Code] |
| Production troubleshooting | Senior engineers are judged on incidents | A method plus real examples | **MUST** | Rerun failures, stale AssignDetails, session mismatch [Code] |
| Techno-managerial scenarios | The purpose of this round | STAR with concrete actions | **MUST** | Feature-level leadership, cross-team work [Stated] |
| Microservices and resilience | JD skill | Concepts + honest mapping to your multi-app platform | **SHOULD** | Multi-app REST integration [Code]; patterns are conceptual |
| System design | L9 expectation | 1 domain design end-to-end + scaling questions | **SHOULD** | Billing pipeline [Code] |
| CI/CD, Docker, Kubernetes | JD mentions deployment | Pipeline stages, pod failures | **SHOULD** | Jenkins → Rancher K8s (GUI), GitHub Actions (ATC) [Code] |
| Security | Every senior round touches it | AuthN/AuthZ, injection, secrets | **SHOULD** | LDAP + session filter (GUI), role-based access commit, log-injection fix [Code]; JWT at RECV [Stated] |
| Java core and concurrency | Always asked | Collections, concurrency, JVM basics | **SHOULD** | Java 8 (API), Java 17 (ATC) [Code] |
| Testing and code quality | Lead-level expectation | Strategy, not trivia | **SHOULD** | Sonar/JaCoCo gates in GUI pipeline [Code]; JUnit/Mockito [Stated] |
| Cloud (AWS/Azure) | In JD, not your core | Concepts + honest positioning | **AWARENESS** | AZ-900 [Stated]; Rancher clusters on Azure-named credentials [Code, platform-managed] |
| React / Angular | In JD | Basic React; honest about Angular | **AWARENESS** | ATC React changes for the edit flow [Code]; frontend was largely AI-assisted [Stated] |

---

# PART 2 — MY PROJECT MASTER STORY

> Rule: **every sentence here survives three follow-ups.** No metrics except the ones on your resume, and only if you can source them.

## 2.1 Thirty-second version

> "For the last two and a half years I was at Lumen Technologies on **MBS**, their enterprise billing platform for telecom customers. It runs a monthly billing pipeline per subsystem: rating usage, one-time charges, taxes through an external tax service, applying payments and adjustments, then generating invoices. It also integrates with SAP BRIM for finance. I was a backend developer on the Spring Boot API. My main ownership was stabilizing the **Bill Rerun** workflow, which lets billing operations safely recompute a bill cycle before invoices are released."

## 2.2 Sixty-second version

> "MBS is Lumen's billing platform for enterprise and carrier customers. Customers are partitioned into **subsystems**, each with its own billing cycle date. For each cycle, a batch computes the bill in stages: rating usage and one-time charges against product rates, billing (applying payments and adjustments), tax from an external tax API by service address, then formatting the PDF invoice. Expenses and their voucher approvals run on a side track. Billing agents review the results before release.
>
> Technically it's a set of Spring Boot applications on a shared **Oracle** database: a core REST API that holds the billing logic, a JSP-based operations GUI for billing agents, a newer React + Spring Boot app called ATC for construction-project billing, and a legacy batch engine that was being migrated into the API.
>
> I worked mainly in the API. I owned the **Bill Rerun** service: making reruns reliable, making partial failures visible, and cleaning up data left behind. I also worked on BRIM charge-feed mapping, GUI query fixes, a security fix, and the ATC edit API and its CI/CD pipeline."

## 2.3 Two-minute version

> "**Business problem:** Lumen bills enterprise and carrier customers every month, and these bills are revenue. A wrong bill costs more than a late one: it means disputes, credits and audit problems. So the system is built around correctness and human checkpoints.
>
> **How it works:** Customers belong to subsystems, each with a bill cycle date. For a cycle, the batch picks the customers eligible in a control table and runs the stages: **rating** (usage × rate by product code into BILL_COMPUTE), **billing** (payments and adjustments applied, balance history updated), **taxing** (an external tax service called with the service address and zip code), and **formatting** (the PDF invoice, with totals stored in BILL_INV_FILE). One-time charges and expenses run on a side track, with an approval workflow for vouchers above a user's dollar limit. Agents review the output in the GUI before invoices are released to delivery and downstream finance.
>
> **Architecture:** Spring Boot REST API (Java 8, WAR on Tomcat) with the billing logic; a JSP GUI for billing agents with LDAP login; ATC, a React + Spring Boot 3 app; all on Oracle. Integrations: SAP BRIM for finance feeds, the external tax service, SAP S4 for cost-center validation, and an internal directory database for approval hierarchies.
>
> **My role:** backend developer in the API. My main piece was **Bill Rerun**. When agents find a bad cycle before release, rerun wipes that cycle's computed data and returns it to the billing batch. It had been unreliable: a multi-cycle rerun gave agents no clear picture of what succeeded, and it left some dependent records behind. I restructured it to process cycles independently, return **success / partial / failure** with the exact failed dates, clean up the missed dependent data, and log a per-table summary. That removed the recurring manual follow-ups agents had been doing.
>
> I also handled BRIM charge-mapping changes, GUI query fixes, a log-injection fix, and in ATC the single edit API and pipeline work."

## 2.4 Five-minute deep-dive version (structure; expand each point from Part 3/4)

1. **Domain (30s):** enterprise telecom billing; subsystems; monthly cycles; correctness over speed; agent review before release.
2. **Pipeline (60s):** control tables decide who is billed → rating → billing (payments/adjustments) → tax (external) → formatting → agent review → media/downstream release, with OCC/expense + voucher approval as a side track. Name the tables: CURR_USG, PRODUCT_SERV_RATE → BILL_COMPUTE; EXPENSE_COMPUTE; TAX_COMPUTE; BILL_PAYMENT / BILL_ADJUSTMENTS → BILL_BALHISTORY; BILL_INV_FILE.
3. **Control model (45s):** `BILLPULL_DETAIL` holds the state of a cycle for a system (run / release / media / rerun). `BILL_CONTROL` holds per-customer stage completion. Each batch stage only picks customers whose earlier stages are complete, so a half-computed bill never gets formatted.
4. **Architecture (45s):** API / GUI / ATC / legacy batch; shared Oracle; REST between GUI and API; external BRIM, tax, S4, LDAP; GUI on Kubernetes through Jenkins; API on Tomcat servers.
5. **My work (90s):** Bill Rerun in depth (Part 4); then one line each on BRIM mapping, GUI query fix, log injection, ATC edit consolidation, ATC pipeline.
6. **Challenges and decisions (30s):** reset-and-recompute vs patching; per-cycle isolation vs fail-fast; explicit partial status; human-in-the-loop instead of automatic reruns.
7. **What I'd improve (30s):** explicit per-cycle transaction boundary, concurrency guard on the rerun flag, structured JSON response instead of text, metrics and dashboards instead of email-only alerts, and the automated invoice-vs-data reconciliation that was starting when I left.

## 2.5 Your career arc (for "walk me through your resume")

> "About five and a half years in Java backend work. Most recently about two and a half years at Lumen on the MBS billing platform. Before that, fintech work building a Merchant Cash Advance underwriting backend (Spring Boot, JWT security, PostgreSQL), and earlier at TCS on an event-driven retail inventory platform using Kafka. The common thread is backend services where data correctness matters: money, stock, bills."

*Keep prior roles to what your resume says. If probed on Kafka at TCS or JWT at RECV, answer at the depth you truly worked. Those roles aren't in this repo, so this document doesn't invent details for them.*

---

# PART 3 — PROJECT DEEP-DIVE Q&A

### Q1: Explain the architecture of your project.
**Priority:** MUST PREPARE
**Why they may ask:** It's the standard opener after "tell me about your project".
**What they are testing:** Whether you see the whole system, and whether you inflate it.
**Answer:**
"It's a multi-application platform on one Oracle database. The **core API** is Spring Boot 2.5 on Java 8, deployed as a WAR on Tomcat, and holds the billing and payment logic as REST endpoints, including the batch stages: rating, billing, taxing, formatting and rerun. The **GUI** is an older Spring Boot + JSP app used by billing agents. It authenticates against LDAP, calls the API over REST, and reads some data directly from the DB. **ATC** is a newer React + Spring Boot 3 / Java 17 app for construction-project billing that calls MBS APIs. A **legacy batch engine** (plain Hibernate, a JAR) was being migrated into the API during my time. External systems: SAP BRIM for finance, an external tax service, SAP S4 for GL and cost-center validation, and a directory database for approval hierarchies.
I'd call it a **service-oriented, multi-application system rather than microservices**: the apps deploy separately but share a database."
**Project connection:** You worked mostly in the API; also the GUI (queries, security) and ATC (edit API, pipeline).
**Likely follow-ups:**
- Why not microservices? → Q69
- Why a shared DB? What are the downsides? → coupling, schema changes need coordination, and one app's slow query can starve the others' connections. The upside was simplicity and strong consistency for a billing domain.
- Where would it break at 10x? → Part 9
**Defensible detail:** App versions, WAR on Tomcat, Oracle, LDAP, the external systems.
**Avoid saying:** "Microservices architecture I designed"; "cloud-native".

### Q2: Walk me through the billing flow end to end.
**Priority:** MUST PREPARE
**Why they may ask:** To test domain understanding.
**What they are testing:** Whether you can explain a business flow through data.
**Answer:**
"Per subsystem and bill cycle date:
1. **Eligibility.** The cycle row in `BILLPULL_DETAIL` is released (`REL_INDR='Y'`) and not yet run. Customers for that date are listed in `BILL_CONTROL` with `BILL_COMPLETE='N'`.
2. **Rating.** Usage records in `CURR_USG` for the customer's usage window are multiplied by rates in `PRODUCT_SERV_RATE` by product code, giving `BILL_COMPUTE`. Usage rows are marked processed so they aren't rated twice.
3. **Side track: expenses.** Expense lines give `EXPENSE_COMPUTE` and an `EXPENSE_VOUCHER`. Vouchers are routed for approval, and the approver is found by walking the manager hierarchy until someone's dollar limit covers the amount.
4. **Billing.** Payments (`BILL_PAYMENT`) and adjustments (`BILL_ADJUSTMENTS`) are applied, updating `BILL_BALHISTORY`.
5. **Taxing.** Product address and zip go to the external tax service, and results land in `TAX_COMPUTE`.
6. **Formatting.** The PDF invoice is generated, and totals are stored in `BILL_INV_FILE`. Only customers whose earlier stages are complete are formatted.
7. **Review and release.** Agents review. If something's wrong, they flag a **rerun**; otherwise the invoices are released to media/delivery and downstream feeds."
**Project connection:** Rerun sits at step 7 (Part 4).
**Likely follow-ups:** How do you avoid rating usage twice? (usage process indicator) · What if the tax service fails? (Q10) · Who approves vouchers? (hierarchy + limit)
**Defensible detail:** Table names are from the data-flow diagram and code.
**Avoid saying:** Exact volumes or timings.

### Q3: What exactly did YOU work on?
**Priority:** MUST PREPARE
**Answer:** Use **Part 0.3** word for word, then offer: "Happy to go deep on Bill Rerun. That's the piece I know best."
**What they are testing:** Honest separation of "I" and "we".
**Likely follow-ups:** "Which of these did you design vs. implement?" → "Within Bill Rerun I designed the result contract and the per-cycle processing approach and implemented them, reviewed with my lead. The platform architecture, batch stages and schema existed before me."
**Avoid saying:** "We built…" for things you didn't touch; "I led…" without a team.

### Q4: What were your day-to-day responsibilities?
**Priority:** MUST PREPARE
**Answer:**
"Picking up stories and defects in the API and GUI; implementing and unit-testing them; code reviews with my lead; supporting QA and UAT with billing ops; investigating batch or rerun issues from logs and DB state during billing windows; and deployment support and pipeline changes on ATC. In Agile terms: sprint planning, estimation, stand-ups, demos."
**What they are testing:** Realistic scope for 5+ years.
**Follow-ups:** How did you estimate? What was your definition of done?
**Avoid saying:** Things you didn't do (release management, people management) unless true.

### Q5: Which APIs did you build or change? Walk me through one.
**Priority:** MUST PREPARE
**Answer:**
"The rerun endpoints in `BatchInvoiceController`:
- `POST /batch-invoice/rerun-billing/system?systemName=…` reruns every flagged cycle for that subsystem.
- `POST /batch-invoice/rerun-billing/customer?customerId=…&billDate=…&systemName=…` reruns one customer.

The flow: validate input (missing system → **400**; bad date format → **400** with the expected format). For the customer path, check eligibility (the cycle must be flagged for rerun on that date; otherwise **200** with 'no eligible cycle'). Call the service, which returns a `BillRerunResult` (successful cycles, failed cycles, error flag). Map it to **200** when everything succeeded, **206** when partial, **500** when everything failed, with a message naming the exact dates. They're documented with OpenAPI annotations."
**Likely follow-ups:** Why 206? (Q24) · Why POST and not PUT? (Q25) · Is it idempotent? (Q22)
**Defensible detail:** Endpoint paths, parameters and status mapping are in the code.
**Avoid saying:** "It returns a JSON DTO". It returns a text message today, and that's one of your improvement points.

### Q6: Explain the database design you worked with.
**Priority:** MUST PREPARE
**Answer:**
"Oracle, with the schema owned by DBAs, so `ddl-auto` was off. Most tables use **composite keys that include the system name**. For example `BILL_CONTROL` is keyed on customer ID + bill pull date + system name, mapped in JPA with `@IdClass`. That's how one schema serves multiple subsystems. The main groups are customer master (`CUSTOMER_DETAIL`), usage and rates, compute tables (bill, expense, tax), invoice (`BILL_INV_FILE`), balance history, payments and adjustments, and the control tables (`BILLPULL_DETAIL` for the cycle, `BILL_CONTROL` per customer). Repositories were Spring Data JPA with a lot of custom JPQL and some native SQL."
**Likely follow-ups:** `@IdClass` vs `@EmbeddedId`? (Q51) · Why composite keys? · What indexing problems did you see? (Q77, the TRIM issue)
**Avoid saying:** "I designed the schema."

### Q7: How does authentication and authorization work in your project?
**Priority:** SHOULD PREPARE
**Answer:**
"In the GUI, users log in with their corporate ID. The app does an LDAP bind over LDAPS to Active Directory and stores the user in the HTTP session, and a servlet filter rejects requests without a valid session with 401. Authorization is role-based on screens: I added role-based access plus sorting to the Bill Process tab. Service-to-service, ATC calls the MBS API with **OAuth2 bearer tokens** (client-credentials), and external systems use their own auth: OAuth2 client credentials for the tax service, Basic auth for some BRIM endpoints.
For stateless JWT auth I have hands-on experience from my previous fintech project (Spring Security filter, BCrypt, role-based access)."
**Likely follow-ups:** Session vs JWT trade-off (Q93) · How would you secure the rerun endpoint? (role check plus audit of who triggered it)
**Avoid saying:** "We used JWT in MBS."

### Q8: What external integrations exist, and which did you touch?
**Priority:** MUST PREPARE
**Answer:**
"SAP **BRIM** for finance (charges, accruals, payments, write-offs); the external **tax service** (by address, OAuth2); **SAP S4** validation for GL, cost center and WBS; the **directory DB** for approval hierarchy; LDAP; SMTP; and ActiveMQ from the GUI. I worked on the **BRIM charge-feed mapping**: handling legacy long-distance product codes differently for interstate and intrastate, looking up the revenue accounting code, and validating cost centers against the new S4 mapping. If the cost center can't be mapped, that charge isn't sent, rather than sending bad finance data. The Kafka consumer and validation for inbound BRIM payment data were owned by a teammate. I worked downstream of that, on the MBS side."
**Likely follow-ups:** What if BRIM is down? · How do you prevent duplicate payments? (Part 9, SD2) · Why reject instead of defaulting a cost center? ("finance data correctness; a wrong GL posting costs more than a delayed one")
**Defensible detail:** CPPEWMB-5443 is tagged with your ID in the charge service.

### Q9: How is configuration managed across environments?
**Priority:** SHOULD PREPARE
**Answer:**
"Spring profiles and environment-specific property files (dev, test, pre-prod, prod). In Kubernetes the GUI gets `SPRING_PROFILES_ACTIVE` per environment. Some values are **database-driven at runtime**: for example log levels are read from a config table by a dynamic logger, so we could raise logging during a billing window without redeploying. ATC also cached app properties from a DB table. Secrets in the ATC pipeline came from the CI secret store."
**Likely follow-ups:** How do you handle secrets properly? (Q96) · Downside of DB-driven config? (auditability, cache staleness, needs change control)
**Avoid saying:** "We used Spring Cloud Config / Vault" (not evidenced).

### Q10: What happens if the tax service is down during a billing run?
**Priority:** MUST PREPARE
**Answer:**
"The customer's taxing stage doesn't complete, so that customer isn't marked complete. Formatting only picks customers whose earlier stages are complete, so no invoice is generated with missing tax. When the service is back, the stage is re-run for the incomplete customers. That's the design principle: **a delayed bill is acceptable, a wrong bill isn't.** If I were hardening it, I'd add explicit timeouts, a retry with backoff for transient errors, a circuit breaker so we don't hammer a dead service across thousands of customers, and an alert on the number of customers stuck in taxing."
**What they are testing:** Failure thinking.
**Follow-ups:** How would you size timeouts? · Would you cache tax rates? (Part 9, SD3)
**Defensible detail:** Formatting eligibility requires earlier stage flags to be complete [Code]. Retry/circuit breaker are **proposals**, not claims.

### Q11: How did you do logging and monitoring?
**Priority:** MUST PREPARE
**Answer:**
"Mostly structured application logs plus alerts. In the rerun flow I log the start and end with durations, the counts it found, and a **summary of rows deleted or updated per table**, so without DB access you can see exactly what a rerun did. Failures send an **email alert** to the billing distribution list. Log levels can be raised at runtime through the DB-driven logger. Connection-leak detection was turned on in the Hikari pool. Honestly, observability was log- and email-centric. I'd add Actuator + Micrometer metrics (rerun count, failures, duration), dashboards, and correlation IDs."
**Follow-ups:** How would you trace one request across GUI → API? (Q73) · What alerts would you add?
**Avoid saying:** "We used AppDynamics/Splunk dashboards" unless you did.

### Q12: What was your production support involvement?
**Priority:** MUST PREPARE
**Answer:**
"During billing windows I supported the batch and rerun flows: an agent reports a cycle that failed or looks wrong, I check the logs for that system and date, check `BILLPULL_DETAIL` and `BILL_CONTROL` flags to see where the cycle is stuck, work out whether it's data (missing rate, bad address), environment (DB, tax service) or code, and either guide a rerun or fix the defect. Fixes went through the normal pipeline; hotfixes followed the release process with lead approval."
**Follow-ups:** Walk me through one incident (Part 16, S1) · What's your triage order? (Part 10)
**Adjust:** Say what access you really had (Part 0.4 #6).

### Q13: What was the most challenging part of the project?
**Priority:** MUST PREPARE
**Answer:**
"Making reruns trustworthy. Rerun touches a dozen tables: computed charges, taxes, expenses, vouchers and approvals, invoice files, payments, adjustments, usage flags and control flags. If you miss one dependent table, the recomputed bill is wrong or duplicated, and the agent doesn't find out until a customer disputes it. I found that expense-voucher approval assignments were being left behind, fixed that, and changed the contract so every rerun tells you exactly which cycles succeeded and which failed."
**Follow-ups:** How did you find the missing table? (debugging story, Part 4) · How did you test it?

### Q14: If you rejoined the project tomorrow, what would you improve first?
**Priority:** MUST PREPARE
**Answer (pick 3, in this order):**
1. "**Explicit transaction per cycle** for rerun (with `TransactionTemplate` or a separate transactional bean), because of the self-invocation pitfall (Part 4, Q26).
2. A **concurrency guard** on rerun: claim the cycle with a conditional update (`SET RERUN_INDR='I' WHERE RERUN_INDR='Y'`, check the row count).
3. A **structured JSON response** plus metrics, instead of plain text and email.
4. Remove `TRIM()` on indexed columns by cleaning the data or adding function-based indexes.
5. Finish the **automated invoice-vs-computed-data reconciliation** that had started."
**What they are testing:** Ownership and humility.
**Avoid saying:** "Nothing, it was well designed," or anything that trashes the team.

---

# PART 4 — BILL RERUN: DEEP DIVE

> This is your flagship. Expect **5–10 minutes** of drilling. Everything below comes from the actual code (`BatchBillRerunServiceImpl`, `BatchBillRerunHelper`, `BillRerunResult`, `BatchInvoiceController`, `MBSBillPullDetailRepository`, `BillControlRespository`, GUI `BNCController`) unless marked **[Inference]**.

## 4.1 Business explanation

**30 seconds:**
> "After the monthly billing batch runs for a subsystem, billing agents review the bills before they go out. If they find a problem, like a wrong rate, late usage, a tax issue or a missing adjustment, they need to recompute that bill cycle. **Bill Rerun** does that safely. It wipes everything computed for that cycle, resets the flags, and hands the cycle back to the billing batch to recompute from clean data. It's only allowed before the invoices are released."

**1 minute:**
> "Billing runs per subsystem and bill cycle date. After a run, the cycle is 'billed but not released', and agents review it in the GUI. If something's wrong, the agent clicks **Bill ReRun**, which flags that cycle. The rerun service then deletes the computed charges, expenses, taxes, invoice records, expense vouchers and their approval assignments; resets the payments and adjustments that were applied; marks the usage records unprocessed again; resets each customer's stage flags; and finally marks the cycle 'released, not run'. The next batch run recomputes it exactly as if it were the first time. It can run for a whole subsystem or for one customer. My work was making it reliable: each cycle processed independently, a clear success / partial / failure result, and cleanup of data it used to leave behind."

**2 minutes:** the 1-minute version, plus:
> "Why reset instead of patching? Because billing values depend on each other. Tax depends on charges, balance depends on payments, and the invoice depends on all of them. Patching one table risks inconsistent totals. Resetting and reusing the normal pipeline means there's **one piece of computation logic**, and a rerun produces exactly what a first run would. The eligibility rule is strict: the cycle must be billed, released, flagged for rerun, and **not yet sent to media**. After release, corrections are done with adjustments, not reruns. Operationally, the service logs a per-table summary of what it deleted and updated, emails the billing team on failure, and returns 200, 206 or 500 with the exact cycle dates that succeeded or failed. Before my changes, agents couldn't tell which cycles had worked and were chasing it manually several times a week. That stopped."

## 4.2 Why Bill Rerun exists

| Question | Answer |
|---|---|
| Business problem | A billed cycle can be wrong because of bad input (rate table error, late or missing usage, wrong address → wrong tax, adjustment entered late). Invoices must not go out wrong. |
| When it's needed | After the billing run and **before** media/invoice release, when the review finds a problem and the root cause has been fixed. |
| Trigger conditions | Cycle state: `RUN_INDR='Y'`, `REL_INDR='Y'`, `MEDIA_INDR` not released (GUI requires `'N'`; API excludes `'P'`), and `RERUN_INDR='Y'` after the agent flags it. |
| Who triggers it | **Billing agent** flags it in the GUI (Bill ReRun button). **Execution** goes through the API rerun endpoints (system-level for all flagged cycles of a subsystem, or customer-level for one customer). *Confirm who invoked the API in your setup (Part 0.4 #3).* |
| Without it | Manual SQL cleanup of about 12 tables by support or DBAs: slow, error-prone, no audit, and a high risk of double-counted charges or a wrong balance. Or wrong invoices get released and must be fixed later with adjustments and customer disputes. |

## 4.3 End-to-end flow

```
 ┌───────────────────────────────────────────────────────────────────────────┐
 │ 1. BILLING RUN (normal batch) for system S, cycle date D                   │
 │    BILLPULL_DETAIL(S,D): RUN_INDR=Y, REL_INDR=Y, MEDIA_INDR=N              │
 │    BILL_CONTROL(cust,S,D): BILL_COMPLETE=Y, stage flags=Y                  │
 └───────────────────────────────────────────────────────────────────────────┘
                 │ agent reviews in GUI (Bill Preview / Bill Compare)
                 ▼  problem found → root cause fixed (e.g., rate corrected)
 ┌───────────────────────────────────────────────────────────────────────────┐
 │ 2. FLAG (GUI, mbs-app-GUI BNCController)                                   │
 │    GET  /getAvailableSystemsForBillReRun?billPullDate=D                    │
 │         → systems where REL=Y, RUN=Y, MEDIA=N for D                        │
 │    POST /updateBillReRunIndrToY?billPullDate=D   body: ["S1","S2"]         │
 │         → BILLPULL_DETAIL.BILL_RERUN_INDR = 'Y'                            │
 └───────────────────────────────────────────────────────────────────────────┘
                 ▼
 ┌───────────────────────────────────────────────────────────────────────────┐
 │ 3. EXECUTE (mbs-app-API BatchInvoiceController)                            │
 │    POST /batch-invoice/rerun-billing/system?systemName=S                   │
 │    POST /batch-invoice/rerun-billing/customer?customerId=C&billDate=D&…    │
 │    Validate: system name present/valid · date format (dd-MMM-yy[yy])       │
 │              · eligible cycle: RERUN=Y, REL=Y, RUN=Y, MEDIA<>'P'           │
 └───────────────────────────────────────────────────────────────────────────┘
                 ▼   (system path: loop over every flagged cycle, oldest first;
                 ▼    each cycle in its own try/catch → one failure ≠ all fail)
 ┌───────────────────────────────────────────────────────────────────────────┐
 │ 4. RESET ONE CYCLE (BatchBillRerunHelper)                                  │
 │   a. count usage-based compute rows (decides if usage flags need reverting)│
 │   b. delete BILL_COMPUTE_DETAIL, BILL_COMPUTE                              │
 │   c. delete EXPENSE_COMPUTE_DETAIL, EXPENSE_COMPUTE                        │
 │   d. delete TAX_COMPUTE_DETAIL, TAX_COMPUTE                                │
 │   e. delete invoice records (BILL_INV_FILE) for the cycle                  │
 │   f. delete ASSIGN_DETAILS linked to the cycle's expense vouchers  ← (my fix)
 │   g. delete EXPENSE_VOUCHER for the cycle                                  │
 │   h. reset payments & adjustments applied in the cycle                     │
 │   i. delete auto-generated affiliate APAY payments                         │
 │   j. revert CURR_USG process indicator (processed → unprocessed) within    │
 │      each customer's usage window (inactive/mismatch → warn & skip)        │
 │   k. reset BILL_CONTROL: BILL_COMPLETE=N, BILL_TYPE=N, all stage flags=N   │
 │   l. ONLY IF a–k all succeeded:                                            │
 │      BILLPULL_DETAIL → RUN=N, REL=Y, RERUN=N   (hand back to billing)      │
 └───────────────────────────────────────────────────────────────────────────┘
                 ▼
 ┌───────────────────────────────────────────────────────────────────────────┐
 │ 5. RESULT + OBSERVABILITY                                                  │
 │   BillRerunResult(successfulCycles, failedCycles, hasErrors)               │
 │   → 200 all OK · 206 partial · 500 all failed · 400 bad input              │
 │   Logs: START/END markers, duration, per-table deleted/updated counts      │
 │   Email to billing DL on invalid system / unexpected failure               │
 └───────────────────────────────────────────────────────────────────────────┘
                 ▼
 ┌───────────────────────────────────────────────────────────────────────────┐
 │ 6. RECOMPUTE: next billing batch picks cycles with RUN=N, REL=Y and only   │
 │    customers with BILL_COMPLETE=N → full pipeline runs again cleanly       │
 └───────────────────────────────────────────────────────────────────────────┘
```

**Customer-level rerun differences:** steps b–k are filtered by that customer. Vouchers, assignments and APAYs are matched by customer too. Only that customer's `BILL_CONTROL` row is reset. The cycle flag is still reset to "released, not run", so the batch runs again, and it processes **only customers with `BILL_COMPLETE='N'`**. Everyone else in the cycle is skipped.

### Cycle state model (`BILLPULL_DETAIL`, one row per system + bill date)

| RUN | REL | MEDIA | RERUN | Meaning |
|---|---|---|---|---|
| N | N | N | N | Cycle defined, not yet released for billing |
| N | Y | N | N | Released → the billing batch will pick it |
| Y | Y | N | N | Billed → awaiting review / formatting / release |
| Y | Y | N | **Y** | Agent flagged rerun → the rerun service will pick it |
| N | Y | N | N | After a successful rerun → back to "released"; the batch recomputes |
| Y | Y | Y / P | – | Released to media / in progress → **rerun no longer allowed**; fix with adjustments |

Per-customer (`BILL_CONTROL`): `BILL_COMPLETE`, `BILL_TYPE` (A = approved, H = hold, W = rejected/withheld, N = reset; the batch bills only A or N), and stage flags `RATING_HOLD`, `BILLING_HOLD`, `TAXING_HOLD`, `FORMATTING_HOLD`.
**A detail that shows you read the code:** despite the "HOLD" names, `'Y'` on these columns effectively means *that stage completed* for the customer. Formatting picks only customers with `BILL_COMPLETE='Y'` and the rating and billing flags `'Y'`. It's legacy naming.

## 4.4 Technical architecture of rerun

| Aspect | Detail |
|---|---|
| APIs | GUI: `getAvailableSystemsForBillReRun`, `updateBillReRunIndrToY`. API: `POST /batch-invoice/rerun-billing/system`, `POST /batch-invoice/rerun-billing/customer` (OpenAPI-documented). |
| Services | `BatchBillRerunService` → `BatchBillRerunServiceImpl` (orchestration; extends `BatchBillRerunHelper`, which holds the step methods and repositories). |
| Data | ~12 tables touched (see step list). All Spring Data JPA: `@Modifying` JPQL deletes and updates, plus entity load-modify-save for `BILL_CONTROL` / `BILLPULL_DETAIL`. |
| Result contract | `BillRerunResult { successfulCycles, failedCycles, hasErrors }` with `isCompleteSuccess()`, `isPartialSuccess()`, `isCompleteFailure()`. |
| External systems | **None during the reset itself.** It's DB-only. External calls (tax) happen when the batch recomputes. |
| Messaging / async | None. The HTTP call runs synchronously. *(Improvement: async job + status endpoint.)* |
| Batch | Rerun resets and relies on the normal billing batch for recomputation. |
| Auth | GUI actions are behind LDAP login plus the session filter; the API is called service-to-service. *(Improvement: record who flagged and who triggered.)* |
| Error handling | Each step catches its exception, logs it, and returns `false`. Any `false` → cycle marked failed and the cycle flag is **not** cleared. Invalid system → email + `IllegalArgumentException`. Unexpected exception → email + cycle failed. |
| Logging | Dynamic (DB-configurable) logger; START/END banners; duration in ms; a summary with ~17 counters (rows deleted/updated per table). |
| Monitoring | Email alerts to a configured billing distribution list; logs. |

## 4.5 Your personal role (be exact)

| Category | What to say |
|---|---|
| **Implemented** | The `BillRerunResult` contract; the per-cycle loop that collects successful and failed dates and never lets one cycle's failure abort the rest; the controller mapping to 200 / 206 / 500 with the specific dates in the message; cleanup of `ASSIGN_DETAILS` linked to expense vouchers; the optimized usage-indicator update; the logging and preview/rerun session fixes. *(Commits `b21683a`, `8fedd4b`, `3f63398`, `12e841f`.)* |
| **Modified** | `BatchBillRerunServiceImpl`, `BatchBillRerunHelper`, `BatchInvoiceController`; repositories for expense compute, bill compute, assign details and expense vouchers. |
| **Designed** | The result model and the "continue per cycle, report precisely" behavior, reviewed and agreed with my lead. |
| **Debugged** | Rerun cases where results were ambiguous or data was left behind; tracing which tables still held rows for a rerun cycle. |
| **Reviewed / tested** | Unit tests on the outcome mapping; end-to-end rerun checks in the test environment with QA and billing ops. |
| **Supported** | Agents during billing windows when a rerun failed or looked wrong. |
| **Not mine** | The GUI flag screen (already on the GUI master branch), the batch stage computations, the schema, the scheduler, the tax and BRIM integrations. |

> *Adjust any line that doesn't match your memory. Removing a claim is always safer than defending one you can't.*

## 4.6 The Bill Rerun follow-up chain (practice as one conversation)

### Q15: What is Bill Rerun?
**Priority:** MUST PREPARE — use the 30-second answer in 4.1, then stop and let them pull.

### Q16: Why is it needed? Couldn't agents just fix the wrong number?
**Priority:** MUST PREPARE
**Answer:** "Numbers in a bill depend on each other: tax on charges, balance on payments and adjustments, and invoice totals on all of them. Fixing one table by hand leaves the others inconsistent. Rerun guarantees the recomputed bill comes from **the same pipeline logic** as a normal run. And there's no manual SQL in production."
**Testing:** Whether you understand data dependencies.

### Q17: When is it triggered, and who triggers it?
**Priority:** MUST PREPARE
**Answer:** "After billing and before invoices are released to media. The billing agent flags it in the GUI, which sets `BILL_RERUN_INDR='Y'` for that date and the selected systems. Execution happens through the API endpoints: system-level processes every flagged cycle for the subsystem, customer-level is for a targeted fix. *(Add: in our setup it was invoked by ___.)*"
**Avoid:** Guessing a cron schedule you can't confirm.

### Q18: What validations happen?
**Priority:** MUST PREPARE
**Answer:** "Four layers. **Input:** system name required; the bill date must parse as dd-MMM-yy or dd-MMM-yyyy, otherwise 400 with the expected format. **System validity:** checked against configuration, and an invalid system sends an alert email and fails. **Eligibility:** the cycle for that date must be flagged `RERUN=Y`, released, billed, and not in media. If not, it returns 'no eligible bill cycle' and does nothing. **Per-customer checks during the usage revert:** an inactive customer, or a usage window that doesn't match the cycle, is logged and skipped rather than corrupting usage flags."
**Improvement to volunteer:** "An invalid system name should really return 400 through `@ControllerAdvice`, not 500 plus an email."

### Q19: Which service handles it? Walk me through the code path.
**Priority:** MUST PREPARE
**Answer:** "`BatchInvoiceController` → `BatchBillRerunService.runBillReRunForSystem(system)`. It validates, loads all flagged cycles for the system ordered by date, and for each cycle calls `billReRun(system, date)` inside its own try/catch. `billReRun` runs the reset steps from the helper. Every step returns true or false, and only if all are true does it clear the rerun flag and set the cycle back to 'released, not run'. The loop builds `BillRerunResult`, and the controller maps it to a status code and a message."

### Q20: What exactly happens in the database?
**Priority:** MUST PREPARE — recite steps a–l from 4.3 in plain words. Name at least: compute tables, tax, invoice, vouchers **and their approval assignments**, payments and adjustments reset, usage flags reverted, control flags reset, and the cycle flag last.
**Follow-up:** "Why is the cycle flag updated **last**?" → "It's the commit point for the business process. If any earlier step failed, the cycle stays flagged for rerun, so it gets picked again and nothing half-reset goes back to billing."

### Q21: Why reset-and-recompute instead of resuming only the failed stage?
**Priority:** MUST PREPARE
**Answer:** "Two different needs. For **failures during a normal run**, the pipeline already behaves in a resumable way: stage flags mean formatting only picks customers whose earlier stages are complete, and incomplete customers get picked up again. **Rerun** is for bills that ran successfully but are *wrong*, usually because the input changed. Inputs changed, so every stage downstream of them is suspect. Resetting everything for the scope is the only way to guarantee consistency.
Trade-off: we recompute more (including tax calls) than strictly needed, but correctness beats compute cost for a monthly revenue process."
**Avoid:** "Rerun skips completed stages" (the code doesn't).

### Q22: Is it idempotent? How do you prevent duplicate processing or double billing?
**Priority:** MUST PREPARE
**Answer:** "Yes, at the data level, by design:
1. Every reset step is a **delete** or a **conditional state update**. Running it twice gives the same end state (the second run deletes zero rows).
2. The **eligibility gate** is on the flag. After a successful rerun the flag is 'N', so a second call returns 'no eligible cycle' and does nothing.
3. **Delete-before-recompute** means the batch never adds new charges on top of old ones.
4. **Usage flags are reverted** so usage is re-rated exactly once. Normally the processed flag prevents re-rating; rerun deliberately reverses it for the window.
5. In the recompute, the batch only processes customers with `BILL_COMPLETE='N'`, so in a customer-level rerun the other customers aren't billed twice.
6. **Media gate:** nothing already released can be rerun, so downstream never sees two versions of an invoice."
**Testing:** Idempotency vs "exactly once" thinking.

### Q23: What if two rerun requests arrive at the same time?
**Priority:** MUST PREPARE
**Answer (honest and senior):** "Nothing in the code **explicitly** serializes it. Two calls could both read `RERUN=Y` before either clears it. Because every step is idempotent, the data would still end up correct, but you'd get duplicated work, possible Oracle lock waits or deadlocks on the same rows, and misleading log counts. The counters are instance fields on a singleton bean, so they aren't thread-safe either. Rerun and billing for the same cycle can't overlap, though: billing needs `RUN='N'` and rerun needs `RUN='Y'`.
The fix I'd make: **claim the cycle atomically** with a conditional update (`UPDATE … SET RERUN_INDR='I' WHERE … AND RERUN_INDR='Y'`) and proceed only if one row was updated. Or `SELECT … FOR UPDATE` on the cycle row. Then make the counters local to each run."
**Follow-ups:** Optimistic vs pessimistic here? → "Optimistic (the conditional update) is enough: conflicts are rare and it doesn't hold locks while the rerun runs."
**Avoid:** "We handled concurrency with distributed locks."

### Q24: Why return 206 Partial Content for partial success?
**Priority:** MUST PREPARE
**Answer:** "It was a pragmatic internal convention: the calling tooling and support needed to tell 'everything worked', 'some cycles failed' and 'everything failed' apart at a glance, and the body lists the exact dates. Strictly, **206 belongs to HTTP range requests**, so for a public API I'd return **200 with a structured body** (`status: PARTIAL`, `successfulCycles`, `failedCycles`), or 207 Multi-Status. The important design decision is the explicit partial state, not the specific code."
**Testing:** Whether you know HTTP semantics *and* can defend a pragmatic choice.

### Q25: Why POST? Is that RESTful?
**Priority:** SHOULD PREPARE
**Answer:** "It's an action or command, 'rerun this cycle', not a resource update, so POST fits, like `POST /jobs`. If I redesigned it as a resource: `POST /bill-cycles/{system}/{date}/reruns` returns **202 Accepted** with a rerun ID, and `GET /reruns/{id}` returns the status. That also fixes the synchronous-timeout risk for large systems."

### Q26: What happens if it fails midway? Is the rerun atomic?
**Priority:** MUST PREPARE (**the deepest probe**)
**Answer:** "Each step catches its own exception and returns false. The cycle is marked failed, the **cycle flag isn't cleared**, it stays `RERUN=Y`, and an alert goes out. Because the steps are idempotent, **re-running converges**: steps that already ran do nothing, and the failed step retries.
On atomicity, honestly: `billReRun` is annotated `@Transactional`, but it's called from `runBillReRunForSystem` **on the same object**. Spring transactions are proxy-based, so **that self-invocation bypasses the transaction**. In practice each repository operation commits on its own. So the design's real safety comes from **idempotent steps + flag-last**, not from one big transaction.
If I made it strictly atomic per cycle, I'd move the per-cycle work behind a proxy (a separate bean or `TransactionTemplate`) and **stop swallowing exceptions inside the steps**. Otherwise one inner failure marks the transaction rollback-only and you get `UnexpectedRollbackException` at commit. One transaction per *cycle*, not per system, keeps lock duration reasonable."
**Framing tip:** If you found this yourself, say so. If you noticed it while reviewing the code later, say: "Looking back at it, one thing I'd change is…". **Don't claim it was designed that way on purpose.**
**Testing:** Real Spring depth (proxies, self-invocation, rollback-only).

### Q27: How is retry handled?
**Priority:** MUST PREPARE
**Answer:** "Deliberately **manual**. A failed cycle stays flagged, the team gets an email and the response names the failed dates. Someone looks at the cause, fixes it if it's data, and triggers again. That's safe because the reset is idempotent. I'd add a bounded automatic retry (with backoff) **only for transient infrastructure errors** like a deadlock or connection timeout, never for data errors, which would just fail again."

### Q28: What if a downstream system fails?
**Priority:** MUST PREPARE
**Answer:** "The reset itself makes **no external calls**; it's database-only, so the only 'downstream' is Oracle. External dependencies come in when the batch recomputes. If the tax service fails then, the customer's stage doesn't complete and they're never formatted with missing tax. Downstream consumers of invoices only see data after media release **[Inference: the journal and EDW release flags are on the same cycle row]**, and rerun is blocked after release, so downstream never receives two versions."

### Q29: In a multi-cycle rerun, why continue after one cycle fails instead of failing fast?
**Priority:** SHOULD PREPARE
**Answer:** "Cycles are independent: different dates, different data. Failing fast would leave the healthy cycles unprocessed and create more manual work. Continuing and **reporting precisely** gives maximum safe completion plus a clear list to retry. Fail-fast makes sense when steps depend on each other. *Inside* a cycle, a failure does stop that cycle from being handed back to billing."

### Q30: A rerun failed in production. How do you troubleshoot it?
**Priority:** MUST PREPARE
**Answer:**
1. "Get the scope: system, date, customer, and the API message (which dates failed).
2. **Logs:** find the `BILL RE-RUN … STARTING` marker for that system and date, then the first `Error deleting/updating …` line. That tells me the failing step and its cause (ORA error, lock timeout, constraint).
3. **Summary counters:** zeros where I expected rows, or suspiciously large numbers.
4. **DB state:** is `BILLPULL_DETAIL` still `RERUN=Y`? Are `BILL_CONTROL` rows reset? Are there leftover compute or invoice rows for that date?
5. **Classify:** data (inactive customer, cycle-window mismatch), locking (the batch or another session holding rows), environment (DB or connections), or a code defect.
6. **Fix and re-trigger:** safe, because it's idempotent. Then confirm the next batch recomputes, and verify totals with the agent.
7. **Prevent:** add a test, a log line or a guard so it doesn't recur."
**Testing:** Method, not heroics.

### Q31: What was broken before you worked on it, and what did you change?
**Priority:** MUST PREPARE
**Answer (STAR, ~90s):**
- **S:** "Billing batch logic, including rerun, was moving from the legacy batch engine into the Spring Boot API. The rerun service worked, but it wasn't dependable for operations."
- **T:** "I was asked to stabilize it so agents could trust a rerun without follow-up."
- **A:** "First, the **result contract**: it gave no clear per-cycle outcome, so a multi-cycle rerun didn't tell agents what had worked. I introduced `BillRerunResult` with successful and failed cycles and mapped it to success, partial and failure responses naming the exact dates. Second, **data completeness**: I traced which tables still held rows after a rerun and found that approval assignments linked to the cycle's expense vouchers weren't being deleted, so stale approvals survived the recompute. I added that cleanup and improved the usage-indicator update. Third, **diagnosability**: per-table counters, durations and clearer logs, plus a fix for a session mismatch between preview and rerun logging."
- **R:** "The recurring manual follow-ups agents had been raising, multiple times a week, stopped, and when a rerun did fail, the response and logs said exactly where."
**Avoid:** Invented percentages or incident counts.

### Q32: How did you test it?
**Priority:** SHOULD PREPARE
**Answer:** "Unit tests with Mockito on the orchestration: all cycles succeed → complete success; one step returns false → that cycle fails and the rest continue → partial; an exception in one cycle → the others still run; zero eligible cycles → 'nothing to do'. Controller tests for the status mapping. End-to-end in the test environment with QA: run billing for a seeded cycle, flag it, rerun, check the tables are cleared and the flags reset, run the batch again, and compare the recomputed invoice with the expected totals. Billing ops did UAT on real-looking cycles."
*(Trim to what you actually did.)*

### Q33: What would you improve? What are the trade-offs of the current design?
**Priority:** MUST PREPARE
**Answer:**
| Current | Trade-off | Improvement |
|---|---|---|
| Hard delete of computed data | Simple and consistent, but the "before" version is lost for audit or comparison | Archive or version compute rows by run ID, so before and after can be compared |
| Synchronous HTTP call | Simple, but a big system could hit HTTP timeouts | Async job, 202 + status endpoint |
| No explicit concurrency guard | Fine at low concurrency | Atomic claim with a conditional update |
| Implicit transaction boundaries | Relies on idempotency | Explicit per-cycle transaction |
| Plain-text response | Readable, not machine-friendly | JSON DTO with a status enum |
| Email + logs | Works, but reactive | Metrics (count, failures, duration), dashboard, alert thresholds |
| Who triggered isn't audited | Traceability gap | Store user, time and reason with the flag |
| Entity-by-entity save for control rows | N updates | Bulk JPQL update (with care for the persistence context) |

### Q34: How would it behave at 10x scale?
**Priority:** SHOULD PREPARE
**Answer:** "The reset is dominated by deletes on large compute tables filtered by date and system. At 10x: (1) make sure those filters are **index-friendly**; today's `TRUNC()`/`TRIM()` on columns can block normal indexes, so use function-based indexes or clean the data; (2) **chunk** the work by customer batches so transactions and locks stay small; (3) run the system-level rerun **asynchronously**; (4) the real cost is the recompute, especially the tax calls, so parallelize the batch per customer with a bounded thread pool and add rate limits and circuit breakers on the tax client."

### Q35: Why is rerun manual instead of automatic?
**Priority:** SHOULD PREPARE
**Answer:** "It's revenue data, and the reason for a rerun is usually a **bad input someone has to fix first**, like a rate or an address. Automatically rerunning with the same input just reproduces the same wrong bill. The human checkpoint is a deliberate control. What *should* be automated is detection: the reconciliation check comparing invoice values with the computed data, which the team was starting when I left, so agents know when a rerun is needed."

---

# PART 5 — SPRING BOOT (Core, Boot, MVC/REST, JPA/Hibernate)

> Hands-on context you can cite: **MBS API** (Spring Boot 2.5, Java 8, WAR on Tomcat, Spring Data JPA, Oracle); **MBS GUI** (Spring Boot 2.1, JSP views, RestTemplate); **ATC** (Spring Boot 3.2, Java 17, `TransactionTemplate`, `@Valid`).

## 5.1 Spring Core

### Q36: What are IoC and dependency injection? (worked through all six depth levels)
**Priority:** MUST PREPARE
- **L1 Basic:** "IoC means the container, not my code, creates and wires objects. DI is how it does that: dependencies are passed in through the constructor, a setter or a field."
- **L2 Practical:** "Every service, repository and controller in MBS is a Spring bean. `BatchInvoiceController` gets `BatchBillRerunService` injected; the service gets its repositories."
- **L3 Internal:** "At startup Spring scans packages, registers bean definitions, then instantiates beans in dependency order. For each injection point it resolves a candidate by type, then by `@Qualifier`/`@Primary`/name. Beans needing proxies (`@Transactional`, AOP) are wrapped by a BeanPostProcessor, so what gets injected is the **proxy**."
- **L4 Production:** "If a bean fails to create at startup, I read the *root* cause at the bottom of the stack trace: `NoSuchBeanDefinitionException` (not scanned or wrong profile), `NoUniqueBeanDefinitionException` (two candidates), an unresolved `${property}`, or a failing datasource. Running with `--debug` gives the auto-configuration conditions report."
- **L5 Trade-off:** "**Constructor injection** makes dependencies explicit, allows `final` fields, is easy to unit test without Spring, and fails fast. Field injection hides dependencies. The legacy MBS code used field injection and a base 'helper' class holding the repositories. In new code I'd use constructor injection and **composition**, for example a separate `RerunSteps` bean, which also fixes the transaction self-invocation issue."
- **L6 SME:** "If two developers disagree (field vs constructor injection, inheritance vs composition), I'd agree on a team convention, record it in a short coding guideline, enforce it in code review or Sonar rules, and apply it to *new and touched* code instead of starting a risky mass refactor."

### Q37: Explain the Spring bean lifecycle.
**Priority:** SHOULD PREPARE
**Answer:** "Bean definition registered → BeanFactoryPostProcessors can modify definitions (for example placeholder resolution) → instantiate (constructor) → populate dependencies → `*Aware` callbacks → BeanPostProcessor *before-init* → `@PostConstruct` / `afterPropertiesSet` / init-method → BeanPostProcessor *after-init*, **where AOP proxies are created** → ready → on shutdown `@PreDestroy` / destroy-method."
**Project connection:** "ATC's report scheduler loads its schedules from the DB after startup; a directory watcher registers on startup and closes on `@PreDestroy`."
**Follow-up:** Why doesn't `@Transactional` work in `@PostConstruct`? → the proxy isn't fully in play yet, and you're calling on `this`; use `ApplicationReadyEvent` instead.

### Q38: Bean scopes, and the singleton statefulness trap.
**Priority:** MUST PREPARE
**Answer:** "singleton (the default, one per container), prototype (new instance per lookup), and the web scopes request, session and application. Singletons are shared across threads, so they **must be stateless** or thread-safe.
**Project example:** the rerun helper keeps its per-run counters as **instance fields** on a singleton service. Two concurrent reruns would mix counts. The fix is to keep run state in a local object (a `RerunStats` created per call), not in fields."
**Follow-up:** Injecting a prototype into a singleton gives you one instance only. Use `ObjectProvider` / lookup method.

### Q39: `@Configuration` + `@Bean` vs `@Component`. And circular dependencies?
**Priority:** SHOULD PREPARE
**Answer:** "`@Component` is for my own classes, found by scanning. `@Bean` methods inside `@Configuration` are for third-party objects or custom construction, like a `RestTemplate` with SSL or timeouts, or a `TransactionTemplate`. `@Configuration` classes are CGLIB-proxied, so calling one `@Bean` method from another returns the same singleton.
**Circular dependencies:** constructor-injection cycles fail at startup, and since Boot 2.6 circular references are disallowed by default. The right fix is design: extract the shared logic into a third bean, or use events. `@Lazy` or setter injection are workarounds, not fixes."

### Q40: Profiles and externalized configuration: what wins?
**Priority:** SHOULD PREPARE
**Answer:** "Rough precedence, highest first: command-line args → OS environment variables → profile-specific `application-{profile}.properties` → `application.properties` → defaults. Profiles select environment config; in MBS the GUI's pods get `SPRING_PROFILES_ACTIVE` per environment. I prefer type-safe `@ConfigurationProperties` over scattered `@Value`. **Secrets never go in property files**; they come from the platform's secret store as environment variables."

## 5.2 Spring Boot

### Q41: How does auto-configuration work?
**Priority:** MUST PREPARE
**Answer:** "`@SpringBootApplication` = `@Configuration` + `@ComponentScan` + `@EnableAutoConfiguration`. Auto-config classes are listed in `spring.factories` (Boot 2.x) or `AutoConfiguration.imports` (2.7+/3.x). Each is guarded by conditions such as `@ConditionalOnClass`, `@ConditionalOnMissingBean` and `@ConditionalOnProperty`. For example, with a JDBC driver and a `spring.datasource.url` you get a Hikari `DataSource`, JPA and transaction manager, unless you define your own. **My bean wins** because of `@ConditionalOnMissingBean`."
**Production angle:** "When the MBS API needs a second database (the directory DB), you define that DataSource explicitly, mark one `@Primary`, and keep the entity managers and transaction managers separate, otherwise auto-config wires the wrong one."
**Follow-ups:** How do you see what got configured? (`--debug` report, the `/actuator/conditions` endpoint) · How do you exclude one? (`exclude=` on the annotation, or a property)

### Q42: What happens when a Spring Boot app starts? And for a WAR on Tomcat?
**Priority:** SHOULD PREPARE
**Answer:** "`SpringApplication.run` prepares the Environment (properties, profiles), creates the ApplicationContext, loads bean definitions, refreshes the context (instantiating singletons), starts the embedded server, runs `CommandLineRunner`/`ApplicationRunner`, then publishes `ApplicationReadyEvent`.
**MBS API and GUI are WARs**: the class extends `SpringBootServletInitializer`, so the *external* Tomcat bootstraps the Spring context instead of the embedded server."

### Q43: What is Actuator, and how would you use it in production?
**Priority:** SHOULD PREPARE
**Answer:** "Production endpoints: `health` (with liveness and readiness groups for Kubernetes probes), `info`, `metrics` (Micrometer), `loggers` (change a log level at runtime), `threaddump`, `env`. Expose only what's needed and secure them.
**Honest mapping:** MBS relied on logs, email alerts and a DB-driven log-level switch. I'd replace the custom switch with `/actuator/loggers` and add Micrometer metrics for batch and rerun duration and failures."

## 5.3 Spring MVC / REST

### Q44: Walk me through a request in Spring MVC.
**Priority:** MUST PREPARE
**Answer:** "Servlet filters first (in the GUI, the authorization filter checking the session) → `DispatcherServlet` → `HandlerMapping` finds the controller method → `HandlerAdapter` resolves arguments (`@RequestParam`, `@PathVariable`, `@RequestBody` via Jackson `HttpMessageConverter`, `@Valid` validation) → controller → service → repository → return value: `ResponseEntity` / `@ResponseBody` serialized by Jackson. In the GUI, a view name is resolved by the `ViewResolver` to a **JSP**. Exceptions go through `HandlerExceptionResolver`s, including `@ControllerAdvice`."

### Q45: How do you do validation and exception handling properly?
**Priority:** MUST PREPARE
**Answer:** "DTOs carry Bean Validation annotations (`@NotBlank`, `@Pattern`, and so on) and the controller uses `@Valid`, as in ATC's composite edit request. Business rules are validated in the service and throw specific exceptions. A single `@RestControllerAdvice` maps exceptions to a **consistent error body** (code, message, timestamp, correlation ID) and the right status: 400 for validation, 404 not found, 409 conflict, 500 for unexpected errors. The MBS API has a global handler for its mapping exceptions.
**What I'd fix in the legacy GUI:** several controllers catch `Exception` and return an empty list. The caller can't tell 'no data' from 'failure'. Errors should propagate to the advice."

### Q46: Pagination, filtering and sorting on big tables?
**Priority:** SHOULD PREPARE
**Answer:** "Spring Data `Pageable` → `Page` (runs a count query) or `Slice` (no count, cheaper). For very large tables, **keyset/seek pagination** (`WHERE id > :last ORDER BY id`) beats OFFSET. Filtering and sorting belong **in the query**, not in Java.
**Project:** On the Bill Process tab I moved sorting into the query (descending by date) and added a 7-day date filter, which we later removed while keeping the sort *(give the real reason, Part 0.4 #4)*."

## 5.4 JPA / Hibernate

### Q47: `@Transactional`: how does it work, and what are the pitfalls?
**Priority:** MUST PREPARE
**Answer:** "It's implemented through an **AOP proxy**: the proxy opens the transaction, calls the method, and commits or rolls back.
Pitfalls: (1) **self-invocation** bypasses the proxy (see the Bill Rerun Q26 story); (2) it only works on public methods called through the proxy; (3) by default it **rolls back only on unchecked exceptions and `Error`**, so checked exceptions need `rollbackFor`; (4) catching an exception inside a participating method still leaves the transaction **rollback-only**, giving `UnexpectedRollbackException` at commit; (5) the propagation choice matters: `REQUIRED` joins, `REQUIRES_NEW` suspends and starts a new one (good for audit logs that must persist even if the main work fails); (6) `readOnly=true` lets Hibernate skip dirty checking.
**Project:** In ATC's edit flow the DB changes run in a `TransactionTemplate` block, and the downstream MBS sync is called **after** commit. We don't hold DB locks during a slow HTTP call, and a failed sync doesn't roll back a valid local change; it's reported back instead."
**Follow-up:** "What if the sync fails?" → "The local edit is committed and the response says the sync failed. The gap is that there's no automatic retry; an outbox table plus a retry job would close it."

### Q48: Persistence context, entity lifecycle and dirty checking.
**Priority:** MUST PREPARE
**Answer:** "States: transient → managed (persist or find) → detached (the context closed or `clear()`) → removed. The persistence context is the first-level cache: within one transaction, `find` returns the same instance, and at flush Hibernate compares managed entities with their snapshots and issues UPDATEs automatically (**dirty checking**). So inside a transaction, calling `save()` on an already-managed entity is redundant.
**Project:** The rerun resets `BILL_CONTROL` by loading the rows, setting the flags and calling `save()` on each. That's N updates. A bulk JPQL `UPDATE` would be one statement."

### Q49: `@Modifying` bulk updates: what's the catch?
**Priority:** MUST PREPARE
**Answer:** "Bulk JPQL `UPDATE`/`DELETE` goes **straight to the database and bypasses the persistence context**, so already-loaded entities become stale. Use `@Modifying(clearAutomatically = true, flushAutomatically = true)` when needed. They need an active transaction, and they return the affected row count. **The rerun summary logs those returned counts**, which is how we know what each step did."

### Q50: Lazy vs eager, N+1, and LazyInitializationException.
**Priority:** MUST PREPARE
**Answer:** "`@ManyToOne`/`@OneToOne` default to EAGER and collections to LAZY; I make almost everything LAZY and fetch deliberately. **N+1:** load N parents, touch a lazy relation on each, and you get N extra queries. Fixes: `JOIN FETCH`, `@EntityGraph`, `@BatchSize`, or a DTO projection. `LazyInitializationException` means a lazy association was accessed after the session closed. Fix it with the right fetch, not by turning on open-in-view (Boot enables open-in-view by default and warns about it).
**Project honesty:** MBS mostly used explicit JPQL/native queries over flat composite-key entities rather than deep relationship graphs, so the typical issues were query shape and index use more than N+1."

### Q51: Composite keys: `@IdClass` vs `@EmbeddedId`?
**Priority:** SHOULD PREPARE
**Answer:** "Both map multi-column keys. `@IdClass` keeps the key fields on the entity (`BILL_CONTROL`: customer ID + bill pull date + system name, with a separate `BillControlPK` class). `@EmbeddedId` puts them in an `@Embeddable` object. Either way the key class must be `Serializable` and implement `equals`/`hashCode` correctly, or the persistence context and caching break."

### Q52: Optimistic vs pessimistic locking. Where would you use each?
**Priority:** MUST PREPARE
**Answer:** "**Optimistic:** a `@Version` column. The update checks the version and throws `OptimisticLockException` on conflict. It suits low-contention data and holds no locks. **Pessimistic:** `@Lock(PESSIMISTIC_WRITE)` → `SELECT … FOR UPDATE`. It suits hot rows that must be serialized; the risks are lock waits and deadlocks.
**Project:** Customer ID generation in the MBS API uses a `FOR UPDATE` read of a sequence row, so two requests can't get the same ID *(existing code, not mine)*. For the rerun claim I'd use an optimistic conditional update."

### Q53: JPQL vs native vs derived queries? First-level vs second-level cache?
**Priority:** SHOULD PREPARE
**Answer:** "Derived queries for simple lookups; JPQL for entity-level queries that stay portable; **native** when you need database-specific features or complex joins. MBS has plenty of Oracle-specific `TO_CHAR`/`TRUNC` usage. Always bind parameters; never concatenate strings. The first-level cache is per persistence context and always on; the second-level cache is shared, optional, and good for reference data. For billing transactional data I wouldn't use it."

### Q54: How do you insert or update large volumes efficiently with JPA?
**Priority:** SHOULD PREPARE
**Answer:** "Set `hibernate.jdbc.batch_size`, persist in chunks, and `flush()` + `clear()` every N rows so the persistence context doesn't grow without bound (OOM risk). Use sequence-based IDs, because `IDENTITY` disables insert batching. For the biggest loads, go JDBC batch or database-native. **Project context:** ATC's master-data import persists in batches of 1000 with flush/clear and logs rows per second *(existing code in the app; describe it, don't claim it)*."

---

# PART 6 — JAVA (senior-level, practical)

> Context: MBS API/GUI on **Java 8**, ATC on **Java 17** (records, switch expressions). Be ready to say what changed after Java 8 without claiming production depth on Java 21.

### Q55: SOLID: give me real examples, good and bad, from your codebase.
**Priority:** SHOULD PREPARE
**Answer:**
- "**SRP, good:** ATC's workflow-transition service only records status transitions; the decision *when* to transition lives in the callers. **SRP, bad:** the legacy GUI's main controller has dozens of unrelated endpoints (login, bill preview, rerun flags, reports). I'd split it by feature.
- **OCP / DIP:** services are interfaces with `…Impl` classes (`BatchBillRerunService` → `BatchBillRerunServiceImpl`), so controllers depend on the abstraction and tests can mock it.
- **Composition over inheritance:** the rerun service *extends* a helper class just to reach its repositories and step methods. I'd inject a steps component instead. That's cleaner, easier to test, and fixes the transaction proxy issue.
- **ISP:** keep repository or service interfaces focused, not 'god interfaces'."

### Q56: How does HashMap work? What changed in Java 8?
**Priority:** MUST PREPARE
**Answer:** "An array of buckets. The index comes from `hash(key)` (with the high bits spread in: `h ^ (h >>> 16)`) masked by capacity − 1. Collisions chain in a bucket. **Java 8:** a bucket with more than 8 entries (when capacity ≥ 64) becomes a red-black tree, so the worst case goes from O(n) to O(log n); it turns back into a list at 6. Load factor 0.75: when size exceeds capacity × 0.75 it resizes to double capacity. One null key is allowed. It isn't thread-safe."
**Follow-ups:** What if `hashCode` changes after insertion? (the entry is effectively lost) · Why is capacity a power of two? (a fast mask instead of modulo)

### Q57: ConcurrentHashMap vs Hashtable / `Collections.synchronizedMap`.
**Priority:** MUST PREPARE
**Answer:** "Hashtable and synchronizedMap lock the **whole map** for every operation. Java 8+ `ConcurrentHashMap` uses **CAS for empty bins and `synchronized` on the individual bin head** for updates. Reads are lock-free, and iterators are weakly consistent (no `ConcurrentModificationException`). No null keys or values. `compute`/`computeIfAbsent`/`merge` are **atomic per key**, which is how you implement 'check and refresh' safely.
**Project:** ATC's OAuth token manager keeps the token in a `ConcurrentHashMap` and refreshes it through `compute()`, so concurrent requests don't all trigger a refresh at once *(existing code)*."

### Q58: The equals/hashCode contract. Why does it matter in your project?
**Priority:** MUST PREPARE
**Answer:** "Equal objects must have equal hash codes. Unequal objects may collide. Override both together, based on the same immutable fields. **Project:** JPA composite-key classes like `BillControlPK` implement both over customer ID, bill date and system name. Without that, the persistence context can't identify entities and `find` or caching misbehaves. Also: never use mutable fields in the key of a HashMap."

### Q59: String immutability, the pool, and `==` vs `equals`.
**Priority:** SHOULD PREPARE
**Answer:** "Strings are immutable, which gives thread safety, safe use as map keys (hash code cached), security (can't be changed after validation) and interning in the pool. `==` compares references; `equals` compares content. There was a real example in our codebase: a payment controller check compared a string to `""` with `==`, which fails for strings built at runtime. The right way is `isEmpty()` / `StringUtils.hasText()`. In loops, use `StringBuilder` rather than `+=`."

### Q60: Exception handling: what are your rules?
**Priority:** SHOULD PREPARE
**Answer:** "Checked exceptions for recoverable conditions the caller must handle; unchecked for programming errors and most business failures in Spring apps (plus, they trigger rollback by default). Create **specific** exceptions (`InvoiceNotFoundException` in the rerun flow) and translate them at the boundary with `@ControllerAdvice`. Don't swallow exceptions; if you catch one, add context and log it **once**. `try-with-resources` for streams and connections. In batch loops, catch per item so one bad record doesn't kill the run, and always report the failures (exactly what the rerun loop does per cycle)."

### Q61: Streams, lambdas, Optional, functional interfaces: practical use and pitfalls.
**Priority:** SHOULD PREPARE
**Answer:** "Functional interfaces (`Function`, `Predicate`, `Supplier`, `Consumer`) back lambdas. In the rerun controller: `Optional.ofNullable(result.getFailedCycles()).orElse(emptyList())` to avoid nulls, and `stream().map(sdf::format).collect(joining(", "))` to build the message. Pitfalls: side effects inside streams; `Optional` as a field or parameter (use it only as a return type); `parallelStream()` runs on the shared common ForkJoinPool, so it's bad for blocking I/O in a server; `Optional.get()` without a check. Also `SimpleDateFormat` isn't thread-safe; `java.time.DateTimeFormatter` is."

### Q62: Generics: PECS and type erasure.
**Priority:** AWARENESS
**Answer:** "`? extends T` when you read from a structure (producer), `? super T` when you write to it (consumer). Generics are checked at compile time and **erased** at runtime, so no `new T()` and no `instanceof List<String>`."

### Q63: JVM memory, GC, and a memory problem in production.
**Priority:** MUST PREPARE
**Answer:** "Heap (young and old generations), per-thread stacks, and **Metaspace** for class metadata (replaced PermGen in Java 8). GC: Java 8's default is Parallel GC; since Java 9 it's **G1**. The MBS API on Java 8 would be on Parallel unless configured.
**Diagnosing:** `OutOfMemoryError: Java heap space` → take a heap dump (`-XX:+HeapDumpOnOutOfMemoryError`), analyse it in MAT, look for dominators, typically huge lists (loading a whole table), unbounded caches, or a persistence context that's never cleared in batch. `Metaspace` OOM → classloader leaks, often from redeploying WARs repeatedly on the same Tomcat. In containers, keep `-Xmx` well below the pod memory limit, or use `MaxRAMPercentage`, otherwise the kernel **OOM-kills** the pod (exit code 137) before Java ever throws."
**Project:** The GUI pod sets explicit JVM heap and metaspace options inside a memory-limited Tomcat container.

### Q64: Concurrency basics: synchronized, volatile, locks, atomics, race conditions, deadlocks.
**Priority:** MUST PREPARE
**Answer:** "`synchronized` gives mutual exclusion **and** visibility. `volatile` gives visibility and ordering only, so `count++` on a volatile is still a race; use `AtomicInteger`/`LongAdder`. `ReentrantLock` adds `tryLock` with a timeout, interruptibility and fairness. A **race condition** is a check-then-act or read-modify-write on shared state. A **deadlock** needs mutual exclusion, hold-and-wait, no preemption and circular wait; prevent it with a **consistent lock order** and timeouts. The same applies to database rows.
**Project:** the rerun counters on a singleton are a textbook race. The ATC report scheduler's `refreshSchedules()` is `synchronized` so two refreshes can't interleave cancel and register. The directory watcher hands files to a single-thread executor so files are processed one at a time."

### Q65: ExecutorService and CompletableFuture: where would you use them?
**Priority:** SHOULD PREPARE
**Answer:** "Always use a **bounded** pool (`ThreadPoolExecutor` with a bounded queue and a rejection policy); an unbounded cached pool can exhaust threads. `CompletableFuture.supplyAsync(task, myExecutor)` (never the common pool for I/O), then compose with `thenApply` / `thenCompose` / `thenCombine`, wait with `allOf`, and handle errors with `exceptionally` / `handle`.
**Project use case:** a system-level billing recompute calls the tax service per customer sequentially. At 10x I'd fan out per customer with a bounded executor (sized to what the tax service allows), use `allOf` to join, and give each call a timeout (`orTimeout` in Java 9+, or `get(timeout)` on Java 8), so one slow customer doesn't stall the run."
**Follow-up:** Java 21 virtual threads → "They make blocking I/O cheap to scale, but you still need to limit calls to a downstream that can't take the load."

---

# PART 7 — REST & MICROSERVICES

## 7.1 Used by me vs conceptual (say this distinction out loud when relevant)

| Topic | Used by me (hands-on) | In the project, owned by others | Conceptual / would apply |
|---|---|---|---|
| REST controllers, DTOs, status codes | ✅ rerun endpoints, ATC edit endpoint | | |
| OpenAPI / Swagger annotations | ✅ | | |
| Service-to-service REST calls | ✅ GUI → API (RestTemplate), ATC → MBS | | |
| OAuth2 client credentials | | ✅ ATC→MBS, tax service | |
| JWT auth | ✅ previous fintech project [Stated] | | |
| Kafka / event ingestion | TCS [Stated] | ✅ BRIM inbound pipeline (teammate) | |
| ActiveMQ | | ✅ GUI publishes to a billing topic | |
| Timeouts / retry / circuit breaker / bulkhead | | | ✅ |
| API gateway / service discovery | | NGINX ingress for GUI | ✅ |
| Correlation IDs / distributed tracing | | | ✅ |
| Saga / outbox / eventual consistency | ATC edit + post-commit sync is a *simple* version | | ✅ |

### Q66: What makes an API RESTful? Methods, idempotency, status codes.
**Priority:** MUST PREPARE
**Answer:** "Resources identified by URIs, standard methods, stateless requests, representations (JSON), and proper status codes. **Safe:** GET, HEAD. **Idempotent:** GET, PUT, DELETE (and HEAD/OPTIONS). **Not idempotent:** POST, and PATCH in general. Status codes I use: 200 OK, 201 Created (with `Location`), 202 Accepted (async), 204 No Content, 400 validation, 401 unauthenticated, 403 forbidden, 404, 409 conflict/duplicate, 422 business-rule failure (optional), 429 rate-limited, 500, 503.
**Project mapping:** rerun uses 400 for bad input, 200/206/500 for the outcome. My improvement would be 200 with a structured status, or 202 plus polling for long reruns (see Q24–Q25)."

### Q67: As a lead, what API standards would you set for the team?
**Priority:** MUST PREPARE (L9 angle)
**Answer:** "(1) Resource naming and plural nouns; (2) one **error model** via `@RestControllerAdvice`; (3) validation at the edge with Bean Validation; (4) pagination for lists; (5) **idempotency keys** for create operations that clients may retry (payments!); (6) versioning policy and backward-compatibility rules; (7) OpenAPI spec reviewed in PRs; (8) correlation ID header propagated and logged; (9) no sensitive data in logs or URLs; (10) contract tests for consumers. I'd write this as a one-page guideline and enforce it in reviews rather than as a heavy process."

### Q68: How do you version APIs and avoid breaking consumers?
**Priority:** SHOULD PREPARE
**Answer:** "Prefer **additive, backward-compatible** changes: new optional fields; never rename or remove fields; tolerant readers that ignore unknown fields. For a real breaking change: a new version (`/v2/...` in the URI is the most visible, or a header or media type), run both, publish a deprecation date, track consumer usage from logs, then remove.
**Project:** the MBS API is consumed by both the GUI and ATC, so a change to a shared endpoint needs both teams told and both regression-tested. That's why I'd add consumer contract tests."

### Q69: Your system isn't microservices. Would you split it? How?
**Priority:** MUST PREPARE
**Answer:** "Not as a big-bang rewrite. The pain points are the **shared database** (every app coupled to every table) and the **large legacy GUI**. I'd use a **strangler** approach: first make the API the only writer (the GUI currently also reads and writes the DB directly), then carve out bounded contexts with clear ownership, such as billing computation (batch stages + rerun), payments and adjustments, finance integration (BRIM outbound/inbound), and customer and product master. Each gets its own schema or ownership of its tables and communicates via APIs or events.
**Trade-off:** microservices buy independent deployment and scaling but cost distributed transactions, more ops, and eventual consistency. For a monthly billing batch, strong consistency matters more than independent scaling, so I'd split only where team ownership or scaling truly differs."
**Avoid:** "We should just convert everything to microservices."

### Q70: Sync vs async communication. When do you pick which?
**Priority:** SHOULD PREPARE
**Answer:** "**Sync REST** when the caller needs the answer now and the operation is short (customer lookup, validation). **Async messaging** when you want decoupling, buffering, retries and fan-out, or the work is long (feeds, notifications, recomputation). In MBS: GUI → API and ATC → MBS are REST; **inbound BRIM payment data arrives through Kafka** (a teammate owned that consumer); the GUI publishes billing data to an ActiveMQ topic. For a long rerun I'd move to async: accept the request with a job ID, process in the background, and expose status."

### Q71: How do you make a service resilient to a slow or failing dependency?
**Priority:** MUST PREPARE
**Answer:** "Layered:
1. **Timeouts** on every remote call (connect + read). The default RestTemplate has *no* timeout, which is a classic production bug.
2. **Retry** only transient failures, only idempotent operations, with exponential backoff + jitter and a small maximum.
3. A **circuit breaker** (closed → open after a failure-rate threshold → half-open trial calls → closed) so you stop hammering a dead dependency and fail fast.
4. A **bulkhead**: a separate, limited thread or connection pool per dependency, so the tax service can't consume all threads.
5. A **fallback** that fits the domain. In billing that means **hold the customer**, never guess a tax value.
6. **Rate limiting** toward a provider's quota.
Library: Resilience4j (conceptual for me; the MBS code didn't use it)."
**Project:** The tax service during billing (see Q10).

### Q72: API gateway, service discovery, load balancing: what do they do?
**Priority:** SHOULD PREPARE
**Answer:** "A **gateway** is the single entry point: routing, authentication, rate limiting, TLS, request logging. **Service discovery** means clients find healthy instances by name, not by hard-coded host (Eureka/Consul, or natively a Kubernetes `Service` + DNS). **Load balancing** is server-side (an LB/ingress) or client-side (a client picks an instance).
**Project mapping:** the GUI sits behind an NGINX ingress with TLS on Kubernetes, and the Kubernetes Service load-balances across pods. The API was reached on fixed per-environment hostnames on Tomcat servers. A gateway and discovery would matter once there are more services."

### Q73: How would you trace a request across GUI → API → DB → external?
**Priority:** SHOULD PREPARE
**Answer:** "A servlet filter generates or reads an `X-Correlation-Id`, puts it in the **MDC** so every log line carries it, and a RestTemplate interceptor forwards it downstream. Every response and error body includes it. For full tracing, Micrometer Tracing / OpenTelemetry (Sleuth on Boot 2) with trace and span IDs, exported to Zipkin/Jaeger or the APM. **Honest gap:** in MBS we correlated by system, date and customer in the logs; a correlation ID would have made rerun and batch investigations faster."

### Q74: Distributed transactions: 2PC, saga, outbox, eventual consistency.
**Priority:** SHOULD PREPARE
**Answer:** "Avoid 2PC across services (blocking, poor availability). **Saga:** a sequence of local transactions, each with a **compensating action** if a later step fails. Orchestration has a coordinator; choreography uses events. **Outbox:** write the business change and the outgoing event in the **same local transaction**, then a relay publishes it, which avoids 'DB committed but message lost'. Consumers must be **idempotent**.
**Project:** ATC's edit commits locally, then calls MBS. If that call fails, the systems diverge until someone re-syncs. That's a saga without compensation or retry. An outbox row plus a retry job would give eventual consistency with no manual step."

### Q75: Event-driven basics: delivery guarantees, ordering, DLQ.
**Priority:** SHOULD PREPARE
**Answer:** "Kafka is typically **at-least-once**, so consumers must be idempotent: dedupe on a business key or event ID with a unique constraint. Ordering is guaranteed **only within a partition**, so key by the entity (customer or account) that needs ordering. Commit offsets after processing. Poison messages go to a **DLQ** after bounded retries, with alerting and a replay path. In our platform the BRIM inbound pipeline was my teammate's. On the MBS side, payment posting checks for duplicates before inserting, and ATC's BRIM payment poller treats unique-constraint violations as 'already processed' rather than as errors."

---

# PART 8 — DATABASE / SQL / JPA

> Hands-on: **Oracle** at Lumen (JPQL + native SQL, composite keys, DBA-owned schema). **PostgreSQL** in the fintech project [Stated]. The JD says MySQL or PostgreSQL, so know the Oracle → PostgreSQL/MySQL differences (Q85).

### Q76: Write SQL on your own domain (joins, aggregation, subqueries, window functions, CTEs).
**Priority:** MUST PREPARE. They may ask you to write live SQL. Practise these four.
```sql
-- 1) Total charges per customer for a cycle (join + aggregation)
SELECT bc.customer_id, SUM(c.amount) AS total_charges
FROM   bill_control bc
JOIN   bill_compute c  ON c.customer_id = bc.customer_id
                      AND c.system_name = bc.system_name
                      AND c.bill_pull_date = bc.bill_pull_date
WHERE  bc.system_name = :sys AND bc.bill_pull_date = :billDate
GROUP  BY bc.customer_id
HAVING SUM(c.amount) > 0;

-- 2) Customers marked complete but with no invoice row (anti-join)
SELECT bc.customer_id
FROM   bill_control bc
WHERE  bc.system_name = :sys AND bc.bill_pull_date = :billDate
AND    bc.bill_complete = 'Y'
AND    NOT EXISTS (SELECT 1 FROM bill_inv_file i
                   WHERE i.customer_id = bc.customer_id
                   AND   i.system_name = bc.system_name
                   AND   i.bill_date   = bc.bill_pull_date);

-- 3) Latest balance row per invoice (window function)
SELECT * FROM (
  SELECT h.*, ROW_NUMBER() OVER (PARTITION BY invoice_no ORDER BY updated_ts DESC) rn
  FROM   bill_balhistory h
) WHERE rn = 1;

-- 4) CTE + running total of payments per customer
WITH pay AS (
  SELECT customer_id, payment_date, amount FROM bill_payment WHERE system_name = :sys
)
SELECT customer_id, payment_date, amount,
       SUM(amount) OVER (PARTITION BY customer_id ORDER BY payment_date) AS running_paid
FROM pay;
```
*(Column names here are illustrative. Say so if asked: "I'm using representative column names.")*
**Follow-ups:** `NOT EXISTS` vs `NOT IN` (NOT IN breaks when the subquery returns NULL) · `WHERE` vs `HAVING` · `RANK` vs `DENSE_RANK` vs `ROW_NUMBER`.

### Q77: Indexing: composite indexes and when an index isn't used.
**Priority:** MUST PREPARE
**Answer:** "B-tree indexes. For a **composite** index, put equality columns first, then range or sort columns; queries can use the **leftmost prefix**. For example `(system_name, bill_pull_date, customer_id)` serves 'this system and date' and 'this system, date and customer'. A covering index avoids table access. Indexes slow writes and cost space, so index for real query patterns.
**When an index is NOT used:** a function on the indexed column, leading wildcards (`LIKE '%x'`), implicit type conversion, low selectivity, or stale statistics.
**Project, and a good senior talking point:** many MBS queries wrap columns in `TRIM()` and `TRUNC()` (`TRIM(bc.custId) = TRIM(:custId)`, `TRUNC(bill_pull_date) = …`) because the legacy data has padded CHAR values and dates carrying a time part. A plain index on those columns **can't be used** for that predicate. Fixes: clean the data and normalize it on insert (the best fix), create **function-based indexes** on `TRIM(col)` / `TRUNC(col)`, or rewrite date filters as ranges (`col >= :d AND col < :d + 1`)."

### Q78: A query became slow. How do you tune it? (your Bill Preview story)
**Priority:** MUST PREPARE
**Answer:** "Reproduce it with a representative input → capture the actual SQL (enable SQL logging, or get it from the DBA) → **execution plan** (`EXPLAIN PLAN` / `DBMS_XPLAN`) → look for full scans on big tables, bad join order, cartesian joins, row explosions (duplicates) and sorts → check indexes and statistics → fix the query shape first (filter early, select only needed columns, remove the duplicate-producing join or add `DISTINCT` deliberately), then indexes → verify **correctness** (same rows) and time, in a test environment.
**Project:** the Bill Preview endpoint `getUnbilledCarrierDetails`: I refactored the JPQL to filter tightly on cycle state (not run, not released, not in media, for this system and month), return distinct customers, and order predictably. On the Bill Process tab I added role-based access, descending sort and a 7-day filter, then removed the filter while keeping the sort. **Correctness first:** in billing, a faster query that hides a record is worse than a slow one."
**Avoid:** Before/after timings unless you actually measured them.

### Q79: ACID and isolation levels. What does Oracle do by default?
**Priority:** MUST PREPARE
**Answer:** "Atomicity, Consistency, Isolation, Durability. Isolation levels and the anomalies they prevent: READ UNCOMMITTED (dirty reads), READ COMMITTED (non-repeatable reads remain), REPEATABLE READ (phantoms remain in the standard), SERIALIZABLE. **Oracle's default is READ COMMITTED with MVCC**: readers don't block writers, it never allows dirty reads, and each statement sees a consistent snapshot. PostgreSQL's default is also READ COMMITTED; **MySQL InnoDB's default is REPEATABLE READ**. For check-then-act races (like the rerun claim), don't rely on isolation. Use a conditional update or `SELECT … FOR UPDATE`."

### Q80: Deadlocks in the database: cause, detection, fix.
**Priority:** SHOULD PREPARE
**Answer:** "Two transactions each hold a lock the other needs. Oracle detects it and rolls back one statement (ORA-00060) and writes a trace file. Common causes: updating the same rows in a **different order** in two code paths; long transactions; **unindexed foreign keys** (in Oracle these can cause wider locks on child tables). Fix: consistent access order, short transactions, indexes on FK columns, and retry the victim transaction if the operation is idempotent.
**Project risk:** a rerun deleting compute rows while another session (another rerun, or a stuck batch) touches the same cycle. The flags keep billing and rerun apart, and an atomic rerun claim removes the rerun-vs-rerun case."

### Q81: Normalization vs denormalization: an example from your project?
**Priority:** SHOULD PREPARE
**Answer:** "Transaction data is normalized (charges, taxes and payments in their own tables with keys). The invoice record stores **totals computed at generation time** (current charges, taxes, expenses) in `BILL_INV_FILE`: a deliberate denormalized snapshot. An issued invoice must not change if reference data changes later, and reads for display and downstream are fast. That's also why rerun must delete and regenerate the invoice row rather than just recompute a line."

### Q82: How do constraints help with correctness and duplicates?
**Priority:** SHOULD PREPARE
**Answer:** "Primary and unique keys are the only **race-proof** duplicate check. A 'select, then insert if absent' check has a gap between the two statements. With a unique constraint, the second insert fails with `DataIntegrityViolationException`, which you handle as 'already processed'. ATC's BRIM payment poller does exactly that *(existing code)*. Also: FKs for referential integrity, `NOT NULL` and `CHECK` for flag values (`'Y'/'N'`)."

### Q83: Stored procedures: yes or no?
**Priority:** AWARENESS
**Answer:** "Pros: set-based work close to the data, fewer round trips, DBA-owned. Cons: business logic split between Java and PL/SQL, harder to unit test and version, vendor lock-in. Some legacy MBS report generation called stored procedures through JDBC `CallableStatement`. I keep business rules in Java and use procedures for heavy set-based data operations where the DBA team owns them."

### Q84: Connection pooling. How do you size it, and what happens when it's exhausted?
**Priority:** MUST PREPARE
**Answer:** "HikariCP (Boot's default). The MBS API ran with **max 30 / min idle 10**, a 30 s connection timeout, and **leak detection at 20 s**. More connections isn't better: the database has finite CPU, so the pool should be modest and queries fast. **Exhaustion symptoms:** requests hang, then `SQLTransientConnectionException: Connection is not available, request timed out after 30000ms`. **Causes:** slow queries holding connections, a leak (a connection not returned), long transactions wrapping remote HTTP calls, or too many threads. **Diagnose:** Hikari pool metrics and logs, leak-detection stack traces, a thread dump (threads waiting in `HikariPool.getConnection`), and active sessions and long-running SQL on the DB side. **Fix:** kill or tune the slow SQL, fix the leak, and keep remote calls out of transactions (the ATC edit design)."

### Q85: Oracle vs PostgreSQL/MySQL: what would change if the client uses PostgreSQL?
**Priority:** SHOULD PREPARE (JD)
**Answer:** "Mostly SQL dialect and types, while JPA hides a lot: `ROWNUM` / `FETCH FIRST` vs `LIMIT/OFFSET`; `NVL` vs `COALESCE`; **Oracle treats `''` as NULL, PostgreSQL doesn't**; Oracle `DATE` includes a time component (which is why MBS uses `TRUNC`), while PostgreSQL has `date` and `timestamp`; sequences vs `IDENTITY`/`SERIAL`; PL/SQL vs PL/pgSQL. Test native queries first; they're where migrations break. I've used PostgreSQL with Spring Data JPA in the fintech project."

---

# PART 9 — SYSTEM DESIGN (L9 level, anchored in your domain)

> Framework to say out loud: **clarify → functional → non-functional → high-level → APIs → data → deep dive → failures → security → monitoring → scale → trade-offs.** Name trade-offs explicitly and check priorities with the interviewer.

## SD1: Design a billing-cycle processing platform with safe rerun (your home turf)
**Priority:** MUST PREPARE

1. **Clarify:** How many customers and subsystems? Monthly cycles? SLA for a cycle to finish? Is human review required before release? External tax dependency? (Assume 70k customers, about 20 subsystems, a cycle must finish within a few hours, review required.)
2. **Functional:** schedule cycles per subsystem; rate usage; OCC/expense with approvals; tax; apply payments and adjustments; generate invoices; review; rerun (system or customer) before release; release to delivery and finance.
3. **Non-functional:** **correctness over availability**, auditability, idempotency, recoverability, isolation between subsystems (blast radius), observability; throughput enough to meet the cycle window.
4. **High level:**
   ```
   Scheduler ─▶ Cycle Orchestrator ─▶ work queue (per customer) ─▶ Stage workers
                     │                                             (rate → OCC → tax → balance → format)
                     ▼                                                     │
              Cycle/Customer state DB  ◀────────────────────────────────────┘
                     │                                 Tax client (timeouts, CB, rate limit)
              Review UI ─▶ Rerun API ─▶ Reset job (idempotent) ─▶ back to orchestrator
                     │
              Release ─▶ outbox ─▶ delivery + finance (BRIM) feeds
   ```
5. **APIs:** `POST /cycles/{sys}/{date}/runs` (202 + runId) · `GET /runs/{id}` · `POST /cycles/{sys}/{date}/reruns` (+ optional `customerIds`) → 202 · `POST /cycles/{sys}/{date}/release`.
6. **Data:** `cycle(sys, date, state, version)`, `customer_cycle(sys, date, cust, stage_state…, attempt, last_error)`, compute tables keyed by **run_id** (versioned, not hard-deleted), `invoice(snapshot totals, run_id)`, `audit(who, what, when, why)`, `outbox`.
7. **Components:** orchestrator (state machine), stateless workers (horizontally scalable), tax client, invoice renderer, review UI, release publisher.
8. **Failures:** a customer failure is isolated (retry transient errors with backoff, then mark `FAILED` for review); tax down → the circuit opens → customers park in `TAX_PENDING` and are auto-retried later; a worker crash is recovered by leases/visibility timeouts; reruns are idempotent because they claim the cycle with an atomic state transition.
9. **Security:** role-based access (only billing ops can rerun or release), audit of every rerun and release, service-to-service auth, PII in invoices protected, no sensitive data in logs.
10. **Monitoring:** per-stage throughput, failures, tax latency, cycle completion ETA, rerun counts; alerts on the cycle SLA and on stuck customers.
11. **Scale:** partition by subsystem and customer; parallel workers with bounded concurrency per external dependency; batch DB writes; index on (sys, date, cust); archive old compute rows.
12. **Bottlenecks:** tax API quota, DB write throughput, PDF generation (CPU): scale renderers separately and generate asynchronously.
13. **Trade-offs:** versioned compute rows (audit and compare, but more storage) vs hard delete (simple); async jobs (resilient, but needs a status API) vs sync (simple); a message queue (decoupling, but eventual consistency) vs a DB-driven work table (simpler, transactional).
**How it maps to MBS:** "This is MBS's model made explicit: control tables become the state machine, per-customer stage flags become customer_cycle, and rerun's delete-and-reset becomes a versioned reset. I'd keep the human review and the media gate exactly as they are."

## SD2: Design an idempotent payment-posting API (no duplicate payments)
**Priority:** MUST PREPARE
- **Requirements:** post payments from the UI and from upstream feeds (BRIM); an invoice may be missing; retries are common; money must never be double-applied.
- **API:** `POST /payments` with an **`Idempotency-Key` header** (or a natural key: source system + source payment ID). The response is stored per key and replayed on retry.
- **Data:** `payment(id, source, source_payment_id UNIQUE, customer, invoice_no NULLABLE, amount, status)`, `idempotency(key UNIQUE, request_hash, response, created_at)`, `balance_history`.
- **Flow:** validate → one transaction: insert the idempotency row (a unique violation means it's a retry, so return the stored response) → insert the payment → update the invoice balance with a **conditional update** or row lock → write the audit and outbox rows → commit → the outbox feeds downstream.
- **No invoice number:** route to the account level or an unapplied-cash queue for review; never "apply to the first open invoice".
- **Concurrency:** two payments against one invoice → lock the invoice balance row (`FOR UPDATE`) or use optimistic versioning with a retry.
- **Failures:** DB down → 503, and the client retries with the same key; downstream down → the outbox retries.
- **Trade-offs:** a stored-response table (exact replay, but storage and TTL cleanup) vs natural-key uniqueness only (simpler, but no response replay).
**Project connection:** MBS payment posting validates the customer and invoice and checks for duplicates before inserting; ATC's BRIM poller relies on a unique constraint. *(Existing code in the platform, not yours; say "the platform did X, I'd formalize it as Y".)*

## SD3: Design a resilient integration with an external tax service
**Priority:** SHOULD PREPARE
- **Constraints:** a third-party API with a quota, variable latency, OAuth2 tokens that expire, and results that must be exact.
- **Design:** a tax-client component with a **cached OAuth token** (refreshed before expiry, single-flight refresh), **timeouts**, **retries** for 5xx or timeouts with backoff, a **circuit breaker**, a **bulkhead** (a dedicated pool), and **rate limiting** to the quota. Optionally cache **jurisdiction/geocode lookups** by normalized address (tax *rates* change, so use a short TTL or cache only the geocode).
- **Failure behavior:** don't guess; mark the customer `TAX_PENDING` and retry later; alert when the pending count crosses a threshold.
- **Correctness:** store the request and response for audit and disputes; validate that the returned jurisdiction matches the input address, and flag mismatches for review.
- **Security:** client secrets in a vault or secret store; TLS; parsing XML with **XXE protection** (the platform's tax adapter disables DTDs and external entities).
- **Trade-off:** caching reduces load and latency but risks stale tax rules, so cache the geocode, not the rate.

## SD4: How would you modernize or migrate this platform (to the cloud)?
**Priority:** SHOULD PREPARE (connects to the JD's AWS/Azure)
- **Step 0, why:** reduce operational risk (single-instance GUI pod, VM-hosted WARs), speed up releases, add observability. Don't migrate for its own sake.
- **Lift and improve:** containerize the API like the GUI (Docker + Kubernetes: AKS/EKS or the existing Rancher clusters), externalize config and secrets (Key Vault / Secrets Manager), add Actuator probes and metrics, run ≥2 replicas behind an ingress or load balancer.
- **Data:** keep Oracle at first (a managed or DBA-run instance); later consider a PostgreSQL migration (dialect work, see Q85); set up backups, a DR replica and PITR.
- **Strangler:** make the API the only writer; move the legacy batch into Spring Boot services (already underway while I was there with billing batch and rerun); carve bounded contexts.
- **Async:** outbox + a message broker for BRIM feeds and re-sync; job-based reruns.
- **Delivery:** one CI/CD template (build → test → Sonar → image → deploy → smoke test → promote), with blue/green or rolling deployments and rollback.
- **Risks:** billing correctness during migration. Run old and new **in parallel on a copy of real cycles and compare invoice totals** before switching.

## 9.5 Quick answers to the standard scaling questions

| Question | 20-second answer |
|---|---|
| **How would you scale this application?** | "Stateless app tier horizontally behind a load balancer; scale the batch by partitioning work per subsystem and customer across workers; fix the DB first (indexes, query shape, batch writes), then read replicas and archiving; protect external dependencies with bounded concurrency." |
| **Traffic increases 10x?** | "Find the first bottleneck by measuring. For MBS: DB queries (TRIM/TRUNC on indexed columns), the connection pool, and the tax API quota. Fix queries, add replicas and parallel workers, rate-limit and cache the geocode lookups, and move long operations to async jobs." |
| **Highly available?** | "≥2 replicas across zones, health probes, rolling deploys, no local state (sessions externalized or tokens), a DB with a standby and automatic failover, idempotent jobs so a restart is safe." |
| **Downstream failure?** | "Timeout → retry transient errors → circuit breaker → domain fallback (hold, not guess) → alert → auto-resume." |
| **Make a workflow idempotent?** | "Natural or idempotency keys with unique constraints; state-machine transitions via conditional updates; delete-and-recompute or upsert instead of blind inserts; record processed markers (like MBS's usage flags)." |
| **Prevent duplicate requests?** | "Client idempotency key + a server-side store; a unique DB constraint as the final guard; disable double submit in the UI; for events, dedupe on the event ID." |
| **What would you redesign?** | "The rerun's transaction boundary and concurrency claim, versioned compute data instead of hard deletes, the API as the only DB writer, and metrics-based monitoring (Q14, Q33)." |

---

# PART 10 — CI/CD, DEVOPS & PRODUCTION

> What the evidence shows: **GUI** goes through a Jenkins shared-library pipeline: Maven build → SonarQube → JaCoCo gate → Docker image (Tomcat 9 base) → `kubectl apply` to **Rancher-managed Kubernetes** clusters (dev, dev1, test1, test3, pre-prod, prod, with an authorization step for prod). **API** is a WAR deployed to Tomcat servers per environment. **ATC** uses **GitHub Actions** (your commits are concentrated in `cicd.yml`): config validation, secret retrieval, build/test, artifact handling, SSH connectivity check, **backup**, WAR deploy to Tomcat.

### Q86: Walk me through your CI/CD pipeline and your role in it.
**Priority:** MUST PREPARE
**Answer:** "Different apps had different pipelines because of their history. The **GUI** ran on Jenkins: Maven build, SonarQube analysis and a JaCoCo coverage gate, then a Docker image on a Tomcat 9 base, deployed with kubectl to Rancher-managed Kubernetes clusters per environment, with an explicit approval step for production. The **ATC** app used **GitHub Actions**, which is where I did most of my pipeline work: validating configuration before deployment, pulling secrets from the CI secret store, build and test, then deploying the WAR to Tomcat over SSH, with a connectivity check first and a **backup of the current version** so rollback was quick. The **core API** was deployed as a WAR to Tomcat servers per environment. My role was pipeline changes on ATC and deployment validation, not owning release management for the platform."
**Follow-ups:** How do you roll back? (Q90) · Why not the same pipeline for all? ("History. I'd converge on one template.")
**Avoid:** Helm, ArgoCD, HPA (not evidenced).

### Q87: Git workflow, code review and branching.
**Priority:** SHOULD PREPARE
**Answer:** "Feature branches per ticket (Jira IDs in commits, e.g. CPPEWMB-5443), some longer-lived stabilization branches for bigger efforts (the batch and BRIM work had one), pull requests reviewed by the lead or a peer before merge, then environment promotion. In reviews I look at correctness first (edge cases, transactions, nulls, concurrency), then readability and tests, and I keep comments specific and explain the *why*."
**Follow-up:** Merge vs rebase? → "Rebase my own local branch to keep history clean; never rewrite shared history; merge commits for shared branches."

### Q88: Docker: image vs container, and what's good about your GUI's Dockerfile?
**Priority:** SHOULD PREPARE
**Answer:** "An image is an immutable, layered template; a container is a running instance with a writable layer. The GUI image starts **from the company's standard Tomcat 9 base image** and copies in the WAR. The deployment runs it **as a non-root user** with a **read-only root filesystem**, and Tomcat's logs, temp and work directories mounted as `emptyDir` volumes, with privilege escalation disabled. Good practices beyond that: small base images, multi-stage builds, pinned versions and immutable tags (never `latest` in prod), and no secrets baked into images."

### Q89: Kubernetes: what happens when a pod crashes? Explain probes, resources, rolling updates.
**Priority:** MUST PREPARE
**Answer:** "A Deployment manages ReplicaSets, which keep N pods running. If the container exits, the kubelet restarts it; repeated failures give **CrashLoopBackOff** with increasing back-off. **Liveness** probe failing → restart. **Readiness** failing → removed from Service endpoints, no traffic, no restart. **Requests** drive scheduling; **limits** cap usage, and exceeding the memory limit gets the pod **OOMKilled (exit 137)**. A rolling update brings up new pods, waits for readiness, then terminates old ones (`maxSurge` / `maxUnavailable`), and `kubectl rollout undo` rolls back.
**Project:** the GUI pod runs **one replica** behind a ClusterIP Service and an NGINX ingress with TLS, with the Spring profile set per environment. One replica means a pod restart is a short outage. I'd run at least two with readiness probes."
**Follow-ups:** Pod keeps restarting → Part 16, S6 · ConfigMap vs Secret → config vs sensitive values (base64 is *not* encryption; use a real secret store).

### Q90: How do you deploy safely and roll back?
**Priority:** SHOULD PREPARE
**Answer:** "Rolling, blue/green or canary depending on risk. Deploy an **immutable artifact** (image tag or versioned WAR), smoke-test after deploy, and watch error rates. Rollback = redeploy the previous tag (`kubectl rollout undo`), or for Tomcat restore the **backed-up WAR** (the ATC pipeline takes that backup). **Database changes must be backward compatible** (expand then contract): add a column, deploy code that handles both, migrate, remove the old column later. That way a code rollback never breaks on the schema. For billing I'd also avoid deploying during an active billing window."

### Q91: How were environments and quality gates managed?
**Priority:** SHOULD PREPARE
**Answer:** "Dev → test (several test environments) → pre-prod → prod, each with its own Spring profile, database and cluster credentials. Quality gates on the GUI pipeline: SonarQube analysis and a JaCoCo coverage threshold. Production needed an explicit approval step. Promotion was the same artifact, different config. As a lead I'd keep 'build once, deploy many', and make pre-prod data realistic enough for billing regression, like comparing invoices for a sample cycle."

### Q92: Your general production-troubleshooting approach.
**Priority:** MUST PREPARE
**Answer (use it as the skeleton for every scenario in Part 16):**
1. "**Impact first:** who's affected, is money or billing at risk, is it getting worse? Communicate early.
2. **What changed?** Deployment, config, data, traffic, a downstream system, infrastructure.
3. **Signals:** error logs (first error, not the last), metrics (latency, errors, CPU/memory, pool usage), DB (locks, long SQL), dependency health.
4. **Isolate the layer:** client → ingress → app → DB → external.
5. **Mitigate before root cause:** roll back, disable the feature, scale, or pause the batch or rerun. Stop the bleeding without destroying evidence (take thread or heap dumps first).
6. **Root cause and fix** through the normal pipeline, with a test that reproduces it.
7. **Post-incident:** timeline, root cause, why detection was late, and actions (alerting, guards, runbook)."

---

# PART 11 — SECURITY

### Q93: Authentication vs authorization, and session vs JWT: which did your projects use?
**Priority:** MUST PREPARE
**Answer:** "AuthN is who you are; AuthZ is what you're allowed to do. **MBS GUI:** LDAP bind against Active Directory over LDAPS, the user stored in the **HTTP session**, a servlet filter returning 401 for a missing or expired session, and role-based access on screens (I added role-based access to the Bill Process tab). **Fintech project:** **stateless JWT** with Spring Security: login checks BCrypt-hashed credentials, issues a signed token with the user and roles, and a filter validates the signature and expiry on each request and sets the `SecurityContext`, then role rules apply.
**Trade-off:** sessions are simple and easy to revoke, but they're server state (sticky sessions or a shared store when scaling). JWT is stateless and scales horizontally, but revocation is hard (short expiry + refresh tokens + a denylist), and the tokens must be protected in the browser."

### Q94: JWT in depth.
**Priority:** SHOULD PREPARE
**Answer:** "Three parts: header, payload (claims: `sub`, `roles`, `exp`, `iat`, `iss`, `aud`), signature. The payload is **encoded, not encrypted**, so no secrets go in it. HS256 uses a shared secret; RS256/ES256 use a private key to sign and a public key to verify, which is better when many services verify tokens. Validate the signature, algorithm, `exp`, `iss` and `aud`. Short-lived access tokens plus refresh tokens. Store them in an httpOnly Secure cookie (with CSRF protection) or in memory, not `localStorage`, because of XSS."

### Q95: OAuth2 and OIDC concepts. Where did you see them?
**Priority:** SHOULD PREPARE
**Answer:** "Roles: resource owner, client, authorization server, resource server. Grants: **authorization code + PKCE** for user login in web and mobile apps; **client credentials** for service-to-service calls. OIDC adds an ID token for user identity on top of OAuth2. **Project:** ATC calls the MBS API with client-credentials bearer tokens, and the tax service needs OAuth2 client credentials. The tokens are cached and refreshed before expiry, so we don't request a new token on every call."

### Q96: OWASP basics in your code: SQL injection, XSS, CSRF, CORS, XXE, secrets.
**Priority:** MUST PREPARE
**Answer:**
- "**SQL injection:** every repository query uses bound parameters (`:param`); no string concatenation into SQL.
- **XSS:** encode output. In JSP use `<c:out>` / `fn:escapeXml`; React escapes by default (avoid `dangerouslySetInnerHTML`).
- **CSRF:** relevant for session-cookie apps like the GUI, so use CSRF tokens and SameSite cookies. Much less of an issue for header-token APIs.
- **CORS:** allow only known origins. ATC's backend allowed all origins and had permissive security config at the time; I'd tighten that.
- **XXE:** the platform's tax adapter disables DTDs and external entities when parsing XML responses.
- **Log injection:** my fix (Q97).
- **Secrets:** not in property files or images; use a vault or secret store. Some legacy configs held credentials in properties; moving them out is a standard hardening item."

### Q97: Tell me about the security fix you did.
**Priority:** MUST PREPARE
**Answer (STAR):**
- **S:** "In the MBS GUI, several controllers and services logged user-supplied values (customer IDs, search inputs) directly."
- **T:** "That allows **log injection** (CWE-117): a value containing CR/LF characters can forge fake log lines, which undermines audits and misleads investigations. *(It was flagged by ___: a scan or a review. Say which.)*"
- **A:** "I fixed the affected paths (customer controller, legacy data service, error-log service, state-detail service): neutralized CR/LF and control characters in user-controlled values before logging, and used parameterized logging (`log.info(\"… {}\", value)`) instead of concatenation. I checked that normal log output was unchanged."
- **R:** "The finding was closed. I also started checking logging of user input in code reviews."
**Follow-ups:** Why not just validate input? ("Validation enforces business rules; encoding for the log context is a separate concern. Defense in depth.") · What else shouldn't be logged? (passwords, tokens, full account numbers, personal data)
**Avoid:** "Pen test confirmed…", "trained 15 developers…" (unless true).

### Q98: How would you secure the rerun endpoint?
**Priority:** SHOULD PREPARE
**Answer:** "Authenticate the caller (service token or user). **Authorize** by role, e.g. only a billing-ops lead can trigger a system-level rerun. **Audit** who flagged and who triggered, when and why (today the flag update doesn't record the user). Validate inputs strictly. Rate-limit or lock per cycle to prevent repeated triggers. A confirmation step in the UI showing what will be reset."

---

# PART 12 — TESTING & CODE QUALITY

### Q99: What's your testing strategy for a Spring Boot service?
**Priority:** MUST PREPARE
**Answer:** "A pyramid. **Unit tests** (JUnit 5 + Mockito) for business logic like the rerun outcome rules. **Slice tests:** `@WebMvcTest` + MockMvc for controllers (status mapping, validation, error body) and `@DataJpaTest` for repository queries. **Integration tests:** `@SpringBootTest`, ideally against a real database engine with Testcontainers, because H2 hides dialect issues like Oracle's `TRUNC`/`TO_CHAR`. **Contract tests** for APIs other apps consume. Then QA regression and **UAT** with billing ops. For billing specifically, a **golden-data regression**: run a known cycle and compare invoice totals with the expected values."
*(State honestly which layers you personally wrote; don't claim Testcontainers if you didn't use it. Present it as what you'd standardize.)*

### Q100: Show me how you'd unit test the partial-success rule.
**Priority:** SHOULD PREPARE
```java
@ExtendWith(MockitoExtension.class)
class BillRerunServiceTest {
  @Spy @InjectMocks BatchBillRerunServiceImpl service;   // or better: inject a mocked RerunSteps bean
  @Mock BatchCommonModuleService common; @Mock MBSBillPullDetailRepository pullRepo; // ...

  @Test
  void oneCycleFails_othersSucceed_returnsPartial() {
    when(common.isSystemValid("LEXCIS")).thenReturn(true);
    when(pullRepo.findBillReRunIndicator("LEXCIS")).thenReturn(List.of(cycle(d1), cycle(d2)));
    doReturn(true).when(service).billReRun("LEXCIS", d1);
    doThrow(new RuntimeException("ORA-00060")).when(service).billReRun("LEXCIS", d2);

    BillRerunResult r = service.runBillReRunForSystem("LEXCIS");

    assertTrue(r.isPartialSuccess());
    assertEquals(List.of(d1), r.getSuccessfulCycles());
    assertEquals(List.of(d2), r.getFailedCycles());
  }
}
```
**Point to make:** "Needing a spy here is a design smell. With the steps in a separate bean, I'd mock that bean instead. Testability and the transaction-proxy fix point the same way."

### Q101: SonarQube, static analysis, coverage: how do you use them without gaming them?
**Priority:** SHOULD PREPARE
**Answer:** "The quality gate applies to **new code**: no new bugs or vulnerabilities, security hotspots reviewed, coverage on new code above a threshold (JaCoCo feeds Sonar; the GUI pipeline had both). Coverage is a floor, not a goal: 80% line coverage with no assertions is worthless. In review I check that tests would **fail if the logic broke**. Static analysis also catches things like string `==` comparisons, swallowed exceptions and log injection."

### Q102 (L9): A developer delivers working code with weak test coverage, and the deadline is close. What do you do?
**Priority:** MUST PREPARE
**Answer:** "Separate the risk from the person. First, **assess risk**: is it a critical path (billing, payments) or low-risk UI text? For critical logic, missing tests are a release risk, so I'd pair with the developer to add tests for the core and edge cases, splitting the work so it fits the deadline. If time truly doesn't allow, I agree an explicit, **visible** exception with the lead or PO: a ticket in the next sprint and extra manual QA on those paths, not a silent skip. Then fix the cause: make test expectations part of the definition of done, give an example test template, and check tests in reviews early, not on the last day."

---

# PART 13 — CLOUD (JD: AWS or Azure)

### Q103: What is your AWS/Azure experience? (answer honestly)
**Priority:** MUST PREPARE
**Answer:** "My hands-on strength is application-side: Spring Boot services, containers, and deploying to **Kubernetes clusters managed through Rancher**, plus Tomcat-based deployments. The underlying infrastructure at Lumen was run by platform teams; the cluster and DB naming suggested Azure hosting, but I didn't provision cloud resources myself. I hold the **Azure Fundamentals (AZ-900)** certification, and earlier I used a few AWS services at the application level (S3, DynamoDB) *(only as your resume states)*. I know how our components map to managed cloud services and I'm comfortable ramping up quickly on the client's cloud."
**Avoid:** "I designed our Azure architecture" / "I managed EKS clusters."

### Q104: Map your stack to cloud services.
**Priority:** SHOULD PREPARE
| Our component | Azure | AWS |
|---|---|---|
| Spring Boot WAR on Tomcat VMs | App Service / VMs / AKS | Elastic Beanstalk / EC2 / EKS |
| GUI on Rancher Kubernetes | **AKS** | **EKS** |
| NGINX ingress + TLS | App Gateway / AGIC / Front Door | ALB + Ingress Controller / CloudFront |
| Oracle DB | Oracle on VM / Oracle Database@Azure; or Azure DB for PostgreSQL | RDS for Oracle; or RDS/Aurora PostgreSQL |
| Properties + DB-driven config | App Configuration | AppConfig / Parameter Store |
| Credentials | **Key Vault** | **Secrets Manager** / KMS |
| Docker registry (Nexus) | ACR | ECR |
| Email alerts / logs | Azure Monitor, App Insights, Log Analytics | CloudWatch, X-Ray |
| Scheduled batch | Functions / Container Apps jobs / AKS CronJob | EventBridge Scheduler + Batch/ECS / EKS CronJob |
| Messaging (Kafka / ActiveMQ) | Event Hubs (Kafka API) / Service Bus | MSK / Amazon MQ / SQS |
| PDF / file storage | Blob Storage | S3 |
| LDAP / AD | Entra ID | IAM Identity Center / Cognito |

### Q105: Core cloud concepts, one line each.
**Priority:** AWARENESS
- **IAM / RBAC:** least privilege; workloads use managed identities or roles, not keys in code.
- **Networking:** VNet/VPC, subnets (private for apps and DB), security groups/NSGs, private endpoints to managed services.
- **Load balancing:** L7 (path/host routing, TLS) vs L4.
- **Autoscaling:** HPA on CPU or custom metrics for pods; VM scale sets / ASGs. Batch scales on queue depth.
- **HA:** multiple instances across availability zones, health checks, stateless apps.
- **DR:** backups, cross-region replicas; know **RPO** (acceptable data loss) and **RTO** (acceptable downtime).
- **Managed DB:** automated backups, PITR, read replicas, failover.
- **Monitoring:** metrics + logs + traces + alerts on SLOs.
- **Cost:** right-sizing, autoscaling, reserved capacity; turn off non-prod at night.

---

# PART 14 — REACT / FRONTEND

### Q106: How strong are you in React / Angular?
**Priority:** MUST PREPARE
**Answer:** "I'm a backend engineer first. In React, I've worked in the ATC app at the integration level. For the edit-flow refactor, I changed the edit modal and API module so the UI builds one payload containing only the changed sections and calls the single backend endpoint. I'm comfortable reading and changing components, props, state, hooks and API calls, but I wouldn't call myself a frontend specialist, and much of that UI was built with AI-assisted scaffolding that I reviewed. I haven't worked with **Angular** in a project; I understand its model (components, services, DI, RxJS observables) and could contribute to its integration layer. On a full-stack team I'd own the API contract and work closely with the frontend developers."

### Q107: React basics you should be able to explain.
**Priority:** AWARENESS
- **Components / props / state:** props are inputs (read-only); state is local and changes trigger re-render.
- **Hooks:** `useState`, `useEffect` (side effects such as fetching; dependency array; cleanup), `useMemo`/`useCallback` (avoid needless recomputation or re-renders), `useContext`.
- **API integration:** an Axios/fetch module per domain (ATC had `projectEditApi.js` etc.), loading and error states, cancel on unmount.
- **Auth:** attach the token or cookie; handle 401 by redirecting to login; role-based rendering is UX only, and **the backend must enforce**.
- **Errors:** error boundaries for render errors, a friendly message plus correlation ID for API errors.
- **Performance:** stable `key`s in lists, memoization, code splitting, avoiding huge re-renders.
- **Angular mapping:** components ≈ components; services + DI ≈ hooks/context + API modules; RxJS `Observable` ≈ promises with streams.

---

# PART 15 — L9 TECHNO-MANAGERIAL Q&A

> **Positioning for this whole part:** you weren't a people manager. You've led **at feature level**: owning a workflow end to end, coordinating with other developers, QA, the billing ops users and dependent teams, and reviewing work. Say that plainly, then show *how you would lead a team*, grounded in what you've done. L9 panels respect "here's what I did, and here's how I'd scale it" far more than claimed titles.
>
> **STAR discipline:** Situation 1 sentence · Task 1 sentence · **Action 3–5 sentences using "I"** · Result 1–2 sentences + learning.
> Items marked *(use only if true)* are templates. Replace them with a real memory or drop them.

## 15.1 Ownership

### M1: Tell me about a feature you owned end to end.
**Priority:** MUST PREPARE → **Bill Rerun, Q31** (90 seconds). Close with: "What I'm proudest of is that the agents stopped needing to come back to us after a rerun."

### M2: Tell me about a difficult technical decision you made.
**Priority:** MUST PREPARE
**Answer (ATC edit flow):**
- **S:** "In ATC, a project edit (cancel, statement-of-work change, billing change, amount change) was spread across separate endpoints. The UI made several calls for one business action, so a failure halfway left a project partly updated, and billing changes also had to sync to MBS."
- **T:** "Consolidate it into one API without making it fragile."
- **A:** "I made it **one composite request** with optional sections and strict validation (for example, cancellation can't be combined with other edits). The key decision was the transaction boundary. Wrapping everything, including the MBS sync call, in one `@Transactional` would hold DB locks during a remote HTTP call, and would either roll back a valid local change because of a remote hiccup, or commit locally while the remote side silently failed. So I ran the local changes in a **`TransactionTemplate`** block and made the MBS sync a **separate step after commit, only when billing data actually changed**, with the sync result reported back in the response. On the React side, the modal sends only the changed sections."
- **R:** "One call per business action, no partial local updates, and a clear sync status. **Trade-off I accepted:** if the sync fails, the two systems differ until it's re-synced. I'd close that gap with an outbox and a retry job."
**Follow-ups:** Why not a distributed transaction? (2PC across HTTP isn't practical; saga/outbox instead) · How did you test the invalid combinations?

### M3: Tell me about a production issue you handled.
**Priority:** MUST PREPARE → **Part 16, S1** (the rerun failure), or the stale-approval data issue (Q31, Action part).

### M4: Tell me about a mistake or failure.
**Priority:** MUST PREPARE *(use only if true; candidate below, confirm the details)*
**Candidate: the Bill Process 7-day filter.**
- **S:** "I added role-based access, descending sort and a default 7-day date filter to the Bill Process tab to make it faster and more focused."
- **T:** "The filter was my call, to reduce clutter and query load."
- **A:** "Once it was in use, *(billing agents needed to see older cycles still in review; replace with the real reason)*. I owned it: I removed the 7-day filter quickly, kept the sort and the access control that were useful, and talked to the users about what view they actually needed."
- **R/Learning:** "I changed my practice: **changes to default behavior on operational screens get validated with real users before release**, and filters are opt-in rather than silently limiting data. In billing, hiding a record is worse than being slow."
**Avoid:** A fake failure, or one with no learning. Also avoid a catastrophic one you then have to defend.

### M5: How do you handle ambiguous requirements?
**Priority:** MUST PREPARE
**Answer (BRIM legacy product codes):** "The BRIM charge-feed change for long-distance legacy product codes (CPPEWMB-5443) needed different handling for interstate and intrastate, plus a new S4 cost-center validation, and the rules weren't fully written down. I wrote out concrete examples (product code × jurisdiction → expected RAC and cost center), got them confirmed by *(the BA / finance SME / my lead; say who)*, and wrote the code against those examples. Where the rules were unknown, I **failed safe**: if a cost center can't be mapped, we don't send the charge rather than send a wrong one. The approach went through a review round with my lead before it was finalized.
**General approach:** clarify with examples, write down assumptions, get sign-off, build the smallest thing that proves the understanding, and default to the safe behavior when unsure."

## 15.2 Team leadership

### M6: How do you allocate tasks within a team?
**Priority:** MUST PREPARE
**Answer:** "I look at three things: **risk**, **skills** and **growth**. Critical-path or high-risk items (billing calculations, anything touching money) go to the most experienced person, or they pair with someone who's learning it. Well-defined tasks are growth opportunities for juniors, with a named reviewer. I keep work visible on the board, break stories into pieces that finish within a day or two, and check load weekly so no one is the bottleneck. I also avoid a single point of knowledge. After Bill Rerun, for example, I'd make sure at least two people could support it."

### M7: How do you do code reviews? What do you look for?
**Priority:** MUST PREPARE
**Answer:** "Correctness first: edge cases, nulls, transaction boundaries, concurrency (like shared state in singletons), idempotency for anything retried, error handling that doesn't swallow exceptions. Then security (input validation, parameter binding, no sensitive data in logs), then tests that would actually fail if the logic broke, then readability and consistency. I review in small batches, explain the *why* with a suggested fix, separate 'must fix' from 'nit', and do a quick call when a thread goes back and forth. My own code went through the same process; the BRIM charge change had a review round with my lead that improved the design."

### M8: How do you mentor or support junior developers? Give an example.
**Priority:** MUST PREPARE
**Answer:** "*(Use only if true; real example: explaining the billing flow and the rerun reset steps to a new teammate, writing a short KT note or runbook for rerun troubleshooting, pairing on a defect.)* The pattern I use: give context first (the billing flow and why correctness matters), pair on the first task, then let them drive the next one while I review, and ask questions in reviews ('what happens if this runs twice?') instead of just giving answers. I measure success by whether they can handle the next similar issue without me."
**Avoid:** "I mentored 3 juniors to Rising Star…" unless it really happened.

### M9: A junior developer caused a production issue. What do you do?
**Priority:** SHOULD PREPARE
**Answer:** "Fix first, blame never. I'd lead the mitigation with them involved, so they learn the recovery path: roll back or apply a data fix and communicate impact. Then a **blameless review**: why did the process allow it (a missing test, a missing review check, a risky deploy timing)? The fixes are process fixes, and I let the junior implement the preventive fix so the learning sticks. Privately, I make sure they're OK and understand what to do differently."

### M10: Two developers disagree on an approach. How do you resolve it?
**Priority:** MUST PREPARE
**Answer:** "Move it from opinions to criteria. I ask each to state the approach with its trade-offs against what matters here (correctness, operability, effort, risk, reversibility), and if needed we spike both for an hour or two. If it's still a tie, pick the **simpler and more reversible** option, record the decision briefly (a short ADR or a ticket comment), and move on. Once decided, everyone commits. **Example-sized version:** response codes for partial rerun: one view wanted 500 for any failure, another wanted a partial status. The criterion was 'what does the operator need to decide next', which led to an explicit partial result naming the failed cycles."
*(Use the example only if such a discussion happened; otherwise keep the approach part.)*

### M11: How do you ensure quality across the team, not just in your own code?
**Priority:** SHOULD PREPARE
**Answer:** "Make quality the default path: a clear definition of done (tests for business logic, reviewed PR, Sonar gate green, updated runbook for ops-facing changes); a PR checklist for our common risks (transactions, idempotency, logging of user input); automated gates in CI; and learning from incidents by turning each post-incident action into a test or a check. I also keep an eye on escaped defects per sprint as the signal that matters."

## 15.3 Technical decision-making

### M12: How do you choose between two technical approaches or evaluate a new technology?
**Priority:** MUST PREPARE
**Answer:** "Start from the requirement and the constraints, not the technology. Criteria: does it solve the actual problem, can the team support it, how does it behave in production (monitoring, failure modes), cost, migration risk, and how reversible it is. For anything non-trivial, a time-boxed PoC with a success criterion decided upfront, and a one-page decision record. **Example:** for the ATC edit flow the choice was a single `@Transactional` vs a programmatic transaction plus post-commit sync. The deciding criterion was production behavior: not holding DB locks across a remote call."

### M13: Technical debt vs delivery: how do you balance them?
**Priority:** MUST PREPARE
**Answer:** "I make debt **visible and priced**: a ticket with the risk in business terms ('this rerun path isn't protected against concurrent triggers; impact: duplicate work or lock waits during billing'). Debt that threatens correctness or security in a critical path gets fixed now or gets a guard. Other debt goes in a budget, around 10–20% of each sprint, or rides along with feature work in the same area (the 'touch it, improve it' rule). I don't sneak refactors into feature PRs without agreement, because that surprises reviewers and testers."

### M14: You disagree with your lead's or architect's design. What do you do?
**Priority:** MUST PREPARE
**Answer:** "Raise it early, privately and with evidence: what risk I see, a concrete scenario, and an alternative with its cost. If they have context I don't (client constraints, timelines), I listen. If they still choose their design, I **disagree and commit**: implement it well and, if the risk is real, add a mitigation such as a guard, a log or an alert, and write it down. Escalate only if it's a correctness, security or compliance issue that's being ignored."

## 15.4 Client and stakeholder management

### M15: The client gives you an unrealistic deadline.
**Priority:** MUST PREPARE
**Answer:** "Don't just say yes or no; give options. Break the work down, estimate with the team, and show what *can* be done by the date: (a) reduced scope, the must-haves first; (b) the full scope by a later date; (c) more people, only if onboarding doesn't slow things down. Make risks explicit, especially quality risks in critical flows like billing. Agree the plan in writing and track it visibly so there are no surprises. Quality in the critical path isn't negotiable; scope is."

### M16: A requirement changes mid-sprint.
**Priority:** MUST PREPARE
**Answer:** "Understand the *why* and the urgency first. If it's urgent and small, swap it for work of equal size with the PO's agreement. If it's big, it goes to the backlog for the next sprint. Check its impact on work already done and on testing. **Real example:** the Bill Process filter behavior changed after users saw it; we adjusted quickly and kept the parts that worked *(see M4)*."

### M17: The client escalates about a production issue.
**Priority:** MUST PREPARE
**Answer:** "Acknowledge fast and own it without defensiveness. Give a clear status: what's affected, what we're doing, when the next update comes. Stabilize first (workaround or rollback), then root cause. Communicate at the promised intervals even when there's nothing new. Afterwards, share a short RCA with the concrete preventive actions and dates. In billing, the client's real fear is wrong invoices, so I'd tell them explicitly how we're guaranteeing no wrong bill goes out (for example the media gate: nothing is released until it's verified)."

### M18: How do you explain a technical risk to a non-technical stakeholder?
**Priority:** SHOULD PREPARE
**Answer:** "In business terms, with likelihood, impact and options. For example: 'If two people trigger a rerun for the same cycle at the same time, the bill will still be correct, but the run could slow down or need a retry during the billing window. A small change, about two days, prevents it. I recommend we do it before month-end.' No jargon, one recommendation, and the decision is theirs."

### M19: The client asks for something technically risky or infeasible.
**Priority:** SHOULD PREPARE
**Answer:** "Find the underlying need. For example, 'rerun a cycle after invoices are released' is really 'correct a released invoice'. Explain why the literal ask is risky (customers already have the invoice, and finance feeds have consumed it), then offer the safe alternative that meets the need: correct it with **adjustments or credits** through the normal flow. Say yes to the outcome, not to the unsafe method."

## 15.5 Cross-team collaboration

### M20: A dependent team is delaying you.
**Priority:** MUST PREPARE
**Answer:** "Surface it early with a date and an impact. Meanwhile, **decouple**: agree the contract (payload, fields, error cases) and build against a stub or mock so my side is ready and tested. Check in regularly, offer help (a clear example payload, a test environment), and escalate through the leads with facts if it threatens a milestone. **Project flavor:** the BRIM inbound pipeline (Kafka consumer and validator) was owned by a teammate. My side only needed the agreed payload types (payment, overpayment, advance payment), so the contract let us work in parallel."

### M21: Another team shipped a breaking change to an API you consume.
**Priority:** SHOULD PREPARE
**Answer:** "Mitigate first (roll back on their side, or add an adapter or tolerant handling on ours). Then fix the process: a versioning agreement (additive changes only, deprecation windows) and **consumer contract tests** in their pipeline, so a break fails their build instead of our production. The MBS API has two consumers (GUI and ATC), so I'm careful about this in both directions."

### M22: Two systems disagree on data after an integration. How do you drive it?
**Priority:** SHOULD PREPARE
**Answer:** "Agree what the source of truth is for each field, reproduce with one concrete record, trace it through both sides' logs (correlation by business key), and find where the mapping diverges. Fix it, backfill or re-sync the affected records, and add a **reconciliation check** so drift gets detected automatically in future. That was the direction MBS was heading with invoice-vs-data reconciliation."

## 15.6 Accenture-fit questions

### M23: Why Accenture, and why this role?
**Priority:** MUST PREPARE
**Answer:** "Three reasons. **Breadth:** after going deep in one billing domain, I want to apply that to different clients and problems. **Delivery at scale:** Accenture's strength is delivering complex systems for large clients, and that's where I want to grow from feature ownership into owning modules and guiding a team, which is what L9 is. **Learning:** exposure to modern cloud stacks and practices across projects. My background in revenue-critical systems, production support and Spring Boot fits the Custom Software Engineer role directly."

### M24: Why did you leave Lumen / what have you been doing since May 2026?
**Priority:** MUST PREPARE *(fill in the truth, Part 0.4 #8)*
**Answer shape:** "My engagement on MBS ended in May 2026 *(the reason, stated neutrally)*. Since then I've been deliberately upskilling (system design, modern Java 17/21, cloud fundamentals building on AZ-900) and interviewing selectively for a role where I can take on more ownership. Here's what I've been practising: ___."
**Avoid:** Negativity about the previous employer; vague "just taking a break".

### M25: What are your strengths and an area to improve?
**Priority:** SHOULD PREPARE
**Answer:** "**Strength:** I go deep on correctness in critical flows: finding the dependent table a rerun missed, or asking what happens if something runs twice. **Improving:** large-scale system design and cloud-native operations. I've worked on existing architectures more than designing greenfield systems, so I've been practising design end to end and building on my Azure fundamentals. I'm also getting better at delegating instead of fixing things myself."

### M26: Where do you see yourself in 2–3 years?
**Priority:** AWARENESS
**Answer:** "Leading a module or small team technically: owning design and delivery quality, being the go-to person for a client's critical flows, and mentoring engineers. Longer term, a technical architect path."

---

# PART 16 — SCENARIO QUESTIONS

Format: **Situation → Thought process → Strong answer → Follow-ups → Weak/unsafe answer.** Use Q92 (the troubleshooting skeleton) as the backbone for all technical ones.

## 16.1 Technical scenarios

### S1: A system-level rerun returns 206: two cycles succeeded, one failed. It's the billing window.
- **Thought process:** Money at risk? No invoice is released yet (media gate). Contain, diagnose, re-run safely.
- **Strong answer:** "Tell the billing team which cycle failed and that the other two are fine. The response names the dates. In the logs, find that cycle's rerun block and the **first** `Error deleting/updating` line, which gives the step and the exception, e.g. a lock timeout because another session held rows. Check the DB: the cycle is still `RERUN=Y`, and some tables are already cleared. Because the reset is idempotent, once the cause is gone (the blocking session finishes, the data is fixed) I trigger the rerun again for that system. Only the still-flagged cycle is picked. Confirm it's back in 'released, not run', the batch recomputes, and the agent verifies totals. Afterwards: if it was concurrency, add the atomic claim (Q23)."
- **Follow-ups:** What if it fails again? (then it's not transient: code or data, so debug before retrying) · How do you know no bill went out wrong? (the media gate; release is blocked until review)
- **Weak answer:** "Restart the server and run it again" / "Manually delete the rows in prod."

### S2: The billing batch is failing for everyone. The database is unreachable or failing.
- **Thought process:** An infrastructure incident, not a code issue. Protect data consistency and communicate.
- **Strong answer:** "Confirm the scope (every app on that DB? GUI and ATC too, since it's shared), check the connection errors (`SQLTransientConnectionException`, ORA-125xx listener errors), and engage the DBA and infrastructure teams immediately. **Pause** batch and rerun triggers so nothing half-runs. Tell billing ops the cycle will be delayed, not wrong. When the DB is back, check where each cycle stopped (control flags). Incomplete customers stay `BILL_COMPLETE='N'` and get picked up again, so resume. Post-incident: DB HA and failover, an alert on pool-acquisition failures, and a documented batch-resume runbook."
- **Weak answer:** "Increase the connection pool size."

### S3: API latency suddenly jumped from sub-second to 20+ seconds.
- **Thought process:** What changed? Is it everything, or one endpoint? App, DB or downstream?
- **Strong answer:** "Check whether it's all endpoints (infrastructure, GC, pool) or specific ones (a query or a dependency). Check recent deployments and data growth. Thread dump: are threads waiting in `HikariPool.getConnection` (pool starved), in socket reads (downstream without a timeout), or in DB calls (slow SQL)? DB side: long-running SQL and locks for our schema, and the plan for the suspect query (a new plan? stale stats? a query using `TRIM` on an indexed column over grown data?). Mitigate: roll back a suspect release, kill the runaway session, add the missing timeout. Then fix the root cause."
- **Follow-up:** How do you prevent it? (timeouts on every client, slow-query alerts, latency SLO alerts, load-test big data sets)
- **Weak answer:** "Add more servers."

### S4: Memory keeps growing and the service crashes with OOM (or the pod is OOMKilled).
- **Strong answer:** "Tell the two cases apart. A Java `OutOfMemoryError` in the logs means heap exhaustion: capture a heap dump and look for dominators (a huge result list from loading a whole table, a persistence context never cleared in a batch loop, an unbounded cache or map). **OOMKilled with exit 137** and no Java error means the container limit was hit: heap + metaspace + threads + native memory exceed the pod limit, so fix the `-Xmx` vs limit ratio. Short-term: restart and scale; long-term: paginate or stream queries, `flush`/`clear` in batches, bounded caches."
- **Weak answer:** "Just increase `-Xmx`."

### S5: CPU is at 100% on the app server.
- **Strong answer:** "Find the hot threads: `top -H` → thread ID → hex → match it in a `jstack` dump (or Actuator threaddump); take several dumps a few seconds apart. Typical culprits: a tight loop, regex backtracking, heavy JSON or PDF generation (invoices), **GC thrashing** (check GC logs: it's often really a memory problem), or a traffic spike. Mitigate by throttling or scaling, then fix the hotspot."
- **Weak answer:** "Restart it and see if it happens again."

### S6: After a deployment, the pod keeps restarting (CrashLoopBackOff).
- **Strong answer:** "`kubectl describe pod` shows the last state, exit code and events; `kubectl logs --previous` shows the crash. Common causes: a startup exception (missing property or profile, can't reach the DB, bad secret), **OOMKilled (137)**, a **liveness probe** that kills a slow-starting app (add a startup probe or a longer initial delay), or **a read-only root filesystem**: our GUI runs with a read-only root FS, so any new code writing outside the mounted temp, work or log dirs fails in Kubernetes but not locally. Mitigate: `kubectl rollout undo`. Fix, then redeploy."
- **Weak answer:** "Delete the pod." (It just gets recreated with the same problem.)

### S7: The deployment succeeded (green pipeline), but the application is broken in production.
- **Strong answer:** "A green pipeline only proves it built and started. Check the smoke test: a health check passing doesn't mean features work. Compare config between environments (profile, property values, secrets), DB schema compatibility (did the code expect a column not yet migrated?), and the downstream endpoints configured for prod. Roll back fast if the impact is high. Prevention: post-deploy smoke tests on key flows, config validation in the pipeline (ATC's pipeline validates configuration before deploying), and backward-compatible DB changes."

### S8: Suddenly no user can log in to the GUI.
- **Strong answer:** "Authentication is LDAP over LDAPS, so check the path: can the app reach the directory host on port 636 (network, DNS)? Is there an SSL handshake error in the logs (**expired certificate or changed CA** not in the truststore)? Did the service-account password expire or get locked? If only some users fail, check group or role mapping. Fix: update the truststore or credentials and restart, then add **certificate-expiry monitoring**."
- **Weak answer:** "Tell users to reset their passwords."

### S9: The external tax service is down during the billing window.
- **Strong answer:** "No wrong bills. Affected customers don't complete the tax stage, so they're never formatted. Confirm with the provider, tell billing ops the ETA and which subsystems are affected, and let unaffected work continue. When it recovers, re-run the stage for incomplete customers. Improvements: timeouts, a circuit breaker so we stop calling a dead service, an alert on the pending-tax count, and automatic resume (SD3)."

### S10: An agent reports an invoice total that doesn't match the computed charges, or a customer was charged twice.
- **Thought process:** A data-integrity incident. Contain it before release, then find the cause.
- **Strong answer:** "Contain: make sure that cycle isn't released to media (the rerun gate). Compare `BILL_INV_FILE` totals with the sum of `BILL_COMPUTE` + `TAX_COMPUTE` + `EXPENSE_COMPUTE` for that customer and date. Look for **duplicate compute rows** (was usage rated twice because a processed flag wasn't set, or was a previous rerun incomplete?) and for leftover rows from an earlier run (like the stale approval assignments I found). Fix the root cause, then **rerun the cycle** so everything is recomputed consistently, rather than patching one table by hand. Add a check: the reconciliation comparison between invoice and compute totals."
- **Weak answer:** "Update the invoice amount in the DB."

### S11: A query that was fine last month is now slow.
- **Strong answer:** "Data growth or a plan change. Check the execution plan against the previous one, table and index statistics, and whether the predicate can use an index (functions like `TRIM`/`TRUNC` on columns force scans that only hurt once tables grow). Fix: gather stats, rewrite the predicate (date ranges instead of `TRUNC`), add or adjust a (function-based) index, paginate. Validate row counts, not just speed."

### S12: You find credentials committed in a repository or printed in logs.
- **Strong answer:** "Treat them as compromised: **rotate the secret first**, then remove it from code and logs (history rewrite or scrubbing where policy requires), move it to the secret store, and check access logs for misuse. Inform security per policy. Prevent it: secret scanning in CI, log masking, and code-review checks."
- **Weak answer:** "Delete the line and push."

## 16.2 Managerial scenarios

### S13: A team member is consistently underperforming.
- **Thought process:** Understand before judging. Skill, will, or circumstance?
- **Strong answer:** "A private 1:1 with specific examples, not labels. Ask what's getting in the way (unclear requirements, domain knowledge, personal issues). Agree a plan with clear, small goals, pair them on a task, give faster feedback. Check weekly. Recognize improvement. If nothing changes after genuine support, involve my manager with documented facts. Meanwhile, protect delivery by re-balancing critical-path work."
- **Weak answer:** "Escalate to HR" first, or "do their work myself".

### S14: Two team members are in conflict and it's affecting delivery.
- **Strong answer:** "Talk to each separately to understand the issue, then together, focused on the shared goal and facts. If it's technical, use decision criteria (M10). If it's working style, agree norms (review etiquette, who owns what). Clarify ownership so they're not stepping on each other. Follow up. Escalate only if it continues."

### S15: The team is going to miss a sprint commitment or deadline.
- **Strong answer:** "Raise it as soon as I know. Late surprises destroy trust. Come with facts and options: what's done, what's at risk, and what can ship by the date (reduced scope) vs later. Protect quality in critical flows. After the sprint, find out why: estimates, hidden complexity, dependencies, interruptions. Fix the process (smaller stories, spikes for unknowns, dependency tracking)."

### S16: Requirements keep changing.
- **Strong answer:** "Accept that change happens, but make it visible and controlled. Work with the PO on acceptance criteria and examples before sprint start, route changes through the backlog with impact shown, keep designs flexible where change is likely (configuration rather than hard-coding, as MBS did with DB-driven settings), and deliver in thin slices to get feedback earlier."

### S17: A key person leaves and only they knew a critical module. Or a knowledge gap in the team.
- **Strong answer:** "Immediate: collect what exists (code, tickets, logs, runbooks), and schedule handover sessions if they're still serving notice. Record them. Then spread the knowledge: pair two people on the module, write a one-page 'how it works + how to troubleshoot' (for rerun: the flags, the reset steps, how to read the summary log), and rotate on-call. Long-term: no single-owner modules."

### S18: A junior developer strongly disagrees with your technical decision.
- **Strong answer:** "Take it seriously; juniors often spot things. Ask them to explain it against the criteria, and look at the evidence together. If they're right, change the decision and credit them publicly. If not, explain the reasoning clearly so they learn the trade-off, and thank them for raising it. The goal is a team where people speak up."

---

# PART 17 — "CHALLENGE MY EXPERIENCE" QUESTIONS

> These are the questions that expose inflated resumes. Your defense is **precise, modest detail**.

### C1: "You say you stabilized Bill Rerun. What exactly was failing?"
"Two things. The outcome of a multi-cycle rerun wasn't reported per cycle, so agents didn't know what had worked. And some dependent data, the approval assignments tied to the cycle's expense vouchers, survived the reset, leaving stale approvals after recompute. I fixed the reporting with a result object and 200/206/500 mapping, and fixed the cleanup. The usage-indicator update and the logging were improved in the same work." *(Q31)*

### C2: "Did you design the rerun mechanism?"
"No. The flag-based mechanism, meaning the GUI flag and the reset-and-rebill approach, existed. It came over from the legacy batch engine. I designed the result contract and the per-cycle handling, and fixed the gaps."

### C3: "You say it eliminated manual interventions. How do you know? Metrics?"
"We didn't have a dashboard for it. The signal was operational: before, agents were coming back to us several times a week after reruns to check what had happened; after the change they could see it in the response, and those follow-ups stopped. I'd rather say that honestly than invent a percentage. If I built it again, I'd add metrics for rerun count, failures and duration."

### C4: "7,000 customers and $30M revenue: where do those numbers come from?"
*(Only if on your resume.)* "They're the business figures for the platform as I understood them from the team. I didn't measure them myself, and the scale I worked with directly was per subsystem and bill cycle." *If you can't source them, remove them from the resume.*

### C5: "You say you built inbound APIs for SAP BRIM. Walk me through the payload and mapping."
"My part was on the MBS side. The Kafka consumer and the validation step were a teammate's. Validated BRIM data reached MBS and was mapped by type: payments, overpayments and advance payments, applied at invoice level or account level. On the outbound side, I worked on the charge feed: legacy long-distance product codes handled per interstate or intrastate, revenue-accounting-code lookup, and cost-center validation against the new S4 mapping, where an unmapped cost center blocks that charge rather than sending bad finance data." *(Don't describe IDoc/XSD/MapStruct unless you really built them.)*

### C6: "You worked on microservices?"
"I'd describe it accurately: a multi-application Spring Boot platform with separately deployed apps talking over REST, but sharing one Oracle database, so not textbook microservices. I understand the patterns (independent data, resilience, eventual consistency) and how I'd move this platform toward them." *(Q69)*

### C7: "You used Kubernetes. What did YOU do there? What happens when a pod crashes?"
"The GUI was deployed to Rancher-managed Kubernetes through the Jenkins pipeline. I worked at the application level: deploying through the pipeline, checking pod status and logs *(via the Rancher UI or kubectl, say which)*, understanding the deployment config (non-root, read-only root FS, probes, resources). I didn't administer the clusters. When a pod crashes…" *(Q89)*

### C8: "You optimized a query. What exactly changed? How much faster?"
"On Bill Preview, the unbilled-customers query: I tightened the cycle-state filters (not run, not released, not in media, for this system and current month), made it return distinct customers because the join produced duplicates, and made the ordering deterministic. The goal was fewer rows and correct rows. I didn't record a formal before/after benchmark, so I won't quote a number. I verified row counts and response behavior in test. Next steps I'd take: a plan review and removing `TRIM` on indexed columns."

### C9: "You used JPA. What's in the persistence context during a rerun?"
"Very little. Most rerun steps are **bulk `@Modifying` JPQL deletes and updates**, which go straight to the database and bypass the persistence context. They return row counts, which we log. The exceptions are the control rows (`BILL_CONTROL`, `BILLPULL_DETAIL`), which are loaded as entities, modified and saved, so those are managed and dirty-checked. That's also why mixing bulk updates with loaded entities needs care (`clearAutomatically`)." *(Q48–Q49)*

### C10: "You implemented JWT authentication. Walk me through the request lifecycle."
"Login endpoint → the authentication manager checks the BCrypt hash → issue a signed JWT with subject, roles and expiry → the client sends `Authorization: Bearer …` → a `OncePerRequestFilter` extracts it, validates the signature and expiry, and sets an `Authentication` in the `SecurityContext` → authorization rules (`hasRole`, `@PreAuthorize`) → the controller. Stateless session policy, CSRF disabled for pure header-token APIs, 401 vs 403 handled by the entry point and access-denied handler." *(From the fintech project; not MBS.)*

### C11: "You mentored juniors. Give me a concrete example."
*Only a real example: who (a role, not a name), what they struggled with, what you did (pairing, KT, review questions), and what changed.* If there isn't one: "I haven't had formal mentees; what I've done is knowledge sharing: explaining the billing flow and the rerun internals to teammates, and review feedback that explains the why. That's the part of leadership I want to grow in this role."

### C12: "What did you change in the ATC CI/CD pipeline?"
"The GitHub Actions workflow: *(pick what you actually did)* configuration validation before deploy, secret retrieval from the CI secret store, build and test jobs, artifact download for the deploy job, an SSH connectivity test before touching the server, backing up the current WAR, and deploying the new WAR to Tomcat." *(Q86)*

### C13: "Your resume mentions AI tools. Did AI write your code?"
"I use Copilot-style assistants for boilerplate, test scaffolding and exploring unfamiliar APIs, and the ATC frontend scaffolding was AI-assisted. But every line I merge, I've read, adapted to our conventions and can explain. The review, the tests and the production responsibility are mine. It speeds me up; it doesn't replace understanding."

### C14: "You say you know Kafka. Consumer groups? Offsets? Rebalancing?"
*(From the TCS project; answer only at the depth you worked.)* "Partitions are the unit of parallelism. Within a consumer group, each partition goes to one consumer. Offsets are committed after processing (at-least-once), so consumers must be idempotent. A rebalance happens when consumers join or leave, and processing pauses briefly. Ordering holds only within a partition, so key by the entity. At Lumen the Kafka ingestion was a teammate's; I consumed its output downstream."

### C15: "5.5 years total, but 2.5 on this project. What did you do before, and why so many moves?"
"*(Your real arc.)* Each move was toward deeper backend ownership: event-driven retail systems at TCS, then fintech APIs, then revenue-critical billing at Lumen. The common thread is correctness-critical backend work."

---

# PART 18 — FOLLOW-UP TREES

> Practise these as chains: answer → pause → next question. Short pointers only; the full answers are in earlier parts.

**T1: Project architecture**
Explain the architecture (Q1) → *Why separate GUI and API?* (legacy evolution; the API serves both GUI and ATC) → *Why is a shared DB a problem?* (coupling, schema changes, noisy neighbors) → *Why wasn't it split?* (cost and risk; consistency matters more for billing) → *What happens at 10x?* (DB queries, pool, tax quota) → *Bottleneck?* (the compute-table deletes/queries and tax calls) → *Your fix?* (index-friendly predicates, parallel workers, async jobs, a tax client with limits) → *Trade-off introduced?* (more moving parts, eventual consistency, job-status APIs)

**T2: Bill Rerun** (Q15 → Q35 in order). The pivot questions to be sharp on: **Q21** reset vs resume, **Q22** idempotency, **Q23** concurrency, **Q26** atomicity and self-invocation, **Q31** what YOU did.

**T3: The important API (rerun endpoint)**
Walk me through it (Q5) → *Why 206?* (Q24) → *Why POST?* (Q25) → *What if the call times out on the client but the server keeps running?* ("The server completes; the client re-calls; idempotency means the second call finds nothing eligible. Better: 202 + status polling.") → *How is it secured?* (Q98) → *How would you version a response change?* (additive JSON fields; a new version only if breaking)

**T4: Database**
Table design (Q6) → *Why composite keys with system name?* (multi-subsystem in one schema) → *Downside?* (wide keys, every query must include system name) → *The TRIM/TRUNC problem* (Q77) → *Locking during rerun?* (Q80) → *Isolation level?* (Q79) → *How do you detect duplicates race-free?* (Q82)

**T5: Spring Boot**
How does `@Transactional` work? (Q47) → *Where did it bite you?* (self-invocation in rerun, Q26) → *How do you fix it?* (separate bean / `TransactionTemplate`) → *Why did ATC use `TransactionTemplate`?* (remote call after commit, M2) → *What if the remote call fails?* (outbox + retry, Q74) → *How do you test transactional behavior?* (integration test that forces an exception mid-way and asserts a rollback)

**T6: Production issue**
Tell me about one (S1) → *First thing you checked?* (scope + the first error in the logs) → *How did you know it was safe to re-run?* (idempotent steps, the flag stays set until the end) → *What if it had been released already?* (not possible: the media gate; corrections via adjustments) → *How did you prevent recurrence?* (guard, logs, test) → *What did you tell the business?* (impact, ETA, no wrong bills out)

**T7: Technical decision**
Hardest decision (M2) → *Alternatives?* (single `@Transactional`; async sync) → *Why not async?* (more infrastructure for a low-volume edit; the post-commit call plus explicit status was enough) → *What would change your mind?* (higher volume or an unreliable MBS → outbox + retry) → *How did you get agreement?* (walked the lead through the failure scenarios)

**T8: Leadership scenario**
Two devs disagree (M10) → *What if one is senior and insists?* (criteria + a spike; seniority isn't a criterion) → *What if the client prefers the weaker option?* (explain the risk in business terms, record it, respect their call if it's safe) → *How do you know the team is healthy?* (people raise issues early, reviews are quick and respectful, escaped defects are low, there's no single point of knowledge)

---

# PART 19 — QUESTIONS TO ASK THE INTERVIEWER

Pick 3–4. Ask the ones whose answers you actually want.

**Project and architecture**
1. "What does the client's system look like today: a greenfield build, or modernizing an existing platform? Where is the hardest technical risk?"
2. "How are Spring Boot services deployed for this client (Kubernetes on AKS/EKS, app services, VMs), and who owns the platform side?"
3. "How mature is the delivery pipeline (automated tests, quality gates, deployment frequency), and is improving it in scope for this team?"

**Role expectations**
4. "What would a strong first 90 days look like for an L9 in this team?"
5. "How is the L9 role split between hands-on engineering, design ownership and guiding other engineers on this project?"
6. "How much direct interaction would I have with client stakeholders: requirements, demos, incident calls?"

**Team**
7. "How big is the team, and what's the mix of experience and locations? Who would I work most closely with?"
8. "How are technical decisions made and recorded, and how are disagreements with the client's architects handled?"

**Growth**
9. "What paths have people in this role typically taken: architect track, delivery lead track?"
10. "What certifications or cloud skills does the account value most? I'd like to build on my Azure fundamentals."

**Avoid:** questions about salary or leave in a technical round, "what does Accenture do?", or anything you could have googled.

---

# PART 20 — FINAL REVISION SHEET

## 20.1 My project on one page

| Item | Say this |
|---|---|
| **Business purpose** | Lumen's MBS: monthly billing for enterprise/carrier customers by subsystem; correctness over speed; agents review before release |
| **Architecture** | Multi-app Spring Boot platform on a shared Oracle DB: core API (Boot 2.5 / Java 8, WAR on Tomcat), ops GUI (Boot 2.1 + JSP, LDAP), ATC (Boot 3.2 / Java 17 + React), legacy batch engine being migrated into the API |
| **Pipeline** | eligibility (control tables) → rating → billing (payments/adjustments) → tax (external) → formatting (PDF, BILL_INV_FILE) → review → media release; expenses + voucher approvals on a side track. Visual version: `diagrams/mbs-billing-pipeline.html` |
| **Key tables** | BILLPULL_DETAIL (cycle: RUN/REL/MEDIA/RERUN), BILL_CONTROL (customer: COMPLETE, TYPE, stage flags), CURR_USG, PRODUCT_SERV_RATE, BILL_COMPUTE, EXPENSE_COMPUTE, TAX_COMPUTE, EXPENSE_VOUCHER, ASSIGN_DETAILS, BILL_PAYMENT, BILL_ADJUSTMENTS, BILL_BALHISTORY, BILL_INV_FILE |
| **Integrations** | SAP BRIM (finance feeds; inbound via Kafka pipeline owned by a teammate), external tax service (OAuth2), SAP S4 validation, directory DB (approvals), LDAP, SMTP, ActiveMQ |
| **My responsibilities** | Bill Rerun stabilization (owner); BRIM charge mapping (CPPEWMB-5443); Bill Preview / Bill Process query and filter changes; log-injection fix; ATC composite edit API; ATC GitHub Actions pipeline; production support in billing windows |
| **Important APIs** | `POST /batch-invoice/rerun-billing/system`, `/rerun-billing/customer` (200/206/500/400); GUI `updateBillReRunIndrToY`; ATC `POST /{projectDataId}/edit` |
| **Deployment** | GUI: Jenkins → Sonar/JaCoCo → Docker (Tomcat 9) → Rancher K8s (6 envs, prod approval). API: WAR on Tomcat. ATC: GitHub Actions → SSH → WAR on Tomcat (with backup) |
| **Bill Rerun in one breath** | Agent flags a billed, unreleased cycle → the API validates eligibility → per cycle: delete compute/tax/expense/invoice/vouchers/assignments, reset payments/adjustments, revert usage flags, reset customer flags → only if everything succeeded, the cycle goes back to "released, not run" → the batch recomputes; result 200/206/500 with dates; per-table summary log; email on failure |
| **Biggest challenges** | Dependent-data completeness in rerun; honest partial-failure reporting; correctness vs speed in queries |
| **Key decisions** | Per-cycle isolation + explicit partial result; post-commit sync with `TransactionTemplate` (ATC); fail-safe when cost-center mapping fails; reverting a default filter that hid data |
| **What I'd improve** | Explicit per-cycle transaction; atomic rerun claim; JSON result + metrics; versioned compute data; no TRIM/TRUNC on indexed columns; automated reconciliation |

## 20.2 Highest-priority concepts (only these)

1. `@Transactional`: proxy, **self-invocation**, rollback rules, rollback-only, propagation, `TransactionTemplate`
2. Idempotency + duplicate prevention: keys, unique constraints, conditional updates, delete-and-recompute
3. Concurrency: singleton state, race conditions, optimistic vs pessimistic locking, deadlocks
4. JPA: persistence context, dirty checking, bulk `@Modifying`, N+1, composite keys
5. SQL: joins/aggregation/window functions, **index usage and functions on columns**, execution plans, isolation levels
6. REST: methods/idempotency/status codes, error model, versioning, 202 + polling for long jobs
7. Resilience: timeouts → retry → circuit breaker → bulkhead → domain fallback
8. Spring Boot: auto-configuration, bean lifecycle, profiles/config, Actuator
9. Kubernetes basics: probes, limits, OOMKilled, CrashLoopBackOff, rolling update and rollback
10. Security: session vs JWT, OAuth2 client credentials, OWASP (SQLi, XSS, CSRF, XXE, log injection), secrets
11. Java: HashMap/ConcurrentHashMap, equals/hashCode, JVM memory and OOM diagnosis, executors/CompletableFuture
12. System design: SD1 end to end; scaling and HA quick answers

## 20.3 Stories to be ready to tell (12)

| # | Story | Use for | Core line |
|---|---|---|---|
| 1 | **Bill Rerun stabilization** | Ownership, production, successful delivery, "difficult problem" | Per-cycle results + 206 partial + cleanup of stale approvals; manual follow-ups stopped |
| 2 | **Stale approval assignments found in rerun** | Debugging, data inconsistency | Traced leftover rows after a reset → ASSIGN_DETAILS linked to vouchers → added cleanup |
| 3 | **ATC composite edit + post-commit sync** | Technical decision, design trade-off | TransactionTemplate for local changes; sync after commit; outbox as the next step |
| 4 | **BRIM legacy product codes + S4 cost-center gate** | Ambiguous requirements, complex logic, review feedback | Examples-first clarification; fail-safe on unmapped cost center |
| 5 | **Bill Preview query refactor** | Performance, correctness-first | Tighter filters, DISTINCT, ordering; no invented numbers |
| 6 | **Bill Process 7-day filter reverted** *(confirm reason)* | Mistake/failure, requirement change, user feedback | Validate default-behavior changes with users |
| 7 | **Log-injection fix** | Security, quality beyond the ticket | CR/LF neutralization + parameterized logging |
| 8 | **ATC pipeline work** | DevOps, delivery safety | Config validation, SSH check, backup before deploy |
| 9 | **Cross-team BRIM ingestion dependency** | Cross-team collaboration, dependency delays | Agreed payload contract; parallel work; owner boundaries respected |
| 10 | **Learning telecom billing fast** | Learning agility | Traced the pipeline through tables and code; started with defects, then owned rerun |
| 11 | **Knowledge sharing on rerun** *(use only if true)* | Mentoring | Explained the flags and reset steps; runbook for troubleshooting |
| 12 | **Using AI tools responsibly** | Modern engineering judgment | Speed from AI, understanding and review from me |

## 20.4 Rapid-fire drill (answer each in under 15 seconds)

**Spring / Boot**
1. Default bean scope? → Singleton.
2. `@SpringBootApplication` = ? → `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`.
3. Why constructor injection? → Explicit, immutable, testable, fails fast.
4. `@Component` vs `@Bean`? → Class scanning vs factory method for third-party or custom construction.
5. Where are AOP proxies created? → BeanPostProcessor after-initialization.
6. Does `@Transactional` work on private methods? → No (proxy-based).
7. Default rollback? → RuntimeException and Error only.
8. `REQUIRES_NEW`? → Suspends the current transaction, runs in a new one.
9. `readOnly=true`? → A hint: skips dirty checking, may optimize the driver/DB.
10. How to see auto-config decisions? → `--debug` conditions report / `/actuator/conditions`.
11. Deploy Boot as a WAR? → Extend `SpringBootServletInitializer`, packaging war, provided Tomcat.
12. `@RestController` = ? → `@Controller` + `@ResponseBody`.
13. Global exception handling? → `@RestControllerAdvice` + `@ExceptionHandler`.
14. Validate a request body? → `@Valid` + Bean Validation annotations on the DTO.
15. Profile-specific file? → `application-{profile}.properties`, activated with `spring.profiles.active`.
16. Change a log level at runtime? → `/actuator/loggers` (MBS used a DB-driven logger).

**JPA / DB**
17. Persistence context? → First-level cache plus the unit of work for managed entities.
18. Dirty checking? → Changes to managed entities are flushed automatically at commit.
19. Bulk `@Modifying` caveat? → Bypasses the persistence context; needs a transaction; use clearAutomatically.
20. N+1 fix? → JOIN FETCH / EntityGraph / batch size / DTO projection.
21. Default fetch for `@ManyToOne`? → EAGER (make it LAZY).
22. Optimistic locking? → `@Version`; conflict → OptimisticLockException.
23. Pessimistic write in SQL? → `SELECT … FOR UPDATE`.
24. Oracle default isolation? → READ COMMITTED (MVCC).
25. MySQL InnoDB default isolation? → REPEATABLE READ.
26. Why doesn't `TRIM(col) = :x` use the index? → A function on the column; needs a function-based index.
27. `NOT IN` gotcha? → Any NULL in the subquery → no rows.
28. `ROW_NUMBER` vs `RANK`? → Unique sequence vs ties sharing a rank with gaps.
29. `WHERE` vs `HAVING`? → Row filter before grouping vs group filter after.
30. Hikari exhaustion error? → "Connection is not available, request timed out".
31. Oracle deadlock error? → ORA-00060.
32. Oracle `''`? → Treated as NULL.
33. IDENTITY IDs and batching? → IDENTITY disables Hibernate insert batching.

**REST / Microservices**
34. Idempotent methods? → GET, PUT, DELETE (+HEAD, OPTIONS).
35. 401 vs 403? → Not authenticated vs authenticated but not allowed.
36. Status for an async accepted job? → 202 + status location.
37. Status for a duplicate or conflict? → 409.
38. 206 really means? → Partial content for range requests (we used it as a partial-outcome convention).
39. Circuit breaker states? → Closed → Open → Half-open.
40. Retry rule? → Only transient failures, only idempotent ops, with backoff + jitter.
41. Bulkhead? → Isolated resource pools per dependency.
42. Saga? → Local transactions + compensations; orchestration or choreography.
43. Outbox? → Write the event in the same DB transaction; a relay publishes it.
44. Kafka ordering? → Per partition only; key by entity.
45. Correlation ID? → Header → MDC → logs → propagated downstream.

**Java**
46. HashMap treeify threshold? → 8 per bucket (table ≥ 64).
47. Load factor? → 0.75.
48. ConcurrentHashMap nulls? → Not allowed.
49. `volatile` makes `count++` safe? → No; use AtomicInteger.
50. Deadlock prevention? → Consistent lock order + timeouts.
51. Java 8 default GC? → Parallel; Java 9+ → G1.
52. Metaspace? → Class metadata, native memory (replaced PermGen).
53. Is SimpleDateFormat thread-safe? → No; use DateTimeFormatter.
54. `Optional` as a field? → Avoid; return types only.
55. Records (Java 16+)? → Immutable data carriers with generated equals/hashCode/toString.

**DevOps / Security / Cloud**
56. Exit code 137? → OOMKilled (SIGKILL).
57. Liveness vs readiness? → Restart the container vs remove it from traffic.
58. Roll back a K8s deployment? → `kubectl rollout undo`.
59. Image vs container? → Template vs running instance.
60. Secrets in a ConfigMap? → No; use Secret + an external secret store (base64 ≠ encryption).
61. JWT payload encrypted? → No, only signed (unless JWE).
62. OAuth2 grant for service-to-service? → Client credentials.
63. SQL injection defense? → Bound parameters.
64. Log injection defense? → Neutralize CR/LF + parameterized logging.
65. RPO vs RTO? → Acceptable data loss vs acceptable downtime.
66. AKS / EKS? → Managed Kubernetes on Azure / AWS.
67. Key Vault / Secrets Manager? → Managed secret stores on Azure / AWS.

**Your project**
68. What gates a rerun? → RERUN=Y, REL=Y, RUN=Y, media not released.
69. What does rerun hand back to? → The normal billing batch (RUN=N, REL=Y).
70. Why is the flag updated last? → The business commit point; failed resets stay flagged.
71. What did you add to the cleanup? → ASSIGN_DETAILS linked to the cycle's expense vouchers.
72. Rerun response codes? → 200 all, 206 partial, 500 all failed, 400 bad input.
73. Does the reset call external systems? → No, DB only.

---

# PART 21 — TOP 30 MUST-ANSWER QUESTIONS

> If you prepare nothing else, prepare these **out loud**.

| # | Question | Where |
|---|---|---|
| 1 | Tell me about yourself and your current project. | 2.1–2.3, 2.5 |
| 2 | Explain your project architecture. | Q1 |
| 3 | What exactly did YOU work on? What did others own? | Part 0.3, Q3 |
| 4 | Walk me through the billing flow end to end. | Q2 |
| 5 | What is Bill Rerun and why does it exist? | Q15–Q16 |
| 6 | Walk me through the rerun flow: API, validation, DB steps. | 4.3, Q18–Q20 |
| 7 | Why reset-and-recompute instead of resuming a stage? | Q21 |
| 8 | Is rerun idempotent? How do you prevent double billing? | Q22 |
| 9 | What if two reruns run at the same time? | Q23 |
| 10 | What happens if it fails midway? Is it atomic? | Q26 |
| 11 | What was broken before, and what did you change? | Q31 |
| 12 | How would you troubleshoot a failed rerun in production? | Q30, S1 |
| 13 | What would you improve in your project's design? | Q14, Q33 |
| 14 | How does `@Transactional` work, and what are its pitfalls? | Q47 |
| 15 | Explain Spring auto-configuration and the bean lifecycle. | Q41, Q37 |
| 16 | Persistence context, dirty checking, bulk updates, N+1. | Q48–Q50 |
| 17 | How did you tune a slow query? Why don't indexes get used? | Q78, Q77 |
| 18 | Optimistic vs pessimistic locking, with an example. | Q52 |
| 19 | REST design: methods, status codes, idempotency, versioning. | Q66–Q68 |
| 20 | How do you handle a slow or failing downstream service? | Q71, Q10 |
| 21 | Microservices: is your system one? How would you evolve it? | Q69 |
| 22 | Design a billing platform with safe reruns (and scale it 10x). | SD1, 9.5 |
| 23 | Walk me through your CI/CD pipeline; how do you roll back? | Q86, Q90 |
| 24 | A pod keeps restarting after deployment. What do you do? | S6, Q89 |
| 25 | Authentication in your projects: session vs JWT, OAuth2. | Q93–Q95 |
| 26 | What is your AWS/Azure experience? React/Angular? | Q103, Q106 |
| 27 | A difficult technical decision you made. | M2 |
| 28 | Two developers disagree / you disagree with the architect. | M10, M14 |
| 29 | Unrealistic deadline / requirement change / client escalation. | M15–M17 |
| 30 | A mistake you made, and what you learned. | M4 |

---

*Built from the MBS source extracts, git contribution analysis, HLD notes and data-flow diagram in this repository. Where sources conflicted, the code won (see Part 0). Before the interview, spend five minutes on the fact check in Part 0.4.*
