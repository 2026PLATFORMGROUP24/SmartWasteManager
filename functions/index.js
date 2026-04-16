const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp }     = require("firebase-admin/app");
const { getFirestore }      = require("firebase-admin/firestore");
const { getMessaging }      = require("firebase-admin/messaging");

initializeApp();

/**
 * Triggered whenever a new document is created in the
 * notification_requests Firestore collection.
 *
 * Reads the "target" field to decide who receives the push:
 *   "drivers" — only users with role == "driver"
 *   "all"     — every signed-up user
 *
 * After sending, marks the document processed = true so it
 * never fires twice (idempotency guard).
 */
exports.sendNotificationOnRequest = onDocumentCreated(
  "notification_requests/{docId}",
  async (event) => {
    const db   = getFirestore();
    const data = event.data.data();

    // Safety check — do not process the same document twice
    if (data.processed === true) return null;

    const target = data.target || "all";
    const type   = data.type   || "announcement";
    const title  = data.title  || "Smart Waste Manager";
    const body   = data.body   || "";

    // ---- Collect FCM tokens from Firestore ----
    // If target is "drivers", only fetch driver accounts.
    // If target is "all", fetch every user.
    let query = db.collection("users");
    if (target === "drivers") {
      query = query.where("role", "==", "driver");
    }

    const usersSnapshot = await query.get();

    // Filter out any users who have no FCM token saved yet
    const tokens = usersSnapshot.docs
      .map(doc => doc.data().fcmToken)
      .filter(token => token && token.length > 0);

    // If nobody has a token yet, mark processed and exit
    if (tokens.length === 0) {
      await event.data.ref.update({ processed: true });
      return null;
    }

    // ---- Build the data payload ----
    // These key names match exactly what SmartWasteFCMService.kt reads.
    const dataPayload = {
      type:       type,
      title:      title,
      body:       body,
      category:   data.category   || "",
      streetName: data.streetName || "",
      dayOfWeek:  data.dayOfWeek  || "",
      categories: data.categories || "",
    };

    // ---- Send in batches of 500 (FCM multicast limit per call) ----
    const batchSize = 500;
    for (let i = 0; i < tokens.length; i += batchSize) {
      const batch = tokens.slice(i, i + batchSize);
      const message = {
        tokens:       batch,
        data:         dataPayload,
        // "notification" field makes FCM show a system tray notification
        // even when the app is in the background / killed
        notification: {
          title: title,
          body:  body
        },
        android: {
          notification: {
            channelId: getChannelId(type)
          }
        },
      };
      await getMessaging().sendEachForMulticast(message);
    }

    // ---- Mark the request as processed so it never fires again ----
    await event.data.ref.update({ processed: true });
    return null;
  }
);

/**
 * Maps a notification type string to the Android notification channel ID.
 * These channel IDs must match the constants in NotificationHelper.kt.
 */
function getChannelId(type) {
  switch (type) {
    case "new_report":  return "channel_reports";
    case "reminder":    return "channel_reminders";
    default:            return "channel_announcements";
  }
}