import { describe, expect, it } from "vitest";

import {
  filterCommandItems,
  nextThemeSelection,
  themeOptions,
} from "@/lib/app-shell-state";
import { commandItemsFromNavigation, navigationGroups } from "@/lib/navigation";

describe("app shell state helpers", () => {
  it("filters command items by label and group", () => {
    const items = commandItemsFromNavigation(navigationGroups);

    expect(filterCommandItems(items, "classic")).toEqual([
      expect.objectContaining({ title: "Classic Dashboard" }),
    ]);
    expect(filterCommandItems(items, "apps").length).toBeGreaterThan(2);
  });

  it("cycles theme choices in the visible order", () => {
    expect(themeOptions).toEqual(["light", "dark", "system"]);
    expect(nextThemeSelection("light")).toBe("dark");
    expect(nextThemeSelection("dark")).toBe("system");
    expect(nextThemeSelection("system")).toBe("light");
  });
});
