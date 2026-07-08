# cloud-itonami-isic-9601

Open Business Blueprint for **ISIC Rev.5 9601**: Washing and
(dry-)cleaning of textile and fur products.

This repository publishes a garment-care actor -- garment intake,
garment-care regulatory assessment, solvent-handling-certification
screening, cleaning-process application and garment return -- as an
OSS business that any qualified operator can fork, deploy, run,
improve and sell, so a community or independent provider never
surrenders customer data and ledgers to a closed SaaS.

Built on this workspace's
[`langgraph-clj`](https://github.com/com-junkawasaki/langgraph-clj)
StateGraph runtime (portable `.cljc`, supervised superstep loop,
interrupts, Datomic/in-mem checkpoints) -- the same actor pattern as
every prior actor in this fleet
([`cloud-itonami-isic-6511`](https://github.com/cloud-itonami/cloud-itonami-isic-6511),
[`6512`](https://github.com/cloud-itonami/cloud-itonami-isic-6512),
[`6621`](https://github.com/cloud-itonami/cloud-itonami-isic-6621),
[`6622`](https://github.com/cloud-itonami/cloud-itonami-isic-6622),
[`6629`](https://github.com/cloud-itonami/cloud-itonami-isic-6629),
[`6520`](https://github.com/cloud-itonami/cloud-itonami-isic-6520),
[`6530`](https://github.com/cloud-itonami/cloud-itonami-isic-6530),
[`6820`](https://github.com/cloud-itonami/cloud-itonami-isic-6820),
[`6612`](https://github.com/cloud-itonami/cloud-itonami-isic-6612),
[`6492`](https://github.com/cloud-itonami/cloud-itonami-isic-6492),
[`6920`](https://github.com/cloud-itonami/cloud-itonami-isic-6920),
[`6611`](https://github.com/cloud-itonami/cloud-itonami-isic-6611),
[`7120`](https://github.com/cloud-itonami/cloud-itonami-isic-7120),
[`8620`](https://github.com/cloud-itonami/cloud-itonami-isic-8620),
[`8530`](https://github.com/cloud-itonami/cloud-itonami-isic-8530),
[`9200`](https://github.com/cloud-itonami/cloud-itonami-isic-9200),
[`7500`](https://github.com/cloud-itonami/cloud-itonami-isic-7500),
[`9603`](https://github.com/cloud-itonami/cloud-itonami-isic-9603),
[`9521`](https://github.com/cloud-itonami/cloud-itonami-isic-9521),
[`9321`](https://github.com/cloud-itonami/cloud-itonami-isic-9321),
[`8730`](https://github.com/cloud-itonami/cloud-itonami-isic-8730),
[`9102`](https://github.com/cloud-itonami/cloud-itonami-isic-9102),
[`9103`](https://github.com/cloud-itonami/cloud-itonami-isic-9103),
[`9602`](https://github.com/cloud-itonami/cloud-itonami-isic-9602),
[`9000`](https://github.com/cloud-itonami/cloud-itonami-isic-9000),
[`8890`](https://github.com/cloud-itonami/cloud-itonami-isic-8890),
[`8610`](https://github.com/cloud-itonami/cloud-itonami-isic-8610),
[`9311`](https://github.com/cloud-itonami/cloud-itonami-isic-9311),
[`8510`](https://github.com/cloud-itonami/cloud-itonami-isic-8510),
[`9412`](https://github.com/cloud-itonami/cloud-itonami-isic-9412),
[`6491`](https://github.com/cloud-itonami/cloud-itonami-isic-6491),
[`8720`](https://github.com/cloud-itonami/cloud-itonami-isic-8720),
[`8521`](https://github.com/cloud-itonami/cloud-itonami-isic-8521),
[`6619`](https://github.com/cloud-itonami/cloud-itonami-isic-6619),
[`3600`](https://github.com/cloud-itonami/cloud-itonami-isic-3600),
[`6190`](https://github.com/cloud-itonami/cloud-itonami-isic-6190),
[`3030`](https://github.com/cloud-itonami/cloud-itonami-isic-3030),
[`3830`](https://github.com/cloud-itonami/cloud-itonami-isic-3830),
[`7020`](https://github.com/cloud-itonami/cloud-itonami-isic-7020),
[`9420`](https://github.com/cloud-itonami/cloud-itonami-isic-9420),
[`9491`](https://github.com/cloud-itonami/cloud-itonami-isic-9491),
[`2610`](https://github.com/cloud-itonami/cloud-itonami-isic-2610),
[`3512`](https://github.com/cloud-itonami/cloud-itonami-isic-3512),
[`8810`](https://github.com/cloud-itonami/cloud-itonami-isic-8810),
[`8691`](https://github.com/cloud-itonami/cloud-itonami-isic-8691),
[`8569`](https://github.com/cloud-itonami/cloud-itonami-isic-8569),
[`6419`](https://github.com/cloud-itonami/cloud-itonami-isic-6419),
[`7310`](https://github.com/cloud-itonami/cloud-itonami-isic-7310),
[`7320`](https://github.com/cloud-itonami/cloud-itonami-isic-7320),
[`7210`](https://github.com/cloud-itonami/cloud-itonami-isic-7210),
[`7410`](https://github.com/cloud-itonami/cloud-itonami-isic-7410),
[`8710`](https://github.com/cloud-itonami/cloud-itonami-isic-8710),
[`8541`](https://github.com/cloud-itonami/cloud-itonami-isic-8541),
[`8690`](https://github.com/cloud-itonami/cloud-itonami-isic-8690)) --
here it is **LaundryOps-LLM ⊣ Garment Care Governor**.

> **Why an actor layer at all?** An LLM is great at drafting a
> garment-intake summary, normalizing records, and checking whether a
> proposed cleaning process actually appears on a garment's own care
> label as forbidden -- but it has **no notion of which jurisdiction's
> garment-care/solvent-handling law is official, no license to apply a
> real cleaning process or return a real garment, and no way to know
> on its own whether a solvent-handling certification has actually
> stayed current**. Letting it apply a cleaning process or return a
> garment directly invites fabricated regulatory citations, a
> care-label-forbidden process ruining a customer's garment, and a
> lapsed certification being quietly overlooked -- and liability, and
> customer-trust risk, for whoever runs it. This project seals the
> LaundryOps-LLM into a single node and wraps it with an independent
> **Garment Care Governor**, a human **approval workflow**, and an
> immutable **audit ledger**.

## Scope: what this actor does and does not do

This actor covers garment intake through garment-care regulatory
assessment, solvent-handling-certification screening, cleaning-
process application and garment return. It does **not**, by itself,
hold any license required to operate as a laundry/dry-cleaning
business in a given jurisdiction, and it does not claim to. It also
does not model a real laundry-management system, the actual physical
cleaning itself, or fabric-care judgment beyond the garment's own
recorded care label -- `laundry.registry/cleaning-process-forbidden-
by-care-label?` is a pure ground-truth set-membership recompute
against the garment's own recorded fields, not a fabric assessment.
Whoever deploys and operates a live instance (a licensed laundry/dry-
cleaning operator) supplies any jurisdiction-specific license, the
real cleaning-machine workforce and the real laundry-management-
system integrations, and bears that jurisdiction's liability -- the
software supplies the governed, spec-cited, audited execution
scaffold so that operator does not have to build the compliance layer
from scratch.

### Actuation

**Applying a real cleaning process or returning a real garment is
never autonomous, at any phase, by construction.** Two independent
layers enforce this (`laundry.governor`'s `:actuation/apply-cleaning-
process`/`:actuation/return-garment` high-stakes gate and `laundry.
phase`'s phase table, which never puts `:actuation/apply-cleaning-
process`/`:actuation/return-garment` in any phase's `:auto` set) --
see `laundry.phase`'s docstring and `test/laundry/phase_test.clj`'s
`apply-cleaning-process-never-auto-at-any-phase`/`return-garment-
never-auto-at-any-phase`. The actor may draft, check and recommend; a
human laundry/dry-cleaning staff member is always the one who
actually applies a cleaning process or returns a garment. Like
`6512`/`6622`/`6520`/`6530`/`6820`/`6920`/`6611`/`8530`/`9200`/`9521`/
`8730`/`9102`/`9103`/`8890`/`8610`/`8510`/`9412`/`8720`/`8521`/`6619`/
`3600`/`6190`/`3030`/`3830`/`9420`/`9491`/`2610`/`3512`/`8810`/`8691`/
`8569`/`6419`, this actor has TWO actuation events, both POSITIVE
(applying/finalizing a real record), matching the majority pattern in
this fleet (`3600`/`6190` are the fleet's two NEGATIVE-actuation
exceptions).

## The core contract

```
garment intake + jurisdiction facts (laundry.facts, spec-cited)
        |
        v
   ┌──────────────┐   proposal      ┌───────────────────────┐
   │ LaundryOps-  │ ─────────────▶ │ Garment Care Governor:        │  (independent system)
   │ LLM (sealed) │  + citations    │ spec-basis · evidence-        │
   └──────────────┘                 │ incomplete · cleaning-process-  │
          │                 commit ◀┼ forbidden-by-care-label (set-    │
          │                         │ membership) · certification-       │
    record + ledger        escalate ┼ not-current (unconditional) ·        │
          │              (ALWAYS for│ already-cleaned/already-returned      │
          │               :actuation└───────────────────────┘
          │               /apply-cleaning-
          ▼               process /
      human approval      :actuation/return-
                           garment)
```

**The LaundryOps-LLM never applies a cleaning process or returns a
garment the Garment Care Governor would reject, and never does so
without a human sign-off.** Hard violations (fabricated regulatory
requirements; unsupported evidence; a cleaning process forbidden by
the garment's own care label; a not-current solvent-handling
certification; a double application or return) force **hold** and
*cannot* be approved past; a clean proposal still always routes to a
human.

## Run

```bash
clojure -M:dev:run     # walk one clean dual-actuation lifecycle + five HARD-hold cases through the actor
clojure -M:dev:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint        # clj-kondo (errors fail; CI mirrors this)
```

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here a garment-handling robot
assists physical sorting, cleaning-machine loading and pressing under
the actor, gated by the independent **Garment Care Governor**. The
governor never dispatches hardware itself; `:high`/`:safety-critical`
actions require human sign-off.

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, Garment Care Governor, cleaning-application + garment-return draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`9601`). This vertical's service/member records are practice-specific
rather than a shared cross-operator data contract, so `laundry.*`
runs on the generic robotics/identity/forms/dmn/bpmn/audit-ledger
stack only -- no bespoke domain capability lib to reference at all.

## Layout

| File | Role |
|---|---|
| `src/laundry/store.cljc` | **Store** protocol -- `MemStore` ‖ `DatomicStore` (`langchain.db`) + append-only audit ledger + separate cleaning-application/garment-return history. No dynamically-filed sub-record -- both actuation ops act directly on a pre-seeded garment, and the double-actuation guards check dedicated `:cleaning-applied?`/`:garment-returned?` booleans rather than a `:status` value |
| `src/laundry/registry.cljc` | Cleaning-application + garment-return draft records, plus `cleaning-process-forbidden-by-care-label?` -- a GENUINELY NEW concept (grep-verified absent from every prior sibling), the SIXTH instance of this fleet's set-membership/conflict check family (`clinic`/`veterinary`/`entertainment`/`nursing`/`alliedhealth` established the first five) |
| `src/laundry/facts.cljc` | Per-jurisdiction garment-care/solvent-handling catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/laundry/laundryadvisor.cljc` | **LaundryOps-LLM** -- `mock-advisor` ‖ `llm-advisor`; intake/careplan-verification/certification-screening/cleaning-application/garment-return proposals |
| `src/laundry/governor.cljc` | **Garment Care Governor** -- 4 HARD checks (spec-basis · evidence-incomplete · cleaning-process-forbidden-by-care-label, ground-truth set-membership recompute · certification-not-current, unconditional evaluation, the 45th grounding of this discipline, a concept reuse renamed for this domain) + already-cleaned/already-returned guards + 1 soft (confidence/actuation gate) |
| `src/laundry/phase.cljc` | **Phase 0→3** -- read-only → assisted intake → assisted verify → supervised (both cleaning-process application and garment return always human; garment intake is the ONLY auto-eligible op, no direct capital risk) |
| `src/laundry/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph |
| `src/laundry/sim.cljc` | demo driver |
| `test/laundry/*_test.clj` | governor contract · phase invariants · store parity · registry conformance · facts coverage |

## Business-process coverage (honest)

This actor covers garment intake through garment-care regulatory
assessment, solvent-handling-certification screening, cleaning-
process application and garment return -- the core governed lifecycle
this blueprint's own `docs/business-model.md` names as its Offer:

| Covered | Not covered (out of scope for this R0) |
|---|---|
| Garment intake + per-jurisdiction garment-care checklisting, HARD-gated on an official spec-basis citation (`:garment/intake`/`:careplan/verify`) | Real laundry-management-system integration, real physical cleaning itself (see `laundry.facts`'s docstring) |
| Solvent-handling-certification screening, evaluated unconditionally so the screening op itself can HARD-hold on its own finding (`:certification/screen`) | Any fabric/garment-care judgment itself beyond the garment's own recorded care label -- deliberately outside this actor's competence |
| Cleaning-process application, HARD-gated on full evidence and the garment's own care-label ground truth, plus a double-application guard (`:actuation/apply-cleaning-process`) | |
| Garment return, HARD-gated on full evidence, plus a double-return guard (`:actuation/return-garment`) | |
| Immutable audit ledger for every intake/verification/screening/application/return decision | |

Extending coverage is additive: add the next gate (e.g. a garment-
value-insurance-threshold check) as its own governed op with its own
HARD checks and tests, following the SAME "an independent governor
re-verifies against the actor's own records before any real-world act"
pattern this repo's flagship op already establishes.

## Jurisdiction coverage (honest)

`laundry.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `laundry.facts/catalog` --
currently 4 seeded (JPN, USA, GBR, DEU) out of ~194 jurisdictions
worldwide. This is a starting catalog to prove the governor contract
end-to-end, not a claim of global coverage. Adding a jurisdiction is
additive: one map entry in `laundry.facts/catalog`, citing a real
official source -- never fabricate a jurisdiction's requirements to
make coverage look bigger.

## Maturity

`:implemented` -- `LaundryOps-LLM` + `Garment Care Governor` run as
real, tested code (see `Run` above), promoted from the originally-
published `:blueprint`-tier scaffold, modeled closely on the sixty
prior actors' architecture. See `docs/adr/0001-architecture.md` for
the history and design.

## License

Code and implementation templates are AGPL-3.0-or-later.
