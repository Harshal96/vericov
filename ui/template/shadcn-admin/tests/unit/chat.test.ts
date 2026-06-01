import { describe, expect, it } from "vitest";

import { appendChatMessage } from "@/lib/chat";
import { initialChatMessages } from "@/lib/dashboard-data";

describe("chat message helper", () => {
  it("appends a trimmed visitor message without mutating the original list", () => {
    const nextMessages = appendChatMessage(initialChatMessages, "  I need help  ");

    expect(nextMessages).toHaveLength(initialChatMessages.length + 1);
    expect(nextMessages.at(-1)).toEqual(
      expect.objectContaining({
        author: "visitor",
        body: "I need help",
      }),
    );
    expect(initialChatMessages).toHaveLength(4);
  });

  it("ignores blank messages", () => {
    expect(appendChatMessage(initialChatMessages, "   ")).toBe(initialChatMessages);
  });
});
