# Offline operations and recovery

The production frontend can complete sales, receive existing products into stock, transfer stock between authorized locations, and record stock counts without internet. IndexedDB stores a durable queue on the device; PostgreSQL stores the backend acknowledgement and business changes together. Redis is optional backend infrastructure and is not required for this workflow.

## Prepare each device

1. Deploy the backend containing Flyway migration **V34**, then deploy the frontend. Keep the current API URL and frontend domain stable while devices have pending work.
2. Open the production HTTPS site in the browser profile used for business. Sign in online with **Remember this device** enabled. The password itself is never saved; the device stores a salted PBKDF2 verifier for local sign-in.
3. Wait for **Offline ready**. Products, inventory, branches, organization and categories download automatically even if POS has never been opened. Open **Offline & sync → Prepare / refresh products** to repeat preparation. Stock-entry reference data is also downloaded for authorized users.
4. Keep using that browser profile and domain. Offline password sign-in is available for 30 days following online password sign-in; a cached unlocked session also has an expiry. Signing out locks access without deleting queued transactions or drafts.

## What works offline

| Workflow           | Offline behavior                                                                                                                                                                                                                                         |
| ------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| POS sales          | Complete a cash, manual M-Pesa, or other manually recorded tender; print a receipt marked pending backend sync.                                                                                                                                          |
| M-Pesa             | Select **Record M-Pesa manually (no STK push)** to record money already received. Manual tenders are not provider-verified payments. STK requests and live status checks require internet. An existing STK checkout cannot be changed to manual payment. |
| Stock receipt      | Add existing products and receive the supplier invoice. The complete batch is saved as one queued operation.                                                                                                                                             |
| Stock transfer     | Open **Warehouse → Stock transfers**, select source and destination, product and quantity. Both locations must be within the operator's current branch access.                                                                                           |
| Stock count        | Save the physical count against the known system quantity. If backend stock changed before sync, the operation stays saved for review instead of overwriting newer movements.                                                                            |
| History / recovery | **Offline & sync** lists pending, synced and review records, reopens saved sale receipts, retries synchronization and exports/restores backups.                                                                                                          |

New products, shops, suppliers and users; accounting postings; refunds; online payment verification; email delivery; and server shift opening/closing still require internet. Product images hosted on external sites might not be available offline; product names, SKU, prices and stock are saved independently of images.

## Synchronization and conflicts

- Supported transactions save to IndexedDB before the form/cart clears, including when online. A failed storage transaction prevents completion and preserves the entries.
- Each saved operation has an immutable UUID, original operator, captured payload and occurrence time. Completed records are retained locally after acknowledgement. The working set keeps pending work and 100 recent confirmed receipts; older confirmations move atomically to an indexed archive. Load older receipts or export a backup to recover full history. No automatic cleanup deletes the transaction journal.
- The queue runs in order in an open app, on reconnection, periodically with capped backoff, or through **Sync now**. Close/reopen recovery uses the same IDs. A short device lease coordinates tabs; backend row locks and unique receipts make overlapping retries safe even if a tab dies.
- The backend rechecks the actual signed-in operator, organization, current permissions and branch access. It commits each operation and its permanent receipt in one database transaction. A lost response can be retried without creating a second sale, receipt or transfer.
- Acknowledgements include other already-applied queued IDs, current inventory, and a monotonic snapshot revision. Device acknowledgement and baseline replacement commit atomically. This prevents a lost response, restored backup, or delayed tab response from applying a stock movement twice locally. Older HTTP snapshots cannot replace acknowledged stock.
- Original sale, stock receipt and stock count times are retained, together with captured catalogue prices/VAT. The backend still validates products, stock, payment totals and STK receipts. The original payload remains in the sync audit ledger.
- A server session expiry requires online sign-in as the original operator before sync. Passwords are not retained for automatic backend login. Pending work survives sign-out and session expiry.
- Connection failures and 5xx responses retain pending work. A permanent validation/stock conflict becomes **Needs review**, with the backend error shown. Later dependent operations remain queued. Correct the backend condition and use **Retry**; nothing is silently dropped or repriced.
- Two independent offline devices can both sell the same last unit. No disconnected device can know the other's stock movements. Such conflicts are retained for reconciliation; the app does not falsely label them synchronized.

## Retention and backup

Open **Offline & sync → Export backup** before clearing browser/site data, switching profiles/devices, or changing hosting addresses. Store backups securely; they contain business transactions and catalogue data. They do not contain passwords, password verifiers, CSRF tokens, session cookies, or payment-provider secrets. Backup filenames are excluded from Git and Vercel uploads.

Restore while signed into the same account and configured backend. Import merges atomically, validates the backup, preserves transaction IDs and rechecks backend acknowledgements instead of trusting a file's synced flag. Repeated imports do not enqueue duplicates. Exported operations retain their order, even if a device's wall clock changed.

The app requests persistent browser storage. Browsers can still refuse that request; clearing site data, private browsing, disk/device failure or uninstalling a browser can remove local records. Absolute no-loss storage cannot be guaranteed on a browser alone. Keep device exports and normal PostgreSQL backups, including `offline_operations` and the snapshot sequence. Never truncate the deduplication ledger while old devices/backups might retry transactions.

Offline storage is account-scoped and access is locked on sign-out. It is not encrypted at rest by this application. The initial read-cache's 30-day expiry and size limit do not delete the separate transaction journal.

## Verification

Frontend unit tests cover atomic queue writes, full storage, duplicate retries, lost acknowledgements, user isolation, saved operation order, backup restore and offline password verification. Browser tests run against the production service worker with network disabled, including products prepared before visiting POS, cash/manual M-Pesa receipts, reloads, stock receipt and transfer sync, offline logout/login and response loss.

```powershell
# frontend/
npm run test
npm run test:offline

# backend/ (uses an isolated disposable PostgreSQL cluster)
python scripts/run-local-pos-integration.py --tests OfflineOperationsIT
```

Browser API fixtures do not call live payment providers or production databases. Backend integration tests exercise real PostgreSQL transactions, concurrency, permissions and migration V34. Development intentionally disables service workers; use a production build/preview for offline verification.

Browser storage reference: [MDN IndexedDB](https://developer.mozilla.org/en-US/docs/Web/API/IndexedDB_API) and [persistent storage requests](https://developer.mozilla.org/en-US/docs/Web/API/StorageManager/persist).

Password hashing reference: [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html). New local enrollments use PBKDF2-HMAC-SHA256 with a unique salt and 600,000 iterations.
