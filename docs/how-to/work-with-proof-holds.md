# Work with proof holds

A **hold** is an exclusive claim on a proof, taken by a flow that is part-way
through spending it. Two flows take holds, and they share one column deliberately:
that is what makes a swap hold block a melt on the same proof, and vice versa.

| `hold_kind` | Flow | Taken by |
|-------------|------|----------|
| `MELT` | The melt saga | A Lightning payment being attempted |
| `SWAP` | The swap hold | A swap being signed (cashu-mint#400) |

## The rule that matters

**A stale melt hold and a stale swap hold resolve in opposite directions.**

- A stale **melt** hold is **released**. No payment went out, so the wallet should
  get its money back.
- A stale **swap** hold that reached signing must be **committed**. An output may
  already be redeemable, and releasing the inputs on top of it is a double spend.

Before `hold_kind` existed, an operator looking at a held row had to infer which
flow produced it from a `swap-` prefix on the id before they could know which
action was safe. `hold_kind` states the flow outright, which is the whole reason
the column was added in `V7__rename_melt_saga_id_to_hold_id.sql`.

Never resolve a hold without reading its `hold_kind` first.

## Endpoints

All relative to `/vault/proof`.

### Take a hold

```http
POST /vault/proof/mint/{mintId}/hold/{holdId}/insert-or-claim
Content-Type: application/json

[ { "secret": "...", "amount": 8, "unblindedSignature": "..." } ]
```

Atomically inserts or claims each proof as `PENDING` bound to `holdId`, returning
the count bound.

The atomicity is the point. This replaced a two-step `POST /store` followed by
`POST /mark-pending`, which **could not bind freshly-inserted rows**: between the
two calls a proof existed unbound, and a concurrent flow could claim it.

Requirements:

- `mintId` must be a 36-character UUID.
- `holdId` is at most 64 characters.
- Secrets must be **Y-normalised**.
- Every proof needs a non-blank `secret`, a non-null `amount`, and a non-blank
  `unblindedSignature`. Malformed proofs are rejected with `400` up front, rather
  than surfacing later as a quiet partial bind against a `NOT NULL` constraint.
- An empty proof list is a `400`.

### Mark pending

```http
POST /vault/proof/mint/{mintId}/hold/{holdId}/mark-pending
```

Binds already-stored proofs to a hold. Prefer `insert-or-claim` for new proofs.

### Resolve a hold

```http
POST /vault/proof/hold/{holdId}/commit-spent
```

Moves the hold's `PENDING` proofs to spent. Use when the flow completed: a melt
whose payment settled, or **any swap hold that reached signing**.

```http
POST /vault/proof/hold/{holdId}/refund
```

Returns the hold's `PENDING` proofs to `UNSPENT` and clears the `hold_id` binding.
Use when the flow failed without value leaving: a melt whose payment never went
out.

Both return the number of rows updated.

## Resolving a stuck hold

1. **Read `hold_kind` first.** It decides which action is safe.
2. If `MELT`, confirm with the Lightning backend that no payment settled. Then
   `refund`.
3. If `SWAP`, determine whether signing completed. If it did, or if you cannot
   establish that it did not, `commit-spent`. Releasing inputs against a
   potentially redeemable output is a double spend, so the uncertain case resolves
   toward committing.
4. Check the returned row count matches what you expected. A count of zero means
   the hold was already resolved.

## Schema notes

- `hold_id` is indexed (`ix_proof_hold_id`).
- `hold_kind` is `VARCHAR(8)`, holding `MELT` or `SWAP`, and is `NULL` when no hold
  is held.
- Both columns exist on `t_proof` and the archive table `t_proof_a`.
- Rows written before `V7` were classified once from the `swap-` id prefix. That
  inference is exactly what the column removes, and it was safe only because it
  ran once against historical rows.

## Related

- [API reference](../reference/api.md)
- [Configuration reference](../reference/configuration.md)
- [Architecture](../explanation/architecture.md)
