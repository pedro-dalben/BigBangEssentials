# ChestShop

ChestShop is the physical player/admin sign shop. `/shop` belongs to AdminShop;
`/chestshop` and `/cshop` belong to ChestShop.

Player BUY transfers the exact configured amount from buyer to owner and moves
the exact item quantity from chest to buyer. Player SELL transfers owner to
seller and moves the item in the opposite direction. Admin shops use explicit
server debit/credit policy and do not imply a player owner.

Sign clicks use an asynchronous durable saga. The SQL journal is authoritative
when the configured economy backend is `DATABASE`; it records the transaction,
item snapshot, checkpoints, financial keys, compensation key, and recovery
status. Item and money completion are separate checkpoints, so a failed
compensation is visible rather than silently reported as success.

## Commands

| Command | Purpose |
|---|---|
| `/chestshop` | Help |
| `/chestshop list [player]` | List shops; other players require `bigbangessentials.shop.list.others` |
| `/chestshop info` | Inspect the looked-at shop |
| `/chestshop convert` | Convert/register the looked-at sign |
| `/chestshop remove <x> <y> <z>` | Administrative removal |
| `/chestshop reload` | Reload shops |
| `/chestshop admin status` | Count pending/recovery operations |
| `/cshop` | Alias for `/chestshop` |

Use the original transaction ID shown by a recovery message when operating on
an incomplete sale. Never retry a click with a newly invented financial key.

Migration V028 creates `bbe_chestshop_operations`. Existing JSON journal data is
not discarded automatically and must be reviewed before any import.
