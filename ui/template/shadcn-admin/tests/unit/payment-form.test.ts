import { describe, expect, it } from "vitest";

import {
  paymentMethodSchema,
  validatePaymentMethod,
} from "@/lib/payment-validation";

describe("payment method validation", () => {
  it("accepts a complete card payment method", () => {
    const result = validatePaymentMethod({
      method: "card",
      nameOnCard: "Toby Belhome",
      cardNumber: "4242 4242 4242 4242",
      expiry: "09/29",
      cvc: "123",
    });

    expect(result.success).toBe(true);
  });

  it("rejects incomplete payment details with field-level messages", () => {
    const result = paymentMethodSchema.safeParse({
      method: "paypal",
      nameOnCard: "T",
      cardNumber: "123",
      expiry: "13/29",
      cvc: "1",
    });

    expect(result.success).toBe(false);
    if (!result.success) {
      expect(result.error.flatten().fieldErrors).toMatchObject({
        nameOnCard: expect.arrayContaining([expect.any(String)]),
        cardNumber: expect.arrayContaining([expect.any(String)]),
        expiry: expect.arrayContaining([expect.any(String)]),
        cvc: expect.arrayContaining([expect.any(String)]),
      });
    }
  });
});
