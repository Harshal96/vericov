"use client";

import * as React from "react";
import {
  Apple,
  BadgeDollarSign,
  CalendarDays,
  Check,
  ChevronLeft,
  ChevronRight,
  ChevronsUpDown,
  CreditCard,
  Download,
  Ellipsis,
  FileDown,
  Plus,
  SendHorizontal,
} from "lucide-react";
import { Line, LineChart, XAxis, YAxis } from "recharts";
import { toast } from "sonner";

import { appendChatMessage } from "@/lib/chat";
import { cn } from "@/lib/utils";
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
  Command,
  CommandGroup,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from "@/components/ui/popover";
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

const roleOptions = [
  { value: "Viewer", description: "Can view and comment." },
  { value: "Developer", description: "Can view, comment and edit." },
  { value: "Billing", description: "Can view, comment and manage billing." },
  { value: "Owner", description: "Admin-level access to all resources." },
] as const;

const datePresets = [
  "Today",
  "Yesterday",
  "This Week",
  "Last 7 Days",
  "Last 28 Days",
  "This Month",
  "Last Month",
  "This Year",
];

const calendarDays = [
  "31",
  "1",
  "2",
  "3",
  "4",
  "5",
  "6",
  "7",
  "8",
  "9",
  "10",
  "11",
  "12",
  "13",
  "14",
  "15",
  "16",
  "17",
  "18",
  "19",
  "20",
  "21",
  "22",
  "23",
  "24",
  "25",
  "26",
  "27",
  "28",
  "29",
  "30",
  "1",
  "2",
  "3",
  "4",
];

const monthOptions = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];

const yearOptions = [
  "2026",
  "2027",
  "2028",
  "2029",
  "2030",
  "2031",
  "2032",
  "2033",
  "2034",
  "2035",
];

