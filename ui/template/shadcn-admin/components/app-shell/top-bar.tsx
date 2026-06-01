"use client";

import * as React from "react";
import {
  Bell,
  CalendarDays,
  Download,
  Moon,
  Palette,
  Search,
  SidebarIcon,
  Sun,
  UserCircle,
} from "lucide-react";
import { useTheme } from "next-themes";
import { toast } from "sonner";

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
          ⌘ K
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
        <Button
          aria-label="Notifications"
          size="icon"
          variant="ghost"
          className="relative"
          onClick={() => toast("You are all caught up.")}
        >
          <Bell className="size-4" />
          <span className="absolute right-2 top-2 size-1.5 rounded-full bg-red-500" />
        </Button>
        <ModeToggle />
        <Button
          aria-label="Customize appearance"
          size="icon"
          variant="ghost"
          onClick={() => toast("Appearance options are ready for extension.")}
        >
          <Palette className="size-4" />
        </Button>
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

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="overflow-hidden p-0" showCloseButton={false}>
        <DialogTitle className="sr-only">Command Palette</DialogTitle>
        <DialogDescription className="sr-only">
          Search for a command to run...
        </DialogDescription>
        <Command>
          <CommandInput
            placeholder="Type a command or search..."
            value={query}
            onValueChange={setQuery}
          />
          <CommandList>
            <CommandEmpty>No command found.</CommandEmpty>
            <CommandGroup heading="Navigation">
              {items.map((item) => (
                <CommandItem
                  key={`${item.group}-${item.url}`}
                  onSelect={() => {
                    onOpenChange(false);
                    toast(`Opening ${item.title}`);
                  }}
                >
                  <Search className="size-4" />
                  <span>{item.title}</span>
                  <span className="ml-auto text-xs text-muted-foreground">
                    {item.group}
                  </span>
                </CommandItem>
              ))}
            </CommandGroup>
          </CommandList>
        </Command>
      </DialogContent>
    </Dialog>
  );
}

function ModeToggle() {
  const { setTheme } = useTheme();

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button aria-label="Toggle theme" size="icon" variant="ghost">
          <Sun className="size-4 rotate-0 scale-100 transition-all dark:-rotate-90 dark:scale-0" />
          <Moon className="absolute size-4 rotate-90 scale-0 transition-all dark:rotate-0 dark:scale-100" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onClick={() => setTheme("light")}>Light</DropdownMenuItem>
        <DropdownMenuItem onClick={() => setTheme("dark")}>Dark</DropdownMenuItem>
        <DropdownMenuItem onClick={() => setTheme("system")}>
          System
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
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
        <DropdownMenuLabel>Toby Belhome</DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem>
          <UserCircle className="size-4" />
          Profile
        </DropdownMenuItem>
        <DropdownMenuItem>
          <CalendarDays className="size-4" />
          Activity
        </DropdownMenuItem>
        <DropdownMenuItem>
          <Download className="size-4" />
          Downloads
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
