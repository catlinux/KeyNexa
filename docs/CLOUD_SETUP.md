# Cloud copies: setup guide

[English](CLOUD_SETUP.md) | [Español](es/CLOUD_SETUP.md)

> **Verified with a real account for Backblaze B2 (2026-10-15) and Cloudflare R2 (2026-10-16).** The Amazon S3 and MinIO steps follow each service's documentation and have not been tried yet. Free plans, prices and limits change over time and are not promised here. Only the official pages are linked.

KeyNexa only ever sends **the encrypted `.kdbx` file** to the service you choose, and only after you accept the consent screen. The master password, the keys and the decrypted content never leave the device. See [Privacy](PRIVACY.md).

Open **Settings → Copies in the cloud** and follow the assistant. It has five steps: consent, choose the service, what you need, the form and the state of the copies. For Backblaze, Amazon S3 and Cloudflare R2 the region is filled in automatically from the endpoint, so you can leave the region field empty.

## Backblaze B2

1. Create a free account at <https://www.backblaze.com/cloud-storage>. The page can take a while to change after you type the email. Confirm the email, choose a password (a password manager's generator is fine), choose the region where your data is stored (for example Europe or the United States), accept the terms and choose *personal* or *organisation*. Signing in asks for a code sent to your email (two-step check); you can tell it to trust that browser.
2. Create a **private** bucket (not public) with a name that is unique and has no personal data, for example `keynexa-copies-4821`. Leave the server-side options as they are: the copy is already encrypted by KDBX.
3. In the bucket details, note the **endpoint** (it can appear as "Endpoint", "S3 endpoint" or similar, for example `s3.eu-central-003.backblazeb2.com`). The panel shows it without `https://`; the assistant needs it with `https://`.
4. Create an application key (*Application Keys → Add a New Application Key*) **limited to that bucket**, with read and write permission.
5. Copy the `keyID` and the `applicationKey` (the application key is shown only once) into the assistant. Type them in the phone from your password manager.
6. In the form: endpoint `https://s3.<region>.backblazeb2.com`, the bucket name, and leave "use the bucket in the path" on. The region is optional: the assistant fills it in from the endpoint (for example `eu-central-003`), so you can leave the field empty.

## Cloudflare R2

Verified with a real account on 2026-10-16. The dashboard can be switched to Spanish in the account settings; the names below are the English ones.

1. Create an account at <https://dash.cloudflare.com/sign-up> with an email, a password and the human check, then confirm the email.
2. Open **Storage & databases → R2 object storage** and add the R2 subscription to the account. It asks for a payment card even though R2 includes free monthly usage; check the current prices on Cloudflare's own pages. A prepaid card was accepted.
3. Create a bucket (private by default). Location **Automatic** and storage class **Standard** are fine.
4. Create the token: **R2 object storage → Overview → Account Details → API Tokens → Manage → Create Account API token**, with the **Object Read & Write** permission. If the form does not let you limit it to the bucket, the token works for every bucket of the account: revoke it if you later create other buckets. Copy the Access Key ID and the Secret Access Key (the secret is shown only once) into your password manager.
5. In the assistant, use the endpoint `https://<account id>.r2.cloudflarestorage.com` (the account ID is on the R2 overview page). The region is optional: the assistant fills in `auto`. Leave "use the bucket in the path" on. A bucket created in a jurisdiction (EU, US) needs its own endpoint, such as `https://<account id>.eu.r2.cloudflarestorage.com`.

## Amazon S3

Not tried with a real account yet. Amazon asks for a payment card when you sign up and bills by use; a vault is tiny, but check the current prices and free tier on Amazon's own pages.

