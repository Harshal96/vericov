"use client";

import * as React from "react";
import {
  Bell,
  CalendarDays,
  Check,
  Download,
  Moon,
  Palette,
  Search,
  SidebarIcon,
  Sun,
  UserCircle,
} from "lucide-react";
import { useTheme } from "next-themes";

import { filterCommandItems } from "@/lib/app-shell-state";
import { commandItemsFromNavigation, navigationGroups } from "@/lib/navigation";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from "@/components/ui/command";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Separator } from "@/components/ui/separator";
import { SidebarTrigger } from "@/components/ui/sidebar";

const commandItems = commandItemsFromNavigation(navigationGroups);

export function TopBar() {
  const [commandOpen, setCommandOpen] = React.useState(false);

  React.useEffect(() => {
    const down = (event: KeyboardEvent) => {
      if (event.key.toLowerCase() === "k" && (event.metaKey || event.ctrlKey)) {
        event.preventDefault();
        setCommandOpen((isOpen) => !isOpen);
      }
    };

    document.addEventListener("keydown", down);
    return () => document.removeEventListener("keydown", down);
  }, []);

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b bg-background/95 px-4 backdrop-blur supports-[backdrop-filter]:bg-background/80 sm:px-6">
      <SidebarTrigger
        aria-label="Toggle navigation"
        className="size-9 shrink-0 rounded-md"
      >
        <SidebarIcon />
      </SidebarTrigger>
      <Separator orientation="vertical" className="h-5" />
      <button
        className="hidden h-9 w-full max-w-sm cursor-pointer items-center gap-3 rounded-md border bg-background px-3 text-sm text-muted-foreground shadow-xs transition-colors hover:bg-accent md:flex"
        onClick={() => setCommandOpen(true)}
        type="button"
      >
        <Search className="size-4" />
        <span>Search...</span>
        <kbd className="ml-auto rounded bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground">
          ⌘ k
        </kbd>
      </button>
      <Button
        aria-label="Open command palette"
        className="md:hidden"
        onClick={() => setCommandOpen(true)}
        size="icon"
        variant="ghost"
      >
        <Search className="size-4" />
      </Button>
      <div className="ml-auto flex items-center gap-2">
        <Button asChild className="text-fuchsia-600" variant="ghost">
          <a href="/pricing">Get Pro</a>
        </Button>
        <NotificationsMenu />
        <ModeToggle />
        <AppearanceMenu />
        <Separator orientation="vertical" className="hidden h-5 sm:block" />
        <ProfileMenu />
      </div>
      <CommandPalette open={commandOpen} onOpenChange={setCommandOpen} />
    </header>
  );
}

function CommandPalette({
  open,
  onOpenChange,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const [query, setQuery] = React.useState("");
  const items = filterCommandItems(commandItems, query);
  const groupedItems = navigationGroups
    .map((group) => ({
      ...group,
      items: items.filter((item) => item.group === group.label),
    }))
    .filter((group) => group.items.length > 0);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="overflow-hidden p-0" showCloseButton={false}>
        <DialogTitle className="sr-only">Command Palette</DialogTitle>
        <DialogDescription className="sr-only">
          Search for a command to run...
        </DialogDescription>
        <Command>
          <CommandInput
            placeholder="Search for a command to run..."
            value={query}
            onValueChange={setQuery}
          />
          <CommandList>
            <CommandEmpty>No command found.</CommandEmpty>
            {groupedItems.map((group) => (
              <CommandGroup heading={group.label} key={group.label}>
                {group.items.map((item) => (
                  <CommandItem
                    key={`${item.group}-${item.url}`}
                    onSelect={() => onOpenChange(false)}
                  >
                    <Search className="size-4" />
                    <span>{item.title}</span>
                  </CommandItem>
                ))}
              </CommandGroup>
            ))}
          </CommandList>
        </Command>
      </DialogContent>
    </Dialog>
  );
}

function ModeToggle() {
  const { resolvedTheme, setTheme } = useTheme();

  return (
    <Button
      aria-label="Toggle theme"
      onClick={() => setTheme(resolvedTheme === "dark" ? "light" : "dark")}
      size="icon"
      variant="ghost"
    >
      <Moon className="size-4 rotate-0 scale-100 transition-all dark:-rotate-90 dark:scale-0" />
      <Sun className="absolute size-4 rotate-90 scale-0 transition-all dark:rotate-0 dark:scale-100" />
    </Button>
  );
}

