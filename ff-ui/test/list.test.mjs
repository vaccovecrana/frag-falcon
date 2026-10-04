import {after, before, test} from "node:test"
import assert from "node:assert/strict"
import {
  closeBrowser,
  deleteStack,
  exists,
  goto,
  openPage,
  requireUi,
  seedStack,
  text,
  waitForText,
} from "./harness.mjs"

const ID = "e2e-list"
let page

before(async () => {
  if (await requireUi()) {
    page = await openPage()
    await seedStack({
      id: ID,
      services: {
        app: {image: "alpine:latest", restart: "no", command: ["/bin/sh", "-c", "sleep 60"]},
        worker: {image: "alpine:latest", restart: "no", command: ["/bin/sh", "-c", "sleep 60"]},
      },
    })
  }
})

after(async () => {
  await deleteStack(ID)
  await closeBrowser()
})

test("renders the brand and the New stack action", async () => {
  if (!page) return
  await goto(page, "/")
  assert.match(await text(page, ".ff-brand"), /frag-falcon/)
  assert.ok(await exists(page, "a[href='/stack/new']"))
})

test("shows the seeded stack with its services and status", async () => {
  if (!page) return
  await goto(page, "/")
  await waitForText(page, "body", ID)
  const card = await page.evaluate((id) => {
    const a = [...document.querySelectorAll("article.ff-card a")]
      .find((x) => x.href.endsWith("/stack/" + id))
    return a ? a.parentElement.innerText : null
  }, ID)
  assert.ok(card, "stack card not found")
  assert.match(card, new RegExp(`${ID}`))
  assert.match(card, /0\/2 services running/)
  assert.match(card, /app/)
  assert.match(card, /worker/)
  assert.match(card, /STOPPED/)
})

test("reports no console errors on the list page", async () => {
  if (!page) return
  await goto(page, "/")
  assert.deepEqual(page.errors, [])
})
