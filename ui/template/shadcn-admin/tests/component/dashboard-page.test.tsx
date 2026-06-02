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

    await user.click(screen.getByRole("checkbox", { name: "Select all" }));

    expect(screen.getByText("8 of 16 row(s) selected.")).toBeInTheDocument();

    await user.click(screen.getByRole("checkbox", { name: "Select Kenneth Thompson" }));

    expect(screen.getByText("7 of 16 row(s) selected.")).toBeInTheDocument();
  });

  it("opens payment row actions", async () => {
    const user = userEvent.setup();
    render(<DashboardPage />);

    await user.click(screen.getAllByRole("button", { name: "Open menu" })[0]);

    expect(screen.getByText("View details")).toBeInTheDocument();
    expect(screen.getByText("Download receipt")).toBeInTheDocument();
    expect(screen.getByText("Contact customer")).toBeInTheDocument();
  });

  it("shows payment form errors and then accepts valid details", async () => {
    const user = userEvent.setup();
    render(<DashboardPage />);

    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(screen.getByText("Enter the name on the card.")).toBeInTheDocument();
    expect(screen.getByText("Enter the billing city.")).toBeInTheDocument();
    expect(screen.getByText("Enter a valid card number.")).toBeInTheDocument();
    expect(screen.getByText("Select an expiration month.")).toBeInTheDocument();
    expect(screen.getByText("Select an expiration year.")).toBeInTheDocument();
    expect(screen.getByText("Enter a valid CVC.")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Paypal" }));
    await user.type(screen.getByLabelText("Name on the card"), "Toby Belhome");
    await user.type(screen.getByLabelText("City"), "Austin");
    await user.type(screen.getByLabelText("Card number"), "4242 4242 4242 4242");
    await user.click(screen.getByRole("combobox", { name: "Month" }));
    await user.click(screen.getByRole("option", { name: "September" }));
    await user.click(screen.getByRole("combobox", { name: "Year" }));
    await user.click(screen.getByRole("option", { name: "2029" }));
    await user.type(screen.getByLabelText("CVC"), "123");
    await user.click(screen.getByRole("button", { name: "Continue" }));

    expect(screen.queryByText("Enter the name on the card.")).not.toBeInTheDocument();
  });

  it("runs dashboard export actions", async () => {
    const user = userEvent.setup();
    render(<DashboardPage />);

    await user.click(screen.getByRole("button", { name: /05 May 2026/ }));
    await user.click(screen.getByRole("button", { name: /Download/ }));
    await user.click(screen.getByRole("button", { name: /Export/ }));
    expect(screen.getByText("Excel")).toBeInTheDocument();

    expect(screen.getByText("Exercise Minutes")).toBeInTheDocument();
  });
});
