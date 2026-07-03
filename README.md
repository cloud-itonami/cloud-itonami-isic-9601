# cloud-itonami-9601

Open Business Blueprint for **ISIC Rev.5 9601**: Washing and.

This repository designs a forkable OSS business for washing and (dry-)cleaning of textile and fur products -- laundry and dry-cleaning services for customers -- run by a qualified operator so a community or
independent provider never surrenders customer/member data and ledgers to a
closed SaaS.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot performs
the physical domain work**. Here a garment-handling robot assists physical sorting, cleaning-machine loading and pressing,
under an actor that proposes actions and an independent **Garment Care Governor**
that gates them. The governor never dispatches hardware itself;
`:high`/`:safety-critical` actions require human sign-off.

## Core Contract

```text
intake + identity + service/member records
        |
        v
LaundryOps-LLM -> Garment Care Governor -> hold, proceed, or human approval
        |
        v
service/member ledger + evidence record + audit
```

No automated proposal, by itself, can complete the following without governor
approval and audit evidence: applying a cleaning process or returning a garment.

## Capability layer

This blueprint resolves its technology stack via
[`kotoba-lang/industry`](https://github.com/kotoba-lang/industry) (ISIC
`9601`). This vertical's service/member records are practice-specific
rather than a shared cross-operator data contract, so it runs on the generic
identity/forms/dmn/bpmn/audit-ledger stack -- no bespoke domain capability lib.

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Maturity

`:blueprint` -- this repository is the published business/operator design.
The governed actor implementation (`LaundryOps-LLM` + `Garment Care Governor` as
running code) is a follow-up, same as any other `:blueprint`-tier
`cloud-itonami-*` entry in `kotoba-lang/industry`'s registry.

## License

Code and implementation templates are AGPL-3.0-or-later.
