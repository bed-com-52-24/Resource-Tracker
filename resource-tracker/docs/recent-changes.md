# Recent backend changes

This note summarizes the recent changes on the current branch, from the procurement workflow work through budget override approval. It is based on the commits currently present on this branch and the working tree as of 2026-11-19.

## Request decisions and fulfillment

- Department Managers can approve selected quantities on individual request lines. Request status reflects partial approval and can progress to full approval as remaining quantities are decided.
- Internal stock reservations follow approved quantities. Quantity changes update reservations and waitlist entries, with FIFO waitlist processing used when stock becomes available.
- Cancellation and completion are guarded by fulfillment state. A request cannot be cancelled after stock has been issued, and completion depends on approved quantities being issued, handed over, or waived.
- External items progress through procurement authorization, ordering, receipt, and acceptance. Status updates follow the defined sequence, and received quantities are tracked against each request line.
- Each incremental procurement authorization is checked against the remaining department budget; authorizations and commitments account for earlier approvals.

## Budget override approval

- A Department Manager can request a budget override for an external request line that exceeds the remaining department budget, providing a reason and requested amount and quantity.
- The request decision remains recorded while an over-budget line's procurement is held for review.
- Managing Directors can list pending overrides and approve or reject them with a decision comment. Approval commits the budget amount and authorizes procurement; rejection leaves procurement blocked for that line.
- Override decisions and related state changes are persisted and surfaced through notifications and audit events.

## External item handoff

- Once procurement is recorded as received or accepted, a Department Manager can record the item handoff to the requesting Team Lead through the stock issue endpoint.
- Handoffs cannot exceed the quantity recorded as received, after earlier handoffs and waived quantities are accounted for.
- External handoffs do not change catalogue stock and do not enable return tracking. They can still be recorded for a request that was otherwise completed if the delivery had not yet been handed over.

## API and data changes

- Added a pending budget override endpoint and expanded the budget decision operation to handle override decisions.
- Added budget override response fields and external fulfillment and handoff details to relevant response DTOs; request updates accept the revised line-decision data.
- Added database migrations for incremental procurement authorizations, budget override approvals, and external handoff support.
- Updated API and system documentation to cover the override decision path, procurement status sequence, and external handoff behavior. Architecture, security, deployment, and request lifecycle diagrams were also added.

## Validation coverage

Tests were added or updated around partial approvals, procurement progression and budget handling, waitlist changes, request completion, notification behavior, and concurrent request integrity.

## Local deployment configuration

The current working tree (not yet committed) changes the backend image tag from `v10` to `v11` in `docker-compose.yaml` and `start.sh`. The scripts therefore select the same image version for Compose and the direct startup path.

## Recent history

- `3b42875` — adds budget override functionality, pending override review, external item handoff behavior, API/system documentation, diagrams, and migrations.
- `15d5f39` — adds incremental procurement authorization migration and tests for budget handling, completion, and waitlist behavior.
- `14d1c38` — implements partial line approvals, reservation and FIFO waitlist updates, incremental external budget handling, procurement progression, and cancellation/completion safeguards.

For endpoint-level request and response details, see [API documentation](../api-documentation.md). For the broader system and workflow description, see [system description](../system-description.md).
