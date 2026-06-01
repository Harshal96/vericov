"use client";

import * as React from "react";
import {
  CalendarDays,
  CheckCircle2,
  CreditCard,
  Download,
  Ellipsis,
  FileDown,
  Plus,
  SendHorizontal,
  WalletCards,
} from "lucide-react";
import {
  Bar,
  BarChart,
  Line,
  LineChart,
  XAxis,
  YAxis,
} from "recharts";
import { toast } from "sonner";

import { appendChatMessage } from "@/lib/chat";
import {
  exerciseData,
  filterPayments,
  initialChatMessages,
  payments,
  revenueData,
  subscriptionBars,
  summarizePayments,
  teamMembers,
  type Payment,
} from "@/lib/dashboard-data";
import { validatePaymentMethod } from "@/lib/payment-validation";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  CardAction,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
import {
  ChartContainer,
  ChartTooltip,
  ChartTooltipContent,
  type ChartConfig,
} from "@/components/ui/chart";
import { Checkbox } from "@/components/ui/checkbox";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { Tabs, TabsList, TabsTrigger } from "@/components/ui/tabs";

const revenueChartConfig = {
  revenue: {
    label: "Revenue",
    color: "var(--chart-1)",
  },
} satisfies ChartConfig;

const exerciseChartConfig = {
  current: {
    label: "Current",
    color: "var(--chart-1)",
  },
  normal: {
    label: "Normal",
    color: "var(--chart-2)",
  },
} satisfies ChartConfig;

const subscriptionsChartConfig = {
  value: {
    label: "Subscriptions",
    color: "var(--chart-1)",
  },
} satisfies ChartConfig;

export function DashboardPage() {
  return (
    <div className="mx-auto grid max-w-[1134px] min-w-0 gap-4">
      <DashboardHeader />
      <section className="grid min-w-0 grid-cols-1 gap-4 lg:grid-cols-3">
        <TeamMembersCard />
        <SubscriptionsCard />
        <RevenueCard />
        <ChatCard />
        <ExerciseCard />
        <PaymentsCard />
        <PaymentMethodCard />
      </section>
    </div>
  );
}

function DashboardHeader() {
  return (
    <div className="flex min-w-0 items-center gap-3">
      <h1 className="text-2xl font-semibold tracking-normal sm:text-[26px]">
        Dashboard
      </h1>
      <div className="ml-auto flex items-center gap-2">
        <Button
          className="h-9 gap-2 px-3"
          variant="outline"
          onClick={() => toast("Date range picker ready for wiring.")}
        >
          <CalendarDays className="size-4" />
          <span className="hidden sm:inline">01 May 2026 - 28 May 2026</span>
        </Button>
        <Button
          className="h-9 gap-2 bg-primary px-3 text-primary-foreground hover:bg-primary/90"
          onClick={() => toast.success("Dashboard export started.")}
        >
          <Download className="size-4" />
          <span className="hidden sm:inline">Download</span>
        </Button>
      </div>
    </div>
  );
}

