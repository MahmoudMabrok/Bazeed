import { firestore } from "./_init.mjs";

/**
 * Hard-delete announcements whose expirationDate has passed.
 * Batched at 400/commit (Firestore batch limit is 500; leave headroom).
 */
async function main() {
  const now = new Date();
  const snap = await firestore
    .collection("announcements")
    .where("expirationDate", "<", now)
    .get();

  if (snap.empty) {
    console.log("no expired announcements");
    return;
  }

  console.log(`deleting ${snap.size} expired announcement(s)`);
  const BATCH_SIZE = 400;
  for (let i = 0; i < snap.docs.length; i += BATCH_SIZE) {
    const batch = firestore.batch();
    snap.docs.slice(i, i + BATCH_SIZE).forEach((d) => batch.delete(d.ref));
    await batch.commit();
  }
  console.log(`done: deleted=${snap.size}`);
}

main().catch((err) => {
  console.error("fatal:", err);
  process.exit(1);
});
