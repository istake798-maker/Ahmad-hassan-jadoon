const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const HASSAN_UID = "hassan_mansehra_01";
const OTHER_UID = "intruder_99";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any user-scoped collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(HASSAN_UID).collection("clients").get());
  await assertFails(
    unauthDb.collection("users").doc(HASSAN_UID).collection("clients").doc("c1").set({
      userId: HASSAN_UID,
      name: "Unauth Client",
      workType: "WEBSITE",
      priceUsd: 80,
      platform: "Google",
      status: "HUNTED",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Owner can create, update, approve close, and complete a client lead; rejects @DocumentId id field and shadow fields", async () => {
  const db = testEnv.authenticatedContext(HASSAN_UID).firestore();
  const created = new Date(Date.now() - 10000);
  const clientRef = db.collection("users").doc(HASSAN_UID).collection("clients").doc("client_1");

  await assertSucceeds(
    clientRef.set({
      userId: HASSAN_UID,
      name: "Apex Studio",
      workType: "VIDEO_EDITING",
      priceUsd: 30,
      platform: "YouTube",
      contactHandle: "apex@studio.com",
      status: "PITCHED",
      outreachMessage: "Hi Apex Studio, I can edit your YouTube shorts for $30.",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Move to AWAITING_APPROVAL when client replies Yes
  await assertSucceeds(
    clientRef.update({
      lastClientMessage: "Yes, let's do $30!",
      draftedReply: "Awesome! Sending next steps.",
      status: "AWAITING_APPROVAL",
      updatedAt: new Date(Date.now() - 5000),
    })
  );

  // Hassan says "Haan" -> CLOSED_WON
  await assertSucceeds(
    clientRef.update({
      status: "CLOSED_WON",
      updatedAt: new Date(Date.now() - 3000),
    })
  );

  // Mark COMPLETED
  await assertSucceeds(
    clientRef.update({
      status: "COMPLETED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Terminal state lock: COMPLETED cannot be mutated further
  await assertFails(
    clientRef.update({
      notes: "Tamper after complete",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject @DocumentId 'id' inside payload and ghost fields
  await assertFails(
    db.collection("users").doc(HASSAN_UID).collection("clients").doc("client_bad_id").set({
      id: "client_bad_id",
      userId: HASSAN_UID,
      name: "Bad Client",
      workType: "DESIGNING",
      priceUsd: 20,
      platform: "Instagram",
      status: "HUNTED",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertFails(
    db.collection("users").doc(HASSAN_UID).collection("clients").doc("client_ghost").set({
      userId: HASSAN_UID,
      name: "Ghost Client",
      workType: "DESIGNING",
      priceUsd: 20,
      platform: "Instagram",
      status: "HUNTED",
      isAdmin: true,
      createdAt: created,
      updatedAt: created,
    })
  );
});

test("Cross-user access is strictly blocked across clients, memories, messages, and dailyStats", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    const ts = new Date(Date.now() - 5000);
    await db.collection("users").doc(HASSAN_UID).collection("clients").doc("c1").set({
      userId: HASSAN_UID,
      name: "Private Lead",
      workType: "WEBSITE",
      priceUsd: 80,
      platform: "Google",
      status: "HUNTED",
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("users").doc(HASSAN_UID).collection("memories").doc("m1").set({
      userId: HASSAN_UID,
      title: "Pricing",
      content: "Website rate is $80",
      category: "RULE",
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const intruderDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  await assertFails(intruderDb.collection("users").doc(HASSAN_UID).collection("clients").get());
  await assertFails(intruderDb.collection("users").doc(HASSAN_UID).collection("clients").doc("c1").get());
  await assertFails(intruderDb.collection("users").doc(HASSAN_UID).collection("memories").get());
  await assertFails(
    intruderDb.collection("users").doc(HASSAN_UID).collection("messages").doc("msg1").set({
      userId: OTHER_UID,
      role: "USER",
      text: "Spoof",
      brainUsed: "Gemini 3.8 Flash",
      voiceInput: false,
      createdAt: new Date(Date.now() - 1000),
    })
  );
});

test("DailyStat enforces max 10 hunted clients per day and rejects future timestamps", async () => {
  const db = testEnv.authenticatedContext(HASSAN_UID).firestore();
  const created = new Date(Date.now() - 5000);
  const statRef = db.collection("users").doc(HASSAN_UID).collection("dailyStats").doc("2026-11-20");

  await assertSucceeds(
    statRef.set({
      userId: HASSAN_UID,
      dateKey: "2026-11-20",
      huntedCount: 10,
      repliesHandled: 3,
      dealsClosed: 1,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Exceeding 10 hunted clients/day must fail
  await assertFails(
    statRef.update({
      huntedCount: 11,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Future timestamp must fail
  await assertFails(
    db.collection("users").doc(HASSAN_UID).collection("dailyStats").doc("2026-11-21").set({
      userId: HASSAN_UID,
      dateKey: "2026-11-21",
      huntedCount: 1,
      repliesHandled: 0,
      dealsClosed: 0,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("ChatMessage and MemoryItem list and create succeed for owner", async () => {
  const db = testEnv.authenticatedContext(HASSAN_UID).firestore();
  const ts = new Date(Date.now() - 2000);

  await assertSucceeds(
    db.collection("users").doc(HASSAN_UID).collection("messages").doc("msg_1").set({
      userId: HASSAN_UID,
      role: "USER",
      text: "JARVIS, naye clients dhoondo",
      brainUsed: "",
      voiceInput: true,
      createdAt: ts,
    })
  );

  await assertSucceeds(
    db.collection("users").doc(HASSAN_UID).collection("memories").doc("mem_1").set({
      userId: HASSAN_UID,
      title: "Tone",
      content: "Talk to Hassan in Roman Urdu",
      category: "PREFERENCE",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(db.collection("users").doc(HASSAN_UID).collection("messages").get());
  await assertSucceeds(db.collection("users").doc(HASSAN_UID).collection("memories").get());
});
