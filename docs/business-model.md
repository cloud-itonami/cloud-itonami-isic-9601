# Business Model: Washing and

## Classification

- Repository: `cloud-itonami-isic-9601`
- ISIC Rev.5: `9601`
- Activity: washing and (dry-)cleaning of textile and fur products -- laundry and dry-cleaning services for customers
- Social impact: community access, data sovereignty, transparent audit

## Customer

- independent laundries/dry-cleaners
- cooperative garment-care collectives
- community laundry programs

## Offer

- garment intake
- care-plan/quote proposal
- cleaning-completion/return proposal
- immutable audit ledger

## Revenue

- self-host setup: one-time implementation fee
- managed hosting: monthly subscription per shop
- support: monthly retainer with SLA
- migration: import from an incumbent laundry-management system
- per-order fee

## Trust Controls

- no cleaning process is applied and no garment is returned without human sign-off
- a fabricated care-plan forces a hold, not an override
- every cleaning path is auditable
- emergency manual override paths remain outside LLM control
- a cleaning process forbidden by a garment's own care label, or a not-
  current solvent-handling certification, forces a hold, not an
  override
- cleaning-process application and garment return are each logged and
  escalated, and cannot be finalized twice for the same garment: a
  double-application or double-return attempt is held off this
  actor's own garment facts alone, with no upstream comparison needed

## Garment Care Governor: decision rule

`blueprint.edn` fixes `:itonami.blueprint/governor` to `:garment-
care-governor` -- this is not a generic "review step," it is the
gate the two real-world acts this business performs (applying a
cleaning process, returning a garment) must pass. The governor sits
between the LaundryOps-LLM and execution, per the README's Core
Contract:

```text
LaundryOps-LLM -> Garment Care Governor -> hold, proceed, or human approval
```

**Approves**: routine garment-care actions proposed against a garment
that already has a consented care plan on file, a proposed cleaning
process not forbidden by its own care label, and a current solvent-
handling certification. These proceed straight to the garment-care
ledger.

**Rejects or escalates**: the governor refuses to let the advisor
apply a cleaning process or return a garment on its own authority
when any of the following hold -- a fabricated jurisdiction spec-
basis; incomplete evidence; a cleaning process forbidden by the
garment's own care label; a not-current solvent-handling
certification. A clean proposal still always routes to a human --
`:actuation/apply-cleaning-process`/`:actuation/return-garment` are
never auto-committed, at any rollout phase.
