import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { DashboardPage } from "@/components/dashboard/dashboard-page";

describe("DashboardPage", () => {
  it("renders the primary classic dashboard widgets", () => {
    render(<DashboardPage />);

    expect(screen.getByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
    expect(screen.getByText("Team Members")).toBeInTheDocument();
    expect(screen.getByText("Subscriptions")).toBeInTheDocument();
    expect(screen.getByText("Total Revenue")).toBeInTheDocument();
    expect(screen.getByText("Exercise Minutes")).toBeInTheDocument();
    expect(screen.getByText("Latest Payments")).toBeInTheDocument();
    expect(screen.getByText("Payment Method")).toBeInTheDocument();
  });

  it("filters payment rows from the dashboard table", async () => {
    const user = userEvent.setup();
    render(<DashboardPage />);

    await user.type(screen.getByPlaceholderText("Filter payments..."), "Monserrat");

    expect(screen.getByText("Monserrat Rodriguez")).toBeInTheDocument();
    expect(screen.queryByText("Kenneth Thompson")).not.toBeInTheDocument();
  });

  it("lets a visitor append a chat message", async () => {
    const user = userEvent.setup();
    render(<DashboardPage />);

    await user.type(screen.getByPlaceholderText("Type your message..."), "Thanks");
    await user.click(screen.getByRole("button", { name: "Send message" }));

    expect(screen.getByText("Thanks")).toBeInTheDocument();
  });

  it("updates payment selection summaries", async () => {
    const user = userEvent.setup();
    render(<DashboardPage />);

    await user.click(screen.getByRole("checkbox", { name: "Select all visible payments" }));

    expect(screen.getByText("3 of 3 selected")).toBeInTheDocument();
    expect(screen.getByText("Selected total: $1,395")).toBeInTheDocument();

    await user.click(screen.getByRole("checkbox", { name: "Select Kenneth Thompson" }));

    expect(screen.getByText("2 of 3 selected")).toBeInTheDocument();
    expect(screen.getByText("Selected total: $1,079")).toBeInTheDocument();
  });

  it("opens payment row actions", async () => {
    const user = userEvent.setup();
    render(<DashboardPage />);

    await user.click(
      screen.getByRole("button", {
        name: "Payment actions for Kenneth Thompson",
      }),
    );

    expect(screen.getByText("Copy payment ID")).toBeInTheDocument();
    expect(screen.getByText("Download receipt")).toBeInTheDocument();
  });

  it("shows payment form errors and then accepts valid details", async () => {
    const user = userEvent.setup();
    render(<DashboardPage />);

    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(screen.getByText("Enter the name on the card.")).toBeInTheDocument();
    expect(screen.getByText("Enter a valid card number.")).toBeInTheDocument();
    expect(screen.getByText("Use MM/YY format.")).toBeInTheDocument();
    expect(screen.getByText("Enter a valid CVC.")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Paypal" }));
    await user.type(screen.getByLabelText("Name on the card"), "Toby Belhome");
    await user.type(screen.getByLabelText("Card number"), "4242 4242 4242 4242");
    await user.type(screen.getByLabelText("Expires"), "09/29");
    await user.type(screen.getByLabelText("CVC"), "123");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(screen.queryByText("Enter the name on the card.")).not.toBeInTheDocument();
  });

  it("runs dashboard export actions", async () => {
    const user = userEvent.setup();
    render(<DashboardPage />);

    await user.click(screen.getByRole("button", { name: /01 May 2026/ }));
    await user.click(screen.getByRole("button", { name: /Download/ }));
    await user.click(screen.getByRole("button", { name: /Export/ }));

    expect(screen.getByText("Exercise Minutes")).toBeInTheDocument();
  });
});