1. Create an AWS account at <https://aws.amazon.com/s3/> and open the S3 console.
2. Create a bucket. Choose the region closest to you (for example `eu-west-1`) and leave **Block all public access** turned on.
3. In IAM, create a user for KeyNexa with no console access and a policy limited to that bucket: it needs `s3:ListBucket` on the bucket and `s3:GetObject`, `s3:PutObject` and `s3:DeleteObject` on its objects.
4. Create an access key for that user and copy the Access Key ID and the Secret Access Key (the secret is shown only once) into the assistant.
5. For the endpoint, use `https://s3.<region>.amazonaws.com` (for example `https://s3.eu-west-1.amazonaws.com`). The region is filled in from the endpoint, and you can leave "use the bucket in the path" on.

## MinIO or another server of your own

1. Create a bucket on your server, private.
2. Create a user or key with read and write permission over that bucket.
3. Use your HTTPS endpoint, the region your server expects and the bucket name. Keep "use the bucket in the path" on. See <https://min.io/docs>.

## Test the connection

The **Test connection** button lists the backup folder, uploads a tiny test object and deletes it. It tells you which of these happened:

- **Connection works**: the keys, the endpoint and the bucket are fine. Then press **Save and turn on**.
- **The service rejected the keys**: check the access key ID and the secret key, and that the key can read and write in this bucket.
- **The bucket was not found or the address is wrong**: check the endpoint and the bucket name.
- **Could not connect**: check the network and the address, and try again.

Nothing is saved until the test works.

## Copies and errors

- After a successful save, KeyNexa uploads a copy in the background, at most once a day. You can force one with **Copy now**.
- Only copies KeyNexa created are rotated: the newest ones are kept and the oldest are deleted. You choose 5, 10 or 20.
- If a copy fails, the state screen shows a short message; a failed upload never blocks saving the vault.
- **Copy now** needs an open vault: open the vault, then **Settings** in its menu (the three dots) and **Copies in the cloud**. Settings opened from the vault list have no vault to copy.
- The first copy shows no notice: the state line changes to "Last copy: 0 minutes ago". Check it in the service's own panel (in Backblaze: *Buckets → your bucket → Browse Files*, folder `KeyNexa/`).
- If the keys stop working, configure the service again with **Change service**.
- Android does not let an app use the system *Downloads* folder as the location of a vault; keep vaults in another folder.

## Synchronisation

The last step of the assistant, **the state of the copies**, has a **Synchronisation** switch: *Synchronise my vaults through this service*. It is off by default. When you turn it on, KeyNexa first checks against your own bucket that the service honours conditional writes; only if the check passes does the switch stay on. Some services, such as Backblaze B2, may not support them: in that case synchronisation stays off and the service keeps working for copies. The Android app has the same switch, in the cloud assistant's state step; only the cloud edition has it, and the offline edition has no network access at all.

With synchronisation on, the cloud keeps one encrypted object per vault. KeyNexa downloads it, merges it with your local vault and uploads the result only when nothing else changed it, so two devices that are never on at the same time end up the same. Offline use is unaffected: the local file is always the one you work with, and the remote object is always merged before anything is uploaded. The service only sees the encrypted file, exactly as with the copies. Older versions of an entry stay in its history, and deleting the vault on one device deletes it everywhere after the next synchronisation.

Synchronisation runs after a save, when a vault is unlocked and when the window regains the focus, at most once a minute.

A device with no local copy uses **Bring a vault from the cloud** on the home screen (it appears when synchronisation is on), picks a synchronised vault from the list (the name is encrypted, so only the beginning of its identifier, its size and its date are shown), chooses where to save it and unlocks it as usual; an existing file is never overwritten.

On Android, the cloud edition has the same two ways: its group menu has **Sync now** with the state line under it, and its home screen has **Bring a vault from the cloud** (a device with no local copy picks a synchronised vault, chooses where to save it with the system picker and unlocks it as usual; an existing file is never overwritten).

## Delete the copies

**Disconnect and delete** removes the credentials and the consent mark from this device, but it does not delete the copies already uploaded: delete them from the service itself (in its web console or its own tool). The guide to each provider explains where.
