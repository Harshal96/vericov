import { expect, test } from "@playwright/test";

test.describe("classic dashboard template", () => {
  test("renders the dashboard shell without horizontal overflow", async ({ page }) => {
    await page.goto("/dashboard/default");

    await expect(page.getByRole("heading", { name: "Dashboard" })).toBeVisible();
    await expect(page.getByText("Team Members", { exact: true })).toBeVisible();
    await expect(page.getByText("Exercise Minutes", { exact: true })).toBeVisible();

    const hasHorizontalOverflow = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    );
    expect(hasHorizontalOverflow).toBe(false);
  });

  test("supports command search, payment filtering, and theme switching", async ({
    page,
  }) => {
    await page.goto("/dashboard/default");

    await page.keyboard.press("Control+K");
    await expect(page.getByRole("dialog", { name: "Command Palette" })).toBeVisible();
    await page.getByPlaceholder("Search for a command to run...").fill("chat");
    await expect(page.getByRole("option", { name: /Chats/ })).toBeVisible();
    await page.keyboard.press("Escape");

    await page.getByPlaceholder("Filter payments...").fill("Kenneth");
    await expect(page.getByText("Kenneth Thompson")).toBeVisible();
    await expect(page.getByText("Abraham Lincoln")).toBeHidden();

    await page.getByRole("button", { name: "Toggle theme" }).click();
    await expect(page.locator("html")).toHaveClass(/dark/);
  });

  test("supports mobile sidebar and payment method controls", async ({ page }) => {
    await page.goto("/dashboard/default");

    const viewport = page.viewportSize();
    if ((viewport?.width ?? 0) < 768) {
      await page.getByRole("button", { name: "Toggle navigation" }).click();
      await expect(page.getByRole("link", { name: /Classic Dashboard/ })).toBeVisible();
      await page.getByRole("link", { name: /Classic Dashboard/ }).click();
    }

    await page.getByRole("tab", { name: "Paypal" }).click();
    await expect(page.getByRole("tab", { name: "Paypal" })).toHaveAttribute(
      "aria-selected",
      "true",
    );
  });
});
