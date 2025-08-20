# REST API Reference

All endpoints are served under the `/vault` base path.

## Mint

### Create mint
- **Method:** `POST`
- **Path:** `/vault/mint`
- **Body Fields:**
  - *(optional)* `id` (UUID)
  - *(optional)* `archived` (boolean, default `false`)
  - *(optional)* `createdAt` (ISO timestamp)
  - *(optional)* `updatedAt` (ISO timestamp)
  - *(optional)* `version` (integer)
- **Example response:**
```json
{
  "id": "11111111-2222-3333-4444-555555555555",
  "archived": false,
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z",
  "version": 0
}
```

### Retrieve mint
- **Method:** `GET`
- **Path:** `/vault/mint/{id}`
- **Path Parameters:**
  - `id` (UUID, required)
- **Example response:**
```json
{
  "id": "11111111-2222-3333-4444-555555555555",
  "archived": false,
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z",
  "version": 0
}
```

### Archive mint
- **Method:** `POST`
- **Path:** `/vault/mint/archive/{id}`
- **Path Parameters:**
  - `id` (UUID, required)
- **Example response:**
```json
{
  "id": "11111111-2222-3333-4444-555555555555",
  "archived": true,
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z",
  "version": 1
}
```

### Delete mint
- **Method:** `DELETE`
- **Path:** `/vault/mint/{id}`
- **Path Parameters:**
  - `id` (UUID, required)
- **Example response:** `204 No Content`

## Key set

### Create key set
- **Method:** `POST`
- **Path:** `/vault/keyset`
- **Body Fields:**
  - `keySetId` (string, required)
  - `unit` (string, required)
  - `mint.id` (UUID, required)
  - *(optional)* `id`, `archived`, `createdAt`, `updatedAt`, `version`
- **Example response:**
```json
{
  "id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
  "archived": false,
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z",
  "version": 0,
  "keySetId": "abc123",
  "unit": "sat",
  "mint": {"id": "11111111-2222-3333-4444-555555555555"}
}
```

### Retrieve key set by ID
- **Method:** `GET`
- **Path:** `/vault/keyset/{id}`
- **Path Parameters:** `id` (UUID, required)
- **Example response:** as above.

### Retrieve key set by key set ID
- **Method:** `GET`
- **Path:** `/vault/keyset/id/{id}`
- **Path Parameters:** `id` (string, required)
- **Example response:** as above.

### List key sets by unit
- **Method:** `GET`
- **Path:** `/vault/keyset/unit/{unit}`
- **Path Parameters:** `unit` (string, required)
- **Example response:**
```json
[
  {
    "id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
    "archived": false,
    "createdAt": "2024-01-01T00:00:00Z",
    "updatedAt": "2024-01-01T00:00:00Z",
    "version": 0,
    "keySetId": "abc123",
    "unit": "sat",
    "mint": {"id": "11111111-2222-3333-4444-555555555555"}
  }
]
```

### Retrieve key set for mint, unit and ID
- **Method:** `GET`
- **Path:** `/vault/keyset/mint/{mintId}/unit/{unit}/keyset/{keySetId}`
- **Path Parameters:** `mintId` (UUID, required), `unit` (string, required), `keySetId` (string, required)
- **Example response:** as above.

### List key sets for mint
- **Method:** `GET`
- **Path:** `/vault/keyset/mint/{mintId}`
- **Path Parameters:** `mintId` (UUID, required)
- **Example response:** array of key set objects.

### Archive key set
- **Method:** `POST`
- **Path:** `/vault/keyset/archive/{id}`
- **Path Parameters:** `id` (UUID, required)
- **Example response:** key set object with `archived` set to `true`.

### Delete key set
- **Method:** `DELETE`
- **Path:** `/vault/keyset/{id}`
- **Path Parameters:** `id` (UUID, required)
- **Example response:** `204 No Content`

## Key

### Create key
- **Method:** `POST`
- **Path:** `/vault/key`
- **Body Fields:**
  - `amount` (integer, required)
  - `privateKey` (string, required)
  - `keySet.id` (UUID, required)
  - *(optional)* `id`, `archived`, `createdAt`, `updatedAt`, `version`
