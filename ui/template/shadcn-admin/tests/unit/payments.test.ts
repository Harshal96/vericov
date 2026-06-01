import { describe, expect, it } from "vitest";

import {
  filterPayments,
  payments,
  summarizePayments,
} from "@/lib/dashboard-data";

describe("payments data helpers", () => {
  it("filters payments by customer, email, amount, and status", () => {
    expect(filterPayments(payments, "monserrat")).toHaveLength(1);
    expect(filterPayments(payments, "success")).toHaveLength(2);
    expect(filterPayments(payments, "$837")).toEqual([
      expect.objectContaining({ customer: "Monserrat Rodriguez" }),
    ]);
    expect(filterPayments(payments, "no-match")).toEqual([]);
  });

  it("summarizes selected and visible payment rows immutably", () => {
    const selectedIds = new Set(["pay-kenneth", "pay-abraham"]);
    const summary = summarizePayments(payments, selectedIds);

    expect(summary).toEqual({
      total: payments.length,
      selected: 2,
      visibleTotal: 1395,
      selectedTotal: 558,
    });
    expect(payments.map((payment) => payment.id)).toContain("pay-kenneth");
  });
});
