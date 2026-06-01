import { describe, expect, it } from "vitest";

import {
  commandItemsFromNavigation,
  getActiveNavigationItem,
  navigationGroups,
} from "@/lib/navigation";

describe("dashboard navigation", () => {
  it("marks the classic dashboard route as active", () => {
    const activeItem = getActiveNavigationItem("/dashboard/default");

    expect(activeItem?.title).toBe("Classic Dashboard");
    expect(activeItem?.url).toBe("/dashboard/default");
  });

  it("keeps dashboard and app navigation groups available for the sidebar", () => {
    expect(navigationGroups.map((group) => group.label)).toEqual(
      expect.arrayContaining(["Dashboards", "Apps"]),
    );

    expect(
      navigationGroups.flatMap((group) => group.items).map((item) => item.title),
    ).toEqual(expect.arrayContaining(["Classic Dashboard", "Chats", "Kanban"]));
  });

  it("creates searchable command items without mutating navigation data", () => {
    const before = structuredClone(navigationGroups);
    const commandItems = commandItemsFromNavigation(navigationGroups);

    expect(commandItems).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          title: "Classic Dashboard",
          url: "/dashboard/default",
          group: "Dashboards",
        }),
      ]),
    );
    expect(navigationGroups).toEqual(before);
  });
});
