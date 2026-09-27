# MBS Billing Platform — High-Level Design Reference

**Built from:** Abubaker Siddique's firsthand system knowledge (Lumen Technologies, Nov 2023 – May 2026)
**Purpose:** Interview-ready system design narrative for the MBS billing platform — use this as your primary reference for system design rounds when discussing real production experience.

> **How to use this doc:** Sections marked ✅ **Confirmed** are things you built, worked on, or directly observed — defend these confidently. Sections marked 🔶 **Reasoned inference** are logical extensions built during our discussion to complete the picture — if asked directly, frame these as "my understanding" rather than claiming direct ownership.

---

## 1. What MBS Is (30-Second Answer)

> "MBS is Lumen's enterprise telecom billing platform. It runs a multi-stage pipeline — rating usage, computing one-time charges, calculating taxes via an external tax engine, applying payments and adjustments, and generating PDF invoices — for enterprise customers partitioned across multiple subsystems, each on its own billing cycle."

---

## 2. Functional Requirements ✅ Confirmed

| Requirement | Detail |
|---|---|
| Bill enterprise customers | Based on usage, product rates, one-time charges (OCC), and applicable taxes |
| Support multiple subsystems | Each subsystem has its own customer base and billing cycle date |
| Handle external revenue sync | Payments, overpayments, advance payments flow in from BRIM via Kafka |
| Support partial failure recovery | Per-stage tracking allows resuming from a failed stage, not just full restart |
| Support manual verification | Agents can review and rerun specific customers/subsystems before final release |
| Generate PDF invoices | Final formatted output stored for customer delivery |

## 3. Non-Functional Requirements ✅ Confirmed (reasoned from context)

| Requirement | Why It Matters |
|---|---|
| **Consistency over availability** | This is revenue data — an incorrect bill is worse than a delayed bill. The system favors correctness and explicit human checkpoints over full automation. |
| **Auditability** | Per-stage flags (`bill_run`, `bill_tax_hold`, `bill_release`, etc.) create an implicit audit trail of what happened to each customer in each billing run. |
| **Partition tolerance via subsystems** | Each subsystem operates independently — a failure in one subsystem's billing run doesn't block another subsystem's cycle. |

---

## 4. System Architecture — End to End

```
┌──────────────┐      ┌───────┐      ┌──────────────────┐      ┌─────────────────────┐
│     BRIM     │─────▶│ Kafka │─────▶│ Python validator  │─────▶│   MBS API (Spring   │
│ (Revenue Mgmt│ JSON  │       │      │ (owned by another │      │   Boot / Java 8)    │
│   System)    │       │       │      │  teammate — 🔶 not │      │                     │
└──────────────┘       └───────┘      │  my implementation)│      └──────────┬──────────┘
                                       └───────────────────┘                  │
                                                                               ▼
                                                              Maps payload by type:
                                                              payment / overpayment /
                                                              advance payment (adjustment)
                                                              → applied at invoice or
                                                              account level
```

✅ **Confirmed**: You worked downstream of this ingestion layer, consuming validated data via MBS APIs. You do **not** have implementation knowledge of the Python/Kafka consumer internals — that was owned by a specific teammate. This is a safe, honest boundary to state in interviews.

### 4.1 Billing Trigger Flow ✅ Confirmed

```
Cron job (Python) — scheduled
        │
        ▼
Reads billing cycle dates per subsystem
        │
        ▼
Fetches eligible customer list for that subsystem + date
        │
        ▼
BILL_CONTROL table — decides which customer IDs
participate in this billing run
```

### 4.2 The Core Billing Pipeline ✅ Confirmed (from system analysis + flow diagram)

```
STAGE 1 — RATING
  Input:  CURR_USG (usage records), PRODUCT_SERV_RATE (rate table)
  Logic:  Fetch usage records → multiply quantity × rate by product code
  Output: BILL_COMPUTE

STAGE 2 — OCC / EXPENSE (One-Time Charges, parallel track)
  Input:  EXPENSE_BILL_SERVICE_OCC, EXPENSE_SERV_RATE
  Logic:  One-time charges → voucher created → routed for approval
          (ASSIGN_DETAILS + MBS_APPROVAL_LIMIT check against MNET DB
          for approver authority by dollar threshold)
  Output: EXPENSE_COMPUTE (after approval)

STAGE 3 — TAXING
  Input:  Product address + zip code
  Logic:  External API call to tax engine (Vertex/CGS)
  Output: TAX_COMPUTE
  Note:   This is the stage most prone to needing manual verification —
          tax figures originate externally and aren't self-verifiable
          by MBS alone.

STAGE 4 — ADJUSTMENTS
  Input:  BILL_ADJUSTMENTS, BILL_PAYMENT (from BRIM via Kafka pipeline)
  Logic:  Fetch total payments + adjustments → calculate running balance
  Output: BILL_BAL_HISTORY (balance due)

STAGE 5 — FORMATTING
  Input:  All of the above (BILL_COMPUTE, TAX_COMPUTE, EXPENSE_COMPUTE, balance)
  Logic:  Generate PDF invoice with calculated totals
  Output: BILL_INV_FILE
```

