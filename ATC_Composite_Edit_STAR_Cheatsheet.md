# ATC Composite Edit: last-minute STAR sheet

Source: `ProjectEditService`, `ProjectDataController`, and git commit `6cc89873` (backend and React edit modal). ATC = **Aid To Construction**.

## STAR (about 90 seconds spoken)

**S: Situation**
"In ATC, our construction-project billing app, editing a project was spread across separate endpoints: cancel, statement of work, billing info, negotiated amount. One edit in the UI meant several backend calls, so a failure halfway could leave a project partly updated. Billing changes also had to be synced to the MBS billing system."

**T: Task**
"I was asked to consolidate that into a single, safe API and update the React edit screen to match."

**A: Action**
1. "I made **one endpoint**, `POST /{projectDataId}/edit`, taking a composite request with optional sections: cancel, statement of work, billing info, negotiated amount."
2. "**Validation up front:** at least one operation is required, and cancellation can't be combined with anything else. Bad input gets a 400, and cancelling an already-cancelled project gets a 409."
3. "**One local transaction.** All the local writes run inside a `TransactionTemplate` block: the project, customer, notes and workflow-transition rows. If anything throws, all of it rolls back. Billing edits apply only the fields that were actually modified."
4. "**MBS sync after commit, not inside the transaction.** Once the local commit succeeds, we work out whether MBS needs telling: an update for statement-of-work or billing changes, a delete for cancellation, nothing for a negotiated-amount change. Then we call MBS. A remote call inside the transaction would hold database locks during slow HTTP, and a remote failure would roll back a valid local edit."
5. "On the React side, the modal builds the payload from only the sections that changed and sends one request."

**R: Result**
"One call per business action, no partially-applied local edits, and business rules in one service instead of scattered across endpoints. **The trade-off I accepted:** if the MBS sync fails, ATC and MBS differ until someone re-syncs. A failed sync is caught and logged and can't undo the local edit. Today it isn't returned to the user. Next I'd surface the sync status in the response, then add an outbox table and a retry job."

## Facts to have ready

| Question | Answer |
|---|---|
| Why `TransactionTemplate` and not `@Transactional`? | So the transaction ends before the MBS call. `@Transactional` on `editProject` would wrap the remote call too. It also avoids the proxy self-invocation problem, since the private methods run inside the template callback. |
| What rolls back? | Any `RuntimeException` inside the callback, including the `ResponseStatusException` for an unsupported billing field. Local edit, notes and workflow transition all go together. |
| What triggers each sync type? | Cancel → DELETE. Statement of work or billing info → UPDATE. Negotiated amount → no sync. |
| What's in the sync payload? | Rebuilt from the database **after** commit (`prepareCustomerDataForBillingRequest`), so MBS gets the committed state. |
| What happens if MBS is down? | The exception is caught and logged, and the local change stays. There's no automatic retry. |
| Who else calls MBS here? | `MBSServiceAdaptor`, which handles the OAuth2 token and submits the customer billing request. |
| Audit trail? | Each operation writes a note. Cancel also writes a workflow transition (old status → CANCELLED, with the user ID). |

## Likely follow-ups

- **Why a composite endpoint and not separate ones?** "It's one business action. Splitting it across transport calls added failure modes without adding value."
- **Why not a distributed transaction?** "Two-phase commit over HTTP isn't practical. The pattern is: commit locally, then sync, with eventual consistency."
- **What if the sync fails? How do you fix the gap?** "Return the sync status to the UI, store an outbox row in the same transaction as the edit, and let a retry job deliver it. The consumer must be idempotent."
- **Two people edit the same project at once?** "Not handled explicitly: last write wins. I'd add an `@Version` column for optimistic locking." (True: the extracted code has no versioning.)
- **How did you test it?** Say only what you did. Suggested: validation cases (no operations, cancel plus another edit, already cancelled), an unsupported billing field rolling back everything, and the sync being invoked only for the right operations.
- **How is it different from Bill Rerun?** "Different problem. Here I control the transaction boundary explicitly with `TransactionTemplate`. In rerun, the `@Transactional` annotation isn't effective because of self-invocation."

## Don't say

- "Sync status is returned to the UI" (the extracted code discards the sync result).
- JSON Patch, `@Version`, Hibernate Envers or a facade for legacy endpoints (none of it exists here).
- Any percentage like "85% fewer errors" or "4.7 → 1.2 calls".
- "I designed it from scratch." It was a refactor of existing endpoints, so say "consolidated".

## One-liner if you're cut short

"I collapsed four fragmented ATC edit endpoints into one composite API: validated up front, all local writes in one `TransactionTemplate` transaction, and the MBS sync after commit, so a slow or failed remote call can never roll back or block a valid local edit."