function NotificationsMenu() {
  const notifications = [
    {
      title: "Your order is placed",
      body: "Amet minim mollit non deser unt ullamco est sit aliqua.",
      time: "2 days ago",
    },
    {
      title: "Congratulations Darlene 🎉",
      body: "Won the monthly best seller badge",
      time: "11 am",
    },
    {
      title: "Joaquina Weisenborn",
      body: "Requesting access permission",
      time: "12 pm",
      actions: true,
    },
    {
      title: "Brooklyn Simmons",
      body: "Added you to Top Secret Project group...",
      time: "1 pm",
    },
    {
      title: "Margot Henschke",
      body: "Cake pie jelly jelly beans. Marzipan lemon drops halvah cake.",
      time: "3 pm",
    },
    {
      title: "Sal Piggee",
      body: "Toffee caramels jelly-o tart gummi bears cake I love ice cream lollipop.",
      time: "4 pm",
    },
    {
      title: "Miguel Guelff",
      body: "Biscuit powder oat cake donut brownie ice cream I love souffle.",
      time: "7 pm",
    },
  ];

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          aria-label="Notifications"
          className="relative"
          size="icon"
          variant="ghost"
        >
          <Bell className="size-4" />
          <span className="absolute right-2 top-2 size-1.5 rounded-full bg-red-500" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="ms-4 w-80 p-0">
        <div className="flex items-center justify-between border-b px-4 py-3">
          <div className="font-medium">Notifications</div>
          <button className="text-xs text-muted-foreground" type="button">
            View all
          </button>
        </div>
        <div className="max-h-[352px] overflow-y-auto">
          {notifications.map((notification) => (
            <div className="border-b px-4 py-3 last:border-b-0" key={notification.title}>
              <div className="flex items-start gap-3">
                <span className="mt-1 size-2 rounded-full bg-primary" />
                <div className="min-w-0 flex-1">
                  <div className="truncate text-sm font-medium">
                    {notification.title}
                  </div>
                  <p className="mt-1 line-clamp-2 text-xs leading-5 text-muted-foreground">
                    {notification.body}
                  </p>
                  {notification.actions ? (
                    <div className="mt-2 flex gap-2">
                      <Button className="h-7 px-2 text-xs">Accept</Button>
                      <Button className="h-7 px-2 text-xs" variant="outline">
                        Decline
                      </Button>
                    </div>
                  ) : null}
                </div>
                <span className="whitespace-nowrap text-xs text-muted-foreground">
                  {notification.time}
                </span>
              </div>
            </div>
          ))}
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function AppearanceMenu() {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button aria-label="Customize appearance" size="icon" variant="ghost">
          <Palette className="size-4" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="me-4 w-80 p-4 shadow-xl lg:me-0">
        <div className="space-y-5">
          <SettingsRow label="Theme preset:" value="Default" />
          <SegmentedSetting label="Scale:" values={["XS", "LG"]} active="LG" />
          <SegmentedSetting
            label="Radius:"
            values={["SM", "MD", "LG", "XL"]}
            active="MD"
          />
          <SegmentedSetting
            label="Color mode:"
            values={["Light", "Dark"]}
            active="Light"
          />
          <SegmentedSetting
            label="Content layout"
            values={["Full", "Centered"]}
            active="Full"
          />
          <SegmentedSetting
            label="Sidebar mode:"
            values={["Default", "Icon"]}
            active="Default"
          />
          <Button className="w-full" variant="outline">
            Reset to Default
          </Button>
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

function SettingsRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <span className="text-sm text-muted-foreground">{label}</span>
      <span className="text-sm font-medium">{value}</span>
    </div>
  );
}

function SegmentedSetting({
  active,
  label,
  values,
}: {
  active: string;
  label: string;
  values: string[];
}) {
  return (
    <div className="grid gap-2">
      <div className="text-sm text-muted-foreground">{label}</div>
      <div className="grid grid-flow-col gap-2">
        {values.map((value) => (
          <button
            className={
              value === active
                ? "flex h-9 items-center justify-center gap-2 rounded-md border bg-primary px-3 text-sm text-primary-foreground"
                : "flex h-9 items-center justify-center gap-2 rounded-md border bg-background px-3 text-sm"
            }
            key={value}
            type="button"
          >
            {value === active ? <Check className="size-3.5" /> : null}
            {value}
          </button>
        ))}
      </div>
    </div>
  );
}

function ProfileMenu() {
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          aria-label="Open profile menu"
          className="cursor-pointer rounded-full outline-hidden ring-ring transition-shadow focus-visible:ring-2"
          type="button"
        >
          <Avatar className="size-8">
            <AvatarFallback className="bg-stone-900 text-xs text-white">
              TB
            </AvatarFallback>
          </Avatar>
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel>
          <div className="grid text-sm leading-tight">
            <span>Toby Belhome</span>
            <span className="text-xs font-normal text-muted-foreground">
              hello@tobybelhome.com
            </span>
          </div>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem>Upgrade to Pro</DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem>
          <UserCircle className="size-4" />
          Account
        </DropdownMenuItem>
        <DropdownMenuItem>
          <CalendarDays className="size-4" />
          Billing
        </DropdownMenuItem>
        <DropdownMenuItem>
          <Download className="size-4" />
          Notifications
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem>Log out</DropdownMenuItem>
        <DropdownMenuSeparator />
        <div className="grid gap-1 px-2 py-2 text-xs text-muted-foreground">
          <div className="flex items-center justify-between text-foreground">
            <span>Credits</span>
            <span>5 left</span>
          </div>
          <div>Daily credits used first</div>
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
