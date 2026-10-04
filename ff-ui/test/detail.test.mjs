import {after, before, test} from "node:test"
import assert from "node:assert/strict"
import {apiUrl, closeBrowser, deleteStack, exists, goto, openPage, requireUi, seedStack, text,} from "./harness.mjs"

const ID = "e2e-detail"
let page

before(async () => {
  if (await requireUi()) {
    page = await openPage()
    await seedStack({
      id: ID,
      services: {
        app: {image: "alpine:latest", restart: "no", command: ["/bin/sh", "-c", "echo detail-ok; sleep 60"]},
      },
    })
  }
})

after(async () => {
  await deleteStack(ID)
  await closeBrowser()
})

test("renders the detail actions and service card", async () => {
  if (!page) return
  await goto(page, `/stack/${ID}`)
  assert.match(await text(page, "h1"), new RegExp(ID))
  assert.ok(await exists(page, ".ff-panel-grid .ff-panel"), "service panel missing or not grid-wrapped")
  assert.match(await text(page, ".ff-panel"), /app/)
})

test("enables Update only when the stack is stopped", async () => {
  if (!page) return
  await goto(page, `/stack/${ID}`)
  const disabled = await page.evaluate(() => {
    const b = [...document.querySelectorAll("button")].find((x) => x.textContent.trim() === "Update")
    return b ? b.disabled : null
  })
  assert.equal(disabled, false, "Update should be enabled for a stopped stack")
})

test("enables Start and disables Stop when the stack is stopped", async () => {
  if (!page) return
  await goto(page, `/stack/${ID}`)
  const states = await page.evaluate(() => {
    const btn = (label) => [...document.querySelectorAll("button")].find((x) => x.textContent.trim() === label)
    return {start: btn("Start")?.disabled, stop: btn("Stop")?.disabled}
  })
  assert.equal(states.start, false, "Start should be enabled when stopped")
  assert.equal(states.stop, true, "Stop should be disabled when stopped")
})

test("rejects a second concurrent action with 409", async () => {
  if (!page) return
  await goto(page, `/stack/${ID}`)
  // Fire two start requests back-to-back; the per-service lock must 409 one.
  const [a, b] = await page.evaluate(async (id) => {
    const post = () => fetch(`/api/v1/stack/start`, {
      method: "POST",
      headers: {"Content-Type": "application/json"},
      body: JSON.stringify({stackId: id}),
    }).then(r => r.status)
    return Promise.all([post(), post()])
  }, ID)
  const codes = [a, b].sort()
  assert.deepEqual(codes, [200, 409], `expected one 200 and one 409, got ${codes}`)
  // Restore the stopped state for the following tests (the successful start may
  // be provisioning; stop is idempotent once it is running or settles).
  await new Promise((r) => setTimeout(r, 5000))
  await fetch(apiUrl(`/v1/stack/stop`), {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({stackId: ID}),
  }).catch(() => {
  })
  await new Promise((r) => setTimeout(r, 2000))
})

test("has no Logs button (logs are polled automatically)", async () => {
  if (!page) return
  await goto(page, `/stack/${ID}`)
  const hasLogsBtn = await page.evaluate(() =>
    [...document.querySelectorAll("button")].some((b) => b.textContent.trim() === "Logs"))
  assert.equal(hasLogsBtn, false, "the Logs button should be removed")
})

test("reports no console errors on the detail page", async () => {
  if (!page) return
  page.errors.length = 0 // clear errors from the intentional 409 in the previous test
  await goto(page, `/stack/${ID}`)
  assert.deepEqual(page.errors, [])
})