### 4.3 Resumable Execution via Per-Stage Flags ✅ Confirmed — Your Strongest Architectural Insight

```
BILL_CONTROL table tracks status flags per customer, per billing run:

  bill_run        → has rating/billing executed for this customer?
  bill_tax_hold   → Y = taxing failed/held, N = clean
  bill_rerun      → flagged for rerun?
  bill_release    → Y = full pipeline completed successfully (excludes from future fetch)
  bill_media      → formatting/delivery stage status
```

**Why this matters architecturally:** This is the billing-domain equivalent of a **saga pattern with per-step status tracking**. Instead of replaying the entire pipeline on failure, the system can resume *from the exact stage that failed* — e.g., if `bill_tax_hold = Y`, the rerun can skip rating (already succeeded, data sits in `BILL_COMPUTE`) and resume directly at taxing.

This prevents two failure classes simultaneously:
1. **Double-billing** — `bill_release = Y` customers are excluded from future fetch queries entirely
2. **Wasted recomputation** — partial failures don't require redoing successful stages

### 4.4 Rerun Trigger Mechanism ✅ Confirmed

```
Billing agent opens GUI screen with controls: bill_run / bill_rerun / bill_media / bill_release
        │
        ▼
Agent selects scope: date + subsystem  OR  date + customer
        │
        ▼
Backend reads BILL_CONTROL flags for selected scope
        │
        ▼
Determines which stages to skip (already succeeded) vs. re-execute (failed/held)
        │
        ▼
Executes from the appropriate mid-stage, or end-to-end if needed
```

**This is the workflow you stabilized** — your resume bullet ("eliminating multi-weekly manual agent interventions") maps directly to giving agents clear, structured success/partial/failure visibility after a rerun, rather than ambiguous outcomes that required manual investigation.

---

## 5. Why the Design Choices Make Sense (Trade-offs to Articulate)

| Decision | Trade-off Reasoning |
|---|---|
| **Manual rerun trigger** (not fully automated) | Revenue-critical data + externally-sourced tax figures → a human checkpoint is a deliberate control, not a limitation. Automating blindly risks committing wrong numbers at scale. |
| **Subsystem-based partitioning** | Each subsystem bills independently — limits blast radius of failures, allows different billing cycle dates per subsystem, natural horizontal scaling boundary. |
| **External tax API instead of building in-house tax logic** | Telecom tax rules are notoriously complex and jurisdiction-specific (Vertex/CGS specializes in this). Buy vs. build — correctly outsourced. |
| **Per-stage flags instead of a single status field** | Enables precise, granular resumption — critical when reprocessing means real financial recalculation, not just an idempotent retry. |
| **Separate OCC/Expense approval workflow** | One-time charges need human authorization above certain dollar thresholds (`MBS_APPROVAL_LIMIT`) — a financial control, not a technical limitation. |

---

## 6. The Natural Evolution 🔶 Reasoned Inference — Be Careful Framing This

**Confirmed:** A reconciliation feature was in progress — comparing invoice data to DB data for precision verification, intended to reduce manual checking.

**Inferred (logical extension, not confirmed detail):** This reconciliation layer would likely sit between Taxing/Formatting and final release, comparing the final PDF invoice values (`BILL_INV_FILE`) against the underlying computed tables (`BILL_COMPUTE`, `TAX_COMPUTE`, `EXPENSE_COMPUTE`) to catch any calculation-to-output mismatch automatically.

**How to phrase this safely in an interview:**
> "Towards the end of my time there, we were starting to build a reconciliation feature to compare generated invoice values against the underlying computed data — automating what was previously a manual precision check. I wasn't deeply involved in that specific feature's design, but it represented the natural next step for the system: moving from manual verification toward automated sanity checks."

---

## 7. Anticipated Interviewer Follow-Ups (Practice These)

| Question | Your Answer Strategy |
|---|---|
| "What happens if a customer fails halfway through the pipeline?" | Explain the per-stage flag system — `bill_tax_hold`, etc. — and resumable execution |
| "How do you avoid double-billing on rerun?" | `bill_release = Y` excludes customers from future fetch queries |
| "Why is the rerun trigger manual instead of automatic?" | Revenue-critical + externally-sourced tax data → deliberate human checkpoint |
| "What's BRIM and how does data flow in?" | Kafka → Python validator (not your implementation) → MBS API, typed by payment/adjustment category |
| "Why partition by subsystem?" | Independent billing cycles, blast-radius containment, natural scaling boundary |
| "What would you improve if redesigning today?" | Automate the reconciliation check (in progress when you left) to reduce reliance on manual verification, while keeping a human-in-the-loop for exceptions |
| "What don't you know about this system?" | Be honest: Python/Kafka consumer internals, exact BRIM response schema — these were owned by a teammate |

---

*This document reflects a mock system design interview conducted to reconstruct and validate Abubaker's real production architecture knowledge. All ✅ Confirmed sections are based on firsthand recollection cross-checked against system documentation and a data flow diagram. 🔶 Reasoned inference sections were built collaboratively to complete logical gaps and must be framed as understanding, not direct ownership, if probed further.*
