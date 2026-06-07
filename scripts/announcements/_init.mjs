import { initializeApp, cert } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";

/**
 * Initialise firebase-admin from the FIREBASE_SERVICE_ACCOUNT env var (a JSON string).
 * Both scripts call this once at startup. Exits with code 2 if the secret is missing
 * so the GitHub Action surfaces a visible failure rather than silently no-op'ing.
 */
function initAdmin() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw) {
    console.error("FIREBASE_SERVICE_ACCOUNT env var is missing");
    process.exit(2);
  }
  let credential;
  try {
    credential = cert(JSON.parse(raw));
  } catch (err) {
    console.error("FIREBASE_SERVICE_ACCOUNT is not valid JSON:", err.message);
    process.exit(2);
  }
  return initializeApp({ credential });
}

export const app = initAdmin();
export const firestore = getFirestore(app);
export const messaging = getMessaging(app);
export const TOPIC_ANNOUNCEMENTS = "announcements";