export function DashboardPage() {
  return (
    <div className="grid w-full min-w-0 gap-4">
      <DashboardHeader />
      <section className="grid w-full min-w-0 grid-cols-1 gap-4 lg:grid-cols-3">
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
        <DateRangePopover />
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

function DateRangePopover() {
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button className="h-9 gap-2 px-3" variant="outline">
          <CalendarDays className="size-4" />
          <span className="hidden sm:inline">
            05 May 2026 - 01 Jun 2026
          </span>
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-auto p-4">
        <div className="grid grid-cols-[120px_1fr] gap-4">
          <div className="grid content-start gap-1 border-r pr-3">
            {datePresets.map((preset) => (
              <button
                className="rounded-md px-2 py-1.5 text-left text-sm transition-colors hover:bg-accent"
                key={preset}
                type="button"
              >
                {preset}
              </button>
            ))}
          </div>
          <div className="w-[244px]">
            <div className="mb-3 flex items-center justify-center gap-2 text-sm font-medium">
              June 2026
            </div>
            <div className="grid grid-cols-7 gap-1 text-center text-xs text-muted-foreground">
              {["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"].map((day) => (
                <div className="h-7 leading-7" key={day}>
                  {day}
                </div>
              ))}
            </div>
            <div className="grid grid-cols-7 gap-1 text-center text-sm">
              {calendarDays.map((day, index) => (
                <button
                  className={cn(
                    "h-8 rounded-md transition-colors hover:bg-accent",
                    index === 0 || index > 30
                      ? "text-muted-foreground"
                      : "text-foreground",
                  )}
                  key={`${day}-${index}`}
                  type="button"
                >
                  {day}
                </button>
              ))}
            </div>
          </div>
        </div>
      </PopoverContent>
    </Popover>
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
            <RoleCombobox defaultValue={member.role} />
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

function RoleCombobox({
  defaultValue,
}: {
  defaultValue: (typeof roleOptions)[number]["value"];
}) {
  const [open, setOpen] = React.useState(false);
  const [value, setValue] = React.useState(defaultValue);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          aria-expanded={open}
          className="h-9 min-w-[102px] justify-between px-3 font-normal data-[state=open]:bg-background"
          role="combobox"
          variant="outline"
        >
          {value}
          <ChevronsUpDown className="size-4 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-72 p-0">
        <Command>
          <CommandList>
            <CommandGroup>
              {roleOptions.map((role) => (
                <CommandItem
                  className="items-start gap-3 px-3 py-3"
                  key={role.value}
                  onSelect={() => {
                    setValue(role.value);
                    setOpen(false);
                  }}
                >
                  <Check
                    className={cn(
                      "mt-0.5 size-4",
                      value === role.value ? "opacity-100" : "opacity-0",
                    )}
                  />
                  <span className="grid gap-1">
                    <span className="font-medium">{role.value}</span>
                    <span className="text-xs text-muted-foreground">
                      {role.description}
                    </span>
                  </span>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
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
        <div className="mt-7 grid grid-cols-8 items-end gap-2">
          {subscriptionBars.map((bar) => (
            <div className="grid justify-items-center gap-2" key={bar.label}>
              <span className="text-xs">{bar.value}</span>
              <span
                className="w-8 rounded-sm bg-primary"
                style={{ height: `${Math.max(42, bar.value / 3.2)}px` }}
              />
            </div>
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
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button className="h-9 gap-2" variant="outline">
                <FileDown className="size-4" />
                Export
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem>Excel</DropdownMenuItem>
              <DropdownMenuItem>PDF</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
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
  const visiblePayments = filteredPayments.slice(0, 8);
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
      visiblePayments.forEach((payment) => {
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
    visiblePayments.every((payment) => selectedIds.has(payment.id));

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
      <CardContent className="pt-4">
        <div className="overflow-x-auto rounded-lg border">
          <Table className="min-w-[640px]">
            <TableHeader>
              <TableRow>
                <TableHead className="w-10">
                  <Checkbox
                    aria-label="Select all"
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
              {visiblePayments.map((payment) => (
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
        <div className="mt-4 flex items-center gap-3 text-sm text-muted-foreground">
          <span>
            {summary.selected} of {summary.total} row(s) selected.
          </span>
          <div className="ml-auto flex items-center gap-2">
            <Button aria-label="Previous page" size="icon-sm" variant="outline">
              <ChevronLeft className="size-4" />
            </Button>
            <Button aria-label="Next page" size="icon-sm" variant="outline">
              <ChevronRight className="size-4" />
            </Button>
          </div>
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
            <Button aria-label="Open menu" size="icon-sm" variant="ghost">
              <Ellipsis className="size-4" />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem>View details</DropdownMenuItem>
            <DropdownMenuItem>Download receipt</DropdownMenuItem>
            <DropdownMenuItem>Contact customer</DropdownMenuItem>
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
    Failed: "border-red-600 bg-red-600 text-white",
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
    city: "",
    cardNumber: "",
    month: "",
    year: "",
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
      <CardContent className="pt-4">
        <form className="grid gap-6" onSubmit={submitPaymentMethod}>
          <Tabs onValueChange={setMethod} value={method}>
            <TabsList className="grid !h-[94px] w-full grid-cols-3 gap-2 bg-transparent p-0">
              <PaymentMethodTab icon={<CreditCard className="size-6" />} value="card">
                Card
              </PaymentMethodTab>
              <PaymentMethodTab icon={<BadgeDollarSign className="size-6" />} value="paypal">
                Paypal
              </PaymentMethodTab>
              <PaymentMethodTab icon={<Apple className="size-6" />} value="apple">
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
            <Label htmlFor="city">City</Label>
            <Input
              id="city"
              onChange={(event) => updateField("city", event.target.value)}
              value={form.city}
            />
            <FieldError message={errors.city} />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="cardNumber">Card number</Label>
            <Input
              id="cardNumber"
              inputMode="numeric"
              onChange={(event) => updateField("cardNumber", event.target.value)}
              value={form.cardNumber}
            />
            <FieldError message={errors.cardNumber} />
          </div>
          <div className="grid grid-cols-3 gap-3">
            <div className="grid gap-2">
              <Label htmlFor="month">Expires</Label>
              <Select
                onValueChange={(value) => updateField("month", value)}
                value={form.month}
              >
                <SelectTrigger aria-label="Month" id="month" className="h-9 w-full">
                  <SelectValue placeholder="Month" />
                </SelectTrigger>
                <SelectContent>
                  {monthOptions.map((month) => (
                    <SelectItem key={month} value={month}>
                      {month}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FieldError message={errors.month} />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="year">Year</Label>
              <Select
                onValueChange={(value) => updateField("year", value)}
                value={form.year}
              >
                <SelectTrigger aria-label="Year" id="year" className="h-9 w-full">
                  <SelectValue placeholder="Year" />
                </SelectTrigger>
                <SelectContent>
                  {yearOptions.map((year) => (
                    <SelectItem key={year} value={year}>
                      {year}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FieldError message={errors.year} />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="cvc">CVC</Label>
              <Input
                id="cvc"
                inputMode="numeric"
                onChange={(event) => updateField("cvc", event.target.value)}
                placeholder="CVC"
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
