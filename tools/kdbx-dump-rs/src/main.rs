//! Dumps a KeePass database as the canonical JSON the differential test compares.
//!
//! The password is read from standard input (never an argument, which would show in the process list). The vault
//! path is an argument and `--keyfile <path>` is optional. Only the JSON goes to standard output; nothing is ever
//! written to disk.

use std::fs::File;
use std::io::{self, Read, Write};

use keepass::db::{EntryRef, GroupRef};
use keepass::{Database, DatabaseKey};
use serde_json::{json, Map, Value};
use sha2::{Digest, Sha256};

fn main() {
    if let Err(message) = run() {
        eprintln!("kdbx-dump: {message}");
        std::process::exit(1);
    }
}

fn run() -> Result<(), String> {
    let mut arguments = std::env::args().skip(1);
    let path = arguments
        .next()
        .ok_or("usage: kdbx-dump <vault.kdbx> [--keyfile <file>]")?;
    let mut keyfile: Option<String> = None;
    while let Some(argument) = arguments.next() {
        match argument.as_str() {
            "--keyfile" => keyfile = Some(arguments.next().ok_or("--keyfile needs a path")?),
            other => return Err(format!("unknown argument: {other}")),
        }
    }

    let password = read_password()?;
    let mut key = DatabaseKey::new();
    if let Some(keyfile) = keyfile {
        let mut file = File::open(&keyfile).map_err(|error| format!("cannot open the key file: {error}"))?;
        key = key
            .with_keyfile(&mut file)
            .map_err(|error| format!("cannot read the key file: {error}"))?;
    }
    if !password.is_empty() {
        key = key.with_password(&password);
    }
    if key.is_empty() {
        return Err("no database key was provided".into());
    }

    let mut source = File::open(&path).map_err(|error| format!("cannot open the vault: {error}"))?;
    let database =
        Database::open(&mut source, key).map_err(|error| format!("cannot open the vault: {error}"))?;

    let text = serde_json::to_string(&canonical(&database))
        .map_err(|error| format!("cannot serialize the dump: {error}"))?;
    let mut out = io::stdout().lock();
    out.write_all(text.as_bytes())
        .and_then(|()| out.write_all(b"\n"))
        .map_err(|error| format!("cannot write the dump: {error}"))
}

// Only line endings are trimmed: a password may contain spaces.
fn read_password() -> Result<String, String> {
    let mut text = String::new();
    io::stdin()
        .read_to_string(&mut text)
        .map_err(|error| format!("cannot read the password: {error}"))?;
    Ok(text.trim_end_matches(['\r', '\n']).to_string())
}

fn canonical(database: &Database) -> Value {
    let mut groups: Vec<Value> = Vec::new();
    let mut entries: Vec<Value> = Vec::new();
    collect(database.root(), &mut groups, &mut entries);
    sort_by_uuid(&mut groups);
    sort_by_uuid(&mut entries);

    let mut root = Map::new();
    root.insert("entries".into(), Value::Array(entries));
    root.insert("groups".into(), Value::Array(groups));
    Value::Object(root)
}

fn collect(
    group: GroupRef<'_>,
    groups: &mut Vec<Value>,
    entries: &mut Vec<Value>,
) {
    groups.push(group_value(&group));
    for entry in group.entries() {
        entries.push(entry_value(&entry));
    }
    for child in group.groups() {
        collect(child, groups, entries);
    }
}

fn sort_by_uuid(values: &mut [Value]) {
    values.sort_by(|left, right| left["uuid"].as_str().cmp(&right["uuid"].as_str()));
}

fn group_value(group: &GroupRef<'_>) -> Value {
    let mut value = Map::new();
    value.insert("uuid".into(), json!(group.id().uuid().to_string()));
    value.insert(
        "parent".into(),
        match group.parent() {
            Some(parent) => json!(parent.id().uuid().to_string()),
            None => Value::Null,
        },
    );
    value.insert("name".into(), json!(group.name));
    value.insert("notes".into(), json!(group.notes.clone().unwrap_or_default()));
    Value::Object(value)
}

fn entry_value(entry: &EntryRef<'_>) -> Value {
    let mut value = Map::new();
    value.insert("uuid".into(), json!(entry.id().uuid().to_string()));
    value.insert("group".into(), json!(entry.parent().id().uuid().to_string()));

    let mut fields = Map::new();
    for (name, field) in &entry.fields {
        let mut field_value = Map::new();
        field_value.insert("protected".into(), json!(field.is_protected()));
        field_value.insert("value".into(), json!(field.get()));
        fields.insert(name.clone(), Value::Object(field_value));
    }
    value.insert("fields".into(), Value::Object(fields));

    let mut tags = entry.tags.clone();
    tags.sort();
    value.insert("tags".into(), json!(tags));

    let mut attachments = Map::new();
    for (name, attachment) in entry.attachments_named() {
        attachments.insert(name.to_string(), json!(sha256_hex(attachment.data.get())));
    }
    value.insert("attachments".into(), Value::Object(attachments));

    let history = entry
        .history
        .as_ref()
        .map(|history| history.get_entries().len())
        .unwrap_or(0);
    value.insert("history".into(), json!(history));

    Value::Object(value)
}

fn sha256_hex(bytes: &[u8]) -> String {
    let mut hasher = Sha256::new();
    hasher.update(bytes);
    let digest = hasher.finalize();
    digest.iter().map(|byte| format!("{byte:02x}")).collect()
}
