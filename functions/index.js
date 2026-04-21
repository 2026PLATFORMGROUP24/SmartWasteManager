const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp }     = require("firebase-admin/app");
const { getFirestore }      = require("firebase-admin/firestore");
const { getMessaging }      = require("firebase-admin/messaging");

initializeApp();

/**
 * Triggered whenever a new zone document is created in route_zones.
 *
 * Scans ALL collection_points that have an empty zoneId ("").
 * Any point whose (lat, lng) falls within the new zone's circle
 * (haversine distance <= radiusMeters) gets its zoneId updated to
 * the new zone's document ID.
 *
 * This fixes the "no zone for this area" problem for collection points
 * that were created before the zone existed.
 */
exports.assignZoneToExistingCollectionPoints = onDocumentCreated(
  "route_zones/{zoneId}",
  async (event) => {
    const db     = getFirestore();
    const zoneId = event.params.zoneId;
    const zone   = event.data.data();

    const centerLat    = zone.centerLat    || 0;
    const centerLng    = zone.centerLng    || 0;
    const radiusMeters = zone.radiusMeters || 1000;

    // Fetch ALL collection points that have no zone assigned yet
    const snapshot = await db.collection("collection_points")
      .where("zoneId", "==", "")
      .get();

    if (snapshot.empty) return null;

    const batch = db.batch();
    let count   = 0;

    snapshot.docs.forEach((doc) => {
      const pt  = doc.data();
      const loc = pt.location; // Firestore GeoPoint has .latitude / .longitude
      if (!loc) return;

      const dist = haversineMeters(centerLat, centerLng, loc.latitude, loc.longitude);
      if (dist <= radiusMeters) {
        batch.update(doc.ref, { zoneId: zoneId });
        count++;
      }
    });

    if (count > 0) await batch.commit();
    console.log(`Zone ${zoneId}: assigned to ${count} pre-existing collection point(s).`);
    return null;
  }
);

/** Haversine distance in metres between two lat/lng points. */
function haversineMeters(lat1, lng1, lat2, lng2) {
  const R    = 6371000;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a    = Math.sin(dLat / 2) ** 2 +
               Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}
function toRad(deg) { return deg * Math.PI / 180; }

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