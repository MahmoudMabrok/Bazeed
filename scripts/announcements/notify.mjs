import { firestore, messaging, TOPIC_ANNOUNCEMENTS } from "./_init.mjs";

/**
 * Pick up announcements where notified == false, send one FCM each to topic
 * 'announcements', then flip notified = true. Idempotent — safe to re-run.
 *
 * On send failure for a given doc: log + skip (leave notified=false) so the
 * next 5-min tick retries. Other docs in the batch still process.
 */
async function main() {
  const snap = await firestore
    .collection("announcements")
    .where("notified", "==", false)
    .get();

  if (snap.empty) {
    console.log("no un-notified announcements");
    return;
  }

  console.log(`processing ${snap.size} announcement(s)`);
  let sent = 0;
  let failed = 0;

  for (const doc of snap.docs) {
    const data = doc.data();
    const title = data.title;
    const body = data.description;
    if (!title || !body) {
      console.warn(`skipping ${doc.id}: missing title/description`);
      continue;
    }
    try {
      const messageId = await messaging.send({
        topic: TOPIC_ANNOUNCEMENTS,
        notification: { title, body },
      });
      await doc.ref.update({ notified: true });
      console.log(`sent ${doc.id} → ${messageId}`);
      sent++;
    } catch (err) {
      console.error(`send failed for ${doc.id}: ${err.message}`);
      failed++;
    }
  }

  console.log(`done: sent=${sent} failed=${failed}`);
  if (failed > 0) process.exit(1); // make the GH Action turn red on partial failure
}

main().catch((err) => {
  console.error("fatal:", err);
  process.exit(1);
});