function TeamMembersCard() {
  return (
    <Card className="h-[274px] gap-0 overflow-hidden">
      <CardHeader className="pb-3">
        <CardTitle>Team Members</CardTitle>
        <CardDescription>Invite your team members to collaborate.</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {teamMembers.map((member) => (
          <div className="flex items-center gap-3" key={member.id}>
            <Avatar className="size-8">
              <AvatarFallback className={member.color}>
                {member.initials}
              </AvatarFallback>
            </Avatar>
            <div className="min-w-0 flex-1">
              <div className="truncate text-sm font-medium">{member.name}</div>
              <div className="truncate text-sm text-muted-foreground">
                {member.email}
              </div>
            </div>
            <Select defaultValue={member.role}>
              <SelectTrigger className="h-9 w-[126px]">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="Viewer">Viewer</SelectItem>
                <SelectItem value="Developer">Developer</SelectItem>
                <SelectItem value="Admin">Admin</SelectItem>
              </SelectContent>
            </Select>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function SubscriptionsCard() {
  return (
    <Card className="h-[274px] gap-0 overflow-hidden">
      <CardHeader className="pb-2">
        <CardTitle>Subscriptions</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="text-[32px] font-bold leading-none">+4850</div>
        <div className="mt-1 text-sm text-muted-foreground">
          <span className="text-emerald-600">+180.1%</span> from last month
        </div>
        <ChartContainer
          className="mt-4 h-[112px] w-full"
          config={subscriptionsChartConfig}
        >
          <BarChart accessibilityLayer data={subscriptionBars}>
            <XAxis dataKey="label" hide />
            <YAxis hide />
            <ChartTooltip content={<ChartTooltipContent hideLabel />} />
            <Bar
              dataKey="value"
              fill="var(--color-value)"
              radius={[4, 4, 4, 4]}
              barSize={30}
            />
          </BarChart>
        </ChartContainer>
        <div className="mt-1 grid grid-cols-8 gap-1 text-center text-xs">
          {subscriptionBars.map((bar) => (
            <span key={bar.label}>{bar.value}</span>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

function RevenueCard() {
  return (
    <Card className="h-[274px] gap-0 overflow-hidden">
      <CardHeader className="pb-2">
        <CardTitle>Total Revenue</CardTitle>
      </CardHeader>
      <CardContent>
        <div className="text-[32px] font-bold leading-none">$15,231.89</div>
        <div className="mt-1 text-sm text-muted-foreground">
          <span className="text-emerald-600">+20.1%</span> from last month
        </div>
        <ChartContainer
          className="mt-6 h-[122px] w-full"
          config={revenueChartConfig}
        >
          <LineChart accessibilityLayer data={revenueData}>
            <XAxis dataKey="label" hide />
            <YAxis hide domain={["dataMin - 800", "dataMax + 800"]} />
            <ChartTooltip content={<ChartTooltipContent hideLabel />} />
            <Line
              dataKey="revenue"
              dot={{ r: 5, fill: "var(--background)", strokeWidth: 1.5 }}
              stroke="var(--color-revenue)"
              strokeWidth={2}
              type="linear"
            />
          </LineChart>
        </ChartContainer>
      </CardContent>
    </Card>
  );
}

function ChatCard() {
  const [messages, setMessages] = React.useState(initialChatMessages);
  const [draft, setDraft] = React.useState("");

  function sendMessage(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextMessages = appendChatMessage(messages, draft);
    setMessages(nextMessages);
    if (nextMessages !== messages) {
      setDraft("");
      toast.success("Message sent.");
    }
  }

  return (
    <Card className="h-[382px] gap-0 overflow-hidden">
      <div className="flex items-start gap-3 px-6 pb-3 pt-6">
        <Avatar className="size-8">
          <AvatarFallback className="bg-emerald-100 text-emerald-800">
            SD
          </AvatarFallback>
        </Avatar>
        <div className="min-w-0 flex-1">
          <CardTitle>Sofia Davis</CardTitle>
          <CardDescription>m@example.com</CardDescription>
        </div>
        <Button aria-label="Add contact" size="icon" variant="outline">
          <Plus className="size-4" />
        </Button>
      </div>
      <CardContent className="flex min-h-0 flex-1 flex-col gap-2 pb-6">
        <div className="grid min-h-0 flex-1 content-start gap-2 overflow-hidden">
          {messages.map((message) => (
            <div
              className={
                message.author === "visitor"
                  ? "ml-auto max-w-[78%] rounded-md bg-primary px-3 py-1.5 text-sm text-primary-foreground"
                  : "mr-auto max-w-[78%] rounded-md bg-muted px-3 py-1.5 text-sm"
              }
              key={message.id}
            >
              {message.body}
            </div>
          ))}
        </div>
        <form className="flex gap-2" onSubmit={sendMessage}>
          <Input
            className="h-9"
            onChange={(event) => setDraft(event.target.value)}
            placeholder="Type your message..."
            value={draft}
          />
          <Button aria-label="Send message" className="h-9 w-10" type="submit">
            <SendHorizontal className="size-4" />
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

function ExerciseCard() {
  return (
    <Card className="h-[382px] gap-0 overflow-hidden lg:col-span-2">
      <CardHeader>
        <div className="min-w-0 flex-1">
          <CardTitle>Exercise Minutes</CardTitle>
          <CardDescription>
            Your exercise minutes are ahead of where you normally are.
          </CardDescription>
        </div>
        <CardAction>
          <Button
            className="h-9 gap-2"
            variant="outline"
            onClick={() => toast.success("Exercise chart exported.")}
          >
            <FileDown className="size-4" />
            Export
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent>
        <ChartContainer
          className="h-[250px] w-full"
          config={exerciseChartConfig}
        >
          <LineChart accessibilityLayer data={exerciseData}>
            <XAxis dataKey="label" hide />
            <YAxis hide domain={[20, 70]} />
            <ChartTooltip content={<ChartTooltipContent />} />
            <Line
              dataKey="normal"
              dot={{ r: 3 }}
              stroke="var(--color-normal)"
              strokeWidth={2}
              type="monotone"
            />
            <Line
              dataKey="current"
              dot={{ r: 3 }}
              stroke="var(--color-current)"
              strokeWidth={2}
              type="monotone"
            />
          </LineChart>
        </ChartContainer>
      </CardContent>
    </Card>
  );
}

function PaymentsCard() {
  const [query, setQuery] = React.useState("");
  const [selectedIds, setSelectedIds] = React.useState<Set<string>>(new Set());
  const filteredPayments = filterPayments(payments, query);
  const summary = summarizePayments(filteredPayments, selectedIds);

  function togglePayment(paymentId: string) {
    setSelectedIds((current) => {
      const next = new Set(current);
      if (next.has(paymentId)) {
        next.delete(paymentId);
      } else {
        next.add(paymentId);
      }
      return next;
    });
  }

  function toggleVisiblePayments(checked: boolean) {
    setSelectedIds((current) => {
      const next = new Set(current);
      filteredPayments.forEach((payment) => {
        if (checked) {
          next.add(payment.id);
        } else {
          next.delete(payment.id);
        }
      });
      return next;
    });
  }

  const allVisibleSelected =
    filteredPayments.length > 0 &&
    filteredPayments.every((payment) => selectedIds.has(payment.id));

  return (
    <Card className="min-h-[600px] gap-0 overflow-hidden lg:col-span-2">
      <CardHeader>
        <div className="min-w-0 flex-1">
          <CardTitle>Latest Payments</CardTitle>
          <CardDescription>
            See recent payments from your customers here.
          </CardDescription>
        </div>
        <CardAction>
          <Input
            className="h-9 w-full max-w-[176px]"
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Filter payments..."
            value={query}
          />
        </CardAction>
      </CardHeader>
      <CardContent>
        <div className="overflow-x-auto rounded-lg border">
          <Table className="min-w-[640px]">
            <TableHeader>
              <TableRow>
                <TableHead className="w-10">
                  <Checkbox
                    aria-label="Select all visible payments"
                    checked={allVisibleSelected}
                    onCheckedChange={(checked) =>
                      toggleVisiblePayments(checked === true)
                    }
                  />
                </TableHead>
                <TableHead>Customer</TableHead>
                <TableHead>Email</TableHead>
                <TableHead className="text-right">Amount</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="w-10" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredPayments.map((payment) => (
                <PaymentRow
                  isSelected={selectedIds.has(payment.id)}
                  key={payment.id}
                  onToggle={() => togglePayment(payment.id)}
                  payment={payment}
                />
              ))}
            </TableBody>
          </Table>
        </div>
        <div className="mt-3 flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
          <span>
            {summary.selected} of {summary.total} selected
          </span>
          <span>•</span>
          <span>Visible total: ${summary.visibleTotal.toLocaleString()}</span>
          <span>•</span>
          <span>Selected total: ${summary.selectedTotal.toLocaleString()}</span>
        </div>
      </CardContent>
    </Card>
  );
}

function PaymentRow({
  isSelected,
  onToggle,
  payment,
}: {
  isSelected: boolean;
  onToggle: () => void;
  payment: Payment;
}) {
  return (
    <TableRow data-state={isSelected ? "selected" : undefined}>
      <TableCell>
        <Checkbox
          aria-label={`Select ${payment.customer}`}
          checked={isSelected}
          onCheckedChange={onToggle}
        />
      </TableCell>
      <TableCell className="font-medium">{payment.customer}</TableCell>
      <TableCell>{payment.email}</TableCell>
      <TableCell className="text-right">
        ${payment.amount.toLocaleString()}
      </TableCell>
      <TableCell>
        <StatusBadge status={payment.status} />
      </TableCell>
      <TableCell>
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button aria-label={`Payment actions for ${payment.customer}`} size="icon" variant="ghost">
              <Ellipsis className="size-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem>Copy payment ID</DropdownMenuItem>
            <DropdownMenuItem>View customer</DropdownMenuItem>
            <DropdownMenuItem>Download receipt</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </TableCell>
    </TableRow>
  );
}

function StatusBadge({ status }: { status: Payment["status"] }) {
  const statusClassName: Record<Payment["status"], string> = {
    Success: "border-emerald-300 bg-emerald-50 text-emerald-700",
    Processing: "border-blue-300 bg-blue-50 text-blue-700",
    Failed: "border-red-300 bg-red-50 text-red-700",
  };

  return (
    <Badge className={statusClassName[status]} variant="outline">
      {status}
    </Badge>
  );
}

function PaymentMethodCard() {
  const [method, setMethod] = React.useState("card");
  const [form, setForm] = React.useState({
    nameOnCard: "",
    cardNumber: "",
    expiry: "",
    cvc: "",
  });
  const [errors, setErrors] = React.useState<Record<string, string>>({});

  function updateField(field: keyof typeof form, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function submitPaymentMethod(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const result = validatePaymentMethod({
      method: method as "card" | "paypal" | "apple",
      ...form,
    });

    if (result.success) {
      setErrors({});
      toast.success("Payment method saved.");
      return;
    }

    const nextErrors = Object.fromEntries(
      Object.entries(result.error.flatten().fieldErrors)
        .filter(([, messages]) => messages?.[0])
        .map(([field, messages]) => [field, messages?.[0] ?? "Invalid value"]),
    );
    setErrors(nextErrors);
  }

  return (
    <Card className="min-h-[600px] gap-0 overflow-hidden">
      <CardHeader>
        <CardTitle>Payment Method</CardTitle>
        <CardDescription>
          Add a new payment method to your account.
        </CardDescription>
      </CardHeader>
      <CardContent>
        <form className="grid gap-4" onSubmit={submitPaymentMethod}>
          <Tabs onValueChange={setMethod} value={method}>
            <TabsList className="grid !h-[94px] w-full grid-cols-3 gap-2 bg-transparent p-0">
              <PaymentMethodTab icon={<CreditCard className="size-6" />} value="card">
                Card
              </PaymentMethodTab>
              <PaymentMethodTab icon={<WalletCards className="size-6" />} value="paypal">
                Paypal
              </PaymentMethodTab>
              <PaymentMethodTab icon={<CheckCircle2 className="size-6" />} value="apple">
                Apple
              </PaymentMethodTab>
            </TabsList>
          </Tabs>
          <div className="grid gap-2">
            <Label htmlFor="nameOnCard">Name on the card</Label>
            <Input
              id="nameOnCard"
              onChange={(event) => updateField("nameOnCard", event.target.value)}
              value={form.nameOnCard}
            />
            <FieldError message={errors.nameOnCard} />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="cardNumber">Card number</Label>
            <Input
              id="cardNumber"
              inputMode="numeric"
              onChange={(event) => updateField("cardNumber", event.target.value)}
              placeholder="4242 4242 4242 4242"
              value={form.cardNumber}
            />
            <FieldError message={errors.cardNumber} />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="grid gap-2">
              <Label htmlFor="expiry">Expires</Label>
              <Input
                id="expiry"
                onChange={(event) => updateField("expiry", event.target.value)}
                placeholder="MM/YY"
                value={form.expiry}
              />
              <FieldError message={errors.expiry} />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="cvc">CVC</Label>
              <Input
                id="cvc"
                inputMode="numeric"
                onChange={(event) => updateField("cvc", event.target.value)}
                value={form.cvc}
              />
              <FieldError message={errors.cvc} />
            </div>
          </div>
          <Button className="mt-2 h-9" type="submit">
            Continue
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}

function PaymentMethodTab({
  children,
  icon,
  value,
}: {
  children: React.ReactNode;
  icon: React.ReactNode;
  value: string;
}) {
  return (
    <TabsTrigger
      className="h-[94px] w-full min-w-0 flex-col gap-3 rounded-md border bg-background px-2 text-sm shadow-xs data-[state=active]:border-foreground data-[state=active]:shadow-none"
      value={value}
    >
      {icon}
      <span className="max-w-full truncate">{children}</span>
    </TabsTrigger>
  );
}

function FieldError({ message }: { message?: string }) {
  if (!message) {
    return null;
  }

  return <p className="text-xs text-destructive">{message}</p>;
}
