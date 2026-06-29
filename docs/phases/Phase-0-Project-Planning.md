# Phase 0 — Project Planning

## Objective
Think before coding. Define what we're building, why, and how — without writing a single line of Java.

---

## What We Built
- `README.md` at the project root
- Architecture diagram
- Technology stack decision
- Folder structure strategy
- Git branching strategy

---

## Concepts Introduced

### SDLC (Software Development Life Cycle)
```
Planning → Requirements → Design → Implementation → Testing → Deployment → Maintenance
```
Professional teams don't start with code. They plan what to build (requirements), then how to build it (design), then they code. Skipping planning causes wrong features, wasted time, rework.

### Functional vs Non-Functional Requirements
| Type | Question it answers | Example |
|---|---|---|
| Functional | What does the system DO? | "User can register, login, place order" |
| Non-Functional | How WELL does it do it? | "API responds in < 200ms, 99.9% uptime" |

Interviewers test this distinction constantly. If asked "design an e-commerce system" — list both types BEFORE drawing diagrams.

### Monolith vs Microservices
```
Monolith: one deployable unit, one codebase, one DB
  ✓ Easy to build, test, debug
  ✗ Hard to scale individual parts

Microservices: multiple small services, each with its own DB
  ✓ Each service scales independently
  ✗ Network complexity, distributed transactions, harder debugging
```
We start monolith (Phases 1–15), extract microservices in Phase 16. This is how Netflix, Amazon, and Uber did it.

### Package by Feature (not by Layer)
```
❌ Package by Layer (bad):       ✅ Package by Feature (good):
controllers/                     auth/
  UserController                   AuthController
  OrderController                  AuthService
services/                          AuthRepository
  UserService                    order/
  OrderService                     OrderController
repositories/                      OrderService
  UserRepository                   OrderRepository
```
Feature packages are self-contained. Moving a package = moving a microservice.

### Git Strategy — GitHub Flow
```
main (always deployable)
  └── feature/phase-1-health
  └── feature/phase-3-auth
  └── fix/category-500-bug
```
Every branch merges back to main via Pull Request. No long-lived branches.

---

## Interview Questions

**Q: What is the difference between functional and non-functional requirements?**
> Functional = what the system does (register, login, place order). Non-functional = how well it performs (speed, availability, security). Both must be defined before design begins.

**Q: When should you split a monolith into microservices?**
> When you have a concrete reason: a specific service needs to scale independently, or teams need deployment autonomy. Not just because it's trendy. Premature microservices add complexity before you understand your domain boundaries.

**Q: What is 99.9% uptime in real terms?**
> 8.76 hours of allowed downtime per year. 99.99% = 52 minutes. Knowing these numbers demonstrates SLA awareness, critical in production system design.

---

## MFAQ (Most Frequently Asked Questions)

**Why no code in Phase 0?**
Because decisions made here shape every future line of code. A wrong architecture decision costs 10x more to fix later.

**Why PostgreSQL and not MySQL?**
PostgreSQL has better standards compliance, native JSON support, more advanced indexing, and is preferred in the industry for new projects. Both work fine for this project.

**Why Java 17 and not Java 21?**
Java 17 is LTS (Long Term Support) — stable and widely adopted in industry. Java 21 is newer but most enterprise projects are still on 17. We stay close to what interviews expect.
