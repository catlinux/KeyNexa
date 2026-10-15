# Cloud copies: setup guide

[English](CLOUD_SETUP.md) | [Español](es/CLOUD_SETUP.md)

> **Verified with a real account only for Backblaze B2** (2026-10-15). The Cloudflare R2 and MinIO steps follow each service's documentation and have not been tried yet. Free plans, prices and limits change over time and are not promised here. Only the official pages are linked.

KeyNexa only ever sends **the encrypted `.kdbx` file** to the service you choose, and only after you accept the consent screen. The master password, the keys and the decrypted content never leave the device. See [Privacy](PRIVACY.md).

Open **Settings → Copies in the cloud** and follow the assistant. It has five steps: consent, choose the service, what you need, the form and the state of the copies.

## Backblaze B2

1. Create a free account at <https://www.backblaze.com/cloud-storage>. The page can take a while to change after you type the email. Confirm the email, choose a password (a password manager's generator is fine), choose the region where your data is stored (for example Europe or the United States), accept the terms and choose *personal* or *organisation*. Signing in asks for a code sent to your email (two-step check); you can tell it to trust that browser.
2. Create a **private** bucket (not public) with a name that is unique and has no personal data, for example `keynexa-copies-4821`. Leave the server-side options as they are: the copy is already encrypted by KDBX.
3. In the bucket details, note the **endpoint** (it can appear as "Endpoint", "S3 endpoint" or similar, for example `s3.eu-central-003.backblazeb2.com`). The panel shows it without `https://`; the assistant needs it with `https://`.
4. Create an application key (*Application Keys → Add a New Application Key*) **limited to that bucket**, with read and write permission.
5. Copy the `keyID` and the `applicationKey` (the application key is shown only once) into the assistant. Type them in the phone from your password manager.
6. In the form: endpoint `https://s3.<region>.backblazeb2.com`, region the part of the endpoint after `s3.` (for example `eu-central-003`), the bucket name, and leave "use the bucket in the path" on.

## Cloudflare R2

1. Create a Cloudflare account and open R2 at <https://developers.cloudflare.com/r2/>.
2. Create a bucket (private by default).
3. Create an API token with read and write permission over that bucket, and copy the Access Key ID and the Secret Access Key.
4. For the endpoint, use `https://<account id>.r2.cloudflarestorage.com`; for the region, type `auto`. Leave "use the bucket in the path" on.

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

## Delete the copies

**Disconnect and delete** removes the credentials and the consent mark from this device, but it does not delete the copies already uploaded: delete them from the service itself (in its web console or its own tool). The guide to each provider explains where.