- **Example response:**
```json
{
  "id": "bbbbbbbb-cccc-dddd-eeee-ffffffffffff",
  "archived": false,
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z",
  "version": 0,
  "amount": 1,
  "privateKey": "priv",
  "keySet": {"id": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"}
}
```

### Retrieve key
- **Method:** `GET`
- **Path:** `/vault/key/{id}`
- **Path Parameters:** `id` (UUID, required)
- **Example response:** as above.

### List keys by unit
- **Method:** `GET`
- **Path:** `/vault/key/unit/{unit}`
- **Path Parameters:** `unit` (string, required)
- **Example response:** array of key objects.

### Retrieve by private key
- **Method:** `GET`
- **Path:** `/vault/key/privatekey/{privateKey}`
- **Path Parameters:** `privateKey` (string, required)
- **Example response:** key object.

### List keys by key set
- **Method:** `GET`
- **Path:** `/vault/key/keyset/{id}`
- **Path Parameters:** `id` (UUID, required)
- **Example response:** array of key objects.

### Archive key
- **Method:** `POST`
- **Path:** `/vault/key/archive/{id}`
- **Path Parameters:** `id` (UUID, required)
- **Example response:** key object with `archived` set to `true`.

### Delete key
- **Method:** `DELETE`
- **Path:** `/vault/key/{id}`
- **Path Parameters:** `id` (UUID, required)
- **Example response:** `204 No Content`

## Proof

### Create proof
- **Method:** `POST`
- **Path:** `/vault/proof`
- **Body Fields:**
  - `mint.id` (UUID, required)
  - `amount` (integer, required)
  - `secret` (string, required)
  - `unblindedSignature` (string, required)
  - *(optional)* `witness` (string)
  - *(optional)* `state` (string, default `UNSPENT`)
  - *(optional)* `id`, `archived`, `createdAt`, `updatedAt`, `version`
- **Example response:**
```json
{
  "id": "cccccccc-dddd-eeee-ffff-000000000000",
  "archived": false,
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T00:00:00Z",
  "version": 0,
  "mint": {"id": "11111111-2222-3333-4444-555555555555"},
  "amount": 1,
  "secret": "sec",
  "unblindedSignature": "sig",
  "witness": null,
  "state": "UNSPENT"
}
```

### Retrieve proof
- **Method:** `GET`
- **Path:** `/vault/proof/{id}`
- **Path Parameters:** `id` (UUID, required)
- **Example response:** as above.

### List proofs by mint
- **Method:** `GET`
- **Path:** `/vault/proof/mint/{mintId}`
- **Path Parameters:** `mintId` (UUID, required)
- **Example response:** array of proof objects.

### Retrieve by secret
- **Method:** `GET`
- **Path:** `/vault/proof/secret/{secret}`
- **Path Parameters:** `secret` (string, required)
- **Example response:** proof object.

### Retrieve by mint and secret
- **Method:** `GET`
- **Path:** `/vault/proof/mint/{mintId}/secret/{secret}`
- **Path Parameters:** `mintId` (UUID, required), `secret` (string, required)
- **Example response:** proof object.

### List by mint and amount
- **Method:** `GET`
- **Path:** `/vault/proof/mint/{mintId}/amount/{amount}`
- **Path Parameters:** `mintId` (UUID, required), `amount` (integer, required)
- **Example response:** array of proof objects.

### Retrieve by mint and signature
- **Method:** `GET`
- **Path:** `/vault/proof/mint/{mintId}/signature/{unblindedSignature}`
- **Path Parameters:** `mintId` (UUID, required), `unblindedSignature` (string, required)
- **Example response:** proof object.

### Archive proof
- **Method:** `POST`
- **Path:** `/vault/proof/archive/{id}`
- **Path Parameters:** `id` (UUID, required)
- **Example response:** proof object with `archived` set to `true`.

### Delete proof
- **Method:** `DELETE`
- **Path:** `/vault/proof/{id}`
- **Path Parameters:** `id` (UUID, required)
- **Example response:** `204 No Content`

